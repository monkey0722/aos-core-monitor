package com.aoscoremonitor.diagnostics.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A uid or gid in its three forms. Only [effective] decides what a syscall is allowed to do. */
data class CredentialIds(val real: Int, val effective: Int, val saved: Int) {
    /** Whether the process could switch back to another identity, which for an app it cannot. */
    val allTheSame: Boolean get() = real == effective && effective == saved
}

/**
 * One capability set.
 *
 * [hex] is the mask the kernel printed and [names] the bits it stands for. Both are kept: the names
 * are what the set means, and the mask is what was read — including a bit this build of the app has
 * no name for, which arrives as `CAP_41` rather than being dropped.
 */
data class CapabilitySet(val hex: String, val names: List<String> = emptyList()) {
    val isEmpty: Boolean get() = names.isEmpty()
}

/** How much of the kernel's syscall surface is filtered away. */
enum class SeccompMode {
    /** No filter. For an app on a stock build, this is not what is expected. */
    Disabled,

    /** `SECCOMP_MODE_STRICT`: read, write, exit and sigreturn, and nothing else. */
    Strict,

    /** `SECCOMP_MODE_FILTER`: a BPF program decides, which is what zygote installs. */
    Filter,
    Unknown;

    internal companion object {
        fun of(value: Int?): SeccompMode = when (value) {
            0 -> Disabled
            1 -> Strict
            2 -> Filter
            else -> Unknown
        }
    }
}

/**
 * Who this process is to the kernel, and what the kernel will let it do.
 *
 * Every field is nullable or defaulted because each comes from a separate syscall or status line,
 * and a kernel that does not report one — `Umask` predates no kernel Android ships, but `CapAmb`
 * postdates several — should cost that one line rather than the screen.
 */
data class ProcessCredentials(
    val pid: Int = 0,
    val parentPid: Int = 0,
    val processGroup: Int = 0,
    val session: Int? = null,
    val user: CredentialIds? = null,
    val group: CredentialIds? = null,
    val supplementaryGroups: List<Int> = emptyList(),
    val groupsUnavailable: Unavailable? = null,
    val capabilities: Map<String, CapabilitySet> = emptyMap(),
    val noNewPrivs: Boolean? = null,
    val dumpable: Boolean? = null,
    val seccomp: SeccompMode = SeccompMode.Unknown,
    val seccompFilters: Int? = null,
    val umask: String? = null,
    val selinuxContext: String? = null,
    val selinuxUnavailable: Unavailable? = null
) {
    /** The multi-user id Android packed into the uid: 0 on a single-user device. */
    val androidUserId: Int? get() = user?.let { it.effective / PER_USER_RANGE }

    /** The per-user app id, which is what `ro.` properties and package records call the app. */
    val appId: Int? get() = user?.let { it.effective % PER_USER_RANGE }

    /** Whether the effective uid is an ordinary installed app rather than a platform identity. */
    val isAppUid: Boolean get() = appId?.let { it in APP_ID_RANGE } == true

    /**
     * The SELinux type, pulled out of `user:role:type:level`.
     *
     * The type is the part that says what the process may touch — `untrusted_app` against
     * `platform_app` is the whole difference — so it is worth stating on its own rather than
     * leaving it in the middle of a context string that runs to a hundred characters of categories.
     */
    val selinuxType: String? get() = selinuxContext?.split(':')?.getOrNull(SELINUX_TYPE_FIELD)

    private companion object {
        /** Android's `PER_USER_RANGE`: uid = userId * 100000 + appId. */
        const val PER_USER_RANGE = 100_000

        /** `AID_APP_START`..`AID_APP_END` from AOSP's android_filesystem_config.h. */
        val APP_ID_RANGE = 10_000..19_999
        const val SELINUX_TYPE_FIELD = 2
    }
}

/**
 * The name AOSP gives a supplementary group, where it has one.
 *
 * Only the ones an ordinary app is actually granted are listed; the full table runs to hundreds of
 * platform identities an app will never hold, and a number with no name is shown as a number. The
 * source is `android_filesystem_config.h`, and the ranges below it are from the same file.
 */
