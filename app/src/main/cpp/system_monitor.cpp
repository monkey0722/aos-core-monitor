#include <android/log.h>
#include <jni.h>

#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "NativeSystemMonitor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Function to get CPU load
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeSystemMonitor_getCpuInfoNative(JNIEnv* env,
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
Java_com_aoscoremonitor_diagnostics_jni_NativeSystemMonitor_getMemInfoNative(JNIEnv* env,
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
Java_com_aoscoremonitor_diagnostics_jni_NativeSystemMonitor_getProcessInfoNative(JNIEnv* env,
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

// Function to get network interface statistics
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeSystemMonitor_getNetworkStatsNative(
    JNIEnv* env, jobject /* this */) {
  std::ifstream net_dev_file("/proc/net/dev");
  std::string line;
  std::stringstream result;
  std::string json = "{";
  bool first_interface = true;

  if (!net_dev_file.is_open()) {
    LOGE("Failed to open /proc/net/dev");
    return env->NewStringUTF("Error: Failed to read network statistics");
  }

  // Skip header lines (first two lines)
  std::getline(net_dev_file, line);  // Skip header line 1
  std::getline(net_dev_file, line);  // Skip header line 2

  // Process each network interface
  while (std::getline(net_dev_file, line)) {
    std::stringstream line_stream(line);
    std::string interface_name;

    // Extract interface name (format: "  interface_name: stats...")
    std::getline(line_stream, interface_name, ':');
    interface_name = interface_name.substr(interface_name.find_first_not_of(" \t"));

    // Skip loopback interface
    if (interface_name == "lo") {
      continue;
    }

    // Read stats (in order):
    // rx_bytes rx_packets rx_errs rx_drop rx_fifo rx_frame rx_compressed rx_multicast
    // tx_bytes tx_packets tx_errs tx_drop tx_fifo tx_colls tx_carrier tx_compressed
    unsigned long long rx_bytes, rx_packets, rx_errs, rx_drop;
    unsigned long long tx_bytes, tx_packets, tx_errs, tx_drop;

    line_stream >> rx_bytes >> rx_packets >> rx_errs >> rx_drop;
    // Skip 4 fields
    unsigned long long skip;
    line_stream >> skip >> skip >> skip >> skip;
    line_stream >> tx_bytes >> tx_packets >> tx_errs >> tx_drop;

    // Format as JSON
    if (!first_interface) {
      json += ",";
    }
    first_interface = false;

    json += "\"" + interface_name + "\":{";
    json += "\"rx_bytes\":" + std::to_string(rx_bytes) + ",";
    json += "\"rx_packets\":" + std::to_string(rx_packets) + ",";
    json += "\"rx_errors\":" + std::to_string(rx_errs) + ",";
    json += "\"rx_dropped\":" + std::to_string(rx_drop) + ",";
    json += "\"tx_bytes\":" + std::to_string(tx_bytes) + ",";
    json += "\"tx_packets\":" + std::to_string(tx_packets) + ",";
    json += "\"tx_errors\":" + std::to_string(tx_errs) + ",";
    json += "\"tx_dropped\":" + std::to_string(tx_drop);
    json += "}";
  }

  json += "}";
  net_dev_file.close();
  LOGI("Read network interface statistics");

  return env->NewStringUTF(json.c_str());
}

// Function to retrieve TCP connection statistics
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeSystemMonitor_getTcpConnectionsNative(
    JNIEnv* env, jobject /* this */) {
  std::ifstream tcp_file("/proc/net/tcp");
  std::string line;
  std::string json = "{\"connections\":[";
  bool first_connection = true;

  if (!tcp_file.is_open()) {
    LOGE("Failed to open /proc/net/tcp - Permission denied or file not available");
    // Get detailed error information
    char error_msg[256];
    strerror_r(errno, error_msg, sizeof(error_msg));
    std::string error_string = "Error: Failed to read TCP connection information. Reason: ";
    error_string += error_msg;
    return env->NewStringUTF(error_string.c_str());
  }

  // Skip header line
  std::getline(tcp_file, line);

  // Process each TCP connection information
  while (std::getline(tcp_file, line)) {
    std::stringstream line_stream(line);
    std::string sl, local_address, remote_address, status, tx_queue, rx_queue, tr, tm_when,
        retrnsmt, uid_str, timeout, inode, rest;

    line_stream >> sl >> local_address >> remote_address >> status >> tx_queue >> rx_queue >> tr >>
        tm_when >> retrnsmt >> uid_str >> timeout >> inode;

    // Convert hexadecimal status to integer
    int status_int = 0;
    std::stringstream ss;
    ss << std::hex << status;
    ss >> status_int;

    // Convert status to string
    std::string status_str;
    switch (status_int) {
      case 1:
        status_str = "ESTABLISHED";
        break;
      case 2:
        status_str = "SYN_SENT";
        break;
      case 3:
        status_str = "SYN_RECV";
        break;
      case 4:
        status_str = "FIN_WAIT1";
        break;
      case 5:
        status_str = "FIN_WAIT2";
        break;
      case 6:
        status_str = "TIME_WAIT";
        break;
      case 7:
        status_str = "CLOSE";
        break;
      case 8:
        status_str = "CLOSE_WAIT";
        break;
      case 9:
        status_str = "LAST_ACK";
        break;
      case 10:
        status_str = "LISTEN";
        break;
      case 11:
        status_str = "CLOSING";
        break;
      default:
        status_str = "UNKNOWN";
        break;
    }

    // Add data in JSON format
    if (!first_connection) {
      json += ",";
    }
    first_connection = false;

    json += "{";
    json += "\"local_address\":\"" + local_address + "\",";
    json += "\"remote_address\":\"" + remote_address + "\",";
    json += "\"status\":\"" + status_str + "\",";
    json += "\"uid\":" + uid_str + ",";
    json += "\"inode\":\"" + inode + "\"";
    json += "}";
  }

  json += "]}";
  tcp_file.close();
  LOGI("Read TCP connection statistics");

  return env->NewStringUTF(json.c_str());
}
