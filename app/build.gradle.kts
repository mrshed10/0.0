plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.safar.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.safar.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // APK منفصل لكل معمارية (GeckoView ثقيل): arm64 للهواتف، x86_64 للمحاكي
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // توقيع ثابت لكل البناءات ⇒ يمكن تثبيت كل إصدار جديد فوق القديم.
    // مفتاح اختبار عام (ليس سرياً): للنشر في المتاجر استبدله بمفتاح خاص محفوظ في GitHub Secrets.
    signingConfigs {
        create("safarTest") {
            storeFile = file("safar-test.jks")
            storePassword = "safar-test-123"
            keyAlias = "safar"
            keyPassword = "safar-test-123"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("safarTest")
        }
        release {
            // R8 معطّل حالياً: GeckoView وJackson (Anthropic SDK) يعتمدان على الانعكاس، والتصغير يحتاج اختباراً على جهاز
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("safarTest")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // ضغط مكتبات GeckoView الأصلية داخل الـ APK (بدونه يتجاوز الحجم 250MB ويتلف التحميل بسهولة)
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
            pickFirsts += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.media)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.serialization.json)

    // محرك العرض + WebExtensions
    implementation(libs.geckoview)

    // OCR والترجمة على الجهاز
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.tesseract)

    // مزودو الترجمة السحابيون
    implementation(libs.okhttp)
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
