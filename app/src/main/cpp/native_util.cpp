#include "native_util.h"

#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>

#include <cerrno>
#include <charconv>
#include <system_error>

namespace aoscm {
namespace {

constexpr char kHexDigits[] = "0123456789abcdef";

std::string Trim(const std::string& text) {
  const size_t first = text.find_first_not_of(" \t\n\r");
  if (first == std::string::npos) {
    return std::string();
  }
  const size_t last = text.find_last_not_of(" \t\n\r");
  return text.substr(first, last - first + 1);
}

/**
 * Length of the UTF-8 sequence starting at `bytes`, or 0 when it is not a valid one.
 *
 * The ranges are RFC 3629's, so overlong encodings and encoded surrogates are rejected rather
 * than passed through to the JVM.
 */
size_t Utf8SequenceLength(const unsigned char* bytes, size_t available) {
  const unsigned char lead = bytes[0];
  size_t length;
  unsigned char min_second;
  unsigned char max_second;

  if (lead >= 0xC2 && lead <= 0xDF) {
    length = 2;
    min_second = 0x80;
    max_second = 0xBF;
  } else if (lead >= 0xE0 && lead <= 0xEF) {
    length = 3;
    min_second = (lead == 0xE0) ? 0xA0 : 0x80;
    max_second = (lead == 0xED) ? 0x9F : 0xBF;
  } else if (lead >= 0xF0 && lead <= 0xF4) {
    length = 4;
    min_second = (lead == 0xF0) ? 0x90 : 0x80;
    max_second = (lead == 0xF4) ? 0x8F : 0xBF;
  } else {
    return 0;
  }

  if (available < length) {
    return 0;
  }
  if (bytes[1] < min_second || bytes[1] > max_second) {
    return 0;
  }
  for (size_t i = 2; i < length; ++i) {
    if (bytes[i] < 0x80 || bytes[i] > 0xBF) {
      return 0;
    }
  }
  return length;
}

void AppendUtf16Escape(std::string* out, uint32_t unit) {
  out->append("\\u");
  for (int shift = 12; shift >= 0; shift -= 4) {
    out->push_back(kHexDigits[(unit >> shift) & 0xF]);
  }
}

/** Closes the descriptor however the scope is left. */
class ScopedFd {
 public:
  explicit ScopedFd(int fd) : fd_(fd) {}
  ~ScopedFd() {
    if (fd_ >= 0) {
      ::close(fd_);
    }
  }
  ScopedFd(const ScopedFd&) = delete;
  ScopedFd& operator=(const ScopedFd&) = delete;

  [[nodiscard]] int get() const { return fd_; }
  [[nodiscard]] bool valid() const { return fd_ >= 0; }

