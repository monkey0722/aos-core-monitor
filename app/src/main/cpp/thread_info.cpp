#include <dirent.h>
#include <jni.h>
#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <charconv>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <system_error>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;
using aoscm::Reading;

/** Closes the directory however the scope is left, exception included. */
using TaskDir = std::unique_ptr<DIR, decltype([](DIR* dir) { closedir(dir); })>;

// Offsets into /proc/<tid>/stat once the comm field and everything before it is dropped, so index 0
// is `state`, which proc(5) numbers as field 3.
constexpr size_t kStateField = 0;
constexpr size_t kUtimeField = 11;
constexpr size_t kStimeField = 12;
constexpr size_t kPriorityField = 15;
constexpr size_t kNiceField = 16;
constexpr size_t kLastCpuField = 36;
constexpr size_t kRtPriorityField = 37;

/** What the kernel falls back to when sysconf cannot answer. Android has always set USER_HZ to 100.
 */
constexpr uint64_t kAssumedClockTicks = 100;

template <typename T>
std::optional<T> Number(std::string_view text) {
  T value{};
  const char* const first = text.data();
  const char* const last = first + text.size();
  const auto [end, error] = std::from_chars(first, last, value);
  if (error != std::errc() || end != last) {
    return std::nullopt;
  }
  return value;
}

std::optional<std::string_view> FieldAt(const std::vector<std::string_view>& fields, size_t index) {
  if (index >= fields.size()) {
    return std::nullopt;
  }
  return fields[index];
}

/**
 * Splits /proc/<tid>/stat after its comm field.
 *
 * comm is the thread name in parentheses and may contain spaces and parentheses of its own — a
 * thread named "Chrome_IOThread (2)" is legal — so the split is made at the last ") " rather than
 * by counting spaces from the left, which is the mistake this file exists not to repeat.
 *
 * The views point into `line`, which the caller keeps alive for as long as it reads them.
 */
std::vector<std::string_view> StatFieldsAfterComm(std::string_view line) {
  const size_t comm_end = line.rfind(") ");
  if (comm_end == std::string_view::npos) {
    return {};
  }

  std::vector<std::string_view> fields;
  size_t start = comm_end + 2;
  while (start < line.size()) {
    const size_t end = line.find(' ', start);
    if (end == std::string_view::npos) {
      fields.push_back(line.substr(start));
      break;
    }
    fields.push_back(line.substr(start, end - start));
    start = end + 1;
  }
  return fields;
}

/** The scheduling policy's name, as the SCHED_* constant it came back as. */
const char* PolicyName(int policy) {
  switch (policy) {
    case SCHED_OTHER:
      return "other";
    case SCHED_FIFO:
      return "fifo";
    case SCHED_RR:
      return "rr";
    case SCHED_BATCH:
      return "batch";
    case SCHED_IDLE:
      return "idle";
#ifdef SCHED_DEADLINE
    case SCHED_DEADLINE:
      return "deadline";
#endif
    default:
      return "unknown";
  }
}

/**
 * The set as a cpulist — "0-3,6" — which is the form the kernel itself uses in
 * /proc/self/status's Cpus_allowed_list, and reads better than eight ones and zeros.
 */
std::string CpuList(const cpu_set_t& mask, int cpu_count) {
  std::string list;
  int run_start = -1;

  // Runs to cpu_count inclusive so that a run reaching the last CPU is closed by the same branch
  // as every other run, rather than needing a second copy of it after the loop.
  for (int cpu = 0; cpu <= cpu_count; ++cpu) {
    const bool allowed = cpu < cpu_count && CPU_ISSET(cpu, &mask);
    if (allowed && run_start < 0) {
      run_start = cpu;
      continue;
    }
    if (!allowed && run_start >= 0) {
      if (!list.empty()) {
        list.push_back(',');
      }
      list.append(std::to_string(run_start));
      if (cpu - 1 > run_start) {
        list.push_back('-');
        list.append(std::to_string(cpu - 1));
      }
      run_start = -1;
    }
  }
  return list;
}

/**
 * Which CPUs the task may run on.
 *
 * `sched_getaffinity` takes a thread id where it documents a pid, which is how a per-thread mask is
 * asked for at all: Java has no equivalent, and /proc/<tid>/status reports the mask only for the
 * process on some kernels.
 */
