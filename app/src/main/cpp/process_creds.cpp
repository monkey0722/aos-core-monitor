#include <jni.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;
using aoscm::Reading;
using aoscm::StatusLines;

/**
 * Capability names by bit, as the kernel numbers them.
 *
 * Written out rather than taken from <linux/capability.h>, which names the constants but offers no
 * way to turn a bit back into its name. These are kernel identifiers, not prose, so they cross to
 * Kotlin as-is and are shown verbatim — putting forty of them in strings.xml would ask a translator
 * to translate CAP_SYS_ADMIN.
 */
constexpr std::array<const char*, 41> kCapabilityNames = {
        "CAP_CHOWN",
        "CAP_DAC_OVERRIDE",
        "CAP_DAC_READ_SEARCH",
        "CAP_FOWNER",
        "CAP_FSETID",
        "CAP_KILL",
        "CAP_SETGID",
        "CAP_SETUID",
        "CAP_SETPCAP",
        "CAP_LINUX_IMMUTABLE",
        "CAP_NET_BIND_SERVICE",
        "CAP_NET_BROADCAST",
        "CAP_NET_ADMIN",
        "CAP_NET_RAW",
        "CAP_IPC_LOCK",
        "CAP_IPC_OWNER",
        "CAP_SYS_MODULE",
        "CAP_SYS_RAWIO",
        "CAP_SYS_CHROOT",
        "CAP_SYS_PTRACE",
        "CAP_SYS_PACCT",
        "CAP_SYS_ADMIN",
        "CAP_SYS_BOOT",
        "CAP_SYS_NICE",
        "CAP_SYS_RESOURCE",
        "CAP_SYS_TIME",
        "CAP_SYS_TTY_CONFIG",
        "CAP_MKNOD",
        "CAP_LEASE",
        "CAP_AUDIT_WRITE",
        "CAP_AUDIT_CONTROL",
        "CAP_SETFCAP",
        "CAP_MAC_OVERRIDE",
        "CAP_MAC_ADMIN",
        "CAP_SYSLOG",
        "CAP_WAKE_ALARM",
        "CAP_BLOCK_SUSPEND",
        "CAP_AUDIT_READ",
        "CAP_PERFMON",
        "CAP_BPF",
        "CAP_CHECKPOINT_RESTORE",
};

/** The five capability sets, as the JSON key each goes out under and the status line it comes from.
 */
constexpr std::array<std::pair<const char*, const char*>, 5> kCapabilitySets = {{
        {"effective", "CapEff"},
        {"permitted", "CapPrm"},
        {"inheritable", "CapInh"},
        {"bounding", "CapBnd"},
        {"ambient", "CapAmb"},
}};

/**
 * One capability set, as the mask the kernel printed and the names the bits stand for.
 *
 * Both, rather than either alone: the names are what the set means, and the mask is what was
 * actually read — a bit this build has no name for still shows up in it.
 */
void WriteCapabilitySet(JsonWriter* writer, const StatusLines& status, const char* key,
                        const char* status_key) {
    const std::string* text = aoscm::FindStatus(status, status_key);
    if (text == nullptr) {
        return;
    }

    writer->Key(key).BeginObject();
    writer->Field("hex", *text);
    if (const std::optional<uint64_t> mask = aoscm::ParseNumber<uint64_t, 16>(*text)) {
        writer->Key("names").BeginArray();
        for (unsigned bit = 0; bit < 64; ++bit) {
            if ((*mask & (uint64_t{1} << bit)) == 0) {
                continue;
            }
            if (bit < kCapabilityNames.size()) {
                writer->Value(kCapabilityNames[bit]);
            } else {
                // A capability this build predates. Named by its bit so the set still adds up,
                // rather than dropped, which would show a non-empty mask alongside a shorter list
                // than it holds.
                writer->Value("CAP_" + std::to_string(bit));
            }
        }
        writer->EndArray();
    }
    writer->EndObject();
}

/** Writes a `/proc/self/status` line that holds a plain decimal count. */
void WriteStatusNumber(JsonWriter* writer, const StatusLines& status, const char* key,
                       const char* status_key) {
    const std::string* text = aoscm::FindStatus(status, status_key);
    if (text == nullptr) {
        return;
    }
    if (const std::optional<uint64_t> value = aoscm::ParseNumber<uint64_t>(*text)) {
        writer->Field(key, *value);
    }
}

/**
 * An identity in the three forms the kernel keeps it in.
 *
 * uid_t and gid_t are the same width and the two triples are written the same way, so they share
 * this rather than the file carrying the same six lines twice.
 */
void WriteIdTriple(JsonWriter* writer, const char* key, unsigned int real, unsigned int effective,
                   unsigned int saved) {
    writer->Key(key).BeginObject();
    writer->Field("real", static_cast<uint64_t>(real));
    writer->Field("effective", static_cast<uint64_t>(effective));
    writer->Field("saved", static_cast<uint64_t>(saved));
    writer->EndObject();
}

