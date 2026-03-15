package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant

interface ClusterRepository {
    fun save(cluster: ArticleCluster)
    fun saveAll(clusters: List<ArticleCluster>)
    fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster>
    fun deleteByDateRange(start: Instant, end: Instant)
}
