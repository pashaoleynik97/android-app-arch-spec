package {{ROOT_PACKAGE}}.files.auth

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.files.FilesAuthorizer

/**
 * Compiled only when the `auth-session` component is installed (see `files/build.gradle.kts`): files requests carry
 * the session's current access token as a bearer header. `currentAccessToken()` is an in-memory read, so it is safe
 * on OkHttp's threads for every request.
 *
 * A files host that wants a cookie instead: replace the body with `FilesAuthorizer.cookie("<name>") { … }`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionFilesAuthModule {

    @Provides
    @IntoSet
    fun provideSessionFilesAuthorizer(authManager: AuthManager): FilesAuthorizer =
        FilesAuthorizer.bearer { authManager.currentAccessToken() }
}
