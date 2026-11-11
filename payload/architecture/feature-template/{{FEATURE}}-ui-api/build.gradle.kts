// :{{FEATURE}}-ui-api — the feature's navigation contract: the FeatureApi marker and the route strings.
// The host and sibling features may depend on this module; it never depends on UI or domain code.
// The feature.api convention adds `api(project(":feature-api"))`, the contract's only dependency (MOD-09).
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.api)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api"
}
