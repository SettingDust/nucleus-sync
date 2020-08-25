package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusWarpEvent
import kotlinx.coroutines.*
import me.settingdust.laven.present
import me.settingdust.nucleussync.core.BungeeCordService
import me.settingdust.nucleussync.core.WarpQueue
import me.settingdust.nucleussync.core.Warps
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.Sponge
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.TeleportHelper

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleWarp @Inject constructor(
    pluginContainer: PluginContainer,
    eventManager: EventManager,
    private val teleportHelper: TeleportHelper,
    private val bungeeCordService: BungeeCordService
) {
    private val warpService = NucleusAPI.getWarpService().get()
    private val zeroLocation = Location(Sponge.getServer().worlds.first(), 0, 0, 0)

    init {
        eventManager.registerListeners(pluginContainer, this)
    }

    @Listener
    fun onSendCommand(event: SendCommandEvent, @Getter("getCause") cause: Cause, @First(typeFilter = [User::class]) user: User) {
        event.apply {
            if (command == "warp") {
                transaction { syncWarps() }
            }
        }
    }

    @Listener
    fun onCreateWarp(event: NucleusWarpEvent.Create) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                runBlocking {
                    val serverName = bungeeCordService.getServerName()
                    transaction {
                        Warps.insert {
                            it[id] = EntityID(name, this)
                            it[server] = serverName
                        }
                    }
                }
            }
        }
    }

    @Listener
    fun onDeleteWarp(event: NucleusWarpEvent.Delete) {
        event.apply {
            transaction {
                Warps.apply { deleteWhere { id eq name } }
            }
        }
    }

    @Listener
    fun onUseWarp(event: NucleusWarpEvent.Use, @Getter("getTargetUser") targetUser: User, @Getter("getName") warpName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            newSuspendedTransaction {
                Warps.apply {
                    val serverName =
                        slice(server)
                            .select { id eq warpName }
                            .single()[server]
                    if (serverName != bungeeCordService.getServerName()) {
                        bungeeCordService.connectServer(targetUser.name, serverName)
                        WarpQueue.apply {
                            insert {
                                it[id] = EntityID(targetUser.uniqueId, this)
                                it[name] = warpName
                            }
                        }
                    }
                }
            }
        }
    }

    @Listener
    fun onLogin(event: ClientConnectionEvent.Login, @Getter("getTargetUser") targetUser: User, @Getter("getCause") cause: Cause) {
        GlobalScope.launch {
            transaction {
                syncWarps()
                WarpQueue.apply {
                    val uniqueId = targetUser.uniqueId
                    val where = id eq uniqueId
                    slice(name).select { where }.singleOrNull()?.also { row ->
                        warpService
                            .getWarp(row[name])
                            .orElseThrow { NoSuchElementException("Nucleus warp") }
                            .apply { targetUser.rotation = rotation }
                            .run { location }
                            .flatMap { teleportHelper.getSafeLocation(it) }
                            .run { get() }
                            .apply { targetUser.setLocation(position, extent.uniqueId) }
                    }
                    deleteWhere { where }
                }
            }
        }
    }

    private fun syncWarps() {
        Warps.apply {
            val warps = slice(id).selectAll()
            warps
                .map { it[id].value }
                .filterNot { warpService.getWarp(it).present }
                .forEach {
                    warpService.setWarp(it, zeroLocation, Vector3d.UP)
                }
            warpService.allWarps.forEach { warp ->
                val name = warp.name
                if (warps.none { it[id].value == name }) {
                    warpService.removeWarp(name)
                }
            }
        }
    }
}