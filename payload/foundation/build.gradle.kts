// Top-level build file. Declaring the plugins here (`apply false`) puts them on the shared build-script
// classpath, so the build-logic convention plugins can apply them by id on each module, and every module
// that applies one by alias resolves the same version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless) apply false
}
