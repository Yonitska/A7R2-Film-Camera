package com.github.ma1co.openmemories.tweak;

/** Read-only camera identity properties from OpenMemories Tweak v0.11 (MIT,
 * © 2016 ma1co). The package/JNI names must match libtweak.so. The upstream
 * ItemActivity adapter interface is intentionally omitted. */
public class NativeProperty {
    public enum Key {
        ANDROID_PLATFORM_VERSION("android_platform_version"),
        BACKUP_REGION("backup_region"),
        MODEL_NAME("model_name"),
        SERIAL_NUMBER("serial_number");

        public final String str;
        Key(String str) { this.str = str; }
    }

    static {
        System.loadLibrary("tweak");
    }

    private static native boolean nativeIsAvailable(String key);
    private static native String nativeGetStringValue(String key);

    private final String key;

    public NativeProperty(Key key) { this.key = key.str; }

    public boolean isAvailable() { return nativeIsAvailable(key); }

    public String getValue() { return nativeGetStringValue(key); }
}
