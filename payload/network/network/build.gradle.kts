// :network — the business HTTP stack every feature's endpoints go through: the @ApiClient OkHttp client, the
// @ApiRetrofit Retrofit, the one shared Json, the example envelope + error helpers, and debug-only HTTP logging.
// It knows nothing about sessions: optional modules plug credentials and response observers in through DI
// (ApiInterceptor set, @ApiClient Authenticator set), so it builds and runs on its own.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "{{ROOT_PACKAGE}}.network"

    buildFeatures {
        // BASE_URL (below) and BuildConfig.DEBUG, which gates the HTTP logging interceptor.
        buildConfig = true
    }

    buildTypes {
        // The backend environment, one value per build type. This file is the single place it is set
        // (MOD-15): change it here, or re-render it with `render.py rerender BASE_URL_DEBUG BASE_URL_RELEASE`.
        // Paths in Retrofit interfaces are relative ("v1/items"), so the URL MUST end with "/".
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"{{BASE_URL_DEBUG}}\"")
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"{{BASE_URL_RELEASE}}\"")
        }
    }
}

dependencies {
    implementation(project(":lib-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)

    // api: Retrofit, OkHttp and Json types appear in this module's public surface (Retrofit interfaces of the
    // features, ApiInterceptor : okhttp3.Interceptor, the shared Json), so consumers compile against them.
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(project(":core-testing"))
    testImplementation(libs.okhttp.mockwebserver)
}
