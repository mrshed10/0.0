plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvremote.floating"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvremote.floating"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // نسخة الإصدار موقّعة بمفتاح debug لتسهيل التثبيت المباشر على الـ TV Box
            signingConfig = signingConfigs.getByName("debug")
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
