package me.settingdust.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.currentTimestamp
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.task
import me.settingdust.laven.unwrap
import me.settingdust.nucleussync.config.ConfigMain
import me.settingdust.nucleussync.core.DatabaseService
import me.settingdust.nucleussync.pluginName
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandManager
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.game.state.GameStoppingServerEvent
import org.spongepowered.api.plugin.PluginContainer
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleCommandSync @Inject constructor(
    pluginContainer: PluginContainer,
    databaseService: DatabaseService,
    private val configMain: ConfigMain,
    private val commandManager: CommandManager
) {
    private val tableName = "syncedcommands"
    private val task =
        pluginContainer.task(
            name = "$pluginName Command Sync",
            async = true,
            interval = 1000,
        ) {
            databaseService.datasource.connection.use { connection ->
                connection.prepareStatement("""
                    SELECT id,
                           command,
                           arguments
                    FROM $tableName
                    WHERE id > ?
                      AND `timestamp` >= ?
                """.trimIndent())
                    .use {
                        it.setInt(1, currentIndex)
                        it.setTimestamp(2, currentTimestamp())
                        it.executeQuery()
                    }
                    .use {
                        while (it.next()) {
                            val command = it.getString(2)
                            if (!configMain.model.commandSync.contains(command)) continue
                            commandManager.get(command).unwrap()?.callable?.process(Sponge.getServer().console, it.getString(3))
                            currentIndex = it.getInt(1)
                        }
                    }
            }
        }

    private var currentIndex = 0

    init {
        pluginContainer.registerListener<SendCommandEvent> {
            GlobalScope.launch(Dispatchers.IO) {
                if (configMain.model.commandSync.contains(command))
                    databaseService.datasource.connection.use { connection ->
                        connection.prepareStatement("""
                        INSERT INTO $tableName (command, arguments, `timestamp`)
                        VALUES (?, ?, ?)
                    """.trimIndent(), Statement.RETURN_GENERATED_KEYS).use {
                            it.setString(1, command)
                            it.setString(2, arguments)
                            it.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(15)))
                            it.executeUpdate()
                            it.generatedKeys
                                .takeIf(ResultSet::next)
                                ?.use { resultSet -> currentIndex = resultSet.getInt(1) }
                        }
                    }
            }
        }
        pluginContainer.registerListener<GameStoppingServerEvent> { task.cancel() }

        GlobalScope.launch(Dispatchers.IO) {
            databaseService.datasource.connection.use { connection ->
                connection.prepareStatement("TRUNCATE TABLE $tableName").use {
                    it.executeUpdate()
                }
            }
        }
    }
}

