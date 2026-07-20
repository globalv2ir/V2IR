package com.v2ir.data.xray

/**
 * JNI bridge to libsmartxray / libXray.
 * When the native library is not bundled, [isAvailable] is false and
 * [XrayProcessRunner] handles core execution instead.
 */
object XrayNativeBridge {

    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("smartxray")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("xray")
                libraryLoaded = true
            } catch (_: UnsatisfiedLinkError) {
                libraryLoaded = false
            }
        }
    }

    fun isAvailable(): Boolean = libraryLoaded

    external fun startCore(configPath: String, assetsDir: String): Int

    external fun stopCore(): Int

    /** Returns [downlinkBytes, uplinkBytes, downlinkSpeed, uplinkSpeed] or empty if unavailable. */
    external fun queryStats(): LongArray
}




