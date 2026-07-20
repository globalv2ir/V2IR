#!/usr/bin/env kotlin
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

println("Downloading Xray + geo assets (tunnel = hev-socks5-tunnel via ./gradlew buildHevTunnel)")

val assetsDir = File("app/src/main/assets/xray/arm64-v8a")
assetsDir.mkdirs()

val binaries = mapOf(
    "geoip.dat" to "https://github.com/loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
    "geosite.dat" to "https://github.com/loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
    "xray.zip" to "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-android-arm64-v8a.zip"
)

binaries.forEach { (fileName, url) ->
    val destFile = File(assetsDir, fileName)
    println("Downloading $fileName...")
    try {
        URL(url).openStream().use { input ->
            Files.copy(input, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (fileName.endsWith(".zip")) {
            ZipFile(destFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && entry.name.lowercase().contains("xray")) {
                        val out = File(assetsDir, "xray")
                        zip.getInputStream(entry).use { input ->
                            Files.copy(input, out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        out.setExecutable(true, false)
                        println("Extracted xray")
                    }
                }
            }
            destFile.delete()
        }
    } catch (e: Exception) {
        println("Error: $fileName — ${e.message}")
    }
}

println("Done.")
