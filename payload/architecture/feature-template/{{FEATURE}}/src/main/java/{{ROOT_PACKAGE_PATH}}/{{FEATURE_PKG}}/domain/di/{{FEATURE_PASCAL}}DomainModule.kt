package {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.Default{{FEATURE_PASCAL}}Interactor
import {{ROOT_PACKAGE}}.{{FEATURE_PKG}}.domain.{{FEATURE_PASCAL}}Interactor

/**
 * Binds the {{FEATURE}} domain seams. A port this feature needs from a sibling feature is declared here as
 * this feature's own `fun interface` and left unbound — the host binds it, because only the host may see both
 * features.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class {{FEATURE_PASCAL}}DomainModule {

    @Binds
    abstract fun bind{{FEATURE_PASCAL}}Interactor(impl: Default{{FEATURE_PASCAL}}Interactor): {{FEATURE_PASCAL}}Interactor
}
