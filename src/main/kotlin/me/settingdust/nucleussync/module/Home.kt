package me.settingdust.nucleussync.module

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import io.github.nucleuspowered.nucleus.api.events.NucleusHomeEvent
import io.github.nucleuspowered.nucleus.api.nucleusdata.Home
import kotlinx.coroutines.*
import me.settingdust.laven.present
import me.settingdust.laven.sponge.Packet
import me.settingdust.nucleussync.core.*
import org.spongepowered.api.Sponge
import org.spongepowered.api.event.CauseStackManager
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.TeleportHelper
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.filter.type.Include
import org.spongepowered.api.event.network.ClientConnectionEvent
import java.util.*
import kotlin.NoSuchElementException

@Singleton
class ModuleHome @ExperimentalCoroutinesApi @Inject constructor(
    pluginContainer: PluginContainer,
    eventManager: EventManager,
    private val teleportHelper: TeleportHelper,
    private val bungeeCordService: BungeeCordService
) {
    private val homeService = NucleusAPI.getHomeService().get()
    private val zeroLocation = Location(Sponge.getServer().worlds.first(), 0, 0, 0)

    init {
        eventManager.registerListeners(pluginContainer, this)
    }


    @Listener
    @Include(NucleusHomeEvent.Create::class, NucleusHomeEvent.Modify::class)
    fun onCreateHome(event: NucleusHomeEvent) {
        event.apply {
            GlobalScope.launch(Dispatchers.IO) {
                runBlocking {
                    val serverName = bungeeCordService.getServerName()
                    transaction {
                        Homes.apply {
                            insertOrUpdate(server) {
                                it[id] = EntityID(name, this)
                                it[playerUuid] = user.uniqueId
                                it[server] = serverName
                            }
                        }
                    }
                }
            }
        }
    }

    @Listener
    fun onSendCommand(event: SendCommandEvent, @Getter("getCause") cause: Cause, @First(typeFilter = [User::class]) user: User) {
        event.apply {
            if (command == "home") {
                transaction { syncHomes(cause, user) }
            }
        }
    }

    @Listener
    fun onDeleteHome(event: NucleusHomeEvent.Delete) {
        event.apply {
            transaction { Homes.apply { deleteWhere { id eq name and (playerUuid eq home.ownersUniqueId) } } }
        }
    }

    @Listener
    fun onUseHome(event: NucleusHomeEvent.Use, @Getter("getHome") home: Home, @Getter("getName") homeName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            newSuspendedTransaction {
                Homes.apply {
                    val serverName =
                        slice(server)
                            .select { id eq homeName and (playerUuid eq home.ownersUniqueId) }
                            .single()[server]
                    if (serverName != bungeeCordService.getServerName()) {
                        bungeeCordService.connectServer(home.user.name, serverName)
                        HomeQueue.apply {
                            insert {
                                it[id] = EntityID(home.ownersUniqueId, this)
                                it[name] = homeName
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
                syncHomes(cause, targetUser)
                HomeQueue.apply {
                    val uniqueId = targetUser.uniqueId
                    val where = id eq uniqueId
                    slice(name).select { where }.singleOrNull()?.also { row ->
                        homeService
                            .getHome(uniqueId, row[name])
                            .orElseThrow { NoSuchElementException("Nucleus home") }
                            .apply { user.rotation = rotation }
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

    private fun syncHomes(cause: Cause, user: User) {
        Homes.apply {
            val playerHomes = slice(id).select { playerUuid eq user.uniqueId }
            playerHomes
                .filterNot { homeService.getHome(user.uniqueId, it[id].value).present }
                .forEach {
                    homeService.createHome(cause, user, it[id].value, zeroLocation, Vector3d.UP)
                }

            homeService.getHomes(user).forEach { home ->
                playerHomes
                    .none { it[id].value == home.name }
                    .takeIf { it }
                    ?.apply { homeService.removeHome(cause, home) }
            }
        }
    }
}