package {{ROOT_PACKAGE}}.input.debug

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Always "supported" — there is nothing to detect — but at a **negative priority**, so `Auto` prefers any real
 * vendor driver and falls back to this one only where no hardware was found, i.e. an emulator. It can also be
 * forced on a real device from a settings screen. In a release build this module, and so this binding, is absent.
 */
@Singleton
class DebugBroadcastInputProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InputDriverProvider {

    override val id: InputDriverId = ID

    override val displayName: String = "Debug (adb broadcast)"

    override val priority: Int = PRIORITY

    override fun detect(): Detection =
        Detection.Supported("debug build; events injected with adb broadcast ${DebugInputEvents.action(context.packageName)}")

    override fun create(): InputDriver = DebugBroadcastInputDriver(context)

    companion object {
        val ID = InputDriverId("debug")
        const val PRIORITY = -100
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DebugInputModule {

    @Binds
    @IntoSet
    abstract fun bindDebugProvider(impl: DebugBroadcastInputProvider): InputDriverProvider
}
