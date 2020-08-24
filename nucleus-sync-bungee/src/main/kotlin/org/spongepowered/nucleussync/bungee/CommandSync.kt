package org.spongepowered.nucleussync.bungee

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput

data class PacketCommandSync(
    val command: String,
    val arguments: String
) : Packet {
    companion object {
        const val channel = "CommandSync"
    }

    constructor(input: ByteArrayDataInput) : this(
        input.readUTF(),
        input.readUTF()
    )

    override fun write(out: ByteArrayDataOutput): ByteArrayDataOutput {
        out.writeUTF(PacketHomeCreate.channel)
        out.writeUTF(command)
        out.writeUTF(arguments)
        return out
    }
}
