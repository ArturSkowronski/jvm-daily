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
    val debug: List<DebugRejected> = emptyList(),
)

@Serializable
data class DebugRejected(
    val title: String,
    val url: String?,
    val sourceType: String,
    val reason: String,   // e.g. "relevance_gate", "enrichment_failed", "event_logistics"
)

@Serializable
data class DigestCluster(
    val id: String,
    val title: String,
    val summary: String,
    val engagementScore: Double,
    val articles: List<DigestArticle>,
    val type: String = "topic",
    val bullets: List<String> = emptyList(),
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
    val handle: String? = null,           // bluesky: account handle, reddit: subreddit
    val socialLinks: List<DigestSocialLink> = emptyList(),
    val taxonomyArea: String? = null,
    val taxonomySubArea: String? = null,
    val taxonomyImpact: List<String> = emptyList(),
    val taxonomyConfidence: Double? = null,
)

@Serializable
data class DigestSocialLink(
    val source: String,   // "bluesky", "reddit"
    val url: String,      // bsky.app post URL or reddit thread URL
    val handle: String,   // bluesky: "foojay.io", reddit: "r/java"
)
