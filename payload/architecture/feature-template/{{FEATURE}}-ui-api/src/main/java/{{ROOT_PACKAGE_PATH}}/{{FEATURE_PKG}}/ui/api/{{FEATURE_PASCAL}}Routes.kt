package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api

/**
 * Route strings of the {{FEATURE}} feature, namespaced `"{{FEATURE}}/<screen>"`.
 *
 * Conventions for routes added here:
 * - an argument is a `const val ARG_X` name, the registered template embeds it (`"{{FEATURE}}/details/{$ARG_ID}"`),
 *   and a builder function encodes the value (`fun details(id: String) = "{{FEATURE}}/details/${Uri.encode(id)}"`);
 *   navigate to builders, never to templates;
 * - optional arguments are query parameters, so callers without the value build the same route;
 * - routes carry ids only, never payloads — enough to re-read after process death;
 * - a result key (`NavResultKey`) is owned by the screen that **reads** it.
 */
object {{FEATURE_PASCAL}}Routes {
    /** The feature's entry screen. */
    const val MAIN = "{{FEATURE}}/main"
}
