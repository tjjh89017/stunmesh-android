pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // stunmesh-go publishes the Go core as a release asset. A public
        // release download needs no credentials, so CI resolves it with no
        // token, and Gradle caches it like any other dependency.
        // Asset names carry the platform and tag, as the repo's other release
        // assets do: "dev.stunmesh:stunmesh-android:<tag>@aar" maps to
        // .../releases/download/<tag>/stunmesh-android-<tag>.aar
        ivy("https://github.com/tjjh89017/stunmesh-go/releases/download") {
            patternLayout { artifact("[revision]/[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("dev.stunmesh") }
        }
    }
}

rootProject.name = "stunmesh-android"
include(":app")
