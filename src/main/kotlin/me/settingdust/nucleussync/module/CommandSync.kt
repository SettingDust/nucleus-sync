package me.settingdust.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.settingdust.laven.sponge.typeTokenOf
import me.settingdust.laven.unwrap
import me.settingdust.nucleussync.config.ConfigMain
import me.settingdust.nucleussync.core.SyncedCommands
import me.settingdust.nucleussync.pluginName
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Duration
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandManager
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.game.state.GameStoppedServerEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.scheduler.Task
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleCommandSync @Inject constructor(
    eventManager: EventManager,
    pluginContainer: PluginContainer,
    logger: Logger,
    private val configMain: ConfigMain,
    private val commandManager: CommandManager
) {
    private val task: Task = Task.builder().async().name("$pluginName Command Sync").execute { ->
        transaction {
            SyncedCommands.apply {
                val commands =
                    slice(id, command, arguments)
                        .select {
                            id greater currentIndex and
                                (timestamp greater DateTime())
                        }
                var isEmpty = true
                commands
                    .filter { configMain.model.commandSync.contains(it[command]) }
                    .map { commandManager.get(it[command]).unwrap()?.callable to it[arguments] }
                    .forEach {
                        isEmpty = false
                        try {
                            it.first?.process(Sponge.getServer().console, it.second)
                        } catch (e: Exception) {
                            logger.warning(e.localizedMessage)
                        }
                    }
                if (!isEmpty) currentIndex = commands.last()[id].value
            }
        }
    }.interval(1, TimeUnit.SECONDS).submit(pluginContainer);

    init {
        eventManager.registerListener(pluginContainer, typeTokenOf<SendCommandEvent>(), this::onSendCommand)
    }

    private fun onGameStopping(event: GameStoppedServerEvent) = task.cancel()

    private fun onSendCommand(event: SendCommandEvent) {
        if (configMain.model.commandSync.contains(event.command)) {
            transaction {
                SyncedCommands.currentIndex = SyncedCommands.insertAndGetId {
                    it[command] = event.command
                    it[arguments] = event.arguments
                    it[timestamp] = DateTime().plus(Duration.standardSeconds(15))
                }.value
            }
        }
    }
}

