package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusWarpEvent
import kotlinx.coroutines.*
import me.settingdust.laven.present
import me.settingdust.laven.sponge.task
import me.settingdust.nucleussync.core.BungeeCordService
import me.settingdust.nucleussync.core.DatabaseService
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
    private val pluginContainer: PluginContainer,
    databaseService: DatabaseService,
    eventManager: EventManager,
    private val teleportHelper: TeleportHelper,
    private val bungeeCordService: BungeeCordService
) {
    private val tableName: String = "warps"
    private val warpService = NucleusAPI.getWarpService().get()
    private val zeroLocation = Location(Sponge.getServer().worlds.first(), 0, 0, 0)
    private val datasource = databaseService.datasource

    init {
        eventManager.registerListeners(pluginContainer, this)
    }

    @Listener
    fun onSendCommand(event: SendCommandEvent, @Getter("getCause") cause: Cause, @First(typeFilter = [User::class]) user: User) {
        event.apply {
            if (command == "warp") syncWarps()
        }
    }

    @Listener
    fun onCreateWarp(event: NucleusWarpEvent.Create) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                runBlocking {
                    val serverName = bungeeCordService.getServerName()
                    datasource.connection.use { connection ->
                        connection.prepareStatement("""
                            INSERT INTO $tableName
                            (id, server)
                            VALUES (?, ?)
                        """.trimIndent()).use {
                            it.setString(1, name)
                            it.setString(2, serverName)
                            it.executeUpdate()
                        }
                    }
                }
            }
        }
    }

    @Listener
    fun onDeleteWarp(event: NucleusWarpEvent.Delete) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                datasource.connection.use { connection ->
                    connection.prepareStatement("""
                        DELETE FROM $tableName
                        WHERE id = '$name'
                    """.trimIndent()).use { it.executeUpdate() }
                }
            }
        }
    }

    @Listener
    fun onUseWarp(event: NucleusWarpEvent.Use, @Getter("getTargetUser") targetUser: User, @Getter("getName") warpName: String) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                datasource.connection.use { connection ->
                    connection.prepareStatement("""
                        SELECT server FROM $tableName
                        WHERE id = '$name'
                    """.trimIndent())
                        .use { it.executeQuery() }
                        .takeIf { it.next() }
                        ?.use { resultSet ->
                            val serverName = resultSet.getString(1)
                            if (serverName != bungeeCordService.getServerName()) {
                                bungeeCordService.connectServer(targetUser.name, serverName)
                                connection.prepareStatement("""
                                    INSERT INTO warpqueue VALUES(UUID_TO_BIN('${targetUser.uniqueId}', true), '$name')
                                """.trimIndent()).use { it.executeUpdate() }
                            }
                        }
                }
            }
        }
    }

    @Listener
    fun onLogin(event: ClientConnectionEvent.Login, @Getter("getTargetUser") targetUser: User, @Getter("getCause") cause: Cause) {
        event.apply {
            GlobalScope.launch {
                syncWarps()
                val uniqueId = targetUser.uniqueId
                datasource.connection.use { connection ->
                    connection.prepareStatement("SELECT name FROM warpqueue WHERE id=UUID_TO_BIN('$uniqueId', true)")
                        .use { it.executeQuery() }
                        .takeIf { it.next() }
                        ?.use { resultSet ->
                            warpService
                                .getWarp(resultSet.getString(1))
                                .orElseThrow { NoSuchElementException("Nucleus warp") }
                                .apply { targetUser.rotation = rotation }
                                .run { location.get() }
                                .run { teleportHelper.getSafeLocation(this).orElse(this) }
                                .apply { pluginContainer.task { targetUser.setLocation(position, extent.uniqueId) } }

                            connection.prepareStatement("DELETE FROM warpqueue WHERE id=UUID_TO_BIN('$uniqueId', true)")
                                .use { it.executeUpdate() }
                        }
                }
            }
        }
    }

    private fun syncWarps() {
        datasource.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM warps")
                .use { it.executeQuery() }
                .use { resultSet -> generateSequence { if (resultSet.next()) resultSet.getString(1) else null }.toSet() }
                .apply {
                    filterNot { warpService.getWarp(it).present }
                        .forEach { warpService.setWarp(it, zeroLocation, Vector3d.UP) }

                    warpService.allWarps
                        .filter { warp -> none { it == warp.name } }
                        .forEach { warp -> warpService.removeWarp(warp.name) }
                }
        }
    }
}