# The JNI entry points are bound by name.
#
# libsystem_monitor exports symbols spelled out of the Java package and class the method is declared
# in — Java_com_aoscoremonitor_diagnostics_jni_NativeCpuInspector_getCpuStaticNative and its
# fifteen siblings — so renaming one of these classes silently breaks the lookup, and the failure
# arrives at runtime as an UnsatisfiedLinkError on a screen that then shows nothing.
#
# AGP's own proguard-android-optimize.txt already keeps the names of classes that declare native
# methods. This says it again for the package that depends on it, because that default is a
# property of the Android Gradle plugin version rather than of this app, and what it protects is
# only observable by running the app on a device.
-keepclasseswithmembernames,includedescriptorclasses class com.aoscoremonitor.diagnostics.jni.** {
    native <methods>;
}

# Keep the line numbers in a native crash's Java frames, and drop the source file name that would
# otherwise give the obfuscation away for nothing.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
