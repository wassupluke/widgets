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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
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

        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

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
