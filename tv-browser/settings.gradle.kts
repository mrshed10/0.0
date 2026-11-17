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
        // GeckoView (محرك Firefox) يُنشر على مستودع Mozilla فقط
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroupByRegex("org\\.mozilla(\\..*)?") }
        }
    }
}
rootProject.name = "AospTvBrowser"
include(":app")
