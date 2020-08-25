package me.settingdust.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.events.NucleusTeleportEvent
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.plugin.PluginContainer

@Singleton
class ModuleTeleport @Inject constructor(
    pluginContainer: PluginContainer,
    eventManager: EventManager
) {
    init {

    }

    private fun onSendCommand(event: NucleusTeleportEvent.AboutToTeleport) {
        event.apply {
        }
    }
}