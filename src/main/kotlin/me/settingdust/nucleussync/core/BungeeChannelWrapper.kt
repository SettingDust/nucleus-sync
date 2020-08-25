package me.settingdust.nucleussync.core

import org.spongepowered.api.Sponge
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.network.ChannelBuf

fun ChannelBinding.RawDataChannel.sendTo(payload: (ChannelBuf) -> Unit) = sendTo(
    if (Sponge.getServer().onlinePlayers.isEmpty())
        throw IllegalStateException("No player online")
    else
        Sponge.getServer().onlinePlayers.first()
    , payload
)