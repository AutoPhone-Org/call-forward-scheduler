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
        // Shizuku 官方 Maven
        maven(url = "https://maven.shizuku.space/")
    }
}

rootProject.name = "call-forward-scheduler"
include(":app")
