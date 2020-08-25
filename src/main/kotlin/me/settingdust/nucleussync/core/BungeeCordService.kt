package me.settingdust.nucleussync.core

import com.google.inject.Inject
import com.google.inject.Singleton
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.network.ChannelRegistrar
import org.spongepowered.api.plugin.PluginContainer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class BungeeCordService @Inject constructor(
    channelRegistrar: ChannelRegistrar,
    pluginContainer: PluginContainer
) {
    private val channel = channelRegistrar.getOrCreateRaw(pluginContainer, "BungeeCord")
    private lateinit var serverName: String;

    suspend fun getServerName(): String {
        if (!this::serverName.isInitialized)
            serverName = suspendCoroutine { continuation ->
                channel.addListener { data, _, _ ->
                    data
                        .takeIf { it.readUTF() == "GetServer" }
                        ?.apply { continuation.resume(readUTF()) }
                }
                channel.sendTo { it.writeUTF("GetServer") }
            }
        return serverName
    }

    fun connectServer(player: Player, server: String) {
        channel.sendTo(player) {
            it.writeUTF("Connect")
            it.writeUTF(server)
        }
    }

    fun connectServer(name: String, server: String) {
        channel.sendTo {
            it.writeUTF("ConnectOther")
            it.writeUTF(name)
            it.writeUTF(server)
        }
    }
}