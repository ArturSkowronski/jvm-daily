package jvm.daily.model

import kotlinx.datetime.Instant

data class Article(
    val id: String,
    val title: String,
    val content: String,
    val sourceType: String,
    val sourceId: String,
    val url: String? = null,
    val ingestedAt: Instant,
)
