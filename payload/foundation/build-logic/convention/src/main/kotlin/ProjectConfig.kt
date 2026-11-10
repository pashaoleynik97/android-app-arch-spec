import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The SDK and bytecode levels every module shares. Change them here, never in a module build file.
 *
 * COMPILE_SDK may sit one above TARGET_SDK: some androidx releases require a newer compile SDK before an app
 * is ready to target it (see the toolchain notes in the catalog).
 */
object ProjectConfig {
    const val COMPILE_SDK = {{COMPILE_SDK}}
    const val MIN_SDK = {{MIN_SDK}}
    const val TARGET_SDK = {{TARGET_SDK}}

    /** Java bytecode level; AGP aligns Kotlin's `jvmTarget` with it automatically. */
    const val JVM_TARGET = {{JVM_TARGET}}
}

/**
 * The `libs` version catalog, for use inside convention plugins (`libs.findLibrary("…").get()`).
 *
 * MUST stay `internal`: a public `Project.libs` extension leaks onto the build-script classpath of every
 * module and shadows Gradle's generated type-safe `libs` accessor, breaking `libs.androidx.*` there.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
