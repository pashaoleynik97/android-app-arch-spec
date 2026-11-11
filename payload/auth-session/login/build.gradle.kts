// :login — the sign-in feature's domain: input validation and the sign-in / sign-out use cases over the
// session (:auth). No Compose, no ViewModels. The feature.domain convention brings library + Hilt, :lib-common,
// javax.inject and :core-testing for tests.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.domain)
}

android {
    namespace = "{{ROOT_PACKAGE}}.login.domain"
}

dependencies {
    implementation(project(":auth"))
}