 private:
  int fd_;
};

/**
 * Reads a whole file, or reports the errno that stopped it.
 *
 * open and read rather than ifstream: the standard says nothing about errno after a stream fails,
 * and reporting which failure it was is the whole point here. Sysfs also reports a file size it
 * does not have, so the length has to come from reading to the end.
 */
Reading<std::string> ReadFile(const std::string& path) {
  const ScopedFd file(::open(path.c_str(), O_RDONLY | O_CLOEXEC));
  if (!file.valid()) {
    return std::unexpected(errno);
  }

  std::string contents;
  char buffer[4096];
  while (true) {
    const ssize_t count = ::read(file.get(), buffer, sizeof(buffer));
    if (count < 0) {
      if (errno == EINTR) {
        continue;
      }
      return std::unexpected(errno);
    }
    if (count == 0) {
      return contents;
    }
    contents.append(buffer, static_cast<size_t>(count));
  }
}

}  // namespace

void LogCollectorFailure(const char* what) {
  __android_log_print(ANDROID_LOG_ERROR, "NativeCollector", "Collector failed: %s", what);
}

std::string_view DescribeFailure(int error) {
  switch (error) {
    case EACCES:
    case EPERM:
      return "denied";
    case ENOENT:
    case ENODEV:
    case ENXIO:
      return "absent";
    default:
      return "error";
  }
}

Reading<std::string> ReadTrimmedLine(const std::string& path) {
  const Reading<std::string> contents = ReadFile(path);
  if (!contents.has_value()) {
    return std::unexpected(contents.error());
  }
  std::string trimmed = Trim(*contents);
  if (trimmed.empty()) {
    // The file opened and held nothing, which is not the same as being refused.
    return std::unexpected(ENODATA);
  }
  return trimmed;
}

Reading<uint64_t> ReadUint64(const std::string& path) {
  const Reading<std::string> line = ReadTrimmedLine(path);
  if (!line.has_value()) {
    return std::unexpected(line.error());
  }
  // from_chars rather than strtoull: it reports an overflow through its own result instead of
  // through errno, and it cannot read past the end of the string it was given.
  uint64_t value = 0;
  const char* const first = line->data();
  const auto [end, error] = std::from_chars(first, first + line->size(), value);
  if (error != std::errc() || end == first) {
    return std::unexpected(EINVAL);
  }
  return value;
}

void JsonWriter::Separate() {
  if (needs_comma_) {
    out_.push_back(',');
  }
  needs_comma_ = false;
}

void JsonWriter::AppendEscaped(std::string_view value) {
  const auto* bytes = reinterpret_cast<const unsigned char*>(value.data());
  const size_t size = value.size();

  for (size_t i = 0; i < size;) {
    const unsigned char byte = bytes[i];
    if (byte < 0x80) {
      switch (byte) {
        case '"':
          out_.append("\\\"");
          break;
        case '\\':
          out_.append("\\\\");
          break;
        case '\n':
          out_.append("\\n");
          break;
        case '\r':
          out_.append("\\r");
          break;
        case '\t':
          out_.append("\\t");
          break;
        default:
          if (byte < 0x20) {
            AppendUtf16Escape(&out_, byte);
          } else {
            out_.push_back(static_cast<char>(byte));
          }
          break;
      }
      ++i;
      continue;
    }

    const size_t length = Utf8SequenceLength(bytes + i, size - i);
    if (length == 0) {
      // A path is a byte string, so it need not be text at all. Replacing the byte keeps the
      // document decodable instead of handing the JVM something it cannot represent.
      out_.push_back('?');
      ++i;
      continue;
    }
    if (length == 4) {
      // Escaped as a surrogate pair: the JVM's modified UTF-8 has no four-byte form, and passing
      // one to NewStringUTF is undefined.
      const uint32_t code_point = (static_cast<uint32_t>(bytes[i] & 0x07) << 18) |
                                  (static_cast<uint32_t>(bytes[i + 1] & 0x3F) << 12) |
                                  (static_cast<uint32_t>(bytes[i + 2] & 0x3F) << 6) |
                                  static_cast<uint32_t>(bytes[i + 3] & 0x3F);
      const uint32_t offset = code_point - 0x10000;
      AppendUtf16Escape(&out_, 0xD800 + (offset >> 10));
      AppendUtf16Escape(&out_, 0xDC00 + (offset & 0x3FF));
    } else {
      out_.append(value.substr(i, length));
    }
    i += length;
  }
}

JsonWriter& JsonWriter::BeginObject() {
  Separate();
  out_.push_back('{');
  return *this;
}

JsonWriter& JsonWriter::EndObject() {
  out_.push_back('}');
  needs_comma_ = true;
  return *this;
}

JsonWriter& JsonWriter::BeginArray() {
  Separate();
  out_.push_back('[');
  return *this;
}

JsonWriter& JsonWriter::EndArray() {
  out_.push_back(']');
  needs_comma_ = true;
  return *this;
}

JsonWriter& JsonWriter::Key(std::string_view key) {
  Separate();
  out_.push_back('"');
  AppendEscaped(key);
  out_.append("\":");
  return *this;
}

JsonWriter& JsonWriter::Value(std::string_view value) {
  Separate();
  out_.push_back('"');
  AppendEscaped(value);
  out_.push_back('"');
  needs_comma_ = true;
  return *this;
}

JsonWriter& JsonWriter::Value(uint64_t value) {
  Separate();
  out_.append(std::to_string(value));
  needs_comma_ = true;
  return *this;
}

JsonWriter& JsonWriter::Value(bool value) {
  Separate();
  out_.append(value ? "true" : "false");
  needs_comma_ = true;
  return *this;
}

JsonWriter& JsonWriter::Value(const char* value) {
  return Value(value == nullptr ? std::string_view() : std::string_view(value));
}

// to_chars rather than std::format: libc++ is linked statically here, so one format call pulls
// the whole formatting machinery into the library — measured at 210 KB per ABI.
JsonWriter& JsonWriter::ValueHex(uint64_t value) {
  // 16 digits is the widest a uint64_t reaches in base 16, so to_chars cannot run out of room.
  char digits[16];
  const auto [end, error] = std::to_chars(digits, digits + sizeof(digits), value, 16);
  if (error != std::errc()) {
    return Value("0x0");
  }
  return Value("0x" + std::string(digits, end));
}

JsonWriter& JsonWriter::Field(std::string_view key, std::string_view value) {
  return Key(key).Value(value);
}

JsonWriter& JsonWriter::Field(std::string_view key, uint64_t value) {
  return Key(key).Value(value);
}

JsonWriter& JsonWriter::Field(std::string_view key, bool value) {
  return Key(key).Value(value);
}

JsonWriter& JsonWriter::Field(std::string_view key, const char* value) {
  return Key(key).Value(value);
}

JsonWriter& JsonWriter::FieldHex(std::string_view key, uint64_t value) {
  return Key(key).ValueHex(value);
}

JsonWriter& JsonWriter::FieldIfSet(std::string_view key, const Reading<uint64_t>& value) {
  if (value.has_value()) {
    Field(key, *value);
  }
  return *this;
}

JsonWriter& JsonWriter::FieldIfSet(std::string_view key, const Reading<std::string>& value) {
  if (value.has_value()) {
    Field(key, *value);
  }
  return *this;
}

std::string JsonWriter::Take() {
  std::string result;
  result.swap(out_);
  needs_comma_ = false;
  return result;
}

}  // namespace aoscm
