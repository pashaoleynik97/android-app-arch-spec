// :{{FEATURE}} — the feature's domain: interactors, models, data access. No Compose, no ViewModels, so it is
// fully JVM-testable and reusable by any UI. The feature.domain convention brings library + Hilt, :lib-common,
// javax.inject and :core-testing for tests; add infra modules this feature uses (e.g. :network) below.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.feature.domain)
}

android {
    namespace = "{{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain"
}
