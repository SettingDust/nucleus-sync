@file:Suppress("UnstableApiUsage")

package me.settingdust.nucleussync.config

import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import me.settingdust.laven.*
import me.settingdust.laven.ReactiveFile.subscribe
import me.settingdust.laven.sponge.HoconFileEventChannel
import me.settingdust.laven.sponge.get
import me.settingdust.laven.sponge.offerComment
import me.settingdust.laven.sponge.set
import ninja.leaping.configurate.ConfigurationOptions
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent
import org.spongepowered.api.plugin.PluginContainer
import me.settingdust.nucleussync.core.DatabaseService
import java.io.Closeable
import java.nio.file.Path

@ExperimentalCoroutinesApi
abstract class Config(
    path: Path,
    private var loader: HoconConfigurationLoader
) : Closeable {
    protected var saved = false

    constructor(path: Path) : this(
        path,
        HoconConfigurationLoader
            .builder()
            .setPath(path)
            .setDefaultOptions(ConfigurationOptions.defaults().setShouldCopyDefaults(true))
            .build()
    )

    private var channel: FileEventChannel<CommentedConfigurationNode> = path.subscribe(HoconFileEventChannel(loader))
    internal var node: CommentedConfigurationNode

    init {
        path.directory.createDirectories()
        if (!path.exist) path.createFile()

        node = loader.load()

        GlobalScope.launch(Dispatchers.IO) {
            channel.consumeEach {
                node.set(it.data)
                consumeEvent(it.kind, it.data)
                if (it.kind == FileEvent.Kind.Create) saved = true
            }
        }
    }

    protected abstract fun consumeEvent(kind: FileEvent.Kind, data: CommentedConfigurationNode)

    fun save() {
        if (!saved.also { saved = false }) loader.save(node)
    }

    override fun close() {
        channel.cancel()
    }
}

@Suppress("RedundantRequireNotNullCall")
@Singleton
@ExperimentalCoroutinesApi
class ConfigMain @Inject constructor(
    @DefaultConfig(sharedRoot = false) private val path: Path,
    private val injector: Injector,
    private val databaseService: DatabaseService,
    pluginContainer: PluginContainer,
    eventManager: EventManager
) : Config(path) {
    companion object {
        val stringType: TypeToken<String> = TypeToken.of(String::class.java)
    }

    var model: ModelMain

    init {
        eventManager.registerListener(pluginContainer, GameLoadCompleteEvent::class.java, this::onLoadComplete)

        model = ModelMain(node)
    }

    private fun onLoadComplete(event: GameLoadCompleteEvent) = save()

    override fun consumeEvent(kind: FileEvent.Kind, data: CommentedConfigurationNode) {
        model.node = node
        model.modules.node = node["modules"]

        databaseService.connect()

        save()
    }

    class ModelMain(node: CommentedConfigurationNode) {
        var node = node
            set(value) {
                node["modules"].offerComment("Have to restart after modify module settings")
                node["commandSync"].offerComment("The list of commands to sync with other server")
                node["databaseUrl"].offerComment("JDBC url or sponge alias")

                requireNotNull(databaseUrl)
                requireNotNull(commandSync)
                field = value
            }

        val modules = ModelModule(node["modules"])
        var databaseUrl: String
            set(value) {
                node["databaseUrl"].value = value
            }
            get() = node["databaseUrl"].getString("nucleussync")
        var commandSync: MutableList<String>
            set(value) {
                node["commandSync"].value = value
            }
            get() = node["commandSync"].getList(stringType, listOf("ban", "kick"))

        class ModelModule(
            node: CommentedConfigurationNode
        ) {
            var node = node
                set(value) {
                    requireNotNull(warp)
                    requireNotNull(tp)
                    requireNotNull(home)
                    requireNotNull(commandSync)
                    field = value
                }
            var warp
                set(value) {
                    node["warp"].value = value
                }
                get() = node["warp"].getBoolean(true)

            var tp
                set(value) {
                    node["tp"].value = value
                }
                get() = node["tp"].getBoolean(true)

            var home
                set(value) {
                    node["home"].value = value
                }
                get() = node["home"].getBoolean(true)

            var commandSync
                set(value) {
                    node["commandSync"].value = value
                }
                get() = node["commandSync"].getBoolean(true)
        }
    }
}
