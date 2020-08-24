package me.settingdust.nucleussync.bungee

import com.google.common.io.ByteStreams
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import me.settingdust.laven.ReactiveFile.subscribe
import me.settingdust.laven.file
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.connection.Server
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.config.Configuration
import net.md_5.bungee.config.ConfigurationProvider
import net.md_5.bungee.config.YamlConfiguration
import net.md_5.bungee.event.EventHandler
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import me.settingdust.nucleussync.Homes
import me.settingdust.nucleussync.SyncedCommands
import me.settingdust.nucleussync.Warps
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.sql.DriverManager
import java.util.regex.Matcher
import java.util.regex.Pattern

const val pluginName = "NucleusSync"


@Suppress("UnstableApiUsage")
class NucleusSync : Plugin(), Listener {
    companion object {
        lateinit var configuration: Configuration
        private val configurationProvider = ConfigurationProvider.getProvider(YamlConfiguration::class.java)
        private val urlRegex = Pattern.compile("(?:jdbc:)?([^:]+):(//)?(?:([^:]+)(?::([^@]+))?@)?(.*)")
    }

    private val databaseTypeToDriver = mapOf(
        "h2" to "org.h2.Driver",
        "mysql" to "org.mariadb.jdbc.Driver",
        "mariadb" to "org.mariadb.jdbc.Driver",
    )

    @ExperimentalCoroutinesApi
    override fun onLoad() {
        dataFolder.mkdirs()
        val configFile = dataFolder.resolve("config.yml")
        if (!configFile.exists()) Files.copy(getResourceAsStream("config.yml"), configFile.toPath())
        loadConfig(configFile)
        GlobalScope.launch(Dispatchers.IO) {
            configFile.toPath()
                .subscribe<File> { converter { path, _ -> path.file } }
                .consumeEach { loadConfig(it.data) }
        }
    }

    override fun onEnable() {
        proxy.pluginManager.registerListener(this, this)

//        transaction {
//            TransactionManager.current().exec("TRUNCATE ${SyncedCommands.nameInDatabaseCase()}")
//        }
    }

    private fun loadConfig(file: File) {
        configuration = configurationProvider.load(file)
        val url = configuration.getString("database.url")
        val match = urlRegex.matcher(url)
        require(match.matches()) { "URL $url is not a valid JDBC URL" }
        val authlessUrl = "jdbc:${match[1]}:${match[2] ?: ""}${match[5]}"

        val config = HikariConfig().apply {
            jdbcUrl = authlessUrl
            username = if (match[3].isNullOrBlank()) null else URLDecoder.decode(match[3], UTF_8.name())
            password = if (match[4].isNullOrBlank()) null else URLDecoder.decode(match[4], UTF_8.name())
            driverClassName = databaseTypeToDriver[match[1]]
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
    }

    @EventHandler
    fun onReceiveMessage(event: PluginMessageEvent) {
        event.apply {
            if (tag == bungeeChannel) {
                val input = ByteStreams.newDataInput(data)
                var receiverServer = receiver as? Server
                if (receiver is ProxiedPlayer)
                    receiverServer = (receiver as ProxiedPlayer).server
                val subchannel = input.readUTF()
                when (subchannel) {
                    PacketWarpCreate.channel -> {
                        PacketWarpCreate(input)
                            .also { packet ->
                                transaction {
                                    Warps.insert {
                                        it[id] = EntityID(packet.name, this)
                                        it[description] = packet.description ?: ""
                                        it[category] = packet.category ?: ""
                                        it[cost] = packet.cost ?: 0.0
                                        it[server] = receiverServer!!.info.name
                                    }
                                }
                            }
                    }
                    PacketWarpUse.channel -> {
                        PacketWarpUse(input)
                            .also { packet ->
                                val player = proxy.getPlayer(packet.playerUuid)
                                transaction {
                                    Warps.apply {
                                        val server = proxy.getServerInfo(slice(server).select { id eq packet.name }.single()[server])
                                        server.sendBungeeData(
                                            ByteStreams
                                                .newDataOutput()
                                                .writePluginChannel()
                                                .writePacket(packet)
                                        )

                                        player.connect(server)
                                    }
                                }
                            }
                    }
                    PacketHomeCreate.channel -> {
                        PacketHomeCreate(input)
                            .also { packet ->
                                transaction {
                                    Homes.insert {
                                        it[id] = EntityID(packet.name, this)
                                        it[playerUuid] = packet.playerUuid
                                        it[server] = receiverServer!!.info.name
                                    }
                                }
                            }
                    }
                    PacketHomeUse.channel -> {
                        PacketHomeUse(input)
                            .also { packet ->
                                val player = proxy.getPlayer(packet.playerUuid)
                                transaction {
                                    Homes.apply {
                                        val server = proxy.getServerInfo(slice(server).select { id eq packet.name }.single()[server])

                                        server.sendBungeeData(
                                            ByteStreams
                                                .newDataOutput()
                                                .writePluginChannel()
                                                .writePacket(packet)
                                        )

                                        player.connect(server)
                                    }
                                }
                            }
                    }
                    PacketCommandSync.channel -> {
                        PacketCommandSync(input)
                            .also { packet ->
                                proxy.servers
                                    .filterKeys { receiverServer!!.info.name != it }
                                    .forEach { (_, server) ->
                                        server.sendBungeeData(
                                            ByteStreams
                                                .newDataOutput()
                                                .writePluginChannel()
                                                .writePacket(packet)
                                        )
                                    }
                            }
                    }
                }
            }
        }
    }
}

private operator fun Matcher.get(i: Int): String? = this.group(i)
