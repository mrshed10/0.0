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
        // Tesseract4Android (قراءة الروسية والعربية وبقية الحروف التي لا يدعمها ML Kit)
        maven("https://jitpack.io") {
            content { includeGroupByRegex("cz\\.adaptech.*") }
        }
    }
}
rootProject.name = "SaudiSalariesApp"
include(":app")
include(":livetranslate")
