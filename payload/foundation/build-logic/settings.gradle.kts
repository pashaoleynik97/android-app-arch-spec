// The convention-plugin build. Included from the root `settings.gradle.kts` (`includeBuild("build-logic")`),
// it shares the root version catalog so plugin code and module build files read the same versions.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
