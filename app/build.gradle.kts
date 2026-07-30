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
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
