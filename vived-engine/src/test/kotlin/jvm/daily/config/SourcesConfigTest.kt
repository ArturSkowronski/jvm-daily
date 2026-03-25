package jvm.daily.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcesConfigTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load parses rss feeds from yaml`() {
        val configFile = tempDir.resolve("sources.yml")
        configFile.writeText(
            """
            rss:
              - url: "https://example.com/feed.xml"
              - url: "https://another.com/rss"
            """.trimIndent()
        )

        val config = SourcesConfig.load(configFile)

        assertEquals(2, config.rss.size)
        assertEquals("https://example.com/feed.xml", config.rss[0].url)
        assertEquals("https://another.com/rss", config.rss[1].url)
    }

    @Test
    fun `load handles empty rss list`() {
        val configFile = tempDir.resolve("sources.yml")
        configFile.writeText(
            """
            rss: []
            """.trimIndent()
        )

        val config = SourcesConfig.load(configFile)

        assertTrue(config.rss.isEmpty())
    }

    @Test
    fun `load defaults rss to empty when key missing`() {
        val configFile = tempDir.resolve("sources.yml")
        configFile.writeText("{}")

        val config = SourcesConfig.load(configFile)

        assertTrue(config.rss.isEmpty())
    }
}
