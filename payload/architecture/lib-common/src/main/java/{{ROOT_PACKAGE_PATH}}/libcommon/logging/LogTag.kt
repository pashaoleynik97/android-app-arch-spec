package {{ROOT_PACKAGE}}.libcommon.logging

/**
 * The tag a class logs under: its simple name. Declare it once per class, `private val logTag = logTag()`,
 * and pass it as `where = logTag` to every [Reporter] call so each line names its origin.
 */
fun Any.logTag(): String = this::class.java.simpleName
