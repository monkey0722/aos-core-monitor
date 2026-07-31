package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * What a thread is doing, as the single letter /proc reports it.
 *
 * Kept as an enum rather than the letter itself so that the wording lives in strings.xml. A
 * collector that returns "Sleeping" reads fine until the app is translated, at which point the
 * screen is comparing against an English literal — a trap this codebase has already fallen into
 * once, with the HAL screen's "Running".
 */
enum class ThreadState {
    Running,
    Sleeping,

    /** Uninterruptible sleep, which on Android is nearly always a blocking read. */
    DiskSleep,
    Stopped,
    TracingStop,
    Zombie,
    Idle,
    Unknown;

    internal companion object {
        fun of(letter: String?): ThreadState = when (letter) {
            "R" -> Running
            "S" -> Sleeping
            "D" -> DiskSleep
            "T" -> Stopped
            "t" -> TracingStop
            "Z" -> Zombie
            "I" -> Idle
            else -> Unknown
        }
    }
}

/** Which scheduler the kernel is running the thread under. */
enum class SchedulerPolicy {
    Other,
    Fifo,
    RoundRobin,
    Batch,
    Idle,
    Deadline,
    Unknown;

    /** Whether the thread is scheduled ahead of ordinary work, which is worth pointing out. */
    val isRealTime: Boolean get() = this == Fifo || this == RoundRobin || this == Deadline

    internal companion object {
        fun of(name: String?): SchedulerPolicy = when (name) {
            "other" -> Other
            "fifo" -> Fifo
            "rr" -> RoundRobin
            "batch" -> Batch
            "idle" -> Idle
            "deadline" -> Deadline
            else -> Unknown
        }
    }
}

/**
 * One thread of this process.
 *
 * [cpuTicks] is cumulative since the thread started, not a rate: a rate needs two readings, and the
 * total is the honest thing to show from one. [affinity] is a cpulist — "0-3,6" — in the form the
 * kernel uses in /proc/self/status.
 */
data class ThreadInfo(
    val tid: Int,
    val name: String,
    val state: ThreadState,
    val userTicks: Long,
    val systemTicks: Long,
    val priority: Int? = null,
    val nice: Int? = null,
    val policy: SchedulerPolicy = SchedulerPolicy.Unknown,
    val policyUnavailable: Unavailable? = null,
    val realTimePriority: Int? = null,
    val lastCpu: Int? = null,
    val affinity: String? = null,
    val affinityCount: Int? = null
) {
    val cpuTicks: Long get() = userTicks + systemTicks
}

/**
 * The process's threads, and the scheduling ceiling they all sit under.
 *
 * [clockTicks] is USER_HZ, which is what turns the tick counts into milliseconds. It is read rather
 * than assumed because it is a kernel build option; Android has always set it to 100, but a value
 * this app derives every other figure from is not one to hard-code.
 */
data class ThreadSnapshot(
    val threads: List<ThreadInfo> = emptyList(),
    val clockTicks: Long = 0,
    val cpuCount: Int = 0,
    val processAffinity: String? = null,
    val processAffinityCount: Int? = null
) {
    /** Milliseconds of CPU the thread has used since it started. */
    fun cpuMillis(thread: ThreadInfo): Long = if (clockTicks > 0) thread.cpuTicks * MILLIS_PER_SECOND / clockTicks else 0

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * Reads this process's threads through JNI.
 *
 * The thread list itself could be read from Java, but nothing it holds could: `Thread.getPriority`
 * is a hint to the runtime rather than the nice value the kernel scheduled with, and neither the
 * scheduling policy nor the CPU affinity mask is exposed at all. Both come from `sched_*` calls
 * that take a thread id.
 */
class NativeThreadInspector {

    external fun getThreadsNative(): String

    suspend fun read(): ThreadSnapshot? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseThreads(getThreadsNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing thread list", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeThreadInspector"
    }
}

/** Kept apart from the JNI call so it can be exercised without a device. */
internal fun parseThreads(json: String): ThreadSnapshot {
    val root = JSONObject(json)

    val threads = root.optJSONArray("threads")?.mapObjects { thread ->
        ThreadInfo(
            tid = thread.optInt("tid"),
            name = thread.stringOrNull("name").orEmpty(),
            state = ThreadState.of(thread.stringOrNull("state")),
            userTicks = thread.optLong("utime_ticks"),
            systemTicks = thread.optLong("stime_ticks"),
            priority = thread.intOrNull("priority"),
            nice = thread.intOrNull("nice"),
            policy = SchedulerPolicy.of(thread.stringOrNull("policy")),
            policyUnavailable = thread.unavailable("policy_unavailable"),
            realTimePriority = thread.intOrNull("rt_priority"),
            lastCpu = thread.intOrNull("last_cpu"),
            affinity = thread.stringOrNull("affinity"),
            affinityCount = thread.intOrNull("affinity_count")
        )
    }.orEmpty()

    return ThreadSnapshot(
        // Busiest first: a process has one thread worth looking at and twenty that are parked, and
        // the kernel's own order is by thread id, which says nothing about which is which.
        threads = threads.sortedByDescending { it.cpuTicks },
        clockTicks = root.optLong("clock_ticks"),
        cpuCount = root.optInt("cpu_count"),
        processAffinity = root.stringOrNull("affinity"),
        processAffinityCount = root.intOrNull("affinity_count")
    )
}
