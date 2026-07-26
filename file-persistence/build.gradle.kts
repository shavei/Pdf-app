plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pdfapp.persistence"
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

    sourceSets {
        // The shared AcroForm fixture the form write-back tests fill in. It lives
        // beside the reader it was written for; see :core-renderer's own note.
        getByName("test") { java.srcDir("../core-renderer/src/testFixtures/java") }
    }

    testOptions {
        unitTests {
            // The PDF-Test-Harness runs on the JVM via Robolectric (needs a
            // Context for PDFBoxResourceLoader); include Android resources.
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(project(":overlay-engine"))
    // PDDocument appears in PdfFlattener's public API, so expose PdfBox-Android.
    api(libs.pdfbox.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
