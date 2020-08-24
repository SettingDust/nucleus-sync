package me.settingdust.nucleussync.module

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
import org.jetbrains.exposed.sql.deleteWhere
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
import me.settingdust.nucleussync.Homes
import me.settingdust.nucleussync.core.PluginChannel
import me.settingdust.nucleussync.core.sendTo
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.event.filter.type.Include
import org.spongepowered.api.scheduler.Task
import java.util.*

@Singleton
class ModuleHome @ExperimentalCoroutinesApi @Inject constructor(
    pluginContainer: PluginContainer,
    pluginChannel: PluginChannel,
    eventManager: EventManager,
    causeStackManager: CauseStackManager,
    teleportHelper: TeleportHelper,
    serviceManager: ServiceManager
) {
    private val channel = pluginChannel.channel
    private val homeService = NucleusAPI.getHomeService().get()

    init {
        channel.addListener(Platform.Type.SERVER) { data, _, _ ->
            data.resetRead()
            data.let {
                when (data.readUTF()) {
                    PacketHomeCreate.channel -> {
                        PacketHome(data)
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
                    PacketHomeDelete.channel -> {
                        PacketHome(data)
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
                    PacketHomeUse.channel -> {
                        PacketHome(data).apply {
                            homeService.getHome(playerUuid, name).ifPresent { home ->
                                home.location
                                    .flatMap { teleportHelper.getSafeLocation(it) }
                                    .ifPresent { location ->
                                        serviceManager.provideUnchecked(UserStorageService::class.java)[playerUuid].ifPresent { user ->
                                            Task.builder().execute { ->
                                                user.setLocation(location.position, location.extent.uniqueId)
                                                user.rotation = home.rotation
                                            }.submit(pluginContainer)
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }

        eventManager.registerListener(pluginContainer, NucleusHomeEvent::class.java, this::onCreateHome)
        eventManager.registerListener(pluginContainer, NucleusHomeEvent.Delete::class.java, this::onDeleteHome)
        eventManager.registerListener(pluginContainer, NucleusHomeEvent.Use::class.java, this::onUseHome)
    }

    @Include(NucleusHomeEvent.Create::class, NucleusHomeEvent.Modify::class)
    private fun onCreateHome(event: NucleusHomeEvent) {
        event.apply {
            GlobalScope.launch {
                homeService.getHome(user, name).ifPresent { home ->
                    channel.sendTo {
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
        event.apply { channel.sendTo { it.writePacket(PacketHomeDelete(name, home.ownersUniqueId)) } }
    }

    private fun onUseHome(event: NucleusHomeEvent.Use) {
        event.apply {
            channel.sendTo { it.writePacket(PacketHomeUse(name, home.ownersUniqueId)) }
        }
    }
}

open class PacketHome(
    val name: String,
    val playerUuid: UUID
) : Packet {
    open val channelName = ""

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF(),
        channelBuf.readUniqueId()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(this.channelName)
        channelBuf.writeUTF(name)
        channelBuf.writeUniqueId(playerUuid)
    }
}

class PacketHomeCreate(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeCreate"
    }

    override val channelName: String
        get() = channel
}

class PacketHomeDelete(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeDelete"
    }
    override val channelName: String
        get() = channel
}

class PacketHomeUse(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeUse"
    }
    override val channelName: String
        get() = channel
}