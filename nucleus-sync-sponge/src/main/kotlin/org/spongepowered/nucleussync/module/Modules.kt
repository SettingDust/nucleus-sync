package org.spongepowered.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.settingdust.laven.present
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.nucleussync.config.ConfigMain
import org.spongepowered.nucleussync.core.DatabaseService

@ExperimentalCoroutinesApi
@Singleton
@Suppress("RedundantRequireNotNullCall")
class Modules @ExperimentalCoroutinesApi @Inject constructor(
    private val injector: Injector,
    private val configMain: ConfigMain,
    private val databaseService: DatabaseService,
    pluginContainer: PluginContainer,
    eventManager: EventManager
) {
    init {
        eventManager.registerListener(pluginContainer, GamePostInitializationEvent::class.java, this::onPostInit)
    }

    private fun onPostInit(event: GamePostInitializationEvent) {
        configMain.model.modules.apply {
            if (commandSync) requireNotNull(injector.getInstance(ModuleCommandSync::class.java))
            if (warp && NucleusAPI.getWarpService().present) requireNotNull(injector.getInstance(ModuleWarp::class.java))
            if (home && NucleusAPI.getHomeService().present) requireNotNull(injector.getInstance(ModuleHome::class.java))
        }
    }
}