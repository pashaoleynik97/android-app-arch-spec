// :files — server-stored files: FilesConfig, FileUrlResolver and the authenticated @FilesClient OkHttp client.
// Infra: no UI. Image loading (Coil) and the image composables live in :images, so domain modules can resolve file
// URLs without compiling Compose.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.hilt)
}

android {
    namespace = "{{ROOT_PACKAGE}}.files"

    buildFeatures {
        // BuildConfig.DEBUG gates the HTTP logging interceptor.
        buildConfig = true
    }
}

// Optional integration, decided by the project's module list (configuration-cache safe): with the auth-session
// component installed, files requests carry the session token (src/withAuth). Without it the client sends no
// credentials and this module still builds (IMG-04).
val hasSession = rootProject.findProject(":auth") != null
if (hasSession) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension> {
        // `kotlin` source dirs (not the variant API): KSP must see them, or the @IntoSet is silently lost.
        sourceSets.getByName("main").kotlin.directories.add("src/withAuth/java")
    }
}

dependencies {
    implementation(project(":lib-common"))
    // The debug HTTP logging interceptor and its runtime settings.
    implementation(project(":network"))
    if (hasSession) implementation(project(":auth"))

    implementation(libs.javax.inject)
    // api: OkHttpClient (@FilesClient) and okhttp3.Request (FilesAuthorizer) are part of this module's surface.
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    // The type reporterHttpLoggingInterceptor returns.
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(project(":core-testing"))
    testImplementation(libs.okhttp.mockwebserver)
}
