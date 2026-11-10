// :login — the sign-in feature's domain: input validation and the sign-in / sign-out use cases over the
// session (:auth). No Compose, no ViewModels.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.domain"
}

dependencies {
    implementation(project(":lib-common"))
    implementation(project(":auth"))
    implementation(libs.javax.inject)

    testImplementation(project(":core-testing"))
}
