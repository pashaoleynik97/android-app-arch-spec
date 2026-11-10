package {{ROOT_PACKAGE}}.engineering.ui.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringSection
import {{ROOT_PACKAGE}}.engineering.ui.device.DeviceInfoProvider
import {{ROOT_PACKAGE}}.engineering.ui.device.DeviceInfoSection
import {{ROOT_PACKAGE}}.engineering.ui.device.OsDeviceInfoProvider

/**
 * DI for the engineering menu. Declares the [EngineeringSection] set (`@Multibinds`: contributors are optional
 * modules) and contributes the built-in device section.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class EngineeringModule {

    @Binds
    abstract fun bindDeviceInfoProvider(impl: OsDeviceInfoProvider): DeviceInfoProvider

    @Multibinds
    abstract fun engineeringSections(): Set<EngineeringSection>

    @Binds
    @IntoSet
    abstract fun bindDeviceInfoSection(impl: DeviceInfoSection): EngineeringSection
}
