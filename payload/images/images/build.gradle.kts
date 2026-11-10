// :images — Coil image loading over the authenticated @FilesClient, registered as Coil's app-wide singleton, plus the
// {{THEME_PREFIX}}RemoteImage / {{THEME_PREFIX}}Avatar composables. UI modules depend on this; domain modules depend
// on :files only.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.images"
}

dependencies {
    implementation(project(":lib-android"))
    // The @FilesClient the loader fetches through.
    implementation(project(":files"))

    implementation(libs.javax.inject)
    // api: the composables take Coil types (ContentScale is Compose; ImageLoader is Coil) only internally, but feature
    // code that calls AsyncImage directly gets the configured singleton through coil-compose.
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Registers the singleton ImageLoader at process start without editing the host's App class.
    implementation(libs.androidx.startup.runtime)

    testImplementation(project(":core-testing"))
}
