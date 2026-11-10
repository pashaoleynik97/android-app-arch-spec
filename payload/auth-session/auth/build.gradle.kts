// :auth — session infrastructure (no UI): who is signed in, the refresh token at rest, single-flight refresh,
// the activity-gated keep-alive, and the OkHttp seams it plugs into :network's business client through DI.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "{{ROOT_PACKAGE}}.auth"

    buildFeatures {
        // BuildConfig.DEBUG gates the HTTP logging interceptor on the session client.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":lib-common"))
    // The base URL, the shared Json, the envelope, the logging helper and the ApiClient seams it fills.
    // api: AuthModule's public @Provides signatures and AuthInterceptor's supertype are :network types.
    api(project(":network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(project(":core-testing"))
    testImplementation(libs.okhttp.mockwebserver)
}
