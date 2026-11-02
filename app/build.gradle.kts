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

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        // Public sideload/debug certificate only. Never use this key for Google Play.
        create("karoon") {
            val ks = rootProject.file("keystore/karoon-debug.jks")
            storeFile = ks
            storePassword = "karoon_tunnel_debug"
            keyAlias = "karoon"
            keyPassword = "karoon_tunnel_debug"
        }

        // Google Play upload key is supplied only by private CI environment variables.
        create("playUpload") {
            val ksPath = providers.environmentVariable("PLAY_UPLOAD_STORE_FILE").orNull
                ?: "missing-play-upload-key.jks"
            storeFile = rootProject.file(ksPath)
            storePassword = providers.environmentVariable("PLAY_UPLOAD_STORE_PASSWORD").orNull ?: ""
            keyAlias = providers.environmentVariable("PLAY_UPLOAD_KEY_ALIAS").orNull ?: ""
            keyPassword = providers.environmentVariable("PLAY_UPLOAD_KEY_PASSWORD").orNull ?: ""
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("karoon")
        }
        release {
            isMinifyEnabled = false
            // Release signing is exclusively the private Play upload key.
            signingConfig = signingConfigs.getByName("playUpload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Never produce a misleading unsigned or incorrectly signed Play artifact.
tasks.configureEach {
    if (name == "bundleRelease" || name == "assembleRelease") {
        doFirst {
            val required = listOf(
                "PLAY_UPLOAD_STORE_FILE",
                "PLAY_UPLOAD_STORE_PASSWORD",
                "PLAY_UPLOAD_KEY_ALIAS",
                "PLAY_UPLOAD_KEY_PASSWORD"
            )
            val missing = required.filter { System.getenv(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException("Missing Play upload signing environment variables: ${missing.joinToString()}")
            }
            val keyFile = rootProject.file(System.getenv("PLAY_UPLOAD_STORE_FILE")!!)
            if (!keyFile.isFile) throw GradleException("Play upload keystore file does not exist.")
        }
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("androidx.core:core:1.13.1")
}
