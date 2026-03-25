package jvm.daily.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
data class SourcesConfig(
    val rss: List<RssFeedConfig> = emptyList(),
    val reddit: List<RedditSourceConfig> = emptyList(),
    val githubTrending: GitHubTrendingConfig? = null,
    val githubReleases: GitHubReleasesConfig? = null,
    val bluesky: BlueskyConfig? = null,
) {
    companion object {
        fun load(path: Path): SourcesConfig {
            val content = path.readText()
            return Yaml.default.decodeFromString(serializer(), content)
        }
    }
}

@Serializable
data class RssFeedConfig(
    val url: String,
    val sinceDays: Int = 1,
    val splitRoundups: Boolean = false,
)

@Serializable
data class GitHubTrendingConfig(
    val languages: List<String> = emptyList(),
    val minStars: Int = 10,
    val sinceDays: Int = 7,
    val limit: Int = 30,
)

@Serializable
data class GitHubReleasesConfig(
    val repos: List<String> = emptyList(),
    val sinceDays: Int = 7,
)

@Serializable
data class BlueskyConfig(
    val accounts: List<String>,
    val limit: Int = 20,
    val sinceDays: Int = 7,
)

@Serializable
data class RedditSourceConfig(
    val subreddit: String,
    val limit: Int = 50,
    val minComments: Int = 5,
    val timeWindow: String = "week",
)
