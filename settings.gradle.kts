pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView (محرك Firefox) يُنشر على مستودع Mozilla
        maven("https://maven.mozilla.org/maven2/")
        // Tesseract4Android منشورة على JitPack فقط
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\..*") } }
    }
}
rootProject.name = "SafarBrowser"
include(":app")
