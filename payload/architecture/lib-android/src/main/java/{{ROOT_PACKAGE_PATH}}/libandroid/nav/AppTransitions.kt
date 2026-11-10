package {{ROOT_PACKAGE}}.libandroid.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

/**
 * The app's screen transitions — native navigation-compose, no extra library. Pass all four to every
 * non-root destination so the whole app moves the same way:
 *
 * ```
 * navGraphBuilder.composable(
 *     route = XRoutes.DETAILS,
 *     enterTransition = AppTransitions.enter, exitTransition = AppTransitions.exit,
 *     popEnterTransition = AppTransitions.popEnter, popExitTransition = AppTransitions.popExit,
 * ) { … }
 * ```
 */
object AppTransitions {

    private const val DURATION_MS = 300

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.Start, tween(DURATION_MS))
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.Start, tween(DURATION_MS))
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.End, tween(DURATION_MS))
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.End, tween(DURATION_MS))
    }
}
