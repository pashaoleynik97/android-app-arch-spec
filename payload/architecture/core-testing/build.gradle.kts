// :core-testing — shared JVM test fixtures for the kernel seams. Consumed ONLY as
// `testImplementation(project(":core-testing"))`; never from main code.
plugins {
    alias(libs.plugins.{{CONVENTION_PREFIX}}.android.library)
}

android {
    namespace = "{{ROOT_PACKAGE}}.coretesting"
}

dependencies {
    api(project(":lib-common"))
    // The fixtures are written against these, so consumers get them through this module.
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
