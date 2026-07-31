#include <jni.h>
#include <sys/auxv.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <charconv>
#include <optional>
#include <string>
#include <string_view>
#include <system_error>
#include <vector>

#include "native_util.h"

#if defined(__aarch64__) || defined(__arm__)
#include <asm/hwcap.h>
#endif

namespace {

using aoscm::JsonWriter;
using aoscm::Reading;
using aoscm::ReadTrimmedLine;
using aoscm::ReadUint64;

/** A guard against a malformed `present` range asking for millions of directory reads. */
constexpr int kMaxCoresPerRange = 1024;

std::string CpuDir(int id) {
  return "/sys/devices/system/cpu/cpu" + std::to_string(id);
}

std::optional<int> ParseInt(std::string_view text) {
  int value = 0;
  const auto [end, error] = std::from_chars(text.data(), text.data() + text.size(), value);
  if (error != std::errc() || end == text.data()) {
    return std::nullopt;
  }
  return value;
}

/**
 * The CPU ids the kernel knows about, from `/sys/devices/system/cpu/present`.
 *
 * Read rather than derived from `sysconf(_SC_NPROCESSORS_CONF)` because the ids are what name the
 * sysfs directories, and on a big.LITTLE device with cores offline the two need not agree. The
 * format is a range list such as `0-7` or `0-3,6-7`.
 */
std::vector<int> PresentCpus() {
  std::vector<int> ids;
  const Reading<std::string> present = ReadTrimmedLine("/sys/devices/system/cpu/present");

  if (present.has_value()) {
    std::string_view remaining(*present);
    while (!remaining.empty()) {
      const size_t comma = remaining.find(',');
      const std::string_view range = remaining.substr(0, comma);
      const size_t dash = range.find('-');

      const std::optional<int> first = ParseInt(range.substr(0, dash));
      const std::optional<int> last =
          (dash == std::string_view::npos) ? first : ParseInt(range.substr(dash + 1));
      if (first.has_value() && last.has_value()) {
        for (int id = *first; id <= *last && id - *first < kMaxCoresPerRange; ++id) {
          ids.push_back(id);
        }
      }

      if (comma == std::string_view::npos) {
        break;
      }
      remaining.remove_prefix(comma + 1);
    }
  }

  if (ids.empty()) {
    const long configured = sysconf(_SC_NPROCESSORS_CONF);
    for (long id = 0; id < configured; ++id) {
      ids.push_back(static_cast<int>(id));
    }
  }
  return ids;
}

/**
 * Whether a core is currently running.
 *
 * cpu0 has no `online` attribute on most kernels because it cannot be taken offline; a missing
 * file therefore means online, not unknown.
 */
bool IsOnline(int id) {
  const Reading<uint64_t> online = ReadUint64(CpuDir(id) + "/online");
  return online.value_or(1) != 0;
}

/**
 * The instruction set extensions the kernel advertises for this CPU.
 *
 * Taken from the ELF auxiliary vector rather than parsed out of `/proc/cpuinfo`: the auxv is
 * always readable, whereas /proc/cpuinfo's `Features` line is absent on some arm64 kernels and
 * the whole file is filtered for apps on others.
 */
std::vector<std::string> IsaFeatures() {
  std::vector<std::string> features;

#if defined(__aarch64__) || defined(__arm__)
  const unsigned long hwcap = getauxval(AT_HWCAP);
  const unsigned long hwcap2 = getauxval(AT_HWCAP2);
#define AOSCM_CAP(caps, mask, label) \
  do {                               \
    if (((caps) & (mask)) != 0) {    \
      features.emplace_back(label);  \
    }                                \
  } while (0)
#endif

#if defined(__aarch64__)
  AOSCM_CAP(hwcap, HWCAP_FP, "fp");
  AOSCM_CAP(hwcap, HWCAP_ASIMD, "asimd");
  AOSCM_CAP(hwcap, HWCAP_AES, "aes");
  AOSCM_CAP(hwcap, HWCAP_PMULL, "pmull");
  AOSCM_CAP(hwcap, HWCAP_SHA1, "sha1");
  AOSCM_CAP(hwcap, HWCAP_SHA2, "sha2");
  AOSCM_CAP(hwcap, HWCAP_SHA3, "sha3");
  AOSCM_CAP(hwcap, HWCAP_SHA512, "sha512");
  AOSCM_CAP(hwcap, HWCAP_CRC32, "crc32");
  AOSCM_CAP(hwcap, HWCAP_ATOMICS, "atomics");
  AOSCM_CAP(hwcap, HWCAP_FPHP, "fphp");
  AOSCM_CAP(hwcap, HWCAP_ASIMDHP, "asimdhp");
  AOSCM_CAP(hwcap, HWCAP_ASIMDRDM, "asimdrdm");
  AOSCM_CAP(hwcap, HWCAP_ASIMDDP, "asimddp");
  AOSCM_CAP(hwcap, HWCAP_ASIMDFHM, "asimdfhm");
  AOSCM_CAP(hwcap, HWCAP_JSCVT, "jscvt");
  AOSCM_CAP(hwcap, HWCAP_FCMA, "fcma");
  AOSCM_CAP(hwcap, HWCAP_LRCPC, "lrcpc");
  AOSCM_CAP(hwcap, HWCAP_DCPOP, "dcpop");
  AOSCM_CAP(hwcap, HWCAP_SVE, "sve");
  AOSCM_CAP(hwcap, HWCAP_DIT, "dit");
  AOSCM_CAP(hwcap, HWCAP_FLAGM, "flagm");
  AOSCM_CAP(hwcap, HWCAP_SSBS, "ssbs");
  AOSCM_CAP(hwcap, HWCAP_PACA, "paca");
  AOSCM_CAP(hwcap2, HWCAP2_SVE2, "sve2");
  AOSCM_CAP(hwcap2, HWCAP2_I8MM, "i8mm");
  AOSCM_CAP(hwcap2, HWCAP2_BF16, "bf16");
  AOSCM_CAP(hwcap2, HWCAP2_RNG, "rng");
  AOSCM_CAP(hwcap2, HWCAP2_BTI, "bti");
  AOSCM_CAP(hwcap2, HWCAP2_MTE, "mte");
  AOSCM_CAP(hwcap2, HWCAP2_ECV, "ecv");
#elif defined(__arm__)
  AOSCM_CAP(hwcap, HWCAP_NEON, "neon");
  AOSCM_CAP(hwcap, HWCAP_VFP, "vfp");
  AOSCM_CAP(hwcap, HWCAP_VFPv3, "vfpv3");
  AOSCM_CAP(hwcap, HWCAP_VFPv4, "vfpv4");
  AOSCM_CAP(hwcap, HWCAP_IDIVA, "idiva");
  AOSCM_CAP(hwcap, HWCAP_IDIVT, "idivt");
  AOSCM_CAP(hwcap, HWCAP_LPAE, "lpae");
  AOSCM_CAP(hwcap, HWCAP_ASIMDHP, "asimdhp");
  AOSCM_CAP(hwcap, HWCAP_ASIMDDP, "asimddp");
  AOSCM_CAP(hwcap2, HWCAP2_AES, "aes");
  AOSCM_CAP(hwcap2, HWCAP2_PMULL, "pmull");
  AOSCM_CAP(hwcap2, HWCAP2_SHA1, "sha1");
  AOSCM_CAP(hwcap2, HWCAP2_SHA2, "sha2");
  AOSCM_CAP(hwcap2, HWCAP2_CRC32, "crc32");
#elif defined(__i386__) || defined(__x86_64__)
  // x86 kernels put almost nothing in AT_HWCAP, so the emulator would otherwise show an empty
  // list. The compiler builtin reads CPUID instead, which is what the auxv stands in for on ARM.
  // The builtin only takes a literal, so the features are named one by one rather than looped
  // over a table.
#define AOSCM_X86(feature)                 \
  do {                                     \
    if (__builtin_cpu_supports(feature)) { \
      features.emplace_back(feature);      \
    }                                      \
  } while (0)
  AOSCM_X86("sse4.2");
  AOSCM_X86("aes");
  AOSCM_X86("popcnt");
  AOSCM_X86("avx");
  AOSCM_X86("avx2");
  AOSCM_X86("fma");
  AOSCM_X86("bmi");
  AOSCM_X86("bmi2");
  AOSCM_X86("avx512f");
#undef AOSCM_X86
#endif

#if defined(__aarch64__) || defined(__arm__)
#undef AOSCM_CAP
#endif

  return features;
}

}  // namespace

