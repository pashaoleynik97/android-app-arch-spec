// :media — device-local scratch store for captured media: app-internal files, publish/keep/discard by id, and a
// TTL sweep at process start. Infra: no UI, no network.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.media"
}

dependencies {
    implementation(project(":lib-common"))

    implementation(libs.javax.inject)
    // Starts the stale sweep at process start without editing the host's App class (MediaStartupInitializer).
    implementation(libs.androidx.startup.runtime)

    testImplementation(project(":core-testing"))
}
