plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val gitVersionCode = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

val gitVersionName = providers.exec {
    // Match only version tags so the force-pushed `dev` tag can't shadow them.
    commandLine("git", "describe", "--tags", "--match", "v*", "--always")
}.standardOutput.asText.map { it.trim().removePrefix("v").ifEmpty { "0.0.0" } }

val gitShortHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

// The dev prerelease is versioned as the *upcoming* release: the latest v* tag with its patch
// bumped, plus the short commit hash — e.g. tag v2.0.5 at HEAD e11bcb2 -> "v2.0.6-dev-e11bcb2".
// Fully derived from git (the tag is the part of gitVersionName's "<tag>-<n>-g<hash>" describe
// before the first "-"), nothing hardcoded.
val devVersionName = gitVersionName.zip(gitShortHash) { describe, shortHash ->
    val parts = describe.substringBefore("-").split(".").toMutableList()
    parts.lastOrNull()?.toIntOrNull()?.let { parts[parts.lastIndex] = (it + 1).toString() }
    "v${parts.joinToString(".")}-dev-$shortHash"
}

android {
    namespace = "com.wassupluke.widgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wassupluke.widgets"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode.get()
        versionName = gitVersionName.get()
    }

    // BuildConfig.DEBUG gates the Debug logcat wrapper; AGP 9 doesn't generate
    // BuildConfig unless it's asked for.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Coexist with an installed release build so on-device testing doesn't disturb it.
            // The suffixed version name and the debug-only app_name (src/debug/res) keep the two
            // installs tellable apart on-device — they share an icon otherwise.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // The prerelease "dev" build published from main by dev-release.yml. Inherits the release
        // config (minify/shrink/proguard) but gets its own applicationId + app name (src/dev/res)
        // so it installs alongside a real release. Its versionName is fully replaced below (per
        // variant) with `devVersionName` rather than suffixed here.
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    // With AGP 9's built-in Kotlin, the Kotlin jvmTarget follows
    // targetCompatibility, so the old kotlinOptions block is redundant.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Replace (not just suffix) the dev variant's versionName so it reads like the upcoming release,
// e.g. "v2.0.5-dev-67e1446". Scoped to the dev build type, so release/debug are untouched. This
// sets the manifest versionName; the in-app "Version …" line reads it back via PackageManager
// (not BuildConfig, which an output override doesn't reliably reach) so the two always match.
androidComponents {
    onVariants(selector().withBuildType("dev")) { variant ->
        val name = devVersionName.get()
        variant.outputs.forEach { it.versionName.set(name) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
