// :engineering-ui-api — the hidden diagnostics menu's navigation contract (route) and its section contract
// (EngineeringSection), so any module can contribute rows without depending on the menu's UI.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.engineering.ui.api"
}

dependencies {
    api(project(":feature-api"))
    // Flow appears in EngineeringSection's public signature.
    api(libs.kotlinx.coroutines.android)
}
