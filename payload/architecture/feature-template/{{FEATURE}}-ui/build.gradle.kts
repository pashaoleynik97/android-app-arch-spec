// :{{FEATURE}}-ui — the feature's screens, ViewModels and its FeatureImpl (the nav-graph registration).
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui"
}

dependencies {
    implementation(project(":lib-android"))
    implementation(project(":{{FEATURE}}-ui-api"))
    implementation(project(":{{FEATURE}}"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(project(":core-testing"))
}
