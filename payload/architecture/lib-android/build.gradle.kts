// :lib-android — the Android/Compose toolkit every `:x-ui` module builds on: the design system
// ({{THEME_PREFIX}}Theme), host chrome (top bar, top snackbar), the base UI kit, BaseViewModel and navigation
// helpers. It re-exports :lib-common and :feature-api, so a UI module depends on this one alone.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)
}

android {
    namespace = "{{ROOT_PACKAGE}}.libandroid"
}

dependencies {
    api(project(":lib-common"))
    api(project(":feature-api"))

    implementation(libs.androidx.core.ktx)
    // BaseViewModel is part of this module's API, so its supertype must be visible to consumers.
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.animation)

    testImplementation(project(":core-testing"))
}
