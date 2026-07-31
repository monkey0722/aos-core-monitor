#include "native_util.h"

#include <charconv>
#include <cstdio>
#include <fstream>
#include <sstream>
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

/** Reads a whole file. Used only to back ReadTrimmedLine — nothing outside needs raw contents. */
std::optional<std::string> ReadFile(const std::string& path) {
  std::ifstream file(path, std::ios::in | std::ios::binary);
  if (!file.is_open()) {
    return std::nullopt;
  }
  std::stringstream contents;
  contents << file.rdbuf();
  // A sysfs attribute can open and then fail on read — an offline CPU's cpufreq node does exactly
  // that — so an unreadable file must not come back as an empty string.
  if (file.bad()) {
    return std::nullopt;
  }
  return contents.str();
}

}  // namespace

std::optional<std::string> ReadTrimmedLine(const std::string& path) {
  const std::optional<std::string> contents = ReadFile(path);
  if (!contents.has_value()) {
    return std::nullopt;
  }
  const std::string trimmed = Trim(*contents);
  if (trimmed.empty()) {
    return std::nullopt;
  }
  return trimmed;
}

std::optional<uint64_t> ReadUint64(const std::string& path) {
  const std::optional<std::string> line = ReadTrimmedLine(path);
  if (!line.has_value()) {
    return std::nullopt;
  }
  // from_chars rather than strtoull: it reports an overflow through its own result instead of
  // through errno, and it cannot read past the end of the string it was given.
  uint64_t value = 0;
  const char* const first = line->data();
  const auto [end, error] = std::from_chars(first, first + line->size(), value);
  if (error != std::errc() || end == first) {
    return std::nullopt;
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

JsonWriter& JsonWriter::ValueHex(uint64_t value) {
  char buffer[32];
  std::snprintf(buffer, sizeof(buffer), "0x%llx", static_cast<unsigned long long>(value));
  return Value(std::string_view(buffer));
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

JsonWriter& JsonWriter::FieldIfSet(std::string_view key, const std::optional<uint64_t>& value) {
  if (value.has_value()) {
    Field(key, *value);
  }
  return *this;
}

JsonWriter& JsonWriter::FieldIfSet(std::string_view key, const std::optional<std::string>& value) {
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