void WriteUserIds(JsonWriter* writer) {
    uid_t real = 0;
    uid_t effective = 0;
    uid_t saved = 0;
    if (getresuid(&real, &effective, &saved) == 0) {
        WriteIdTriple(writer, "uid", real, effective, saved);
    }
}

void WriteGroupIds(JsonWriter* writer) {
    gid_t real = 0;
    gid_t effective = 0;
    gid_t saved = 0;
    if (getresgid(&real, &effective, &saved) == 0) {
        WriteIdTriple(writer, "gid", real, effective, saved);
    }
}

/**
 * The supplementary groups, which on Android are how an app is granted whole capabilities of the
 * platform — AID_INET for a socket, AID_EXT_STORAGE for the shared volume.
 */
void WriteSupplementaryGroups(JsonWriter* writer) {
    const int count = getgroups(0, nullptr);
    if (count < 0) {
        writer->Field("groups_unavailable", aoscm::DescribeFailure(errno));
        return;
    }

    std::vector<gid_t> groups(static_cast<size_t>(count));
    // Asked for twice on purpose: setgroups can run between the two calls, and getgroups reports
    // that as EINVAL rather than filling a short buffer.
    const int filled = getgroups(count, groups.data());
    if (filled < 0) {
        writer->Field("groups_unavailable", aoscm::DescribeFailure(errno));
        return;
    }

    writer->Key("groups").BeginArray();
    for (int i = 0; i < filled; ++i) {
        writer->Value(static_cast<uint64_t>(groups[static_cast<size_t>(i)]));
    }
    writer->EndArray();
}

/** A switch `prctl` answers with 0 or 1, or the reason it would not answer. */
void WritePrctlFlag(JsonWriter* writer, const char* key, const char* unavailable_key, int option) {
    const int value = prctl(option, 0, 0, 0, 0);
    if (value < 0) {
        writer->Field(unavailable_key, aoscm::DescribeFailure(errno));
        return;
    }
    writer->Field(key, value != 0);
}

void WriteSelinuxContext(JsonWriter* writer) {
    const Reading<std::string> context = aoscm::ReadTrimmedLine("/proc/self/attr/current");
    if (!context.has_value()) {
        writer->Field("selinux_context_unavailable", aoscm::DescribeFailure(context.error()));
        return;
    }
    // The kernel terminates what it writes here, and a NUL is not whitespace, so it survives the
    // trim and would otherwise cross to Kotlin escaped as \u0000 on the end of the domain name.
    const std::string_view text(*context);
    writer->Field("selinux_context", text.substr(0, text.find('\0')));
}

}  // namespace

/**
 * Who this process is to the kernel, and what the kernel will let it do.
 *
 * All of it is about this process rather than about the device, which is what separates it from the
 * security screen: `getenforce` says whether SELinux is enforcing anywhere, and
 * `/proc/self/attr/current` says which domain confines *this* app.
 *
 * None of it can be read from Java. `Process.myUid()` returns the effective uid and stops there:
 * the real and saved ids, the supplementary groups, the five capability sets, the seccomp mode and
 * the no_new_privs bit have no counterpart in the Java API, and the capability sets are not even a
 * syscall away — they are printed by the kernel in `/proc/self/status` and nowhere else.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeCredentialsInspector_getCredentialsNative(
        JNIEnv* env, jobject /* this */) {
    return aoscm::ReturnJson(env, [] {
        const StatusLines status = aoscm::ReadProcStatus();

        JsonWriter writer;
        writer.BeginObject();

        writer.Field("pid", static_cast<uint64_t>(getpid()));
        writer.Field("ppid", static_cast<uint64_t>(getppid()));
        writer.Field("pgid", static_cast<uint64_t>(getpgrp()));
        if (const pid_t session = getsid(0); session >= 0) {
            writer.Field("sid", static_cast<uint64_t>(session));
        }

        WriteUserIds(&writer);
        WriteGroupIds(&writer);
        WriteSupplementaryGroups(&writer);

        writer.Key("capabilities").BeginObject();
        for (const auto& [key, status_key] : kCapabilitySets) {
            WriteCapabilitySet(&writer, status, key, status_key);
        }
        writer.EndObject();

        WritePrctlFlag(&writer, "no_new_privs", "no_new_privs_unavailable", PR_GET_NO_NEW_PRIVS);
        WritePrctlFlag(&writer, "dumpable", "dumpable_unavailable", PR_GET_DUMPABLE);

        WriteStatusNumber(&writer, status, "seccomp", "Seccomp");
        WriteStatusNumber(&writer, status, "seccomp_filters", "Seccomp_filters");

        // As a string, not a number: a umask is octal, and 0077 published as 77 decimal is a
        // different mask that happens to print plausibly.
        if (const std::string* umask = aoscm::FindStatus(status, "Umask")) {
            writer.Field("umask", *umask);
        }

        WriteSelinuxContext(&writer);

        writer.EndObject();
        return writer.Take();
    });
}
