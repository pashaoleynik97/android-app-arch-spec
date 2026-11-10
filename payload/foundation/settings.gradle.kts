pluginManagement {
    // The convention plugins (`{{CONVENTION_PREFIX}}.android.*`) come from this composite build, resolved by id.
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Lets Gradle provision the daemon JVM pinned in gradle/gradle-daemon-jvm.properties. The version is inline
    // because the version catalog is not available inside the settings `plugins {}` block.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // Modules may not declare their own repositories: every dependency resolves from this one list.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "{{PROJECT_SLUG}}"

// One include per line. The installer merges module lines into this block; keep the markers.
// <arch-spec:includes>
include(":app")
// </arch-spec:includes>
