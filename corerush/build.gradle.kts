plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.corerush.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.corerush.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // ملف اللعبة نفسه (core-rush/index.html) يُضمَّن كـ asset بدون نسخه
    sourceSets["main"].assets.srcDirs("../core-rush")
    androidResources {
        ignoreAssetsPattern = "!README.md:!.*:!*~"
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
