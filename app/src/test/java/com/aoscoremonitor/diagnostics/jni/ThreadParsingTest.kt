package com.aoscoremonitor.diagnostics.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadParsingTest {

    @Test
    fun readsAThread() {
        val snapshot = parseThreads(
            """
            {"clock_ticks":100,"cpu_count":8,"affinity":"0-7","affinity_count":8,
             "threads":[{"tid":4821,"name":"scoremonitor","state":"R","utime_ticks":120,
                         "stime_ticks":31,"priority":20,"nice":0,"last_cpu":3,"rt_priority":0,
                         "policy":"other","affinity":"0-7","affinity_count":8}]}
            """.trimIndent()
        )

        val thread = snapshot.threads.single()
        assertEquals(4821, thread.tid)
        assertEquals("scoremonitor", thread.name)
        assertEquals(ThreadState.Running, thread.state)
        assertEquals(SchedulerPolicy.Other, thread.policy)
        assertEquals(151L, thread.cpuTicks)
        assertEquals("0-7", snapshot.processAffinity)
    }

    @Test
    fun ticksBecomeMillisecondsAtTheClockRateTheKernelReported() {
        // The conversion is the reason clock_ticks crosses the boundary at all. At USER_HZ 100 a
        // tick is 10ms; a kernel built with 250 would make the same count 4ms and the same
        // arithmetic wrong.
        val snapshot = parseThreads(
            """{"clock_ticks":250,"threads":[{"tid":1,"utime_ticks":100,"stime_ticks":25}]}"""
        )

        assertEquals(500L, snapshot.cpuMillis(snapshot.threads.single()))
    }

    @Test
    fun aClockRateOfZeroYieldsNoTimeRatherThanADivideByZero() {
        val snapshot = parseThreads("""{"threads":[{"tid":1,"utime_ticks":100,"stime_ticks":25}]}""")

        assertEquals(0L, snapshot.cpuMillis(snapshot.threads.single()))
    }

    @Test
    fun busiestThreadComesFirst() {
        val snapshot = parseThreads(
            """
            {"clock_ticks":100,"threads":[
              {"tid":1,"name":"idle","utime_ticks":1,"stime_ticks":0},
              {"tid":2,"name":"busy","utime_ticks":900,"stime_ticks":100},
              {"tid":3,"name":"middling","utime_ticks":40,"stime_ticks":10}]}
            """.trimIndent()
        )

        assertEquals(listOf("busy", "middling", "idle"), snapshot.threads.map { it.name })
    }

    @Test
    fun everyStateLetterTheKernelUsesIsNamed() {
        val states = listOf("R", "S", "D", "T", "t", "Z", "I", "X")
            .map { letter -> ThreadState.of(letter) }

        assertEquals(
            listOf(
                ThreadState.Running,
                ThreadState.Sleeping,
                ThreadState.DiskSleep,
                ThreadState.Stopped,
                ThreadState.TracingStop,
                ThreadState.Zombie,
                ThreadState.Idle,
                // X is a state /proc can report and this app does not name, which is Unknown rather
                // than a crash or a blank.
                ThreadState.Unknown
            ),
            states
        )
    }

    @Test
    fun realTimePoliciesAreMarkedAndOrdinaryOnesAreNot() {
        assertTrue(SchedulerPolicy.Fifo.isRealTime)
        assertTrue(SchedulerPolicy.RoundRobin.isRealTime)
        assertTrue(SchedulerPolicy.Deadline.isRealTime)
        assertEquals(false, SchedulerPolicy.Other.isRealTime)
        assertEquals(false, SchedulerPolicy.Idle.isRealTime)
    }

    @Test
    fun aThreadWhosePolicyWasRefusedSaysWhy() {
        val snapshot = parseThreads(
            """{"threads":[{"tid":7,"policy_unavailable":"denied"}]}"""
        )

        val thread = snapshot.threads.single()
        assertEquals(SchedulerPolicy.Unknown, thread.policy)
        assertEquals(Unavailable.Denied, thread.policyUnavailable)
    }

    @Test
    fun missingFieldsStayMissingRatherThanBecomingZero() {
        // A nice of 0 and no nice at all are different readings: the first says the thread runs at
        // the default priority, the second that /proc did not say.
        val snapshot = parseThreads("""{"threads":[{"tid":7}]}""")

        val thread = snapshot.threads.single()
        assertNull(thread.nice)
        assertNull(thread.priority)
        assertNull(thread.lastCpu)
        assertNull(thread.affinity)
    }

    @Test
    fun anEmptyDocumentIsAnEmptySnapshot() {
        val snapshot = parseThreads("{}")

        assertTrue(snapshot.threads.isEmpty())
        assertNull(snapshot.processAffinity)
    }
}
