# Native methods are resolved by name from JNI.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.aivoice.flow.whisper.WhisperNative { *; }
