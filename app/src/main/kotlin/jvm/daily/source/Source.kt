package jvm.daily.source

import jvm.daily.model.Article

interface Source {
    val sourceType: String
    suspend fun fetch(): List<Article>
}
