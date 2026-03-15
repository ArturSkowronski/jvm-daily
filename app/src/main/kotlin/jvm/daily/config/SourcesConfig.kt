package jvm.daily.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
data class SourcesConfig(
    val rss: List<RssFeedConfig> = emptyList(),
    val reddit: List<RedditSourceConfig> = emptyList(),
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
)

@Serializable
data class RedditSourceConfig(
    val subreddit: String,
    val limit: Int = 25,
)
