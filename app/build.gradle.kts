plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// How each build type gets the Go core (stunmesh-go's mobile package, built
// with gomobile):
//
//   debug   - libs/stunmesh.aar if you dropped one there, so the app can be
//             iterated against an unreleased core; otherwise the pinned release
//   release - always the pinned release, never the local file: a shipped build
//             must be reproducible from the sources alone
//
// Without either, the build falls back to the stub backend, which moves no
// packets. Set stunmeshCoreVersion in gradle.properties to a stunmesh-go tag
// whose release carries stunmesh.aar.
val localGoCore = file("libs/stunmesh.aar")
val goCoreVersion = providers.gradleProperty("stunmeshCoreVersion")
    .orNull
    ?.takeIf { it.isNotBlank() }
val debugHasGoCore = localGoCore.exists() || goCoreVersion != null
val releaseHasGoCore = goCoreVersion != null
val GO_BACKEND_SRC = "src/gobackend/kotlin"

// Output stunmesh-android-<buildtype>.apk rather than app-<buildtype>.apk, so
// a downloaded artifact says what it is.
base {
    archivesName = "stunmesh-android"
}

android {
    namespace = "dev.stunmesh.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.stunmesh.android"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // GoBackend only compiles where the AAR it binds against is present.
        if (debugHasGoCore) {
            getByName("debug").kotlin.srcDir(GO_BACKEND_SRC)
        }
        if (releaseHasGoCore) {
            getByName("release").kotlin.srcDir(GO_BACKEND_SRC)
        }
    }
}

dependencies {
    if (localGoCore.exists()) {
        debugImplementation(files(localGoCore))
    } else if (goCoreVersion != null) {
        debugImplementation("dev.stunmesh:stunmesh:$goCoreVersion@aar")
    }
    if (goCoreVersion != null) {
        releaseImplementation("dev.stunmesh:stunmesh:$goCoreVersion@aar")
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}