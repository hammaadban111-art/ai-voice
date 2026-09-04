import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// ---------------------------------------------------------------------------
// Whisper model bundling.
//
// The GGML weights are ~190 MB, which is over GitHub's 100 MB per-file limit,
// so they are not committed. Instead they are fetched once into the asset
// directory and packaged into the APK, which keeps the shipped app fully
// self-contained: install the APK and everything needed for on-device
// transcription is already there.
// ---------------------------------------------------------------------------
val whisperModelName = "ggml-small-q5_1.bin"
val whisperModelUrl =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$whisperModelName?download=true"
val whisperModelSize = 190085487L
val whisperModelFile = layout.projectDirectory.file("src/main/assets/models/$whisperModelName").asFile

val downloadWhisperModel by tasks.registering {
    description = "Downloads the whisper GGML weights into the APK assets."
    outputs.file(whisperModelFile)
    outputs.upToDateWhen { whisperModelFile.length() == whisperModelSize }
    doLast {
        if (whisperModelFile.length() == whisperModelSize) return@doLast
        whisperModelFile.parentFile.mkdirs()
        val tmp = File(whisperModelFile.parentFile, "$whisperModelName.part")
        logger.lifecycle("Downloading $whisperModelName (~181 MiB) ...")
        var url = URI(whisperModelUrl).toURL()
        var redirects = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000
            when (conn.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    check(++redirects <= 5) { "Too many redirects fetching $whisperModelName" }
                    url = URI(location).toURL()
                }
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    conn.disconnect()
                    check(tmp.length() == whisperModelSize) {
                        "Downloaded $whisperModelName is ${tmp.length()} bytes, expected $whisperModelSize"
                    }
                    tmp.renameTo(whisperModelFile)
                    return@doLast
                }
                else -> error("Failed to download $whisperModelName: HTTP ${conn.responseCode}")
            }
        }
    }
}

android {
    namespace = "com.aivoice.flow"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.aivoice.flow"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WHISPER_MODEL_ASSET", "\"models/$whisperModelName\"")
        buildConfigField("long", "WHISPER_MODEL_BYTES", "${whisperModelSize}L")

        ndk {
            // 64-bit ARM only: every phone this app targets is arm64, and each
            // extra ABI would duplicate the native whisper build in the APK.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so CI can publish an installable APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // The GGML weights must stay uncompressed so the first-run copy out of
        // the APK is a straight byte copy instead of a 190 MB inflate.
        noCompress += "bin"
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

tasks.named("preBuild") { dependsOn(downloadWhisperModel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
}
