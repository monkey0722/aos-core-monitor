#ifndef AOSCM_NATIVE_UTIL_H_
#define AOSCM_NATIVE_UTIL_H_

#include <jni.h>

#include <cstdint>
#include <exception>
#include <optional>
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
 * Reads the first line of a file with surrounding whitespace removed, or nothing at all.
 *
 * Sysfs and procfs reads fail routinely from the app sandbox — SELinux denies some paths outright,
 * and others exist only on some kernels. Returning an empty optional keeps "could not read" apart
 * from "read a zero", which matters because the screens present the two differently: a missing
 * reading is shown as unavailable rather than as 0.
 */
[[nodiscard]] std::optional<std::string> ReadTrimmedLine(const std::string& path);

/** Reads a file holding a single decimal number, as sysfs counters do. */
[[nodiscard]] std::optional<uint64_t> ReadUint64(const std::string& path);

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
  JsonWriter& FieldIfSet(std::string_view key, const std::optional<uint64_t>& value);
  JsonWriter& FieldIfSet(std::string_view key, const std::optional<std::string>& value);

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
