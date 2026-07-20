/*
 * FIX (Bug #1): JNI package name mismatch corrected.
 *
 * Previous signatures used:
 *   Java_com_smartxray_client_data_xray_XrayNativeBridge_*
 * which never matched the actual Kotlin class at:
 *   com.v2ir.data.xray.XrayNativeBridge
 *
 * The JNI name mangling rule is:
 *   Java_<package_underscored>_<ClassName>_<methodName>
 * So the correct prefix is:
 *   Java_com_v2ir_data_xray_XrayNativeBridge_*
 *
 * These are stub implementations. When the real libxray.so or libsmartxray.so
 * is loaded (via downloadBinaries), these stubs are NOT used — the real library
 * provides its own JNI implementations. These stubs ensure the build succeeds
 * and XrayNativeBridge.isAvailable() returns false gracefully when only this
 * stub library is present.
 */
#include <jni.h>

extern "C" {

/*
 * Returns -1 to signal that this is the stub — XrayController will fall back
 * to XrayProcessRunner when it sees a non-zero return value.
 */
JNIEXPORT jint JNICALL
Java_com_v2ir_data_xray_XrayNativeBridge_startCore(
        JNIEnv *, jobject, jstring, jstring) {
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_v2ir_data_xray_XrayNativeBridge_stopCore(JNIEnv *, jobject) {
    return 0;
}

/*
 * Returns an empty array (size 0). XrayStatsPoller checks native.size >= 2
 * before accessing elements, so this is safe.
 */
JNIEXPORT jlongArray JNICALL
Java_com_v2ir_data_xray_XrayNativeBridge_queryStats(JNIEnv *env, jobject) {
    return env->NewLongArray(0);
}

}
