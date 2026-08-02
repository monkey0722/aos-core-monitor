// The loader is reached through dlopen below rather than linked, so the prototypes this header
// would otherwise declare would be undefined symbols in a library that links no libvulkan.
#define VK_NO_PROTOTYPES

#include <dlfcn.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "native_util.h"

namespace {

using aoscm::JsonWriter;

constexpr char kHexDigits[] = "0123456789abcdef";

/**
 * The entry points this collector calls.
 *
 * Vulkan has no link-time ABI worth binding to: every function past `vkGetInstanceProcAddr` is
 * fetched by name, and which of them exist depends on the version the loader in front of the
 * driver implements. Holding them in one struct is what lets the collector ask whether a function
 * is there instead of assuming it.
 */
struct VulkanApi {
    PFN_vkGetInstanceProcAddr GetInstanceProcAddr = nullptr;
    PFN_vkEnumerateInstanceVersion EnumerateInstanceVersion = nullptr;
    PFN_vkCreateInstance CreateInstance = nullptr;
    PFN_vkDestroyInstance DestroyInstance = nullptr;
    PFN_vkEnumeratePhysicalDevices EnumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties = nullptr;
    PFN_vkGetPhysicalDeviceProperties2 GetPhysicalDeviceProperties2 = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties = nullptr;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties GetPhysicalDeviceQueueFamilyProperties = nullptr;
    PFN_vkEnumerateDeviceExtensionProperties EnumerateDeviceExtensionProperties = nullptr;
};

template <typename Fn>
[[nodiscard]] Fn Resolve(PFN_vkGetInstanceProcAddr get, VkInstance instance, const char* name) {
    return reinterpret_cast<Fn>(get(instance, name));
}

/**
 * Holds the loader open for as long as anything fetched from it is in use.
 *
 * dlopen rather than a link against libvulkan: linking would make the whole of
 * libsystem_monitor.so fail to load on a device with no Vulkan loader, and with it every other
 * native reading in this app. Opening it here costs one failed dlopen on such a device and lets
 * the screen say so.
 */
class Loader {
public:
    Loader() : handle_(dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL)) {}
    ~Loader() {
        if (handle_ != nullptr) {
            dlclose(handle_);
        }
    }
    Loader(const Loader&) = delete;
    Loader& operator=(const Loader&) = delete;

    [[nodiscard]] void* get() const { return handle_; }

private:
    void* handle_;
};

/** Destroys the instance however the scope is left, exception included. */
class ScopedInstance {
public:
    ScopedInstance(PFN_vkDestroyInstance destroy, VkInstance instance)
        : destroy_(destroy), instance_(instance) {}
    ~ScopedInstance() {
        if (destroy_ != nullptr && instance_ != VK_NULL_HANDLE) {
            destroy_(instance_, nullptr);
        }
    }
    ScopedInstance(const ScopedInstance&) = delete;
    ScopedInstance& operator=(const ScopedInstance&) = delete;

private:
    PFN_vkDestroyInstance destroy_;
    VkInstance instance_;
};

/** A packed Vulkan version as the three numbers it encodes. */
std::string VersionString(uint32_t version) {
    return std::to_string(VK_API_VERSION_MAJOR(version)) + '.' +
           std::to_string(VK_API_VERSION_MINOR(version)) + '.' +
           std::to_string(VK_API_VERSION_PATCH(version));
}

/** The four numbers a conformance version carries, which is one more than a Vulkan version has. */
std::string ConformanceString(const VkConformanceVersion& version) {
    return std::to_string(static_cast<unsigned>(version.major)) + '.' +
           std::to_string(static_cast<unsigned>(version.minor)) + '.' +
           std::to_string(static_cast<unsigned>(version.subminor)) + '.' +
           std::to_string(static_cast<unsigned>(version.patch));
}

std::string HexBytes(const uint8_t* bytes, size_t count) {
    std::string hex;
    hex.reserve(count * 2);
    for (size_t i = 0; i < count; ++i) {
        hex.push_back(kHexDigits[bytes[i] >> 4]);
        hex.push_back(kHexDigits[bytes[i] & 0xF]);
    }
    return hex;
}

