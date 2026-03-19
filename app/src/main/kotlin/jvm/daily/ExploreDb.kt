package jvm.daily

import jvm.daily.ui.DuckDbExplorer

fun main(args: Array<String>) {
    val dbPath = System.getenv("DUCKDB_PATH") ?: DEFAULT_DB_PATH
    DuckDbExplorer.start(dbPath)
}
