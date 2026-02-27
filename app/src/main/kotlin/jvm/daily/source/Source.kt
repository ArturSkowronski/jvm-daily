package jvm.daily.source

import jvm.daily.model.Article

/**
 * Boundary contract for all ingestion adapters.
 *
 * Implementations are responsible for fetching source-native content and
 * returning normalized [Article] records.
 *
 * Contract guarantees:
 * - `id`, `title`, `sourceType`, `sourceId`, `ingestedAt` are always populated
 * - partial records are allowed (`url`, `author`, `comments` may be null)
 * - adapter exceptions should be handled by the adapter where possible
 */
interface Source {
    val sourceType: String
    suspend fun fetch(): List<Article>
}