std::string_view DeviceTypeName(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU:
            return "integrated_gpu";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:
            return "discrete_gpu";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:
            return "virtual_gpu";
        case VK_PHYSICAL_DEVICE_TYPE_CPU:
            return "cpu";
        case VK_PHYSICAL_DEVICE_TYPE_OTHER:
            return "other";
        default:
            return "unknown";
    }
}

/**
 * The driver behind the device, as the one enum that names it.
 *
 * This is the reading the whole screen is for: nothing in the Java API distinguishes a Qualcomm
 * driver from a Mesa one, and the renderer string GLES offers is vendor-formatted prose. Only the
 * ids that turn up on Android are named; anything else is reported by number instead of guessed at.
 */
std::string_view DriverIdName(VkDriverId id) {
    switch (id) {
        case VK_DRIVER_ID_QUALCOMM_PROPRIETARY:
            return "qualcomm_proprietary";
        case VK_DRIVER_ID_ARM_PROPRIETARY:
            return "arm_proprietary";
        case VK_DRIVER_ID_IMAGINATION_PROPRIETARY:
            return "imagination_proprietary";
        case VK_DRIVER_ID_IMAGINATION_OPEN_SOURCE_MESA:
            return "imagination_mesa";
        case VK_DRIVER_ID_SAMSUNG_PROPRIETARY:
            return "samsung_proprietary";
        case VK_DRIVER_ID_BROADCOM_PROPRIETARY:
            return "broadcom_proprietary";
        case VK_DRIVER_ID_VERISILICON_PROPRIETARY:
            return "verisilicon_proprietary";
        case VK_DRIVER_ID_MESA_TURNIP:
            return "mesa_turnip";
        case VK_DRIVER_ID_MESA_PANVK:
            return "mesa_panvk";
        case VK_DRIVER_ID_MESA_V3DV:
            return "mesa_v3dv";
        case VK_DRIVER_ID_MESA_VENUS:
            return "mesa_venus";
        case VK_DRIVER_ID_MESA_LLVMPIPE:
            return "mesa_llvmpipe";
        case VK_DRIVER_ID_GOOGLE_SWIFTSHADER:
            return "google_swiftshader";
        default:
            return "";
    }
}

/** The results this collector can actually provoke. Anything else crosses as its number. */
std::string_view ResultName(VkResult result) {
    switch (result) {
        case VK_ERROR_OUT_OF_HOST_MEMORY:
            return "out_of_host_memory";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY:
            return "out_of_device_memory";
        case VK_ERROR_INITIALIZATION_FAILED:
            return "initialization_failed";
        case VK_ERROR_LAYER_NOT_PRESENT:
            return "layer_not_present";
        case VK_ERROR_EXTENSION_NOT_PRESENT:
            return "extension_not_present";
        case VK_ERROR_INCOMPATIBLE_DRIVER:
            return "incompatible_driver";
        default:
            return "";
    }
}

void WriteFailure(JsonWriter* writer, VkResult result) {
    const std::string_view name = ResultName(result);
    if (!name.empty()) {
        writer->Field("instance_error", name);
    } else {
        writer->Field("instance_error_code", static_cast<int64_t>(result));
    }
}

