package me.settingdust.nucleussync.core

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.settingdust.laven.sponge.get
import me.settingdust.laven.sponge.provide
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.typeTokenOf
import me.settingdust.nucleussync.config.ConfigMain
import org.spongepowered.api.event.game.state.GamePreInitializationEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.service.sql.SqlService
import java.util.regex.Pattern
import javax.sql.DataSource

@Suppress("UnstableApiUsage")
@Singleton
@ExperimentalCoroutinesApi
class DatabaseService @Inject constructor(
    private val injector: Injector,
    private val serviceManager: ServiceManager,
    private val pluginContainer: PluginContainer
) {
    lateinit var datasource: DataSource
    private val urlRegex = Pattern.compile("(?:jdbc:)?([^:]+):(//)?(?:([^:]+)(?::([^@]+))?@)?(.*)")
    private val initSql =
        pluginContainer
            .getAsset("create-all.sql")
            .orElseThrow { NoSuchElementException("create-all.sql can't be find") }

    init {
        pluginContainer.registerListener<GamePreInitializationEvent> { connect() }
    }

    fun connect() {
        serviceManager.provide<SqlService>().ifPresent {
            val databaseUrl = injector[typeTokenOf<ConfigMain>()].model.databaseUrl
            it.apply {
                datasource =
                    if (urlRegex.matcher(databaseUrl).matches()) getDataSource(databaseUrl)
                    else getConnectionUrlFromAlias(databaseUrl)
                        .run { orElseThrow { NoSuchElementException("Can't find the database alias") } }
                        .run { getDataSource(pluginContainer, this) }

                datasource.connection.use { connection ->
                    ScriptRunner(connection, true).also { it.setLogWriter(null) }.runScript(initSql.url.openStream().reader())
                }
            }
        }
    }
}