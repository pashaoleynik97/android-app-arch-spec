package {{ROOT_PACKAGE}}.featureapi

/**
 * Process-wide registry of the features present in this build, filled by [FeatureApi.addToRegistry] from
 * `Application.onCreate` and read by the host's `NavHost`.
 *
 * - Keyed by concrete class: registering the same feature type twice keeps one entry (the newest instance),
 *   so a re-run of the registration code cannot attach a graph twice.
 * - Iteration follows registration order, so destination registration is deterministic.
 *
 * Scale-up path: when the feature list grows, replace explicit registration with a Hilt
 * `@IntoSet FeatureApi` multibinding; the `NavHost` side does not change.
 */
object FeaturesHolder {

    private val registered = linkedMapOf<Class<out FeatureApi>, FeatureApi>()

    @Synchronized
    fun addFeature(feature: FeatureApi) {
        registered[feature::class.java] = feature
    }

    @Synchronized
    fun features(): List<FeatureApi> = registered.values.toList()

    /** For tests only: a global registry would otherwise carry state from one test into the next. */
    @Synchronized
    fun clear() = registered.clear()
}
