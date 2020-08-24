package me.settingdust.nucleussync.module

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.settingdust.laven.sponge.Packet
import me.settingdust.laven.sponge.typeTokenOf
import me.settingdust.laven.sponge.writePacket
import me.settingdust.laven.unwrap
import org.spongepowered.api.Platform
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandManager
import org.spongepowered.api.event.EventManager
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.network.ChannelBuf
import org.spongepowered.api.plugin.PluginContainer
import me.settingdust.nucleussync.config.ConfigMain
import me.settingdust.nucleussync.core.BungeeChannel
import me.settingdust.nucleussync.core.sendTo
import me.settingdust.nucleussync.core.writePluginChannel
import me.settingdust.nucleussync.pluginName

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
@Singleton
class ModuleCommandSync @Inject constructor(
    eventManager: EventManager,
    bungeeChannel: BungeeChannel,
    pluginContainer: PluginContainer,
    private val configMain: ConfigMain,
    private val commandManager: CommandManager
) {
    private val channel = bungeeChannel.channel

    init {
        GlobalScope.launch {
//            newSuspendedTransaction(Dispatchers.IO) {
//                while (true) {
//                    delay(3000)
//                    SyncedCommands.apply {
//                        val commands =
//                            slice(id, command, arguments)
//                                .select {
//                                    id greater currentIndex and
//                                        (timestamp greater DateTime())
//                                }
//                        var isEmpty = true
//                        commands
//                            .filter { configMain.model.commandSync.contains(it[command]) }
//                            .map { commandManager.get(it[command]).unwrap()?.callable to it[arguments] }
//                            .forEach {
//                                it.first?.process(Sponge.getServer().console, it.second)
//                                isEmpty = false
//                            }
//                        if (!isEmpty) currentIndex = commands.last()[id].value
//                    }
//                }
//            }


            channel.addListener(Platform.Type.SERVER) { data, _, _ ->
                data.resetRead()
                data.takeIf { data.readString() == pluginName }?.let {
                    when (data.readString()) {
                        PacketCommandSync.channel -> {
                            PacketCommandSync(data)
                                .apply {
                                    command
                                        .takeIf(configMain.model.commandSync::contains)
                                        .run { commandManager.get(command).unwrap()?.callable }
                                        ?.apply { process(Sponge.getServer().console, arguments) }
                                }
                        }
                    }
                }
            }
        }

        eventManager.registerListener(pluginContainer, typeTokenOf<SendCommandEvent>(), this::onSendCommand)
    }

    private fun onSendCommand(event: SendCommandEvent) {
        event.apply {
            if (configMain.model.commandSync.contains(command)) {
                channel.sendTo {
                    it.writePluginChannel()
                    it.writePacket(PacketCommandSync(command, arguments))
                }
            }
        }
//            transaction {
//                SyncedCommands.currentIndex = SyncedCommands.insertAndGetId {
//                    it[command] = event.command
//                    it[arguments] = event.arguments
//                    it[timestamp] = DateTime().plus(Duration.standardSeconds(15))
//                }.value
//            }
    }
}

data class PacketCommandSync(
    val command: String,
    val arguments: String
) : Packet {
    companion object {
        const val channel = "CommandSync"
    }

    constructor(channelBuf: ChannelBuf) : this(
        channelBuf.readUTF(),
        channelBuf.readUTF()
    )

    override fun write(channelBuf: ChannelBuf) {
        channelBuf.writeUTF(channel)
        channelBuf.writeUTF(command)
        channelBuf.writeUTF(arguments)
    }
}


