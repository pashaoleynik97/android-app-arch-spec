// :{{FEATURE}}-ui — the feature's screens, ViewModels and its FeatureImpl (the nav-graph registration).
// The feature.ui convention brings library + Compose + Hilt, :lib-android, navigation-compose, hiltViewModel()
// and :core-testing for tests; this file declares only the feature's own edges.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.ui)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui"
}

dependencies {
    implementation(project(":{{FEATURE}}-ui-api"))
    implementation(project(":{{FEATURE}}"))
}
