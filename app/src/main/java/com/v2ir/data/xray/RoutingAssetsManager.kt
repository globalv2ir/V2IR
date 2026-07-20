package com.v2ir.data.xray

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingAssetsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val geoDirectory: File
        get() = File(context.filesDir, GEO_DIR).also { it.mkdirs() }

    val geoipFile: File get() = File(geoDirectory, GEOIP_NAME)
    val geositeFile: File get() = File(geoDirectory, GEOSITE_NAME)

    suspend fun ensureRoutingAssets(): Boolean {
        return copyAssetIfNeeded(GEOIP_NAME) && copyAssetIfNeeded(GEOSITE_NAME)
    }

    fun areAssetsReady(): Boolean =
        geoipFile.exists() && geoipFile.length() > 0L &&
            geositeFile.exists() && geositeFile.length() > 0L

    private fun copyAssetIfNeeded(fileName: String): Boolean {
        val target = File(geoDirectory, fileName)
        if (target.exists() && target.length() > 0L) return true
        return try {
            context.assets.open(fileName).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.setReadable(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val GEO_DIR = "geo"
        const val GEOIP_NAME = "geoip.dat"
        const val GEOSITE_NAME = "geosite.dat"
    }
}




