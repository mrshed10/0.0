plugins {
    id("com.android.application")
}

// Pinned engine version = reproducible builds (no surprise upgrades).
val geckoviewVersion: String = providers.gradleProperty("geckoviewVersion").getOrElse("160.0.20261104181552")

android {
    namespace = "com.tvbrowser.aosp"
    compileSdk = 37 // required by GeckoView 160+

    defaultConfig {
        applicationId = "com.tvbrowser.aosp"
        minSdk = 26 // GeckoView 160 minimum (Android 8.0)
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0-lite"

        // 32-bit ARM ONLY: the device (Android 13 Go-class, 1GB) runs armeabi-v7a.
        // Drops ~2/3 of the native libraries from the APK.
        ndk { abiFilters += "armeabi-v7a" }

        // Chrome (.crx) add-ons need an unsigned-capable engine; this build is stable-only.
        buildConfigField("boolean", "ALLOW_UNSIGNED", "false")
    }

    buildFeatures {
        buildConfig = true
        resValues = false
        shaders = false
        aidl = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // For testing only: replace with your own signing key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    // The ONLY dependency: the Gecko engine. No AppCompat / Material / Compose / coroutines.
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")
}
