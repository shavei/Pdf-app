plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pdfapp.overlay"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        // This module owns the one custom View in the app, so the touch-handling
        // accessibility check has something to say here: an onTouchEvent that
        // never calls performClick is unreachable to a screen reader
        // (mobile-ui-plan Phase F.3).
        error += listOf("ClickableViewAccessibility", "ContentDescription")
    }
}

dependencies {
    // Shared geometry/model types (PdfPoint, CoordinateMapper) are part of this
    // module's public API surface, so expose them with `api`.
    api(project(":core-renderer"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
