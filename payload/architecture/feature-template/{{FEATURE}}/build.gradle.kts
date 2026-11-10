// :{{FEATURE}} — the feature's domain: interactors, models, data access. No Compose, no ViewModels, so it is
// fully JVM-testable and reusable by any UI.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain"
}

dependencies {
    implementation(project(":lib-common"))
    implementation(libs.javax.inject)

    testImplementation(project(":core-testing"))
}
