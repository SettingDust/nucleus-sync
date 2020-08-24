package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusWarpEvent
import io.github.nucleuspowered.nucleus.api.nucleusdata.Warp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.sponge.Packet
import me.settingdust.laven.sponge.writePacket
import me.settingdust.laven.unwrap
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
import me.settingdust.nucleussync.core.PluginChannel
import me.settingdust.nucleussync.core.sendTo
import me.settingdust.nucleussync.pluginName
import org.spongepowered.api.scheduler.Task
import java.util.*

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleWarp @Inject constructor(
    pluginContainer: PluginContainer,
    pluginChannel: PluginChannel,
    eventManager: EventManager,
    teleportHelper: TeleportHelper,
    serviceManager: ServiceManager
) {
    private val pluginChannel = pluginChannel.channel
    private val warpService = NucleusAPI.getWarpService().get()

    init {
        this.pluginChannel.addListener(Platform.Type.SERVER) { data, _, _ ->
            data.resetRead()
            data.let {
                when (data.readUTF()) {
                    PacketWarpCreate.channel -> {
                        PacketWarpCreate(data)
                            .apply {
                                warpService.setWarp(name, Location(Sponge.getServer().worlds.first(), 0, 0, 0), Vector3d.UP)
                                warpService.setWarpCategory(name, category)
                                warpService.setWarpDescription(name, description)
                                warpService.setWarpCost(name, cost ?: 0.0)
                            }
                    }
                    PacketWarpDelete.channel -> {
                        PacketWarpDelete(data).apply { warpService.removeWarp(name) }
                    }
                    PacketWarpUse.channel -> {
                        PacketWarpUse(data).apply {
                            warpService.getWarp(name).ifPresent { warp ->
                                warp.location
                                    .flatMap { teleportHelper.getSafeLocation(it) }
                                    .ifPresent { location ->
                                        serviceManager.provideUnchecked(UserStorageService::class.java)[playerUuid].ifPresent { user ->
                                            Task.builder().execute { ->
                                                user.setLocation(location.position, location.extent.uniqueId)
                                                user.rotation = warp.rotation
                                            }.submit(pluginContainer)
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
                    pluginChannel.sendTo {
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
        event.apply { pluginChannel.sendTo { it.writePacket(PacketWarpDelete(name)) } }
    }

    private fun onUseWarp(event: NucleusWarpEvent.Use) {
        event.apply {
            GlobalScope.launch {
                pluginChannel.sendTo {
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

data class PacketWarpDelete(
    val name: String
) : Packet {
    companion object {
        const val channel = "WarpDelete"
    }

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(channel)
        channelBuf.writeUTF(name)
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