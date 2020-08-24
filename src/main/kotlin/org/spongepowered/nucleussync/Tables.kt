package org.spongepowered.nucleussync

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction

object Homes : IdTable<String>() {
    override val id = varchar("id", 16).entityId()
    val playerUuid = uuid("playerUuid")
    val server = varchar("server", 32)

    override val primaryKey: PrimaryKey? = PrimaryKey(id, playerUuid)

    init {
        transaction {
            SchemaUtils.create(this@Homes)
        }
    }
}

object Warps : IdTable<String>() {
    override val id = varchar("id", 32).entityId()
    val description = varchar("description", 32)
    val category = varchar("category", 32)
    val cost = double("cost")
    val server = varchar("server", 32)

    init { transaction { SchemaUtils.create(Warps) } }
}

object SyncedCommands : IntIdTable() {
    val command = varchar("command", 32)
    val arguments = varchar("arguments", 64)
    val timestamp = datetime("timestamp")

    var currentIndex = 0

    init { transaction { SchemaUtils.create(SyncedCommands) } }
}