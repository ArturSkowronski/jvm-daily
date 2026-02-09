package jvm.daily.storage

import jvm.daily.model.Article

interface ArticleRepository {
    fun save(article: Article)
    fun saveAll(articles: List<Article>)
    fun findAll(): List<Article>
    fun findBySourceType(sourceType: String): List<Article>
    fun count(): Long
}
