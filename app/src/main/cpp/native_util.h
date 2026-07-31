#ifndef AOSCM_NATIVE_UTIL_H_
#define AOSCM_NATIVE_UTIL_H_

#include <jni.h>

#include <cstdint>
#include <exception>
#include <expected>
#include <string>
#include <string_view>

namespace aoscm {

/** Records why a collector produced nothing. Not shown to the user; logcat is where it lands. */
void LogCollectorFailure(const char* what);

/**
 * Runs a collector and hands its JSON to the JVM, containing anything it throws.
 *
 * "C++ exceptions ... must not be thrown across the JNI transition boundary from C++ code to
 * managed code" — every collector here builds strings and vectors, any of which can throw
 * std::bad_alloc, and none of these entry points is otherwise in a position to stop one. An empty
 * object is returned instead, which each parser turns into the same "no reading" the screens
 * already handle.
 */
template <typename Collect>
jstring ReturnJson(JNIEnv* env, Collect collect) noexcept {
  try {
    return env->NewStringUTF(collect().c_str());
  } catch (const std::exception& failure) {
    LogCollectorFailure(failure.what());
  } catch (...) {
    LogCollectorFailure("unknown exception");
  }
  return env->NewStringUTF("{}");
}

/**
 * A reading, or the errno that stopped it.
 *
 * Sysfs and procfs reads fail routinely from the app sandbox, and the two common reasons mean
 * different things to whoever is reading the screen: SELinux refusing a path is a property of the
 * sandbox, while a path that does not exist is a property of the kernel. An optional could say
 * only that the value was missing, so the reason was thrown away at the point it was known.
 */
template <typename T>
using Reading = std::expected<T, int>;

/** Reads the first line of a file with surrounding whitespace removed. */
[[nodiscard]] Reading<std::string> ReadTrimmedLine(const std::string& path);

/** Reads a file holding a single decimal number, as sysfs counters do. */
[[nodiscard]] Reading<uint64_t> ReadUint64(const std::string& path);

/**
 * Why a reading is missing, in the three words the screens distinguish.
 *
 * Returns "denied", "absent" or "error". The errno itself does not cross to Kotlin: a number would
 * push the decision of what it means out to the UI layer, which is further from the syscall than
 * this is, and the wording belongs in strings.xml either way.
 */
[[nodiscard]] std::string_view DescribeFailure(int error);

/**
 * Builds JSON for the JNI boundary.
 *
 * The existing collectors assemble their JSON by concatenating strings, which holds only as long
 * as no value contains a quote or a backslash. The readings added here are mount options and
 * filesystem paths — arbitrary bytes from the kernel — so they need real escaping. This also
 * validates UTF-8 as it goes: `NewStringUTF` has undefined behaviour on malformed input, and a
 * path is not guaranteed to be valid UTF-8.
 *
 * Structure is the caller's responsibility; the writer only tracks where a comma belongs.
 */
class JsonWriter {
 public:
  JsonWriter& BeginObject();
  JsonWriter& EndObject();
  JsonWriter& BeginArray();
  JsonWriter& EndArray();

  JsonWriter& Key(std::string_view key);

  JsonWriter& Value(std::string_view value);
  JsonWriter& Value(uint64_t value);
  /** For counters the kernel reports signed — a thread's nice value runs from -20. */
  JsonWriter& Value(int64_t value);
  JsonWriter& Value(bool value);
  /**
   * Keeps a C string a string.
   *
   * Without it, `const char*` converts to `bool` by a standard conversion and to `string_view`
   * only by a user-defined one, so overload resolution silently picks the boolean overload and
   * `uname().machine` is written as `true`.
   */
  JsonWriter& Value(const char* value);
  JsonWriter& Field(std::string_view key, std::string_view value);
  JsonWriter& Field(std::string_view key, uint64_t value);
  /** See [Value(int64_t)]. */
  JsonWriter& Field(std::string_view key, int64_t value);
  JsonWriter& Field(std::string_view key, bool value);
  /** See [Value(const char*)] — the same overload trap applies here. */
  JsonWriter& Field(std::string_view key, const char* value);
  /** Writes an address as a `"0x…"` string: JSON numbers cannot hold a 64-bit pointer exactly. */
  JsonWriter& FieldHex(std::string_view key, uint64_t value);

  /**
   * Writes the field only when the reading was taken.
   *
   * Absent keys, rather than nulls or zeros, are how an unavailable reading crosses to Kotlin.
   */
  JsonWriter& FieldIfSet(std::string_view key, const Reading<uint64_t>& value);
  JsonWriter& FieldIfSet(std::string_view key, const Reading<std::string>& value);

  /** Hands over the finished document. The writer is empty afterwards. */
  [[nodiscard]] std::string Take();

 private:
  JsonWriter& ValueHex(uint64_t value);
  void Separate();
  void AppendEscaped(std::string_view value);

  std::string out_;
  bool needs_comma_ = false;
};

}  // namespace aoscm

#endif  // AOSCM_NATIVE_UTIL_H_
