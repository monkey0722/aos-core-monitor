#include <jni.h>
#include <mntent.h>
#include <sys/statvfs.h>

#include <cerrno>
#include <cstdio>
#include <memory>
#include <string>
#include <string_view>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

/** Closes the mount table however the scope is left, exception included. */
using MountTable = std::unique_ptr<FILE, decltype([](FILE* table) { endmntent(table); })>;

/** Whether the mount was made read-only, which is the first option the kernel lists. */
bool IsReadOnly(const char* options) {
    if (options == nullptr) {
        return false;
    }
    const std::string_view text(options);
    return text == "ro" || text.starts_with("ro,");
}

void WriteUsage(JsonWriter* writer, const char* target) {
    struct statvfs64 usage = {};
    if (statvfs64(target, &usage) != 0) {
        // Denied or gone: /proc, /sys and most of /mnt answer this way for an app. The mount is
        // still listed — knowing it exists is part of the picture — with the reason in place of a
        // capacity.
        writer->Field("statvfs_ok", false);
        writer->Field("statvfs_unavailable", aoscm::DescribeFailure(errno));
        return;
    }

    // f_frsize is the unit f_blocks counts in; f_bsize is the preferred I/O size and is not
    // interchangeable with it, though on most Android filesystems the two happen to agree.
    const uint64_t unit = usage.f_frsize != 0 ? usage.f_frsize : usage.f_bsize;
    writer->Field("statvfs_ok", true);
    writer->Field("total_bytes", static_cast<uint64_t>(usage.f_blocks) * unit);
    writer->Field("free_bytes", static_cast<uint64_t>(usage.f_bfree) * unit);
    writer->Field("available_bytes", static_cast<uint64_t>(usage.f_bavail) * unit);

    // A filesystem with no fixed inode table — erofs and f2fs among them — answers with every bit
    // set rather than with a count. Published as a number it reaches the screen as
    // "18446744073709551615 free", so it is left out instead: the count is genuinely unknown.
    const auto inodes_total = static_cast<uint64_t>(usage.f_files);
    const auto inodes_free = static_cast<uint64_t>(usage.f_ffree);
    if (inodes_total != 0 && inodes_total != UINT64_MAX && inodes_free <= inodes_total) {
        writer->Field("inodes_total", inodes_total);
        writer->Field("inodes_free", inodes_free);
    }
}

}  // namespace

/**
 * Every filesystem mounted in this process's mount namespace, with the space each has left.
 *
 * `/proc/self/mounts` describes the caller's own namespace, so it stays readable where the
 * system-wide /proc files this app also reads do not. Android gives each app a partly private
 * namespace, so this is the view the app itself sees rather than the device's.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeStorageInspector_getMountsNative(JNIEnv* env,
                                                                               jobject /* this */) {
    return aoscm::ReturnJson(env, [] {
        JsonWriter writer;
        writer.BeginObject();
        writer.Key("mounts").BeginArray();

        if (const MountTable mounts{setmntent("/proc/self/mounts", "r")}) {
            struct mntent entry = {};
            char buffer[4096];
            // The reentrant form: the plain getmntent() hands back a pointer to shared storage,
            // which is a hazard worth avoiding in a library that any thread may call.
            while (getmntent_r(mounts.get(), &entry, buffer, sizeof(buffer)) != nullptr) {
                writer.BeginObject();
                writer.Field("source", entry.mnt_fsname);
                writer.Field("target", entry.mnt_dir);
                writer.Field("fs_type", entry.mnt_type);
                writer.Field("options", entry.mnt_opts);
                writer.Field("readonly", IsReadOnly(entry.mnt_opts));
                WriteUsage(&writer, entry.mnt_dir);
                writer.EndObject();
            }
        }

        writer.EndArray();
        writer.EndObject();
        return writer.Take();
    });
}
