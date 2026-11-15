plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvremote.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvremote.keyboard"
        minSdk = 21          // يعمل على معظم أجهزة TV Box القديمة (Android 5+)
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // لتسهيل التثبيت: نسخة الإصدار موقّعة بمفتاح debug (غيّره إن أردت النشر على المتجر)
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

// لا توجد مكتبات خارجية: التطبيق يعتمد فقط على Android SDK و Kotlin stdlib
