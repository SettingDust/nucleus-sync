package org.spongepowered.nucleussync

import com.google.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.nucleussync.module.Modules

const val pluginId = "nucleus-sync"
const val pluginName = "Nucleus Sync"

@Suppress("RedundantRequireNotNullCall")
@Plugin(
    id = pluginId,
    name = pluginName,
    version = "@version@",
    description = "Sync nucleus warp, home, etc. (For teleport over server) with BungeeCord",
    authors = ["SettingDust"],
    dependencies = [
        Dependency(id = "nucleus")
    ]
)
class NucleusSync @ExperimentalCoroutinesApi @Inject constructor(modules: Modules) {
    init {
        requireNotNull(modules)
    }
}
