package org.spongepowered.nucleussync.core

import com.google.inject.Inject
import org.spongepowered.api.Sponge
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.network.ChannelRegistrar
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.nucleussync.pluginName
import java.util.function.Consumer

fun ChannelBuf.writePluginChannel(): ChannelBuf = this.writeString(pluginName)

fun ChannelBinding.RawDataChannel.sendTo(payload: (ChannelBuf) -> Unit) = sendToAll(payload)

class BungeeChannel @Inject constructor(
    channelRegistrar: ChannelRegistrar,
    pluginContainer: PluginContainer,
) {
    val channel: ChannelBinding.RawDataChannel = channelRegistrar.getOrCreateRaw(pluginContainer, "BungeeCord")
}