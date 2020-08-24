package me.settingdust.nucleussync.bungee

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import java.util.*

open class PacketHome(
    val name: String,
    val playerUuid: UUID
) : Packet {
    constructor(input: ByteArrayDataInput) : this(
        input.readUTF(),
        input.readUniqueId()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(channelName)
        out.writeUTF(name)
        out.writeUniqueId(playerUuid)
        return out
    }

    open val channelName = ""
}

class PacketHomeCreate(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeCreate"
    }
    override val channelName: String
        get() = channel
}

class PacketHomeDelete(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeDelete"
    }
    override val channelName: String
        get() = channel
}

class PacketHomeUse(
    name: String,
    playerUuid: UUID
) : PacketHome(name, playerUuid) {
    companion object {
        const val channel = "HomeUse"
    }
    override val channelName: String
        get() = channel
}