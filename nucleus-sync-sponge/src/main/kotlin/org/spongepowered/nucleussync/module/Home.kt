package org.spongepowered.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusHomeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.sponge.Packet
import me.settingdust.laven.sponge.writePacket
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.Platform
import org.spongepowered.api.Sponge
import org.spongepowered.api.event.CauseStackManager
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.service.user.UserStorageService
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.TeleportHelper
import org.spongepowered.nucleussync.Homes
import org.spongepowered.nucleussync.core.BungeeChannel
import org.spongepowered.nucleussync.core.sendTo
import org.spongepowered.nucleussync.core.writePluginChannel
import org.spongepowered.nucleussync.pluginName
import java.util.*

@Singleton
class ModuleHome @ExperimentalCoroutinesApi @Inject constructor(
    pluginContainer: PluginContainer,
    bungeeChannel: BungeeChannel,
    eventManager: EventManager,
    causeStackManager: CauseStackManager,
    teleportHelper: TeleportHelper,
    serviceManager: ServiceManager
) {
    private val channel = bungeeChannel.channel
    private val homeService = NucleusAPI.getHomeService().get()

    init {
        channel.addListener(Platform.Type.SERVER) { data, _, _ ->
            data.resetRead()
            data.takeIf { data.readString() == pluginName }?.let {
                when (data.readString()) {
                    PacketHomeCreate.channel -> {
                        PacketHomeCreate(data)
                            .apply {
                                homeService.createHome(
                                    causeStackManager.currentCause,
                                    playerUuid,
                                    name,
                                    Location(Sponge.getServer().worlds.first(), 0, 0, 0),
                                    Vector3d.UP
                                )
                            }
                    }
                    PacketWarpUse.channel -> {
                        PacketHomeUse(data).apply {
                            homeService.getHome(playerUuid, name).ifPresent { home ->
                                home.location
                                    .flatMap { teleportHelper.getSafeLocation(it) }
                                    .ifPresent { location ->
                                        serviceManager.provideUnchecked(UserStorageService::class.java)[playerUuid].ifPresent { user ->
                                            user.setLocation(location.position, location.extent.uniqueId)
                                            user.rotation = home.rotation
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }

        eventManager.registerListener(pluginContainer, NucleusHomeEvent.Create::class.java, this::onCreateHome)
        eventManager.registerListener(pluginContainer, NucleusHomeEvent.Delete::class.java, this::onDeleteHome)
        eventManager.registerListener(pluginContainer, NucleusHomeEvent.Use::class.java, this::onUseHome)
    }

    private fun onCreateHome(event: NucleusHomeEvent.Create) {
        event.apply {
            GlobalScope.launch {
                homeService.getHome(user, name).ifPresent { home ->
                    channel.sendTo {
                        it.writePluginChannel()
                        it.writePacket(
                            PacketHomeCreate(
                                name,
                                home.ownersUniqueId
                            )
                        )
                    }
                }
            }
        }
    }

    private fun onDeleteHome(event: NucleusHomeEvent.Delete) {
        event.apply { Homes.apply { deleteWhere { id eq name } } }
    }

    private fun onUseHome(event: NucleusHomeEvent.Use) {
        event.apply {
            channel.sendTo {
                it.writePluginChannel()
                it.writePacket(PacketHomeUse(name, targetUser.uniqueId))
            }
        }
    }
}

data class PacketHomeCreate(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "HomeCreate"
    }

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF(),
        channelBuf.readUniqueId()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(channel)
        channelBuf.writeUTF(name)
        channelBuf.writeUniqueId(playerUuid)
    }
}

data class PacketHomeUse(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "HomeUse"
    }

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF(),
        channelBuf.readUniqueId()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(channel)
        channelBuf.writeUTF(name)
        channelBuf.writeUniqueId(playerUuid)
    }
}