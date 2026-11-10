import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Android configuration shared by application and library modules.
 *
 * On AGP 9 Kotlin is built in (the `org.jetbrains.kotlin.android` plugin must not be applied), so only the
 * Android DSL is configured here. `CommonExtension` has no type parameters on AGP 9, and its blocks are
 * reached as properties (`defaultConfig.apply { }`), not through the old lambda DSL.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = ProjectConfig.COMPILE_SDK

        defaultConfig.apply {
            minSdk = ProjectConfig.MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.toVersion(ProjectConfig.JVM_TARGET)
            targetCompatibility = JavaVersion.toVersion(ProjectConfig.JVM_TARGET)
        }
    }
}

/**
 * JVM unit-test defaults for every module, so no module build file repeats them.
 *
 * - `isReturnDefaultValues = true`: logging goes through `Reporter`, which calls `android.util.Log`; on the
 *   JVM the stubbed android.jar would throw "Method … not mocked". Returning defaults keeps plain JVM tests
 *   of ViewModels, interactors and policies runnable without Robolectric.
 * - The standard kit: JUnit4, kotlinx-coroutines-test, Turbine. Hand-written fakes, no mocking library.
 * - The instrumented-test basics, so the stock example instrumented test keeps compiling.
 */
internal fun Project.configureUnitTests(commonExtension: CommonExtension) {
    commonExtension.testOptions.apply {
        unitTests.isReturnDefaultValues = true
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        add("testImplementation", libs.findLibrary("turbine").get())
        add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
        add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())
    }
}
