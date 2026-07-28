plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// How each build type gets the Go core (stunmesh-go's mobile package, built
// with gomobile):
//
//   debug   - any .aar dropped in libs/, so the app can be
//             iterated against an unreleased core; otherwise the pinned release
//   release - always the pinned release, never the local file: a shipped build
//             must be reproducible from the sources alone
//
// Without either, the build falls back to the stub backend, which moves no
// packets. Set stunmeshCoreVersion in gradle.properties to a stunmesh-go tag
// whose release carries the AAR.
// Any AAR dropped in libs/, since the artifact carries its version in the name
// (stunmesh-android-<version>.aar) and pinning one spelling would silently
// ignore the file a developer just downloaded.
val localGoCore = fileTree("libs") { include("*.aar") }.files.firstOrNull()
val goCoreVersion = providers.gradleProperty("stunmeshCoreVersion")
    .orNull
    ?.takeIf { it.isNotBlank() }
val debugHasGoCore = (localGoCore != null) || goCoreVersion != null
val releaseHasGoCore = goCoreVersion != null
val GO_BACKEND_SRC = "src/gobackend/kotlin"

// Output stunmesh-android-<buildtype>.apk rather than app-<buildtype>.apk, so
// a downloaded artifact says what it is.
base {
    archivesName = "stunmesh-android"
}

// Version, in precedence order: an explicit -PversionName / VERSION_NAME from
// whatever drives the build, then git, then a placeholder. The override lets a
// release pipeline pin the version without the build script depending on git
// at all; the git fallback keeps local builds meaningful.
//
// Reading git needs the full history: git describe wants the tags and
// rev-list --count wants the commits, so a shallow CI checkout silently
// produces "dev" and 1 unless it overrides or fetches with depth 0.
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

fun override(property: String, environment: String): String? =
    providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }

val appVersionName = override("versionName", "VERSION_NAME")
    ?: git("describe", "--tags", "--always", "--dirty")
    ?: "dev"

val appVersionCode = (override("versionCode", "VERSION_CODE")
    ?: git("rev-list", "--count", "HEAD"))
    ?.toIntOrNull()
    ?: 1

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
        versionCode = appVersionCode
        versionName = appVersionName

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
        // The about screen reads the version and build type from BuildConfig.
        buildConfig = true
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
    if (localGoCore != null) {
        debugImplementation(files(localGoCore!!))
    } else if (goCoreVersion != null) {
        debugImplementation("dev.stunmesh:stunmesh-android:$goCoreVersion@aar")
    }
    if (goCoreVersion != null) {
        releaseImplementation("dev.stunmesh:stunmesh-android:$goCoreVersion@aar")
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
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