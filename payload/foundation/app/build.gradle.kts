plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.application)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.application.compose)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

// --- Version -------------------------------------------------------------------------------------------
// `versionName` comes from version.properties (the single source of truth, bumped by the release pipeline
// or by hand); `versionCode` is derived from it, so the two can never disagree. Every read goes through
// `providers` so the configuration cache tracks the file — otherwise a pipeline that rewrites
// version.properties and then builds could bake a stale version into the APK.
val versionNameBase: String = providers
    .fileContents(rootProject.layout.projectDirectory.file("version.properties"))
    .asText.orNull
    ?.lineSequence()
    ?.firstOrNull { it.trim().startsWith("VERSION_NAME=") }
    ?.substringAfter('=')?.trim()
    ?: error("VERSION_NAME missing from version.properties")

private val versionSegments = versionNameBase.split(".").also {
    require(it.size == 3 && it.all { seg -> seg.toIntOrNull() != null }) {
        "VERSION_NAME must be MAJOR.MINOR.PATCH integers, was '$versionNameBase'"
    }
}.map { it.toInt() }

// MAJOR.MINOR.PATCH -> MMmmmPPP0, e.g. 1.2.95 -> 1020950. Capacities: major and minor <= 99, patch <= 999.
// The trailing digit is reserved (e.g. for a rebuild of the same version that must still be uploadable).
val computedVersionCode: Int =
    (versionSegments[0] * 100_000 + versionSegments[1] * 1_000 + versionSegments[2]) * 10

// A pipeline that cannot push version bumps back passes -PversionCodeOverride=<int> (e.g. the commit count)
// so every CI build still gets a unique, increasing versionCode.
val versionCodeOverride: Int? = providers.gradleProperty("versionCodeOverride").orNull?.toIntOrNull()

// Only the release pipeline passes -PreleaseBuild=true; every other build is visibly a test build.
val isReleaseBuild: Boolean = providers.gradleProperty("releaseBuild").orNull == "true"
val computedVersionName: String = if (isReleaseBuild) versionNameBase else "$versionNameBase-test"

// --- Signing -------------------------------------------------------------------------------------------
// Each value resolves from the local keystore.properties first (IDE / local builds), then from CI
// environment variables. When all four are present BOTH debug and release are signed with that key, so a
// build launched from the IDE matches CI artifacts. With no keystore, release falls back to the debug key:
// keyless builds still succeed (they just cannot be published).
private val localKeystoreProps: Map<String, String> = providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.orNull
    ?.lineSequence()
    ?.mapNotNull { line ->
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || !t.contains('=')) null
        else t.substringBefore('=').trim() to t.substringAfter('=').trim()
    }
    ?.toMap()
    ?: emptyMap()

private fun signingValue(propKey: String, envKey: String): String? =
    localKeystoreProps[propKey] ?: providers.environmentVariable(envKey).orNull

val ksStorePath: String? = signingValue("storeFile", "KEYSTORE_FILE")
    ?: "{{KEYSTORE_FILE}}".takeIf { localKeystoreProps.isNotEmpty() }
val ksStorePassword: String? = signingValue("keystorePassword", "KEYSTORE_PASSWORD")
val ksKeyAlias: String? = signingValue("keyAlias", "KEY_ALIAS")
val ksKeyPassword: String? = signingValue("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning: Boolean =
    ksStorePath != null && ksStorePassword != null && ksKeyAlias != null && ksKeyPassword != null

android {
    namespace = "{{APP_PACKAGE}}"

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(ksStorePath!!)
                storePassword = ksStorePassword
                keyAlias = ksKeyAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "{{APPLICATION_ID}}"
        versionCode = versionCodeOverride ?: computedVersionCode
        versionName = computedVersionName
    }

    buildTypes {
        val sharedSigning = signingConfigs.getByName("release").takeIf { hasReleaseSigning }
        debug {
            sharedSigning?.let { signingConfig = it }
        }
        release {
            // R8 is OFF by default — a deliberate choice for a young codebase: a shrinker that silently
            // strips a reflectively-used class fails only at runtime, on the release build nobody runs
            // daily. To turn it on (record the decision in an ADR, and have a release smoke test first):
            //   1. `enable = true` here;
            //   2. `android.r8.gradual.support=true` in gradle.properties (this AGP refuses
            //      `optimization.enable = true` without it);
            //   3. keep rules in src/main/keepRules/rules.keep (AGP reads every file in that directory).
            optimization {
                enable = false
            }
            signingConfig = sharedSigning ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Modules and features the host aggregates. The installer merges lines into this block; keep the markers.
    // <arch-spec:app-dependencies>
    // </arch-spec:app-dependencies>
}
