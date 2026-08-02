// The loader is reached through dlopen below rather than linked, so the prototypes this header
// would otherwise declare would be undefined symbols in a library that links no libvulkan.
#define VK_NO_PROTOTYPES

#include <dlfcn.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <optional>
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
 * The Vulkan loader, opened once and left open for the life of the process.
 *
 * dlopen rather than a link against libvulkan: linking would make the whole of
 * libsystem_monitor.so fail to load on a device with no Vulkan loader, and with it every other
 * native reading in this app. Opening it here costs one failed dlopen on such a device and lets
 * the screen say so.
 *
 * Never closed, which is the ordinary practice for a graphics driver on Android. A driver starts
 * helper threads and registers pthread_key destructors of its own while an instance exists, and
 * several do not take them down with the instance; dropping the last reference would unmap the
 * driver underneath them, and the crash would land on a thread this app does not own — after the
 * screen had finished loading, or on the next refresh. The cost of keeping it is one mapping in a
 * process that has already asked for Vulkan once.
 */
void* Loader() {
    static void* const handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    return handle;
}

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

/** The device's extension names, or nothing where the enumeration itself failed. */
std::optional<std::vector<std::string>> ReadExtensions(const VulkanApi& api,
                                                       VkPhysicalDevice device) {
    uint32_t count = 0;
    if (api.EnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr) != VK_SUCCESS) {
        return std::nullopt;
    }
    std::vector<VkExtensionProperties> properties(count);
    if (count > 0) {
        // VK_INCOMPLETE is not a failure here any more than it is for the physical devices in
        // Collect(): it says the list grew between the sizing call and this one, and `count` holds
        // how many were written. Treating it as a failure would drop every extension the driver
        // just handed over and take the whole card off the screen with them.
        const VkResult listed =
                api.EnumerateDeviceExtensionProperties(device, nullptr, &count, properties.data());
        if (listed != VK_SUCCESS && listed != VK_INCOMPLETE) {
            return std::nullopt;
        }
        properties.resize(count);
    }

    std::vector<std::string> names;
    names.reserve(properties.size());
    for (const VkExtensionProperties& property : properties) {
        names.emplace_back(property.extensionName);
    }
    return names;
}

/** Absent where the enumeration failed, which is not the same as a device supporting none. */
void WriteExtensions(JsonWriter* writer,
                     const std::optional<std::vector<std::string>>& extensions) {
    if (!extensions.has_value()) {
        return;
    }
    writer->Key("extensions").BeginArray();
    for (const std::string& name : *extensions) {
        writer->Value(name);
    }
    writer->EndArray();
}

/**
 * Whether this device can be asked which driver is behind it.
 *
 * Three separate conditions, and none of them implies another. `vkGetPhysicalDeviceProperties2` is
 * the entry point the query is chained onto and reached core in Vulkan 1.1; the structure being
 * chained, `VkPhysicalDeviceDriverProperties`, reached core in 1.2 and exists below that only where
 * the device enumerates `VK_KHR_driver_properties`. Chaining a structure a device supports neither
 * way is not something an implementation is required to tolerate, so it is not chained.
 *
 * Asked per physical device rather than per instance. The device reports its own API version, which
 * is not the version the instance was created with and need not be the same on two devices of one
 * machine — a discrete GPU and a software rasteriser in the same process routinely differ.
 */
bool CanQueryDriver(const VulkanApi& api, uint32_t device_api_version,
                    const std::optional<std::vector<std::string>>& extensions) {
    if (api.GetPhysicalDeviceProperties2 == nullptr) {
        return false;
    }
    if (device_api_version >= VK_API_VERSION_1_2) {
        return true;
    }
    if (!extensions.has_value()) {
        return false;
    }
    return std::find(extensions->begin(), extensions->end(),
                     VK_KHR_DRIVER_PROPERTIES_EXTENSION_NAME) != extensions->end();
}

void WriteDevice(JsonWriter* writer, const VulkanApi& api, VkPhysicalDevice device) {
    // The 1.0 query first, because it is the one every implementation has and because what it
    // reports — this device's own API version — is what decides whether the driver structure below
    // may be chained at all.
    VkPhysicalDeviceProperties base = {};
    api.GetPhysicalDeviceProperties(device, &base);

    const std::optional<std::vector<std::string>> extensions = ReadExtensions(api, device);
    const bool driver_query = CanQueryDriver(api, base.apiVersion, extensions);

    VkPhysicalDeviceDriverProperties driver = {};
    driver.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;

    VkPhysicalDeviceProperties2 properties2 = {};
    properties2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    properties2.properties = base;
    if (driver_query) {
        properties2.pNext = &driver;
        api.GetPhysicalDeviceProperties2(device, &properties2);
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

    // Said per device, because it is decided per device: whether this one could be asked which
    // driver is behind it. Where it could not, the keys below are absent because no query was made;
    // where it could and they are still absent, the driver was asked and did not answer — it may
    // leave a structure it supports untouched, and the zero it was initialised with is how that
    // arrives, since no VkDriverId is zero.
    writer->Field("driver_query_available", driver_query);

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
    WriteExtensions(writer, extensions);
}

/**
 * Fetches `vkDestroyInstance` on its own, ahead of everything else.
 *
 * It is the one function whose absence would strand the instance that was just created, so it is
 * resolved first and the guard is built from it before anything that can leave the scope runs.
 * `vkGetInstanceProcAddr` is required to answer for a core 1.0 entry point given a live instance,
 * and the loader exports the symbol directly as well — a loader that answers one and not the other
 * still gets its instance destroyed.
 */
PFN_vkDestroyInstance ResolveDestroyInstance(PFN_vkGetInstanceProcAddr get, void* loader,
                                             VkInstance instance) {
    const auto destroy = Resolve<PFN_vkDestroyInstance>(get, instance, "vkDestroyInstance");
    if (destroy != nullptr) {
        return destroy;
    }
    return reinterpret_cast<PFN_vkDestroyInstance>(dlsym(loader, "vkDestroyInstance"));
}

/** Fetches the remaining instance-level functions. False when one that is core in 1.0 is missing.
 */
bool ResolveInstanceFunctions(VulkanApi* api, VkInstance instance) {
    const PFN_vkGetInstanceProcAddr get = api->GetInstanceProcAddr;

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

    return api->EnumeratePhysicalDevices != nullptr &&
           api->GetPhysicalDeviceProperties != nullptr &&
           api->GetPhysicalDeviceMemoryProperties != nullptr &&
           api->GetPhysicalDeviceQueueFamilyProperties != nullptr &&
           api->EnumerateDeviceExtensionProperties != nullptr;
}

std::string Collect() {
    JsonWriter writer;
    writer.BeginObject();

    void* const loader = Loader();
    if (loader == nullptr) {
        writer.Field("loader_present", false);
        writer.EndObject();
        return writer.Take();
    }
    writer.Field("loader_present", true);

    VulkanApi api;
    api.GetInstanceProcAddr =
            reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(loader, "vkGetInstanceProcAddr"));
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

    // Resolved and guarded before anything else runs against the instance: every branch below this
    // point can leave the scope, and the guard is the only thing that destroys what was just
    // created. A null here is a loader that answers neither query for a core 1.0 entry point, which
    // leaves no way to destroy the instance at all — so the collector stops rather than going on to
    // read a device with an instance it cannot give back.
    api.DestroyInstance = ResolveDestroyInstance(api.GetInstanceProcAddr, loader, instance);
    const ScopedInstance scoped(api.DestroyInstance, instance);
    if (api.DestroyInstance == nullptr || !ResolveInstanceFunctions(&api, instance)) {
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
