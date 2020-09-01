package me.settingdust.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.settingdust.laven.present
import me.settingdust.laven.sponge.get
import me.settingdust.laven.sponge.typeTokenOf
import me.settingdust.nucleussync.config.ConfigMain
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.game.state.GameStartingServerEvent
import org.spongepowered.api.plugin.PluginContainer
import kotlin.concurrent.timerTask

@ExperimentalCoroutinesApi
@Singleton
@Suppress("RedundantRequireNotNullCall")
class Modules @ExperimentalCoroutinesApi @Inject constructor(
    private val injector: Injector,
    private val configMain: ConfigMain,
    pluginContainer: PluginContainer,
    eventManager: EventManager
) {
    init {
        eventManager.registerListener(pluginContainer, GameStartingServerEvent::class.java, this::onStarting)
    }

    private fun onStarting(event: GameStartingServerEvent) {
        configMain.model.modules.apply {
            if (commandSync)
                requireNotNull(injector[typeTokenOf<ModuleCommandSync>()])
            if (warp && NucleusAPI.getWarpService().present)
                requireNotNull(injector[typeTokenOf<ModuleWarp>()])
            if (home && NucleusAPI.getHomeService().present)
                requireNotNull(injector[typeTokenOf<ModuleHome>()])
        }

    }
}