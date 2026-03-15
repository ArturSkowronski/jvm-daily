package jvm.daily.workflow

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DigestJson(
    val date: String,
    val generatedAt: String,
    val windowHours: Int = 24,
    val totalArticles: Int,
    val clusters: List<DigestCluster>,
    val unclustered: List<DigestArticle>,
)

@Serializable
data class DigestCluster(
    val id: String,
    val title: String,
    val summary: String,
    val engagementScore: Double,
    val articles: List<DigestArticle>,
)

@Serializable
data class DigestArticle(
    val id: String,
    val title: String,
    val url: String?,
    val summary: String,
    val topics: List<String>,
    val entities: List<String>,
    val engagementScore: Double,
    val publishedAt: Instant,
    val ingestedAt: Instant,
    val sourceType: String,
)
