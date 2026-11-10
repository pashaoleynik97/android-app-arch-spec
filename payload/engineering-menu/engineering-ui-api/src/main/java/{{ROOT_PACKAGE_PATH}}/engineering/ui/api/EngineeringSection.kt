package {{ROOT_PACKAGE}}.engineering.ui.api

import kotlinx.coroutines.flow.Flow

/**
 * One section of the engineering menu, contributed by whichever module owns the diagnostics it shows:
 * `@Binds @IntoSet abstract fun bind(impl: MySection): EngineeringSection` in that module's Hilt module. The menu
 * declares the set with `@Multibinds`, so it works with no contributor at all.
 *
 * Sections are **data**, not screens: the menu renders every row, searches them, and performs their controls.
 * Copy is engineer-facing and English-only, so rows carry plain strings (no resources) — which is also what lets
 * the search match exactly the text on screen.
 */
interface EngineeringSection {

    /** Stable, unique among sections (used as a list key). */
    val key: String

    val title: String

    /** Lower comes first. The built-in device section is `0`; leave gaps. */
    val order: Int get() = 100

    /**
     * The section's rows, live. Collected only while the menu is visible, so a row may reflect fast-changing state
     * (a driver's status, the last hardware reading). Emit at least once.
     */
    val rows: Flow<List<EngineeringRow>>
}

/**
 * One row.
 *
 * [enabled] is a **real** disabled state (dimmed, unresponsive), reserved for a setting this build cannot honour —
 * say why in [description]. Plain information rows are not disabled; they carry [EngineeringControl.None].
 */
data class EngineeringRow(
    /** Unique within its section. */
    val key: String,
    val title: String,
    val description: String = "",
    val control: EngineeringControl = EngineeringControl.None,
    val enabled: Boolean = true,
)

/** What a row does when touched — which is also what it draws on its trailing edge. */
sealed interface EngineeringControl {

    /** A statement, not an affordance. */
    data object None : EngineeringControl

    /** Opens [route] (a destination registered by some feature); drawn with a chevron. */
    data class Navigate(val route: String) : EngineeringControl

    /** Flips a setting in place; drawn with a switch. The contributor persists the value and re-emits its rows. */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : EngineeringControl

    /** Opens a sheet of [options]; drawn with a chevron. [onSelect] receives the chosen option's key. */
    data class Picker(
        val options: List<EngineeringOption>,
        val selectedKey: String?,
        val onSelect: (String) -> Unit,
    ) : EngineeringControl
}

/** One choice of a [EngineeringControl.Picker]. */
data class EngineeringOption(
    val key: String,
    val label: String,
    val description: String = "",
)
