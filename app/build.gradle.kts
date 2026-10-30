plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sshvpn"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        // Production applicationId for Play / sideload (distinct from sample com.example.*)
        applicationId = "app.karoon.tunnel"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "2.1.1"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("karoon") {
            val ks = rootProject.file("keystore/karoon-debug.jks")
            storeFile = ks
            storePassword = "karoon_tunnel_debug"
            keyAlias = "karoon"
            keyPassword = "karoon_tunnel_debug"
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
            signingConfig = signingConfigs.getByName("karoon")
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

dependencies {
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("androidx.core:core:1.13.1")
}