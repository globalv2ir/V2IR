import java.net.URI
import java.net.Proxy
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.v2ir"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.v2ir"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a: All modern Android devices (2017+)
            // x86_64:    Android emulators (for development/testing)
            // armeabi-v7a and x86 removed: Xray-core no longer publishes
            // binaries for these architectures (deprecated since ~2023).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // FIX (Bug #21): Configure Room schema export directory.
        // Required when exportSchema = true in @Database annotation.
        // Schema JSON files are written here and should be committed to VCS
        // to track migration history and enable MigrationTestHelper in tests.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Only build arm64-v8a in debug for faster iteration
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Explicitly exclude deprecated ABIs — Xray-core no longer publishes
            // armeabi-v7a or x86 binaries. These folders exist only for .gitkeep.
            excludes += listOf(
                "**/armeabi-v7a/*.so",
                "**/x86/*.so"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.json)
}

/**
 * Download Xray binary + geo routing assets.
 * VPN tunnel uses hev-socks5-tunnel compiled via NDK — build via: ./gradlew buildHevTunnel
 */
tasks.register("downloadBinaries") {
    group = "v2ir"
    description = "Downloads Xray binary and geoip/geosite assets for all ABIs"

    doLast {
        val assetsDir = file("src/main/assets")
        assetsDir.mkdirs()

        // Set up proxy for downloads
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 12334))

        val abis = listOf("arm64-v8a", "x86_64")
        val abiUrlMap = mapOf(
            "arm64-v8a" to "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-android-arm64-v8a.zip",
            "x86_64"    to "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-android-amd64.zip"
        )
        val geoFiles = mapOf(
            "geoip.dat" to "https://github.com/loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            "geosite.dat" to "https://github.com/loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
        )

        // Download Geo files
        geoFiles.forEach { (fileName, url) ->
            val destFile = File(assetsDir, fileName)
            println("Downloading $fileName via proxy...")
            try {
                URI.create(url).toURL().openConnection(proxy).getInputStream().use { input ->
                    Files.copy(input, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                println("Failed to download $fileName: ${e.message}")
            }
        }

        // Download Xray for each ABI
        abis.forEach { abi ->
            val abiDir = file("src/main/assets/xray/$abi")
            val jniAbiDir = file("src/main/jniLibs/$abi")
            abiDir.mkdirs()
            jniAbiDir.mkdirs()

            // Use the correct URL per ABI (arm64-v8a vs amd64 for x86_64)
            val url = abiUrlMap[abi] ?: return@forEach
            val zipFile = File(abiDir, "xray.zip")
            
            try {
                println("Downloading Xray for $abi via proxy...")
                URI.create(url).toURL().openConnection(proxy).getInputStream().use { input ->
                    Files.copy(input, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (!entry.isDirectory && entry.name.lowercase().contains("xray")) {
                            // Extract to both assets (fallback) and jniLibs (for Android 10+)
                            val extractedAsset = File(abiDir, "xray")
                            val extractedLib = File(jniAbiDir, "libxray.so")
                            
                            zip.getInputStream(entry).use { input ->
                                val bytes = input.readBytes()
                                Files.write(extractedAsset.toPath(), bytes)
                                Files.write(extractedLib.toPath(), bytes)
                            }
                            extractedAsset.setExecutable(true, false)
                            extractedLib.setExecutable(true, false)
                        }
                    }
                }
                zipFile.delete()
                println("Xray for $abi ready (Assets & jniLibs)")
            } catch (e: Exception) {
                println("Failed to download Xray for $abi: ${e.message}")
            }
        }
    }
}

/** Builds libhev-socks5-tunnel.so (requires Android NDK + git). */
tasks.register<Exec>("buildHevTunnel") {
    group = "v2ir"
    description = "Compile hev-socks5-tunnel native library into jniLibs"
    workingDir = rootProject.projectDir
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    if (isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/compile-hevtun.ps1")
    } else {
        commandLine("bash", "scripts/compile-hevtun.sh")
    }
}

tasks.named("preBuild").configure {
    val hevLib = file("src/main/jniLibs/arm64-v8a/libhev-socks5-tunnel.so")
    if (!hevLib.exists()) {
        logger.warn(
            "libhev-socks5-tunnel.so not found. VPN will not work until you run: ./gradlew buildHevTunnel"
        )
    }
}
