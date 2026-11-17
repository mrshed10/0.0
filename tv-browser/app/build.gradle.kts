plugins {
    id("com.android.application")
}

val geckoviewVersion: String = providers.gradleProperty("geckoviewVersion").getOrElse("+")
val geckoviewNightlyVersion: String = providers.gradleProperty("geckoviewNightlyVersion").getOrElse("+")
val appMinSdk: Int = providers.gradleProperty("appMinSdk").getOrElse("26").toInt()

android {
    namespace = "com.tvbrowser.aosp"
    compileSdk = 37  // required by GeckoView 160+

    defaultConfig {
        applicationId = "com.tvbrowser.aosp"
        minSdk = appMinSdk
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { buildConfig = true }

    // نسختان من المحرك:
    //  stable   : GeckoView الرسمي — يثبّت الإضافات الموقّعة من Mozilla (uBlock Origin, ABP, AdGuard ...)
    //  unsigned : GeckoView Nightly — يسمح أيضاً بإضافات Chrome/Brave/Kiwi/Edge المحوّلة من CRX وغير الموقّعة
    flavorDimensions += "engine"
    productFlavors {
        create("stable") {
            dimension = "engine"
            buildConfigField("boolean", "ALLOW_UNSIGNED", "false")
        }
        create("unsigned") {
            dimension = "engine"
            applicationIdSuffix = ".unsigned"
            versionNameSuffix = "-unsigned"
            buildConfigField("boolean", "ALLOW_UNSIGNED", "true")
        }
    }

    // ملف APK منفصل لكل معمارية = حجم أصغر وتثبيت أسرع على أجهزة التخزين المحدود
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // للتجربة فقط: استبدلها بمفتاح التوقيع الخاص بك قبل النشر
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    "stableImplementation"("org.mozilla.geckoview:geckoview:$geckoviewVersion")
    "unsignedImplementation"("org.mozilla.geckoview:geckoview-nightly:$geckoviewNightlyVersion")
}
