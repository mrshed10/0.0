plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvtools.controlpanel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvtools.controlpanel"
        minSdk = 23
        // AOSP 13 = API 33. targetSdk 34 يعمل على أندرويد 13 ويتجنّب قيود edge-to-edge في API 35.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // صلاحية WRITE_SECURE_SETTINGS تُمنح يدويًا عبر adb، وهذا مقصود.
        disable += setOf("ProtectedPermissions", "QueryAllPackagesPermission")
    }
}

// لا توجد اعتماديات خارجية: التطبيق يستخدم واجهات أندرويد الأصلية فقط ليبقى صغيرًا ويعمل على أي شاشة.
