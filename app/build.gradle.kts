plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sshvpn"
    compileSdk = 36
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "app.karoon.tunnel"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "2.1.6"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }

    signingConfigs {
        create("karoon") {
            storeFile = rootProject.file("keystore/karoon-debug.jks")
            storePassword = "karoon_tunnel_debug"
            keyAlias = "karoon"
            keyPassword = "karoon_tunnel_debug"
        }
        val storePass = System.getenv("PLAY_UPLOAD_STORE_PASSWORD")
        val alias = System.getenv("PLAY_UPLOAD_KEY_ALIAS")
        val keyPass = System.getenv("PLAY_UPLOAD_KEY_PASSWORD")
        val uploadFile = rootProject.file("keystore/play-upload.jks")
        if (!storePass.isNullOrBlank() && !alias.isNullOrBlank() &&
            !keyPass.isNullOrBlank() && uploadFile.isFile) {
            create("playUpload") {
                storeFile = uploadFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    externalNativeBuild {
        ndkBuild { path = file("src/main/jni/Android.mk") }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("karoon") }
        release {
            isMinifyEnabled = false
            // Never sign a production build with the public/debug certificate.
            if (signingConfigs.findByName("playUpload") != null) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("androidx.core:core:1.13.1")
}
