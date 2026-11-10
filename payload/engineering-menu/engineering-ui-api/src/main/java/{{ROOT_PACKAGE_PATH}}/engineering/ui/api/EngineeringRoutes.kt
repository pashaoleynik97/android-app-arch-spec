package {{ROOT_PACKAGE}}.engineering.ui.api

/**
 * Routes of the engineering menu. There is **no entry in ordinary navigation**: a screen that offers one receives
 * [MENU] from the host as a nullable constructor seam of its `FeatureImpl` (a hidden gesture such as
 * [TapSequenceDetector] on an "About" row, or an app-menu option on debug builds) and navigates to it with a plain
 * `navigate`, so back returns to where the engineer came from.
 */
object EngineeringRoutes {

    const val MENU = "engineering/menu"
}
