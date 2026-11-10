package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.ui.api

import {{ROOT_PACKAGE}}.featureapi.FeatureApi

/**
 * Navigation contract of the {{FEATURE}} feature. `:{{FEATURE}}-ui` implements it; the host and sibling
 * features reference only this contract and [{{FEATURE_PASCAL}}Routes] — never the feature's UI or domain code.
 */
interface {{FEATURE_PASCAL}}FeatureApi : FeatureApi
