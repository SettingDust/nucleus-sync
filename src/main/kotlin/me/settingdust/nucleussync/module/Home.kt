package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusHomeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.present
import me.settingdust.laven.sponge.registerListeners
import me.settingdust.laven.sponge.task
import me.settingdust.nucleussync.core.BungeeCordService
import me.settingdust.nucleussync.core.DatabaseService
import org.spongepowered.api.Sponge
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.filter.type.Include
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.TeleportHelper

@ExperimentalCoroutinesApi
@Singleton
class ModuleHome @ExperimentalCoroutinesApi @Inject constructor(
    private val pluginContainer: PluginContainer,
    databaseService: DatabaseService,
    private val teleportHelper: TeleportHelper,
    private val bungeeCordService: BungeeCordService
) {
    private val tableName: String = "homes"
    private val homeService = NucleusAPI.getHomeService().get()
    private val zeroLocation = Location(Sponge.getServer().worlds.first(), 0, 0, 0)
    private val datasource = databaseService.datasource

    init {
        pluginContainer.registerListeners(this)
    }


    @Listener
    @Include(NucleusHomeEvent.Create::class, NucleusHomeEvent.Modify::class)
    fun onCreateHome(event: NucleusHomeEvent) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                val serverName = bungeeCordService.getServerName()
                datasource.connection.use { connection ->
                    connection.prepareStatement("""
                        INSERT INTO $tableName
                        VALUES (?, UUID_TO_BIN(?, true), ?) ON DUPLICATE KEY
                        UPDATE `server` = '$serverName'
                    """.trimIndent()).use {
                        it.setString(1, name)
                        it.setString(2, user.uniqueId.toString())
                        it.setString(3, serverName)
                        it.executeUpdate()
                    }
                }
            }
        }
    }

    @Listener
    fun onSendCommand(event: SendCommandEvent, @First(typeFilter = [User::class]) user: User) {
        event.apply {
            if (command == "home") syncHomes(cause, user)
        }
    }

    @Listener
    fun onDeleteHome(event: NucleusHomeEvent.Delete) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                datasource.connection.use { connection ->
                    connection.prepareStatement("""
                        DELETE FROM $tableName
                        WHERE id = '$name'
                        	AND `playeruuid` = UUID_TO_BIN('${home.ownersUniqueId}', true)
                    """.trimIndent()).use { it.executeUpdate() }
                }
            }
        }
    }

    @Listener
    fun onUseHome(event: NucleusHomeEvent.Use) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                datasource.connection.use { connection ->
                    connection.prepareStatement("""
                        SELECT server FROM $tableName
                        WHERE id = '$name'
                        	AND `playeruuid` = UUID_TO_BIN('${home.ownersUniqueId}', true)
                    """.trimIndent())
                        .use { it.executeQuery() }
                        .takeIf { it.next() }
                        ?.use { resultSet ->
                            val serverName = resultSet.getString(1)
                            if (serverName != bungeeCordService.getServerName()) {
                                bungeeCordService.connectServer(home.user.name, serverName)
                                connection.prepareStatement("""
                                    INSERT INTO homequeue VALUES(UUID_TO_BIN('${home.ownersUniqueId}', true), '$name')
                                """.trimIndent()).use { it.executeUpdate() }
                            }
                        }
                }
            }
        }
    }

    @Listener
    fun onLogin(event: ClientConnectionEvent.Login) {
        event.apply {
            GlobalScope.launch {
                syncHomes(cause, targetUser)
                val uniqueId = targetUser.uniqueId
                datasource.connection.use { connection ->
                    connection.prepareStatement("SELECT name FROM homequeue WHERE id=UUID_TO_BIN('$uniqueId', true)")
                        .use { it.executeQuery() }
                        .takeIf { it.next() }
                        ?.use { resultSet ->
                            homeService
                                .getHome(uniqueId, resultSet.getString(1))
                                .orElseThrow { NoSuchElementException("Nucleus home") }
                                .apply { user.rotation = rotation }
                                .run { location.get() }
                                .run { teleportHelper.getSafeLocation(this).orElse(this) }
                                .apply { pluginContainer.task { targetUser.setLocation(position, extent.uniqueId) } }

                            connection.prepareStatement("DELETE FROM homequeue WHERE id=UUID_TO_BIN('$uniqueId', true)")
                                .use { it.executeUpdate() }
                        }
                }
            }
        }
    }

    private fun syncHomes(cause: Cause, user: User) {
        datasource.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM homes WHERE playeruuid=UUID_TO_BIN('${user.uniqueId}', true)")
                .use { it.executeQuery() }
                .use { resultSet -> generateSequence { if (resultSet.next()) resultSet.getString(1) else null }.toSet() }
                .apply {
                    filterNot { homeService.getHome(user.uniqueId, it).present }
                        .forEach { homeService.createHome(cause, user, it, zeroLocation, Vector3d.UP) }

                    homeService.getHomes(user)
                        .filter { home -> none { it == home.name } }
                        .forEach { home -> homeService.removeHome(cause, home) }
                }
        }
    }
}