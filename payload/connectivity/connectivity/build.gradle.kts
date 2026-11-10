// :connectivity — "can we reach the backend?": the OS link state combined with a health probe on the module's
// own short-timeout HTTP client. No UI and no session knowledge; it does not depend on :network, so it installs
// on a project without one.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.connectivity"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // The probe's environment: the same backend as the business API, set here because this module must build
        // without :network. Re-render both with `render.py rerender BASE_URL_DEBUG BASE_URL_RELEASE HEALTH_PATH`.
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"{{BASE_URL_DEBUG}}\"")
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"{{BASE_URL_RELEASE}}\"")
        }
    }
    defaultConfig {
        // Relative to BASE_URL, no leading slash.
        buildConfigField("String", "HEALTH_PATH", "\"{{HEALTH_PATH}}\"")
    }
}

dependencies {
    implementation(project(":lib-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(project(":core-testing"))
    testImplementation(libs.okhttp.mockwebserver)
}
