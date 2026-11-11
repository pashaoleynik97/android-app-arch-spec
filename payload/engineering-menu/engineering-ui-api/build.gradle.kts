// :engineering-ui-api — the hidden diagnostics menu's navigation contract (route) and its section contract
// (EngineeringSection), so any module can contribute rows without depending on the menu's UI.
// The feature.api convention adds `api(project(":feature-api"))`.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.api)
}

android {
    namespace = "{{ROOT_PACKAGE}}.engineering.ui.api"
}

dependencies {
    // Flow appears in EngineeringSection's public signature.
    api(libs.kotlinx.coroutines.android)
}
