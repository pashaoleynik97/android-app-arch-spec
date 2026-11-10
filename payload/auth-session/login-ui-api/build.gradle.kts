// :login-ui-api — the sign-in feature's navigation contract: the FeatureApi marker and its route.
// The host depends on it to send a signed-out user to sign-in without compiling the sign-in UI.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.ui.api"
}

dependencies {
    api(project(":feature-api"))
}
