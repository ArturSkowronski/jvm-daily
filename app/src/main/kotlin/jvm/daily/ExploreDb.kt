package jvm.daily

import jvm.daily.ui.DuckDbExplorer

fun main(args: Array<String>) {
    val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"
    DuckDbExplorer.start(dbPath)
}
