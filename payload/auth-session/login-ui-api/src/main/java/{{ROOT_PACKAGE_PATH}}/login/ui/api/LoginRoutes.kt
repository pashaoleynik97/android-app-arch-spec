package {{ROOT_PACKAGE}}.login.ui.api

/**
 * Routes of the sign-in feature. The session host extension navigates here whenever there is no session, and
 * away from here to the start destination once there is one — the screen itself never navigates.
 */
object LoginRoutes {
    const val MAIN = "login/main"
}
