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
    }
}
rootProject.name = "SafarBrowser"
include(":app")
