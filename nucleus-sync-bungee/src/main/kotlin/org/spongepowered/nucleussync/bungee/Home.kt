package org.spongepowered.nucleussync.bungee

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import java.util.*

data class PacketHomeCreate(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "HomeCreate"
    }

    constructor(input: ByteArrayDataInput) : this(
        input.readUTF(),
        input.readUniqueId()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(channel)
        out.writeUTF(name)
        out.writeUniqueId(playerUuid)
        return out
    }
}

data class PacketHomeUse(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "HomeUse"
    }

    constructor(input: ByteArrayDataInput) : this(
        input.readUTF(),
        input.readUniqueId()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(channel)
        out.writeUTF(name)
        out.writeUniqueId(playerUuid)
        return out
    }
}