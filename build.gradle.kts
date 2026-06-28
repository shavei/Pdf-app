plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Apply static-analysis plugins to every module so `ktlintCheck` / `detekt`
// run project-wide as part of the Lint layer of the verification stack.
subprojects {
    // Plugin IDs as string literals: the version-catalog `libs` accessor is not
    // available against the subproject receiver inside this block, but these
    // plugins are already on the classpath via the root `plugins {}` block.
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
}
