package jvm.daily.source

import jvm.daily.config.RedditSourceConfig
import jvm.daily.model.FeedIngestStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedditSourceTest {

    @Test
    fun `sourceType is reddit`() {
        val source = RedditSource(emptyList())
        assertEquals("reddit", source.sourceType)
    }

    @Test
    fun `fetch from r-java returns articles with comments`() = runTest {
        val source = RedditSource(listOf(RedditSourceConfig("java", limit = 3)))
        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        val outcome = outcomes[0]
        assertEquals(FeedIngestStatus.SUCCESS, outcome.feed.status)
        assertEquals("r/java", outcome.feed.sourceId)
        assertTrue(outcome.articles.isNotEmpty(), "Should fetch at least 1 post")

        val article = outcome.articles.first()
        assertEquals("reddit", article.sourceType)
        assertTrue(article.url!!.contains("reddit.com"), "URL should point to reddit")
        assertTrue(article.author != null, "Should have an author")
        assertTrue(article.content.contains("Score:"), "Content should include score")
    }

    @Test
    fun `articles have canonical IDs`() = runTest {
        val source = RedditSource(listOf(RedditSourceConfig("java", limit = 2)))
        val articles = source.fetch()

        assertTrue(articles.isNotEmpty())
        assertTrue(articles.all { it.id.startsWith("reddit:") }, "IDs should be prefixed with reddit:")
    }

    @Test
    fun `invalid subreddit returns failed outcome`() = runTest {
        val source = RedditSource(listOf(RedditSourceConfig("this_subreddit_definitely_does_not_exist_xyz123")))
        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        assertEquals(FeedIngestStatus.FAILED, outcomes[0].feed.status)
    }
}
