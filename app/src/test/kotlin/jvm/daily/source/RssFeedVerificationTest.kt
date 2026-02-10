package jvm.daily.source

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.assertTrue

/**
 * Integration test that verifies real RSS/Atom feeds from PLAN.md can be parsed.
 * Requires network access. Tagged "integration" so it can be excluded from CI.
 */
@Tag("integration")
class RssFeedVerificationTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "Inside Java,          https://inside.java/feed.xml",
        "Spring Blog,          https://spring.io/blog.atom",
        "Kotlin Blog,          https://blog.jetbrains.com/kotlin/feed/",
        "Baeldung,             https://feeds.feedblitz.com/baeldung",
        "InfoQ Java,           https://feed.infoq.com/java",
        "Quarkus Blog,         https://quarkus.io/feed.xml",
        "Micronaut Blog,       https://micronaut.io/feed/",
        "foojay.io,            https://foojay.io/feed/",
        "Gradle Blog,          https://feed.gradle.org/blog.atom",
        "JetBrains Blog,       https://blog.jetbrains.com/feed/",
        "Vlad Mihalcea,        https://vladmihalcea.com/feed/",
        "Thorben Janssen,      https://thorben-janssen.com/feed/",
        "Marco Behler (dev.to), https://dev.to/feed/marcobehler",
        "Adam Bien,            https://adambien.blog/roller/abien/feed/entries/atom",
        "DZone Java,           https://feeds.dzone.com/java",
        "Hacker News,          https://hnrss.org/newest?q=java+OR+kotlin+OR+jvm+OR+spring+OR+graalvm",
        "GraalVM Blog,         https://medium.com/feed/graalvm",
    )
    fun `can parse feed`(name: String, url: String) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        val input = SyndFeedInput()
        val feed = input.build(XmlReader(connection.inputStream))

        println("✓ $name ($url)")
        println("  Title: ${feed.title}")
        println("  Entries: ${feed.entries.size}")

        feed.entries.take(3).forEach { entry ->
            println("  - ${entry.title}")
            println("    Author: ${entry.author ?: "n/a"}")
            println("    Link: ${entry.link}")
            println("    Has content: ${entry.description != null || entry.contents.isNotEmpty()}")
            println("    Comments: ${entry.comments ?: "n/a"}")
        }
        println()

        assertTrue(feed.entries.isNotEmpty(), "$name feed at $url returned no entries")
    }
}