void WriteAffinity(JsonWriter* writer, pid_t task, int cpu_count) {
  cpu_set_t mask;
  CPU_ZERO(&mask);
  if (sched_getaffinity(task, sizeof(mask), &mask) != 0) {
    writer->Field("affinity_unavailable", aoscm::DescribeFailure(errno));
    return;
  }
  // The cpulist alone: the count of CPUs in it was published too, and it is the length of a list
  // the reader already has.
  writer->Field("affinity", CpuList(mask, cpu_count));
}

void WriteSchedulerPolicy(JsonWriter* writer, pid_t task) {
  const int policy = sched_getscheduler(task);
  if (policy < 0) {
    writer->Field("policy_unavailable", aoscm::DescribeFailure(errno));
    return;
  }
  writer->Field("policy", PolicyName(policy));
}

void WriteThread(JsonWriter* writer, std::string_view tid_name, pid_t tid, int cpu_count) {
  const std::string task = std::string("/proc/self/task/").append(tid_name);
  const Reading<std::string> stat = aoscm::ReadTrimmedLine(task + "/stat");
  if (!stat.has_value()) {
    // The thread exited between listing the directory and reading it, which is ordinary: a thread
    // that is gone has nothing to report and is left out rather than listed as a blank row.
    return;
  }
  const std::vector<std::string_view> fields = StatFieldsAfterComm(*stat);

  writer->BeginObject();
  writer->Field("tid", static_cast<uint64_t>(tid));

  // comm rather than the name inside stat: the same string, but without the parentheses and the
  // escaping that a name containing one would need.
  const Reading<std::string> name = aoscm::ReadTrimmedLine(task + "/comm");
  writer->FieldIfSet("name", name);

  if (const auto state = FieldAt(fields, kStateField)) {
    writer->Field("state", *state);
  }
  if (const auto utime = FieldAt(fields, kUtimeField).and_then(Number<uint64_t>)) {
    writer->Field("utime_ticks", *utime);
  }
  if (const auto stime = FieldAt(fields, kStimeField).and_then(Number<uint64_t>)) {
    writer->Field("stime_ticks", *stime);
  }
  if (const auto priority = FieldAt(fields, kPriorityField).and_then(Number<int64_t>)) {
    writer->Field("priority", *priority);
  }
  if (const auto nice = FieldAt(fields, kNiceField).and_then(Number<int64_t>)) {
    writer->Field("nice", *nice);
  }
  if (const auto last_cpu = FieldAt(fields, kLastCpuField).and_then(Number<uint64_t>)) {
    writer->Field("last_cpu", *last_cpu);
  }
  if (const auto rt_priority = FieldAt(fields, kRtPriorityField).and_then(Number<uint64_t>)) {
    writer->Field("rt_priority", *rt_priority);
  }

  WriteSchedulerPolicy(writer, tid);
  WriteAffinity(writer, tid, cpu_count);
  writer->EndObject();
}

}  // namespace

/**
 * Every thread this process is running, with what the scheduler is doing with each.
 *
 * /proc/self/task is a process's own directory, so none of this can be refused the way the
 * system-wide counters elsewhere in this library are. The scheduling policy and the CPU affinity
 * come from `sched_getscheduler` and `sched_getaffinity`, which have no counterpart in the Java
 * API at all — `Thread` exposes a priority that is a hint to the runtime, not the policy the
 * kernel is actually scheduling the thread under.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeThreadInspector_getThreadsNative(JNIEnv* env,
                                                                               jobject /* this */) {
  return aoscm::ReturnJson(env, [] {
    const long configured_cpus = sysconf(_SC_NPROCESSORS_CONF);
    const int cpu_count =
        configured_cpus > 0 ? static_cast<int>(std::min<long>(configured_cpus, CPU_SETSIZE)) : 1;
    const long clock_ticks = sysconf(_SC_CLK_TCK);

    JsonWriter writer;
    writer.BeginObject();
    writer.Field("clock_ticks",
                 clock_ticks > 0 ? static_cast<uint64_t>(clock_ticks) : kAssumedClockTicks);

    // The process's own mask, which is the ceiling every thread's mask sits under. Written with the
    // same keys as a thread's so that one parser reads both.
    WriteAffinity(&writer, 0, cpu_count);

    writer.Key("threads").BeginArray();
    if (const TaskDir tasks{opendir("/proc/self/task")}) {
      while (const dirent* entry = readdir(tasks.get())) {
        const std::string_view name(entry->d_name);
        const auto tid = Number<pid_t>(name);
        if (!tid.has_value()) {
          // "." and "..", which readdir reports alongside the thread ids.
          continue;
        }
        WriteThread(&writer, name, *tid, cpu_count);
      }
    }
    writer.EndArray();

    writer.EndObject();
    return writer.Take();
  });
}
