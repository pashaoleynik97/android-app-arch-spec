package {{ROOT_PACKAGE}}.engineering.ui.api

import {{ROOT_PACKAGE}}.featureapi.FeatureApi

/**
 * Navigation contract of the hidden engineering (diagnostics) menu. `:engineering-ui` implements it; the host and
 * features that offer an entry reference only this contract and [EngineeringRoutes].
 */
interface EngineeringFeatureApi : FeatureApi
