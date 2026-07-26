plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version naming, in one place:
//   appVersionName  the marketing version — `appVersionName` in
//                   gradle.properties for dev builds, the `vX.Y.Z` tag for a
//                   release build (passed as -PappVersionName by CI).
//   appBuildNumber  set by CI (commit count) for untagged builds. It is
//                   appended to the version name so an installed build is
//                   identifiable at a glance — "1.4.1 (build 102)" reads like a
//                   version, unlike a raw commit hash — and doubles as the
//                   versionCode when none is given.
//   appVersionCode  monotonic install ordering; CI passes the commit count for
//                   releases, so a tagged build always outranks a rolling one.
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.0.0"
val appBuildNumber = (findProperty("appBuildNumber") as String?)?.toIntOrNull()
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: appBuildNumber ?: 1
val appDisplayVersion = appBuildNumber?.let { "$appVersionName (build $it)" } ?: appVersionName

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
        versionName = appDisplayVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Debug builds sign with the keystore committed at config/debug.keystore
        // instead of each machine's auto-generated one. Without a stable key,
        // every CI runner produces a differently-signed APK and Android refuses
        // to install a new "Latest build" over the previous one. This key is
        // intentionally public (sideload/debug only); Play releases use the
        // separate release keystore from CI secrets.
        getByName("debug") {
            storeFile = rootProject.file("config/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        // The home screen shows the running build's version; BuildConfig is how
        // it reads back what was stamped above.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Accessibility regressions fail the build rather than sit in a report
        // (mobile-ui-plan Phase F.3). These are the checks that still apply to a
        // Compose UI: a custom View that eats touches without a click action
        // (OverlayCanvasView), an unlabelled image or input, and anything
        // reachable by touch but not by keyboard.
        error +=
            listOf(
                "ClickableViewAccessibility",
                "ContentDescription",
                "KeyboardInaccessibleWidget",
                "LabelFor",
            )
    }
}

// `./gradlew -q :app:appVersion` prints exactly what a build would stamp, so the
// scheme can be checked — locally or from a CI log — without unpacking an APK.
tasks.register("appVersion") {
    group = "help"
    description = "Prints the versionName and versionCode this build would stamp."
    val summary = "$appDisplayVersion (versionCode $appVersionCode)"
    doLast { println(summary) }
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