fun androidGroupName(gid: Int): String? = when (gid) {
    1015 -> "sdcard_rw"
    1023 -> "media_rw"
    1028 -> "sdcard_r"
    1077 -> "ext_data_rw"
    1078 -> "ext_obb_rw"
    3001 -> "net_bt_admin"
    3002 -> "net_bt"
    3003 -> "inet"
    3004 -> "net_raw"
    3005 -> "net_admin"
    3006 -> "net_bw_stats"
    3007 -> "net_bw_acct"
    3009 -> "readproc"
    3011 -> "uhid"
    9997 -> "everybody"
    9998 -> "misc"
    else -> when (gid) {
        in 20_000..29_999 -> "cache"
        in 40_000..49_999 -> "shared_gid"
        in 50_000..59_999 -> "shared_gid"
        in 99_000..99_999 -> "isolated"
        else -> null
    }
}

/**
 * Reads this process's credentials through JNI.
 *
 * `Process.myUid()` returns the effective uid and stops there. The real and saved ids, the
 * supplementary groups, the five capability sets, the seccomp mode and the no_new_privs bit have no
 * counterpart in the Java API — and the capability sets are not even a syscall away, being printed
 * by the kernel in `/proc/self/status` and nowhere else.
 */
class NativeCredentialsInspector {

    external fun getCredentialsNative(): String

    suspend fun read(): ProcessCredentials? = withContext(Dispatchers.IO) {
        if (!NativeLibrary.isAvailable) return@withContext null
        try {
            parseCredentials(getCredentialsNative())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing process credentials", e)
            null
        }
    }

    private companion object {
        const val TAG = "NativeCredentialsInspector"
    }
}

/** The order the screen shows the capability sets in, which is the order they constrain each other. */
internal val CapabilitySetKeys = listOf("effective", "permitted", "inheritable", "bounding", "ambient")

/** Kept apart from the JNI call so it can be exercised without a device. */
internal fun parseCredentials(json: String): ProcessCredentials {
    val root = JSONObject(json)

    val capabilities = root.optJSONObject("capabilities")?.let { sets ->
        CapabilitySetKeys.mapNotNull { key ->
            val set = sets.optJSONObject(key) ?: return@mapNotNull null
            val names = set.optJSONArray("names")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
            }.orEmpty()
            key to CapabilitySet(hex = set.optString("hex"), names = names)
        }.toMap()
    }.orEmpty()

    val groups = root.optJSONArray("groups")?.let { array ->
        (0 until array.length()).map { array.optInt(it) }
    }.orEmpty()

    return ProcessCredentials(
        pid = root.optInt("pid"),
        parentPid = root.optInt("ppid"),
        processGroup = root.optInt("pgid"),
        session = root.intOrNull("sid"),
        user = root.optJSONObject("uid")?.toCredentialIds(),
        group = root.optJSONObject("gid")?.toCredentialIds(),
        // Sorted so that the platform identities an app is granted — 3003 inet, 9997 everybody —
        // read in a fixed order rather than in whatever order getgroups filled the array.
        supplementaryGroups = groups.sorted(),
        groupsUnavailable = root.unavailable("groups_unavailable"),
        capabilities = capabilities,
        noNewPrivs = root.booleanOrNull("no_new_privs"),
        dumpable = root.booleanOrNull("dumpable"),
        seccomp = SeccompMode.of(root.intOrNull("seccomp")),
        seccompFilters = root.intOrNull("seccomp_filters"),
        umask = root.stringOrNull("umask"),
        selinuxContext = root.stringOrNull("selinux_context"),
        selinuxUnavailable = root.unavailable("selinux_context_unavailable")
    )
}

private fun JSONObject.toCredentialIds() = CredentialIds(
    real = optInt("real"),
    effective = optInt("effective"),
    saved = optInt("saved")
)
