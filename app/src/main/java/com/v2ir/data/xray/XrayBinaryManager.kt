package com.v2ir.data.xray

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayBinaryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val xrayDirectory: File
        get() = File(context.filesDir, "xray").also { it.mkdirs() }

    fun getXrayBinary(): File {
        // Log all possible locations for debugging
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val libXray = File(nativeDir, "libxray.so")
        
        // Priority 1: Native library directory (extracted by Android, name libxray.so)
        if (libXray.exists()) {
            return libXray
        }
        
        // Priority 2: Native library directory with alternate name (if any)
        val files = File(nativeDir).listFiles()
        val found = files?.find { it.name.contains("xray") && it.name.endsWith(".so") }
        if (found != null) return found

        // Priority 3: Custom directory in filesDir (deprecated for Android 10+, but kept for older)
        return File(xrayDirectory, XRAY_BINARY_NAME)
    }

    fun ensureXray(): Boolean {
        val binary = getXrayBinary()
        
        // If it's in the native library directory, it's already managed by Android
        if (binary.absolutePath.startsWith(context.applicationInfo.nativeLibraryDir)) {
            return binary.exists()
        }

        // Otherwise, try to extract and set executable
        if (binary.exists() && binary.length() > 0L) {
            binary.setExecutable(true, false)
            return true
        }
        return extractBinary(XRAY_BINARY_NAME, binary)
    }

    fun ensureBinaries(): Boolean = ensureXray()

    fun isXrayReady(): Boolean {
        val binary = getXrayBinary()
        return binary.exists() && binary.length() > 0L && binary.canExecute()
    }

    private fun extractBinary(assetName: String, target: File): Boolean {
        if (target.exists() && target.length() > 0L) {
            target.setExecutable(true, false)
            return true
        }
        val abiAsset = "xray/${abiFolder()}/$assetName"
        return try {
            context.assets.open(abiAsset).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.setExecutable(true, false)
            true
        } catch (_: Exception) {
            try {
                context.assets.open("xray/$assetName").use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target.setExecutable(true, false)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun abiFolder(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "arm64-v8a"
            abi.startsWith("armeabi-v7") || abi.startsWith("arm") -> "armeabi-v7a"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }

    companion object {
        const val XRAY_BINARY_NAME = "xray"
    }
}




