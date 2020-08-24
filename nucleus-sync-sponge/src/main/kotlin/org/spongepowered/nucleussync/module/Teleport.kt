package org.spongepowered.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.events.NucleusTeleportEvent
import me.settingdust.laven.sponge.isPlayerExist
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.nucleussync.core.BungeeChannel

@Singleton
class ModuleTeleport @Inject constructor(
    pluginContainer: PluginContainer,
    bungeeChannel: BungeeChannel,
    eventManager: EventManager
) {
    init {

    }

    private fun onSendCommand(event: NucleusTeleportEvent.AboutToTeleport) {
        event.apply {
        }
    }
}