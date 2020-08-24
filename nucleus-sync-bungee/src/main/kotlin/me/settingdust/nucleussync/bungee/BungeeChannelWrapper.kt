@file:Suppress("UnstableApiUsage")

package me.settingdust.nucleussync.bungee

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import net.md_5.bungee.api.config.ServerInfo
import java.util.*

const val bungeeChannel = "BungeeCord"

interface Packet {
    fun write(out: ByteArrayDataOutput): ByteArrayDataOutput
}

fun ByteArrayDataOutput.writePacket(packet: Packet) = packet.write(this)

fun ByteArrayDataOutput.writePluginChannel(): ByteArrayDataOutput {
    this.writeUTF(pluginName)
    return this
}

fun ByteArrayDataOutput.writeUniqueId(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

fun ByteArrayDataInput.readUniqueId() = UUID(readLong(), readLong())

fun ServerInfo.sendData(channel: String, output: ByteArrayDataOutput) = sendData(channel, output.toByteArray())

fun ServerInfo.sendBungeeData(output: ByteArrayDataOutput) = sendData(bungeeChannel, output)
