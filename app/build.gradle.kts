import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

// Version is overridable from the release workflow via -P flags; defaults keep
// local/CI debug builds working without any arguments.
val appVersionName = (findProperty("appVersionName") as String?) ?: "0.1.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// Release signing reads from environment variables supplied by CI secrets, so no
// keystore is ever committed. Absent these, release builds fall back to the
// debug signing config so the artifact is still installable for testing.
val releaseKeystore: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.pdfapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pdfapp"
        minSdk = 21
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Gradle Play Publisher: uploads the signed AAB to Google Play. Credentials come
// from the ANDROID_PUBLISHER_CREDENTIALS env var (a CI secret holding the
// service-account JSON), so nothing sensitive is ever committed. Publish tasks
// (e.g. :app:publishReleaseBundle) only run when explicitly invoked, so this is
// inert for normal local/CI builds.
play {
    // Promote through tracks: internal -> alpha -> beta -> production.
    track.set((findProperty("playTrack") as String?) ?: "internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(ReleaseStatus.COMPLETED)
    // Store-listing text/graphics live under src/main/play (see listings/).
    // Skip resolving credentials at configuration time when none are present.
    if (System.getenv("ANDROID_PUBLISHER_CREDENTIALS").isNullOrBlank() &&
        !rootProject.file("play-service-account.json").exists()
    ) {
        enabled.set(false)
    } else if (rootProject.file("play-service-account.json").exists()) {
        serviceAccountCredentials.set(rootProject.file("play-service-account.json"))
    }
}

dependencies {
    implementation(project(":core-renderer"))
    implementation(project(":overlay-engine"))
    implementation(project(":file-persistence"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
