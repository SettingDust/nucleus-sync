package org.spongepowered.nucleussync.core

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.exposed.sql.Database
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GamePreInitializationEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.service.sql.SqlService
import org.spongepowered.nucleussync.config.ConfigMain

@Singleton
@ExperimentalCoroutinesApi
class DatabaseService @Inject constructor(
    private val configMain: ConfigMain,
    private val serviceManager: ServiceManager,
    private val pluginContainer: PluginContainer,
    eventManager: EventManager
) {
    init {
        eventManager.registerListener(pluginContainer, GamePreInitializationEvent::class.java, this::onPreInit)
        eventManager.registerListener(pluginContainer, GameReloadEvent::class.java, this::onReload)
    }

    private fun onPreInit(event: GamePreInitializationEvent) = connect()
    private fun onReload(event: GameReloadEvent) = connect()

    fun connect() {
        val sqlService = serviceManager.provideUnchecked(SqlService::class.java)
        sqlService
            .getConnectionUrlFromAlias(configMain.model.databaseAlias)
            .run { orElseThrow { NoSuchElementException("Cannot find the database alias") } }
            .run { sqlService.getDataSource(pluginContainer, this) }
            .apply(Database::connect)
    }
}