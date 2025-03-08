#include <android/log.h>
#include <jni.h>

#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "NativeSystemMonitor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function to get CPU load
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_NativeSystemMonitor_getCpuInfoNative(JNIEnv* env,
                                                                         jobject /* this */) {
  std::ifstream stat_file("/proc/stat");
  std::string line;
  std::string result;

  if (!stat_file.is_open()) {
    LOGE("Failed to open /proc/stat");
    return env->NewStringUTF("Error: Failed to read CPU information");
  }

  // Get the first line (total CPU information)
  if (std::getline(stat_file, line)) {
    result = line;
  }

  stat_file.close();
  LOGI("Read CPU info: %s", result.c_str());

  return env->NewStringUTF(result.c_str());
}

// Function to get memory information
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_NativeSystemMonitor_getMemInfoNative(JNIEnv* env,
                                                                         jobject /* this */) {
  std::ifstream meminfo_file("/proc/meminfo");
  std::string line;
  std::stringstream result;

  if (!meminfo_file.is_open()) {
    LOGE("Failed to open /proc/meminfo");
    return env->NewStringUTF("Error: Failed to read memory information");
  }

  // Get the first few lines (main memory information)
  int lines_to_read = 5;
  while (lines_to_read-- > 0 && std::getline(meminfo_file, line)) {
    result << line << "\n";
  }

  meminfo_file.close();
  LOGI("Read memory info");

  return env->NewStringUTF(result.str().c_str());
}

// Function to get process information
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_NativeSystemMonitor_getProcessInfoNative(JNIEnv* env,
                                                                             jobject /* this */,
                                                                             jint pid) {
  std::stringstream path;
  path << "/proc/" << pid << "/status";

  std::ifstream status_file(path.str());
  std::string line;
  std::stringstream result;

  if (!status_file.is_open()) {
    LOGE("Failed to open %s", path.str().c_str());
    return env->NewStringUTF("Error: Process not found or permission denied");
  }

  // Read contents of the status file
  while (std::getline(status_file, line)) {
    result << line << "\n";
  }

  status_file.close();
  LOGI("Read process info for PID: %d", pid);

  return env->NewStringUTF(result.str().c_str());
}
