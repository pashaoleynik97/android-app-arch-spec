// :input-api — the hardware-input contract (driver, provider, event, detection). A leaf: no Hilt, no Compose, no
// vendor. Vendor modules depend on this module ONLY, so a vendor SDK never reaches the rest of the app.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.input.api"
}

dependencies {
    // Flow / StateFlow appear in InputDriver's public signature.
    api(libs.kotlinx.coroutines.android)
}
