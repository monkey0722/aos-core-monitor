#include <jni.h>
#include <malloc.h>
#include <sys/resource.h>

#include <array>
#include <cstdlib>
#include <fstream>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

/**
 * What a mapping is for, decided from the path the kernel prints for it.
 *
 * The keys are stable identifiers rather than labels: the display names live in strings.xml so
 * that renaming a category — or translating it — does not mean rebuilding the native library.
 * The order here is the order the screen lists them in.
 */
enum class Category {
  kNativeLib,
  kArt,
  kDalvik,
  kNativeHeap,
  kStack,
  kAnon,
  kOther,
  kCount,
};

constexpr std::array<const char*, static_cast<size_t>(Category::kCount)> kCategoryKeys = {
    "native_lib", "art", "dalvik", "native_heap", "stack", "anon", "other",
};

bool EndsWith(std::string_view text, std::string_view suffix) {
  return text.size() >= suffix.size() &&
         text.compare(text.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool StartsWith(std::string_view text, std::string_view prefix) {
  return text.size() >= prefix.size() && text.compare(0, prefix.size(), prefix) == 0;
}

Category Classify(std::string_view path) {
  if (path.empty()) {
    return Category::kAnon;
  }
  if (StartsWith(path, "[stack") || StartsWith(path, "[anon:stack_and_tls")) {
    return Category::kStack;
  }
  if (path == "[heap]" || StartsWith(path, "[anon:libc_malloc") ||
      StartsWith(path, "[anon:scudo") || StartsWith(path, "[anon:GWP-ASan")) {
    return Category::kNativeHeap;
  }
  if (path.find("dalvik") != std::string_view::npos) {
    return Category::kDalvik;
  }
  if (EndsWith(path, ".so") || path.find(".so ") != std::string_view::npos) {
    return Category::kNativeLib;
  }
  if (EndsWith(path, ".art") || EndsWith(path, ".oat") || EndsWith(path, ".odex") ||
      EndsWith(path, ".vdex") || EndsWith(path, ".dex") || EndsWith(path, ".jar") ||
      EndsWith(path, ".apk")) {
    return Category::kArt;
  }
  if (StartsWith(path, "[")) {
    return Category::kAnon;
  }
  return Category::kOther;
}

struct CategoryTotals {
  uint64_t count = 0;
  uint64_t size_kb = 0;
};

struct MapsSummary {
  uint64_t total_regions = 0;
  uint64_t reserved_regions = 0;
  uint64_t reserved_kb = 0;
  std::array<CategoryTotals, static_cast<size_t>(Category::kCount)> categories = {};
};

/**
 * Walks `/proc/self/maps` and totals the address space by category.
 *
 * Every line is `start-end perms offset dev inode [path]`, and the path may contain spaces, so it
 * is taken as the remainder of the line rather than as a whitespace-delimited field.
 *
 * Mappings with no access at all are counted apart from the rest. Four fifths of a 64-bit
 * Android process's address space is exactly that — the CFI shadow, WebView's reservation and
 * Scudo's primary reserve, all mapped `---p` so that nothing else can take the range. Folding
 * them into the category totals put 8 GB of "native heap" at the top of a screen describing a
 * process holding 100 MB, which is true of the address space and says nothing about the memory.
 */
MapsSummary SummarizeMaps() {
  MapsSummary summary;
  std::ifstream maps("/proc/self/maps");
  if (!maps.is_open()) {
    return summary;
  }

  std::string line;
  while (std::getline(maps, line)) {
    const size_t dash = line.find('-');
    const size_t space = line.find(' ');
    if (dash == std::string::npos || space == std::string::npos || dash > space) {
      continue;
    }

    const uint64_t start = std::strtoull(line.c_str(), nullptr, 16);
    const uint64_t end = std::strtoull(line.c_str() + dash + 1, nullptr, 16);
    if (end <= start) {
      continue;
    }

    summary.total_regions += 1;

    // The permissions are the fixed-width field straight after the address range.
    const std::string_view permissions = std::string_view(line).substr(space + 1, 4);
    const bool accessible =
        permissions.size() == 4 &&
        (permissions[0] == 'r' || permissions[1] == 'w' || permissions[2] == 'x');
    if (!accessible) {
      summary.reserved_regions += 1;
      summary.reserved_kb += (end - start) / 1024;
      continue;
    }

    // Five whitespace-separated fields precede the path; whatever follows them is the path,
    // spaces included.
    size_t position = 0;
    int fields = 0;
    while (fields < 5 && position < line.size()) {
      position = line.find(' ', position);
      if (position == std::string::npos) {
        break;
      }
      position = line.find_first_not_of(' ', position);
      ++fields;
    }
    const std::string_view path =
        (fields == 5 && position != std::string::npos && position < line.size())
            ? std::string_view(line).substr(position)
            : std::string_view();

    auto& totals = summary.categories[static_cast<size_t>(Classify(path))];
    totals.count += 1;
    totals.size_kb += (end - start) / 1024;
  }
  return summary;
}

/** The smaps counters worth showing, in the order the screen shows them. */
constexpr std::array<const char*, 8> kRollupKeys = {
    "Rss",          "Pss",          "Private_Clean", "Private_Dirty",
    "Shared_Clean", "Shared_Dirty", "Swap",          "SwapPss",
};

/** The JSON key each smaps counter is published under. */
constexpr std::array<const char*, 8> kRollupJsonKeys = {
    "rss_kb",          "pss_kb",          "private_clean_kb", "private_dirty_kb",
    "shared_clean_kb", "shared_dirty_kb", "swap_kb",          "swap_pss_kb",
};

struct Rollup {
  bool present = false;
  bool from_rollup_file = false;
  std::array<uint64_t, kRollupKeys.size()> values = {};
};

/**
 * Totals the per-mapping counters in a smaps-formatted file.
 *
 * `/proc/self/smaps_rollup` is the kernel doing this sum itself and is one short read; it needs
 * kernel 4.14, so `/proc/self/smaps` — hundreds of mappings, each with twenty counter lines —
 * remains as the fallback. Both files have the same `Key:  value kB` shape, so one parser covers
 * them and only the cost differs.
 */
Rollup ReadSmaps(const char* path, bool is_rollup_file) {
  Rollup rollup;
  std::ifstream file(path);
  if (!file.is_open()) {
    return rollup;
  }

  std::string line;
  while (std::getline(file, line)) {
    const size_t colon = line.find(':');
    if (colon == std::string::npos) {
      continue;
    }
    const std::string_view key = std::string_view(line).substr(0, colon);
    for (size_t i = 0; i < kRollupKeys.size(); ++i) {
      if (key == kRollupKeys[i]) {
        rollup.values[i] += std::strtoull(line.c_str() + colon + 1, nullptr, 10);
        rollup.present = true;
        break;
      }
    }
  }
  rollup.from_rollup_file = is_rollup_file && rollup.present;
  return rollup;
}

/** The `/proc/self/status` lines the screen shows, in order. */
constexpr std::array<const char*, 6> kStatusKeys = {
    "VmSize", "VmRSS", "VmHWM", "VmSwap", "Threads", "FDSize",
};

void WriteStatus(JsonWriter* writer) {
  std::ifstream status("/proc/self/status");
  if (!status.is_open()) {
    return;
  }

  writer->Key("status").BeginObject();
  std::string line;
  while (std::getline(status, line)) {
    const size_t colon = line.find(':');
    if (colon == std::string::npos) {
      continue;
    }
    const std::string key = line.substr(0, colon);
    for (const char* wanted : kStatusKeys) {
      if (key == wanted) {
        const size_t value_start = line.find_first_not_of(" \t", colon + 1);
        if (value_start != std::string::npos) {
          writer->Field(key, std::string_view(line).substr(value_start));
        }
        break;
      }
    }
  }
  writer->EndObject();
}

void WriteLimit(JsonWriter* writer, const char* key, int resource) {
  rlimit limit = {};
  if (getrlimit(resource, &limit) != 0 || limit.rlim_cur == RLIM_INFINITY) {
    // An unlimited resource is left out rather than published as a sentinel, so the screen has
    // nothing to misreport as a very large number.
    return;
  }
  writer->Field(key, static_cast<uint64_t>(limit.rlim_cur));
}

}  // namespace

/**
 * The app's own address space.
 *
 * Everything here comes from `/proc/self`, which a process can always read regardless of SELinux
 * policy — unlike the system-wide counters the older native screen reads, this never falls back
 * to sample data.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeMemoryInspector_getMemoryMapNative(
    JNIEnv* env, jobject /* this */) {
  JsonWriter writer;
  writer.BeginObject();

  Rollup rollup = ReadSmaps("/proc/self/smaps_rollup", true);
  if (!rollup.present) {
    rollup = ReadSmaps("/proc/self/smaps", false);
  }
  if (rollup.present) {
    writer.Key("rollup").BeginObject();
    for (size_t i = 0; i < kRollupJsonKeys.size(); ++i) {
      writer.Field(kRollupJsonKeys[i], rollup.values[i]);
    }
    writer.Field("from_rollup_file", rollup.from_rollup_file);
    writer.EndObject();
  }

  WriteStatus(&writer);

  const MapsSummary maps = SummarizeMaps();
  writer.Key("regions").BeginObject();
  writer.Field("total", maps.total_regions);
  writer.Field("reserved_count", maps.reserved_regions);
  writer.Field("reserved_kb", maps.reserved_kb);
  writer.Key("categories").BeginArray();
  for (size_t i = 0; i < kCategoryKeys.size(); ++i) {
    if (maps.categories[i].count == 0) {
      continue;
    }
    writer.BeginObject();
    writer.Field("key", kCategoryKeys[i]);
    writer.Field("count", maps.categories[i].count);
    writer.Field("size_kb", maps.categories[i].size_kb);
    writer.EndObject();
  }
  writer.EndArray();
  writer.EndObject();

  const struct mallinfo2 heap = mallinfo2();
  writer.Key("malloc").BeginObject();
  writer.Field("arena", static_cast<uint64_t>(heap.arena));
  writer.Field("in_use", static_cast<uint64_t>(heap.uordblks));
  writer.Field("free", static_cast<uint64_t>(heap.fordblks));
  writer.Field("free_chunks", static_cast<uint64_t>(heap.ordblks));
  writer.Field("mmapped", static_cast<uint64_t>(heap.hblkhd));
  writer.Field("peak", static_cast<uint64_t>(heap.usmblks));
  writer.Field("releasable", static_cast<uint64_t>(heap.keepcost));
  writer.EndObject();

  writer.Key("limits").BeginObject();
  WriteLimit(&writer, "address_space", RLIMIT_AS);
  WriteLimit(&writer, "data", RLIMIT_DATA);
  WriteLimit(&writer, "stack", RLIMIT_STACK);
  WriteLimit(&writer, "open_files", RLIMIT_NOFILE);
  writer.EndObject();

  writer.EndObject();
  return env->NewStringUTF(writer.Take().c_str());
}
