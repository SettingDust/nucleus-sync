package me.settingdust.nucleussync.core

import com.google.inject.Inject
import org.spongepowered.api.Sponge
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.network.ChannelRegistrar
import org.spongepowered.api.plugin.PluginContainer
import me.settingdust.nucleussync.pluginName

fun ChannelBinding.RawDataChannel.sendTo(payload: (ChannelBuf) -> Unit) = sendTo(
    if (Sponge.getServer().onlinePlayers.isEmpty())
        throw IllegalStateException("No player online")
    else
        Sponge.getServer().onlinePlayers.first(), payload
)

class PluginChannel @Inject constructor(
    channelRegistrar: ChannelRegistrar,
    pluginContainer: PluginContainer,
) {
    val channel: ChannelBinding.RawDataChannel = channelRegistrar.getOrCreateRaw(pluginContainer, pluginName)
}