package jvm.daily.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Normalized article ready for AI processing and clustering.
 * Created by EnrichmentWorkflow from raw Article.
 */
@Serializable
data class ProcessedArticle(
    val id: String,
    val originalTitle: String,
    val normalizedTitle: String,  // Cleaned, lowercase for dedup
    val summary: String,            // LLM-generated summary (max 200 words)
    val originalContent: String,
    val sourceType: String,
    val sourceId: String,
    val url: String? = null,
    val author: String? = null,
    val publishedAt: Instant,
    val ingestedAt: Instant,
    val processedAt: Instant,
    val entities: List<String> = emptyList(),  // NER: JDK versions, frameworks, companies, JEPs
    val topics: List<String> = emptyList(),     // Topic tags for clustering
    val engagementScore: Double = 0.0,          // For prioritization (0-100)
    val outcomeStatus: EnrichmentOutcomeStatus = EnrichmentOutcomeStatus.SUCCESS,
    val failureReason: String? = null,
    val lastAttemptAt: Instant? = null,
    val attemptCount: Int = 1,
    val warnings: List<String> = emptyList(),
)

@Serializable
enum class EnrichmentOutcomeStatus {
    SUCCESS,
    FAILED,
    SKIPPED,  // Filtered out by relevance gate before enrichment
}

/**
 * Thematic cluster of related articles across sources.
 * Created by ClusteringWorkflow.
 */
@Serializable
data class ArticleCluster(
    val id: String,
    val title: String,              // Human-readable cluster name
    val summary: String,            // Cross-source synthesis
    val articles: List<String>,     // Article IDs in this cluster
    val sources: List<String>,      // Unique source types in cluster
    val totalEngagement: Double,
    val createdAt: Instant,
    val type: String = "topic",              // "topic" | "release"
    val bullets: List<String> = emptyList(), // up to 5 bullets for release clusters
)

/**
 * Final compiled newsletter issue.
 * Created by CompilationWorkflow.
 */
@Serializable
data class NewsletterIssue(
    val id: String,
    val date: Instant,
    val headline: String,
    val topStories: List<ArticleCluster>,      // Cross-source, 3-5 clusters
    val twitterRecap: String,                   // Markdown section
    val redditRecap: String,                    // Markdown section
    val discordRecap: String,                   // Markdown section (future)
    val githubTrending: String,                 // Markdown section (future)
    val blogHighlights: List<String>,           // Article IDs from RSS
    val stats: NewsletterStats,
    val tags: List<String>,
    val markdownContent: String,                // Full compiled markdown
    val createdAt: Instant,
)

@Serializable
data class NewsletterStats(
    val totalArticles: Int,
    val twitterCount: Int = 0,
    val redditCount: Int = 0,
    val discordCount: Int = 0,
    val githubCount: Int = 0,
    val blogCount: Int,
    val readingTimeSavedMinutes: Int,
)
