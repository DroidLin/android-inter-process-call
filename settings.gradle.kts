pluginManagement {
    repositories {
        maven {
            url = uri("${rootProject.projectDir}/repo")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("${rootProject.projectDir}/repo")
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "android-inter-process-call"
include(":app")
include(":library")
include(":ksp-compiler")
include(":annotation")
