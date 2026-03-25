package jvm.daily.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * JVM-specific sources config — extends the generic engine SourcesConfig
 * with OpenJDK mailing list and JEP source support.
 */
@Serializable
data class JvmSourcesConfig(
    val rss: List<RssFeedConfig> = emptyList(),
    val reddit: List<RedditSourceConfig> = emptyList(),
    val githubTrending: GitHubTrendingConfig? = null,
    val githubReleases: GitHubReleasesConfig? = null,
    val bluesky: BlueskyConfig? = null,
    val openjdkMail: List<OpenJdkMailConfig> = emptyList(),
    val jep: JepConfig? = null,
) {
    fun toEngineConfig() = SourcesConfig(rss, reddit, githubTrending, githubReleases, bluesky)

    companion object {
        fun load(path: Path): JvmSourcesConfig {
            val content = path.readText()
            return Yaml.default.decodeFromString(serializer(), content)
        }
    }
}

@Serializable
data class OpenJdkMailConfig(
    val list: String,
    val minReplies: Int = 2,
    val sinceDays: Int = 2,
)

@Serializable
data class JepConfig(
    val enabled: Boolean = false,
    val initialSeed: Boolean = false,
    val activeStatuses: List<String> = listOf(
        "Draft", "Candidate", "Proposed to Target", "Targeted", "Integrated"
    ),
)
