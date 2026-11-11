// :login-ui — the sign-in screen, its ViewModel and LoginFeatureImpl (the nav-graph registration).
// The feature.ui convention brings library + Compose + Hilt, :lib-android, navigation-compose, hiltViewModel()
// and :core-testing for tests.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.ui)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.ui"
}

dependencies {
    implementation(project(":login-ui-api"))
    implementation(project(":login"))
}
