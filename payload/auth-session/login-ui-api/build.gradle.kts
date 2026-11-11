// :login-ui-api — the sign-in feature's navigation contract: the FeatureApi marker and its route.
// The host depends on it to send a signed-out user to sign-in without compiling the sign-in UI.
// The feature.api convention adds `api(project(":feature-api"))`, the contract's only dependency (MOD-09).
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.api)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.ui.api"
}
