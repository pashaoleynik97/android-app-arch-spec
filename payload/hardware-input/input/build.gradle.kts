// :input — hardware-input core: the controller (Activity lifecycle), resolver, settings store and the
// CollectInputWhileResumed helper. Vendor drivers live in their own modules and plug in via @IntoSet.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    // For CollectInputWhileResumed only — the module has no screens.
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.input"
}

dependencies {
    // InputEvent / InputDriverId appear in this module's public API, so consumers see the contract through it.
    api(project(":input-api"))
    // HostActivityObserver: the controller is registered on MainActivity's lifecycle through the host seam.
    implementation(project(":lib-android"))
    implementation(project(":lib-common"))

    implementation(libs.javax.inject)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":core-testing"))
}
