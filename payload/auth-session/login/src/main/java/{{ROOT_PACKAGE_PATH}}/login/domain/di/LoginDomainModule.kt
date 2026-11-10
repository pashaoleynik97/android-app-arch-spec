package {{ROOT_PACKAGE}}.login.domain.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.login.domain.DefaultLoginInteractor
import {{ROOT_PACKAGE}}.login.domain.LoginInteractor

/** Binds the sign-in domain. Leaves nothing unbound. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoginDomainModule {

    @Binds
    abstract fun bindLoginInteractor(impl: DefaultLoginInteractor): LoginInteractor
}
