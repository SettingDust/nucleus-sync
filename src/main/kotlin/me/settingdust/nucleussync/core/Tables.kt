package me.settingdust.nucleussync.core

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

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

object HomeQueue : IdTable<UUID>() {
    override val id = uuid("id").entityId()
    val name = varchar("name", 16)

    override val primaryKey: PrimaryKey? = PrimaryKey(id)

    init {
        transaction {
            SchemaUtils.create(this@HomeQueue)
        }
    }
}

object Warps : IdTable<String>() {
    override val id = varchar("id", 32).entityId()
    val description = varchar("description", 32).default("")
    val category = varchar("category", 32).default("")
    val cost = double("cost").default(0.0)
    val server = varchar("server", 32)

    init {
        transaction { SchemaUtils.create(this@Warps) }
    }
}

object WarpQueue : IdTable<UUID>() {
    override val id = uuid("id").entityId()
    val name = varchar("name", 16)

    override val primaryKey: PrimaryKey? = PrimaryKey(id)

    init {
        transaction {
            SchemaUtils.create(this@WarpQueue)
        }
    }
}

object SyncedCommands : IntIdTable() {
    val command = varchar("command", 32)
    val arguments = varchar("arguments", 64)
    val timestamp = datetime("timestamp")

    var currentIndex = 0

    init {
        transaction {
            SchemaUtils.create(this@SyncedCommands)
            exec("TRUNCATE ${SyncedCommands.nameInDatabaseCase()}")
        }
    }
}