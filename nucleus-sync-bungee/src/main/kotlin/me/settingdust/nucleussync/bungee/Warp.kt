package me.settingdust.nucleussync.bungee

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import java.util.*

data class PacketWarpCreate(
    val name: String,
    val description: String?,
    val category: String?,
    val cost: Double?
) : Packet {
    companion object {
        const val channel = "WarpCreate"
    }

    constructor(input: ByteArrayDataInput) : this(
        input.readUTF(),
        input.readUTF(),
        input.readUTF(),
        input.readDouble()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(channel)
        out.writeUTF(name)
        out.writeUTF(description ?: "")
        out.writeUTF(category ?: "")
        out.writeDouble(cost ?: 0.0)
        return out
    }
}

data class PacketWarpDelete(
    val name: String
) : Packet {
    companion object {
        const val channel = "WarpDelete"
    }

    constructor(input: ByteArrayDataInput) : this(
        input.readUTF()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(channel)
        out.writeUTF(name)
        return out
    }
}

data class PacketWarpUse(
    val name: String,
    val playerUuid: UUID
) : Packet {
    companion object {
        const val channel = "WarpUse"
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