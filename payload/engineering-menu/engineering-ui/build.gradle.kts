// :engineering-ui — the hidden engineering (diagnostics) menu. Rows come from EngineeringSection contributions
// (a Hilt multibinding), so installed modules add their own diagnostics without this module knowing them.
// The feature.ui convention brings library + Compose + Hilt, :lib-android, navigation-compose, hiltViewModel()
// and :core-testing for tests. A feature without a domain module (MOD-08 allows that for a trivial feature).
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.ui)
}

android {
    namespace = "{{ROOT_PACKAGE}}.engineering.ui"
}

// Optional integration, decided by the project's module list (configuration-cache safe): when the hardware-input
// component is installed, compile its section (src/withInput) and its tests (src/testWithInput). Each component
// stays buildable on its own, in any install order, with no hand edit. See the engineering-menu spec (ENG-07).
val hasHardwareInput = rootProject.findProject(":input") != null
if (hasHardwareInput) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension> {
        // `kotlin` source dirs (not the variant API): KSP must see them, or the section's @IntoSet is silently lost.
        sourceSets.getByName("main").kotlin.directories.add("src/withInput/java")
        sourceSets.getByName("test").kotlin.directories.add("src/testWithInput/java")
    }
}

dependencies {
    implementation(project(":engineering-ui-api"))

    // The hardware-input section (src/withInput) reads the input controller and settings.
    if (hasHardwareInput) implementation(project(":input"))
}