void WriteMemory(JsonWriter* writer, const VulkanApi& api, VkPhysicalDevice device) {
    VkPhysicalDeviceMemoryProperties memory = {};
    api.GetPhysicalDeviceMemoryProperties(device, &memory);

    writer->Key("memory_heaps").BeginArray();
    for (uint32_t index = 0; index < memory.memoryHeapCount; ++index) {
        const VkMemoryHeap& heap = memory.memoryHeaps[index];
        writer->BeginObject();
        writer->Field("index", static_cast<uint64_t>(index));
        writer->Field("size_bytes", static_cast<uint64_t>(heap.size));
        writer->Field("device_local", (heap.flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0);
        writer->EndObject();
    }
    writer->EndArray();

    writer->Key("memory_types").BeginArray();
    for (uint32_t index = 0; index < memory.memoryTypeCount; ++index) {
        const VkMemoryType& type = memory.memoryTypes[index];
        const VkMemoryPropertyFlags flags = type.propertyFlags;
        writer->BeginObject();
        writer->Field("index", static_cast<uint64_t>(index));
        writer->Field("heap", static_cast<uint64_t>(type.heapIndex));
        writer->Field("device_local", (flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0);
        writer->Field("host_visible", (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0);
        writer->Field("host_coherent", (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0);
        writer->Field("host_cached", (flags & VK_MEMORY_PROPERTY_HOST_CACHED_BIT) != 0);
        writer->Field("lazily_allocated", (flags & VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT) != 0);
        writer->Field("protected_memory", (flags & VK_MEMORY_PROPERTY_PROTECTED_BIT) != 0);
        writer->EndObject();
    }
    writer->EndArray();
}

void WriteQueueFamilies(JsonWriter* writer, const VulkanApi& api, VkPhysicalDevice device) {
    uint32_t count = 0;
    api.GetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count);
    if (count > 0) {
        api.GetPhysicalDeviceQueueFamilyProperties(device, &count, families.data());
        families.resize(count);
    }

    writer->Key("queue_families").BeginArray();
    for (size_t index = 0; index < families.size(); ++index) {
        const VkQueueFamilyProperties& family = families[index];
        const VkQueueFlags flags = family.queueFlags;
        writer->BeginObject();
        writer->Field("index", static_cast<uint64_t>(index));
        writer->Field("count", static_cast<uint64_t>(family.queueCount));
        writer->Field("graphics", (flags & VK_QUEUE_GRAPHICS_BIT) != 0);
        writer->Field("compute", (flags & VK_QUEUE_COMPUTE_BIT) != 0);
        writer->Field("transfer", (flags & VK_QUEUE_TRANSFER_BIT) != 0);
        writer->Field("sparse_binding", (flags & VK_QUEUE_SPARSE_BINDING_BIT) != 0);
        writer->Field("protected_memory", (flags & VK_QUEUE_PROTECTED_BIT) != 0);
        writer->Field("timestamp_bits", static_cast<uint64_t>(family.timestampValidBits));
        writer->EndObject();
    }
    writer->EndArray();
}

void WriteLimits(JsonWriter* writer, const VkPhysicalDeviceLimits& limits) {
    writer->Key("limits").BeginObject();
    writer->Field("max_image_dimension_2d", static_cast<uint64_t>(limits.maxImageDimension2D));
    writer->Field("max_bound_descriptor_sets",
                  static_cast<uint64_t>(limits.maxBoundDescriptorSets));
    writer->Field("max_push_constants_size", static_cast<uint64_t>(limits.maxPushConstantsSize));
    writer->Field("max_memory_allocation_count",
                  static_cast<uint64_t>(limits.maxMemoryAllocationCount));
    writer->Field("max_compute_shared_memory_size",
                  static_cast<uint64_t>(limits.maxComputeSharedMemorySize));
    writer->Field("max_compute_work_group_invocations",
                  static_cast<uint64_t>(limits.maxComputeWorkGroupInvocations));
    writer->EndObject();
}

void WriteExtensions(JsonWriter* writer, const VulkanApi& api, VkPhysicalDevice device) {
    uint32_t count = 0;
    if (api.EnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr) != VK_SUCCESS) {
        return;
    }
    std::vector<VkExtensionProperties> extensions(count);
    if (count > 0) {
        if (api.EnumerateDeviceExtensionProperties(device, nullptr, &count, extensions.data()) !=
            VK_SUCCESS) {
            return;
        }
        extensions.resize(count);
    }

    writer->Key("extensions").BeginArray();
    for (const VkExtensionProperties& extension : extensions) {
        writer->Value(extension.extensionName);
    }
    writer->EndArray();
}

void WriteDevice(JsonWriter* writer, const VulkanApi& api, VkPhysicalDevice device) {
    VkPhysicalDeviceDriverProperties driver = {};
    driver.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;

    VkPhysicalDeviceProperties2 properties2 = {};
    properties2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    properties2.pNext = &driver;

    if (api.GetPhysicalDeviceProperties2 != nullptr) {
        api.GetPhysicalDeviceProperties2(device, &properties2);
    } else {
        api.GetPhysicalDeviceProperties(device, &properties2.properties);
    }
    const VkPhysicalDeviceProperties& properties = properties2.properties;

    writer->Field("name", properties.deviceName);
    writer->Field("type", DeviceTypeName(properties.deviceType));
    writer->Field("api_version", VersionString(properties.apiVersion));
    writer->FieldHex("vendor_id", static_cast<uint64_t>(properties.vendorID));
    writer->FieldHex("device_id", static_cast<uint64_t>(properties.deviceID));

    // How a driver packs its own version is vendor-defined, so the number is published as it was
    // read. The conventional major.minor.patch split is offered beside it rather than instead of
    // it: on a driver that packs differently the split is wrong, and the raw value never is.
    writer->Field("driver_version_raw", static_cast<uint64_t>(properties.driverVersion));
    writer->Field("driver_version", VersionString(properties.driverVersion));

    // What changes when the driver is updated, which is the one identifier that does.
    writer->Field("pipeline_cache_uuid", HexBytes(properties.pipelineCacheUUID, VK_UUID_SIZE));

    // VkPhysicalDeviceDriverProperties reached core in Vulkan 1.2. A 1.1 driver answers the
    // properties2 query but leaves a structure it does not know untouched, so the zero it was
    // initialised with is how "this driver does not report a driver id" arrives — no VkDriverId is
    // zero.
    if (driver.driverID != 0) {
        const std::string_view name = DriverIdName(driver.driverID);
        if (!name.empty()) {
            writer->Field("driver_id_name", name);
        }
        writer->Field("driver_id", static_cast<uint64_t>(driver.driverID));
        writer->Field("driver_name", driver.driverName);
        writer->Field("driver_info", driver.driverInfo);
        writer->Field("conformance_version", ConformanceString(driver.conformanceVersion));
    }

    WriteLimits(writer, properties.limits);
    WriteMemory(writer, api, device);
    WriteQueueFamilies(writer, api, device);
    WriteExtensions(writer, api, device);
}

/** Fetches the instance-level functions. False when one that is core in 1.0 is missing. */
bool ResolveInstanceFunctions(VulkanApi* api, VkInstance instance) {
    const PFN_vkGetInstanceProcAddr get = api->GetInstanceProcAddr;

    api->DestroyInstance = Resolve<PFN_vkDestroyInstance>(get, instance, "vkDestroyInstance");
    api->EnumeratePhysicalDevices =
            Resolve<PFN_vkEnumeratePhysicalDevices>(get, instance, "vkEnumeratePhysicalDevices");
    api->GetPhysicalDeviceProperties = Resolve<PFN_vkGetPhysicalDeviceProperties>(
            get, instance, "vkGetPhysicalDeviceProperties");
    api->GetPhysicalDeviceMemoryProperties = Resolve<PFN_vkGetPhysicalDeviceMemoryProperties>(
            get, instance, "vkGetPhysicalDeviceMemoryProperties");
    api->GetPhysicalDeviceQueueFamilyProperties =
            Resolve<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
                    get, instance, "vkGetPhysicalDeviceQueueFamilyProperties");
    api->EnumerateDeviceExtensionProperties = Resolve<PFN_vkEnumerateDeviceExtensionProperties>(
            get, instance, "vkEnumerateDeviceExtensionProperties");

    // Core since 1.1 and legitimately absent below it, so its absence is a fallback rather than a
    // failure. It is the only way to the driver id.
    api->GetPhysicalDeviceProperties2 = Resolve<PFN_vkGetPhysicalDeviceProperties2>(
            get, instance, "vkGetPhysicalDeviceProperties2");

    return api->DestroyInstance != nullptr && api->EnumeratePhysicalDevices != nullptr &&
           api->GetPhysicalDeviceProperties != nullptr &&
           api->GetPhysicalDeviceMemoryProperties != nullptr &&
           api->GetPhysicalDeviceQueueFamilyProperties != nullptr &&
           api->EnumerateDeviceExtensionProperties != nullptr;
}

std::string Collect() {
    JsonWriter writer;
    writer.BeginObject();

    // Declared before the instance below, so that the instance — which holds pointers into this
    // library — is destroyed before the library is closed.
    const Loader loader;
    if (loader.get() == nullptr) {
        writer.Field("loader_present", false);
        writer.EndObject();
        return writer.Take();
    }
    writer.Field("loader_present", true);

    VulkanApi api;
    api.GetInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(loader.get(), "vkGetInstanceProcAddr"));
    if (api.GetInstanceProcAddr == nullptr) {
        writer.Field("instance_ok", false);
        writer.Field("instance_error", "initialization_failed");
        writer.EndObject();
        return writer.Take();
    }

    // Absent on a 1.0 loader, and there is no other way to tell one apart: every other version
    // query needs an instance, and creating one means naming the version first.
    api.EnumerateInstanceVersion = Resolve<PFN_vkEnumerateInstanceVersion>(
            api.GetInstanceProcAddr, nullptr, "vkEnumerateInstanceVersion");
    uint32_t instance_version = VK_API_VERSION_1_0;
    if (api.EnumerateInstanceVersion == nullptr ||
        api.EnumerateInstanceVersion(&instance_version) != VK_SUCCESS) {
        instance_version = VK_API_VERSION_1_0;
    }
    writer.Field("instance_version", VersionString(instance_version));

    api.CreateInstance =
            Resolve<PFN_vkCreateInstance>(api.GetInstanceProcAddr, nullptr, "vkCreateInstance");
    if (api.CreateInstance == nullptr) {
        writer.Field("instance_ok", false);
        writer.Field("instance_error", "initialization_failed");
        writer.EndObject();
        return writer.Take();
    }

    VkApplicationInfo application = {};
    application.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    application.pApplicationName = "AOS Core Monitor";
    // Asking for more than the loader reported fails outright with VK_ERROR_INCOMPATIBLE_DRIVER, so
    // the request is capped at what it just said. 1.1 is as high as this collector needs: that is
    // where vkGetPhysicalDeviceProperties2 became core.
    application.apiVersion = std::min<uint32_t>(instance_version, VK_API_VERSION_1_1);

    // No layers and no extensions: this reads the physical devices and never presents anything, so
    // it needs neither a surface nor a swapchain, and it creates no logical device.
    VkInstanceCreateInfo create = {};
    create.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create.pApplicationInfo = &application;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult created = api.CreateInstance(&create, nullptr, &instance);
    if (created != VK_SUCCESS) {
        // A device with no Vulkan driver behind the loader answers here, and so does one whose
        // driver refused to start. Which of the two it was is what the result carries.
        writer.Field("instance_ok", false);
        WriteFailure(&writer, created);
        writer.EndObject();
        return writer.Take();
    }

    const bool resolved = ResolveInstanceFunctions(&api, instance);
    const ScopedInstance scoped(api.DestroyInstance, instance);
    if (!resolved) {
        writer.Field("instance_ok", false);
        writer.Field("instance_error", "initialization_failed");
        writer.EndObject();
        return writer.Take();
    }
    writer.Field("instance_ok", true);

    // Both results are checked, because the vector is sized from the first call and filled by the
    // second: a failure in either leaves entries value-initialised — a null VkPhysicalDevice — and
    // the loop below would hand one to vkGetPhysicalDeviceProperties. VK_INCOMPLETE is not a
    // failure; it says the list grew between the two calls and device_count holds how many were
    // written.
    uint32_t device_count = 0;
    if (api.EnumeratePhysicalDevices(instance, &device_count, nullptr) != VK_SUCCESS) {
        device_count = 0;
    }
    std::vector<VkPhysicalDevice> devices(device_count);
    if (device_count > 0) {
        const VkResult listed =
                api.EnumeratePhysicalDevices(instance, &device_count, devices.data());
        if (listed != VK_SUCCESS && listed != VK_INCOMPLETE) {
            devices.clear();
        } else {
            devices.resize(device_count);
        }
    }

    writer.Key("devices").BeginArray();
    for (VkPhysicalDevice device : devices) {
        writer.BeginObject();
        WriteDevice(&writer, api, device);
        writer.EndObject();
    }
    writer.EndArray();

    writer.EndObject();
    return writer.Take();
}

}  // namespace

/**
 * The GPUs Vulkan can see, and which driver is behind each.
 *
 * Vulkan has no Java binding on Android — it is a C API and the platform has never wrapped it — so
 * none of this is reachable from the framework. What the Java side can reach is the GLES renderer
 * string, which is three lines of vendor-formatted prose; the driver id, the conformance version
 * the driver passed, the memory heaps and the queue families are only here.
 *
 * Nothing is drawn and no logical device is created: the instance exists to enumerate physical
 * devices and is destroyed before this returns.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aoscoremonitor_diagnostics_jni_NativeVulkanInspector_getVulkanNative(JNIEnv* env,
                                                                              jobject /* this */) {
    return aoscm::ReturnJson(env, [] { return Collect(); });
}
