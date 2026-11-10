// :lib-common — the kernel every module may depend on: logging, flow-scoped state, process lifecycle and the
// dispatcher / clock seams. No Compose and no UI; it may use the Android framework and Hilt.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.libcommon"

    buildFeatures {
        // Reporter reads BuildConfig.DEBUG to gate local logcat output.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)
    // AppForegroundWatch observes the whole-process lifecycle.
    implementation(libs.androidx.lifecycle.process)
}
