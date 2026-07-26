plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Test-only sources shared with :file-persistence; see the `sourceSets` note below.
val testFixturesDir = "src/testFixtures/java"

android {
    namespace = "com.pdfapp.core.renderer"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        // The hand-built AcroForm PDF the Phase 4 form tests assert against is
        // needed by this module's reader tests *and* by :file-persistence's
        // writer tests, so it lives in its own directory that both add to their
        // test source set — one fixture instead of two copies to drift apart.
        // (AGP's `testFixtures` variant would be the idiomatic home, but the
        // Kotlin Android plugin does not compile Kotlin for it.)
        getByName("test") { java.srcDir(testFixturesDir) }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Read-only PdfBox use (text geometry, outline, links). Rasterisation
    // stays on PdfRenderer; writing stays in :file-persistence.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
