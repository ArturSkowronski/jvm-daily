package jvm.daily.storage

import java.sql.Connection
import java.sql.DriverManager

object DuckDbConnectionFactory {
    fun inMemory(): Connection =
        DriverManager.getConnection("jdbc:duckdb:")

    fun persistent(path: String): Connection =
        DriverManager.getConnection("jdbc:duckdb:$path")
}
