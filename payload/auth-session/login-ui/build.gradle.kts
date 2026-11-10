// :login-ui — the sign-in screen, its ViewModel and LoginFeatureImpl (the nav-graph registration).
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library.compose)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.ui"
}

dependencies {
    implementation(project(":lib-android"))
    implementation(project(":login-ui-api"))
    implementation(project(":login"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(project(":core-testing"))
}
