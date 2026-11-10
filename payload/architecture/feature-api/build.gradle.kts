// :feature-api — the contract every feature implements and the registry the host iterates. Every
// `:x-ui-api` module depends on this and nothing else.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.featureapi"
}

dependencies {
    api(project(":lib-common"))
    // NavGraphBuilder / NavHostController appear in the FeatureApi signature.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.navigation.compose)
}
