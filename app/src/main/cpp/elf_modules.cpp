#include <elf.h>
#include <jni.h>
#include <link.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

constexpr char kHexDigits[] = "0123456789abcdef";

struct Segment {
  std::string flags;
  uint64_t memsz = 0;
};

struct Module {
  std::string path;
  uint64_t base = 0;
  uint64_t mapped_size = 0;
  std::string build_id;
  bool has_relro = false;
  bool has_tls = false;
  size_t phnum = 0;
  std::vector<Segment> segments;
};

struct Collector {
  std::vector<Module> modules;
  unsigned long long adds = 0;
  unsigned long long subs = 0;
  bool counts_valid = false;
};

std::string DescribeFlags(uint32_t flags) {
  std::string described(3, '-');
  if ((flags & PF_R) != 0) {
    described[0] = 'r';
  }
  if ((flags & PF_W) != 0) {
    described[1] = 'w';
  }
  if ((flags & PF_X) != 0) {
    described[2] = 'x';
  }
  return described;
}

/**
 * Pulls the GNU build id out of a `PT_NOTE` segment.
 *
 * The build id is what ties a loaded library back to the symbols the platform build produced, and
 * nothing outside the ELF headers carries it — it is exactly the kind of reading that has to be
 * taken here rather than from the Java side.
 *
 * Notes are a packed sequence of `Nhdr`, name, and descriptor, each padded to four bytes.
 */
std::string ReadBuildId(const ElfW(Phdr) & header, ElfW(Addr) base) {
  const auto* cursor = reinterpret_cast<const unsigned char*>(base + header.p_vaddr);
  const unsigned char* const end = cursor + header.p_memsz;

  while (cursor + sizeof(ElfW(Nhdr)) <= end) {
    const auto* note = reinterpret_cast<const ElfW(Nhdr)*>(cursor);
    const size_t name_size = (note->n_namesz + 3) & ~3u;
    const size_t desc_size = (note->n_descsz + 3) & ~3u;
    const unsigned char* const name = cursor + sizeof(ElfW(Nhdr));
    const unsigned char* const descriptor = name + name_size;

    if (descriptor + desc_size > end) {
      break;
    }
    if (note->n_type == NT_GNU_BUILD_ID && note->n_namesz == 4 &&
        std::equal(name, name + 4, reinterpret_cast<const unsigned char*>("GNU"))) {
      std::string hex;
      hex.reserve(note->n_descsz * 2);
      for (size_t i = 0; i < note->n_descsz; ++i) {
        hex.push_back(kHexDigits[descriptor[i] >> 4]);
        hex.push_back(kHexDigits[descriptor[i] & 0xF]);
      }
      return hex;
    }
    cursor = descriptor + desc_size;
  }
  return std::string();
}

/**
 * Records one loaded object.
 *
 * The linker holds its own lock across this callback, so the work here stays to reading the
 * program headers and copying what they say. Anything else — JNI calls, building the JSON — waits
 * until the walk is over. `dlpi_name` points into the linker's own storage and is copied for the
 * same reason.
 */
int CollectModule(dl_phdr_info* info, size_t size, void* data) {
  auto* collector = static_cast<Collector*>(data);

  // dlpi_adds and dlpi_subs were added after the original struct, so they are only there when the
  // linker says the struct is big enough to hold them.
  if (!collector->counts_valid &&
      size >= offsetof(dl_phdr_info, dlpi_subs) + sizeof(info->dlpi_subs)) {
    collector->adds = info->dlpi_adds;
    collector->subs = info->dlpi_subs;
    collector->counts_valid = true;
  }

  Module module;
  module.path = (info->dlpi_name != nullptr) ? info->dlpi_name : "";
  module.base = static_cast<uint64_t>(info->dlpi_addr);
  module.phnum = info->dlpi_phnum;

  uint64_t lowest = UINT64_MAX;
  uint64_t highest = 0;
  for (size_t i = 0; i < info->dlpi_phnum; ++i) {
    const ElfW(Phdr) & header = info->dlpi_phdr[i];
    switch (header.p_type) {
      case PT_LOAD:
        lowest = std::min<uint64_t>(lowest, header.p_vaddr);
        highest = std::max<uint64_t>(highest, header.p_vaddr + header.p_memsz);
        module.segments.push_back({DescribeFlags(header.p_flags), header.p_memsz});
        break;
      case PT_NOTE:
        if (module.build_id.empty()) {
          module.build_id = ReadBuildId(header, info->dlpi_addr);
        }
        break;
      case PT_GNU_RELRO:
        module.has_relro = true;
        break;
      case PT_TLS:
        module.has_tls = true;
        break;
      default:
        break;
    }
  }
  if (lowest != UINT64_MAX) {
    module.mapped_size = highest - lowest;
  }

  collector->modules.push_back(std::move(module));
  return 0;
}

}  // namespace

/**
 * Every shared object the dynamic linker has loaded into this process.
 *
 * There is no Java equivalent: the linker publishes this through `dl_iterate_phdr` and nowhere
 * else, so the list — with each object's load address, mapped size and build id — can only be
 * taken from native code.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeModuleInspector_getLoadedModulesNative(
    JNIEnv* env, jobject /* this */) {
  Collector collector;
  dl_iterate_phdr(CollectModule, &collector);

  JsonWriter writer;
  writer.BeginObject();
  if (collector.counts_valid) {
    writer.Field("adds", static_cast<uint64_t>(collector.adds));
    writer.Field("subs", static_cast<uint64_t>(collector.subs));
  }

  writer.Key("modules").BeginArray();
  for (const Module& module : collector.modules) {
    writer.BeginObject();
    writer.Field("path", module.path);
    writer.FieldHex("base", module.base);
    writer.Field("mapped_size", module.mapped_size);
    writer.Field("phnum", static_cast<uint64_t>(module.phnum));
    writer.Field("relro", module.has_relro);
    writer.Field("tls", module.has_tls);
    if (!module.build_id.empty()) {
      writer.Field("build_id", module.build_id);
    }
    writer.Key("segments").BeginArray();
    for (const Segment& segment : module.segments) {
      writer.BeginObject();
      writer.Field("flags", segment.flags);
      writer.Field("memsz", segment.memsz);
      writer.EndObject();
    }
    writer.EndArray();
    writer.EndObject();
  }
  writer.EndArray();

  writer.EndObject();
  return env->NewStringUTF(writer.Take().c_str());
}
