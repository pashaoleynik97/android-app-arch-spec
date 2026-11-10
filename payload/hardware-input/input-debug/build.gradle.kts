// :input-debug — an input driver fed by adb broadcasts, for emulators and devices without the real hardware.
// The host takes it as `debugImplementation` ONLY: neither the driver nor its exported receiver exists in a release
// build (MOD-17), and `assembleRelease` proves it.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.input.debug"
}

dependencies {
    // Same rule as a real vendor module: the contract only, never :input.
    implementation(project(":input-api"))
    implementation(project(":lib-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)
}
