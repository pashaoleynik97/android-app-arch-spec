plugins {
    `kotlin-dsl`
}

group = "{{ROOT_PACKAGE}}.buildlogic"

// Convention plugins compile against the AGP / Kotlin / Compose / KSP / Hilt Gradle plugin APIs. They are
// `compileOnly` because the plugins themselves are put on the classpath by the consuming (root) build,
// which declares them `apply false` with versions from the catalog.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

// Each convention plugin under a stable id; modules apply them by catalog alias,
// e.g. `alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)`.
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "{{CONVENTION_PREFIX}}.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "{{CONVENTION_PREFIX}}.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "{{CONVENTION_PREFIX}}.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "{{CONVENTION_PREFIX}}.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "{{CONVENTION_PREFIX}}.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
