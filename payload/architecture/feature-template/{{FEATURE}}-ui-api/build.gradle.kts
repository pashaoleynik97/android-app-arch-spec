// :{{FEATURE}}-ui-api — the feature's navigation contract: the FeatureApi marker and the route strings.
// The host and sibling features may depend on this module; it never depends on UI or domain code.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api"
}

dependencies {
    api(project(":feature-api"))
}
