#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <climits>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

/** Closes the directory however the scope is left, exception included. */
using FdDir = std::unique_ptr<DIR, decltype([](DIR* dir) { closedir(dir); })>;

/**
 * The status flags worth naming, and the token each goes out under.
 *
 * Tested by masked equality rather than by a single bit, because O_SYNC is two bits on Linux — it
 * carries O_DSYNC — and `flags & O_SYNC` is therefore true for a descriptor that only asked for
 * data synchronisation.
 */
constexpr std::array<std::pair<int, const char*>, 6> kStatusFlags = {{
        {O_APPEND, "append"},
        {O_NONBLOCK, "nonblock"},
        {O_DIRECT, "direct"},
        {O_SYNC, "sync"},
        {O_NOATIME, "noatime"},
        {O_PATH, "path"},
}};

/**
 * What the descriptor is open on, as the mode bits report it.
 *
 * Takes the bits as a `uint32_t` rather than as a `mode_t`, because `stat64::st_mode` is not one:
 * on the 32-bit ABIs this ships for it is an `unsigned int` while `mode_t` is an `unsigned short`,
 * so a `mode_t` parameter narrows the very field it exists to read.
 */
const char* TypeName(uint32_t mode) {
    switch (mode & S_IFMT) {
        case S_IFREG:
            return "regular";
        case S_IFDIR:
            return "directory";
        case S_IFCHR:
            return "character";
        case S_IFBLK:
            return "block";
        case S_IFIFO:
            return "fifo";
        case S_IFSOCK:
            return "socket";
        case S_IFLNK:
            return "symlink";
        default:
            return "unknown";
    }
}

const char* AccessName(int status_flags) {
    switch (status_flags & O_ACCMODE) {
        case O_RDONLY:
            return "read";
        case O_WRONLY:
            return "write";
        case O_RDWR:
            return "readwrite";
        default:
            return "unknown";
    }
}

/**
 * Every descriptor number the process holds.
 *
 * Collected in full, and the directory closed, before anything is asked about any of them: the
 * directory handle is itself an open descriptor and appears in its own listing. Closing it first
 * means the second pass finds nothing at that number and drops it under the same rule that drops a
 * descriptor another thread closed mid-read, rather than needing a special case for it.
 *
 * The second pass therefore uses only syscalls that take a descriptor — readlink on the /proc
 * entry, fstat, fcntl, lseek — and opens no file of its own, so no number is taken and reported
 * back as though the process had been holding it all along.
 */
std::vector<int> ListDescriptorNumbers() {
    std::vector<int> numbers;
    const FdDir descriptors{opendir("/proc/self/fd")};
    if (!descriptors) {
        return numbers;
    }
    while (const dirent* entry = readdir(descriptors.get())) {
        if (const std::optional<int> fd = aoscm::ParseNumber<int>(entry->d_name)) {
            numbers.push_back(*fd);
        }
        // Anything else is "." or "..", which readdir reports alongside the numbers.
    }
    return numbers;
}

/**
 * One descriptor, or nothing at all when the number no longer names one.
 *
 * A descriptor that closes between the two passes is left out entirely rather than listed as a
 * blank row. One that closes partway through this function keeps whatever was read before it went;
 * that race is inherent to reading a live process and costs a field, not a wrong reading.
 */
void WriteDescriptor(JsonWriter* writer, int fd) {
    char link[PATH_MAX];
    const std::string path = "/proc/self/fd/" + std::to_string(fd);
    const ssize_t length = readlink(path.c_str(), link, sizeof(link));
    const int link_error = length < 0 ? errno : 0;
    if (link_error == ENOENT || link_error == EBADF) {
        return;
    }

    writer->BeginObject();
    writer->Field("fd", static_cast<uint64_t>(fd));

    if (length >= 0) {
        // readlink neither terminates what it writes nor fails when the buffer is short — it
        // truncates — so the length it returns is the only thing that says where the target ends.
        // PATH_MAX is what the kernel caps these links at, so the truncating case is unreachable
        // here.
        writer->Field("target", std::string_view(link, static_cast<size_t>(length)));
    } else {
        writer->Field("target_unavailable", aoscm::DescribeFailure(link_error));
    }

    struct stat64 info = {};
    if (fstat64(fd, &info) != 0) {
        writer->Field("stat_unavailable", aoscm::DescribeFailure(errno));
    } else {
        writer->Field("type", TypeName(info.st_mode));
        writer->Field("inode", static_cast<uint64_t>(info.st_ino));
        if (S_ISREG(info.st_mode)) {
            // Only a regular file has a length worth reporting: a socket answers with zero and a
            // device with whatever its driver felt like, neither of which is a size.
            writer->Field("size_bytes", static_cast<uint64_t>(info.st_size));
        }
    }

    const int status_flags = fcntl(fd, F_GETFL);
    if (status_flags >= 0) {
        writer->Field("access", AccessName(status_flags));
        writer->Key("flags").BeginArray();
        for (const auto& [bit, name] : kStatusFlags) {
            if ((status_flags & bit) == bit) {
                writer->Value(name);
            }
        }
        writer->EndArray();
    }

    // The descriptor flag, which is not among the status flags above: close-on-exec belongs to the
    // descriptor rather than to the open file it names, and F_GETFD is what reports it.
    const int descriptor_flags = fcntl(fd, F_GETFD);
    if (descriptor_flags >= 0) {
        writer->Field("close_on_exec", (descriptor_flags & FD_CLOEXEC) != 0);
    }

    // Where the next read would start. Asking for the current offset moves nothing. A pipe or a
    // socket has no offset at all, which lseek reports as ESPIPE; the key is then left out rather
    // than sent as a zero, which would read as "at the beginning".
    const off64_t offset = lseek64(fd, 0, SEEK_CUR);
    if (offset >= 0) {
        writer->Field("offset", static_cast<uint64_t>(offset));
    }

    writer->EndObject();
}

}  // namespace

/**
 * Every file, socket, pipe and device this process has open, and how close it is to its ceiling.
 *
 * `/proc/self/fd` is the process's own directory, so none of this can be refused the way the
 * system-wide /proc files elsewhere in this library are. Java can list the numbers in that
 * directory and go no further: the target of each entry needs `readlink` — `File.getCanonicalPath`
 * throws on `socket:[12345]`, which is not a path — the kind needs `fstat`, the access mode and
 * close-on-exec flag need `fcntl`, and the file offset needs `lseek`. None of the four has a
 * counterpart in the Java API.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeDescriptorInspector_getDescriptorsNative(
        JNIEnv* env, jobject /* this */) {
    return aoscm::ReturnJson(env, [] {
        JsonWriter writer;
        writer.BeginObject();

        rlimit limit = {};
        if (getrlimit(RLIMIT_NOFILE, &limit) == 0) {
            // An unlimited ceiling is left out rather than published as a sentinel, so the screen
            // has nothing to draw a headroom bar against.
            if (limit.rlim_cur != RLIM_INFINITY) {
                writer.Field("limit_soft", static_cast<uint64_t>(limit.rlim_cur));
            }
            if (limit.rlim_max != RLIM_INFINITY) {
                writer.Field("limit_hard", static_cast<uint64_t>(limit.rlim_max));
            }
        }

        writer.Key("descriptors").BeginArray();
        for (const int fd : ListDescriptorNumbers()) {
            WriteDescriptor(&writer, fd);
        }
        writer.EndArray();

        writer.EndObject();
        return writer.Take();
    });
}
