package {{ROOT_PACKAGE}}.connectivity.di

import javax.inject.Qualifier

/** The probe's own OkHttp client: short timeouts, no credentials, unaffected by any session state. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConnectivityClient

/** App-lifetime scope (`SupervisorJob + Default`) for the state driver and the banner controller. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConnectivityScope
