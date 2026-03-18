package jvm.daily.storage

import jvm.daily.model.JepSnapshot

interface JepSnapshotRepository {
    fun findAll(): List<JepSnapshot>
    fun upsert(jep: JepSnapshot)
    fun count(): Int
}
