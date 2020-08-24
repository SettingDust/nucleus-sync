package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusWarpEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.sponge.Packet
import me.settingdust.laven.sponge.writePacket
import me.settingdust.laven.unwrap
import org.h2.engine.User
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.Platform
import org.spongepowered.api.Sponge
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.service.user.UserStorageService
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.serializer.TextSerializers
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.TeleportHelper
import me.settingdust.nucleussync.Warps
import me.settingdust.nucleussync.core.BungeeChannel
import me.settingdust.nucleussync.core.sendTo
import me.settingdust.nucleussync.core.writePluginChannel
import me.settingdust.nucleussync.pluginName
import java.util.*

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleWarp @Inject constructor(
    pluginContainer: PluginContainer,
    bungeeChannel: BungeeChannel,
    eventManager: EventManager,
    teleportHelper: TeleportHelper,
    serviceManager: ServiceManager
) {
    private val bungeeCordChannel = bungeeChannel.channel
    private val warpService = NucleusAPI.getWarpService().get()

    init {
        bungeeCordChannel.addListener(Platform.Type.SERVER) { data, _, _ ->
            data.resetRead()
            data.takeIf { data.readString() == pluginName }?.let {
                when (data.readString()) {
                    PacketWarpCreate.channel -> {
                        PacketWarpCreate(data)
                            .apply {
                                warpService.setWarp(name, Location(Sponge.getServer().worlds.first(), 0, 0, 0), Vector3d.UP)
                                warpService.setWarpCategory(name, category)
                                warpService.setWarpDescription(name, description)
                                warpService.setWarpCost(name, cost ?: 0.0)
                            }
                    }
                    PacketWarpUse.channel -> {
                        PacketWarpUse(data).apply {
                            warpService.getWarp(name).ifPresent { warp ->
                                warp.location
                                    .flatMap { teleportHelper.getSafeLocation(it) }
                                    .ifPresent { location ->
                                        serviceManager.provideUnchecked(UserStorageService::class.java)[playerUuid].ifPresent { user ->
                                            user.setLocation(location.position, location.extent.uniqueId)
                                            user.rotation = warp.rotation
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }

        eventManager.registerListener(pluginContainer, NucleusWarpEvent.Create::class.java, this::onCreateWarp)
        eventManager.registerListener(pluginContainer, NucleusWarpEvent.Delete::class.java, this::onDeleteWarp)
        eventManager.registerListener(pluginContainer, NucleusWarpEvent.Use::class.java, this::onUseWarp)
    }

    private fun onCreateWarp(event: NucleusWarpEvent.Create) {
        event.apply {
            GlobalScope.launch {
                warpService.getWarp(name).ifPresent { warp ->
                    bungeeCordChannel.sendTo {
                        it.writePluginChannel()
                        it.writePacket(
                            PacketWarpCreate(
                                name,
                                warp.description.unwrap(),
                                warp.category.unwrap(),
                                warp.cost.unwrap()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun onDeleteWarp(event: NucleusWarpEvent.Delete) {
        event.apply { GlobalScope.launch { transaction { Warps.apply { deleteWhere { id eq name } } } } }
    }

    private fun onUseWarp(event: NucleusWarpEvent.Use) {
        event.apply {
            GlobalScope.launch {
                bungeeCordChannel.sendTo {
                    it.writePluginChannel()
                    it.writePacket(PacketWarpUse(name, targetUser.uniqueId))
                }
            }
        }
    }
}

data class PacketWarpCreate(
    val name: String,
    val description: Text?,
    val category: String?,
    val cost: Double?
) : Packet {
    companion object {
        const val channel = "WarpCreate"
    }

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF(),
        TextSerializers.JSON.deserialize(channelBuf.readUTF()),
        channelBuf.readUTF(),
        channelBuf.readDouble()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(channel)
        channelBuf.writeUTF(name)
        channelBuf.writeUTF(if (description != null) TextSerializers.JSON.serialize(description) else "")
        channelBuf.writeUTF(category ?: "")
        channelBuf.writeDouble(cost ?: 0.0)
    }
}

data class PacketWarpUse(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "WarpUse"
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