/** Everything about the CPU that does not change while the app runs. */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeCpuInspector_getCpuStaticNative(JNIEnv* env,
                                                                              jobject /* this */) {
  return aoscm::ReturnJson(env, [] {
    JsonWriter writer;
    writer.BeginObject();

    const long configured = sysconf(_SC_NPROCESSORS_CONF);
    const long online = sysconf(_SC_NPROCESSORS_ONLN);
    writer.Field("configured", static_cast<uint64_t>(configured > 0 ? configured : 0));
    writer.Field("online", static_cast<uint64_t>(online > 0 ? online : 0));
    writer.Field("page_size", static_cast<uint64_t>(sysconf(_SC_PAGESIZE)));
    writer.Field("clock_ticks", static_cast<uint64_t>(sysconf(_SC_CLK_TCK)));

    utsname system_name = {};
    if (uname(&system_name) == 0) {
      writer.Field("machine", system_name.machine);
      writer.Field("kernel_release", system_name.release);
    }

    writer.Key("cores").BeginArray();
    for (const int id : PresentCpus()) {
      const std::string dir = CpuDir(id);
      writer.BeginObject();
      writer.Field("id", static_cast<uint64_t>(id));
      writer.FieldIfSet("core_id", ReadUint64(dir + "/topology/core_id"));
      writer.FieldIfSet("package_id", ReadUint64(dir + "/topology/physical_package_id"));
      writer.FieldIfSet("min_khz", ReadUint64(dir + "/cpufreq/cpuinfo_min_freq"));
      writer.FieldIfSet("max_khz", ReadUint64(dir + "/cpufreq/cpuinfo_max_freq"));
      writer.EndObject();
    }
    writer.EndArray();

    writer.Key("features").BeginArray();
    for (const std::string& feature : IsaFeatures()) {
      writer.Value(feature);
    }
    writer.EndArray();

    writer.EndObject();
    return writer.Take();
  });
}

/** The part that moves: which cores are up, how fast they are running, and under which governor. */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeCpuInspector_getCpuFrequenciesNative(
    JNIEnv* env, jobject /* this */) {
  return aoscm::ReturnJson(env, [] {
    JsonWriter writer;
    writer.BeginObject();
    writer.Key("cores").BeginArray();

    for (const int id : PresentCpus()) {
      const std::string dir = CpuDir(id);
      writer.BeginObject();
      writer.Field("id", static_cast<uint64_t>(id));
      writer.Field("online", IsOnline(id));

      // scaling_cur_freq is denied to apps on some devices and missing entirely on others. The
      // screen says which rather than showing a stale or zero frequency.
      const Reading<uint64_t> current = ReadUint64(dir + "/cpufreq/scaling_cur_freq");
      writer.FieldIfSet("cur_khz", current);
      if (!current.has_value()) {
        writer.Field("cur_khz_unavailable", aoscm::DescribeFailure(current.error()));
      }

      writer.FieldIfSet("governor", ReadTrimmedLine(dir + "/cpufreq/scaling_governor"));
      writer.EndObject();
    }

    writer.EndArray();
    writer.EndObject();
    return writer.Take();
  });
}
