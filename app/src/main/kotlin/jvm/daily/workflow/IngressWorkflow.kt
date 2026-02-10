package jvm.daily.workflow

import jvm.daily.source.SourceRegistry
import jvm.daily.storage.ArticleRepository

class IngressWorkflow(
    private val sourceRegistry: SourceRegistry,
    private val articleRepository: ArticleRepository,
) : Workflow {

    override val name: String = "ingress"

    override suspend fun execute() {
        val sources = sourceRegistry.all()
        println("[ingress] Starting ingress workflow with ${sources.size} source(s)")

        var totalNew = 0
        var totalSkipped = 0
        var totalErrors = 0

        for (source in sources) {
            val articles = source.fetch()
            var newCount = 0
            var skippedCount = 0

            for (article in articles) {
                if (articleRepository.existsById(article.id)) {
                    skippedCount++
                } else {
                    articleRepository.save(article)
                    newCount++
                }
            }

            println("[ingress] ${source.sourceType}: ${articles.size} fetched, $newCount new, $skippedCount duplicate")
            totalNew += newCount
            totalSkipped += skippedCount
        }

        println("[ingress] Done. New: $totalNew, Duplicates: $totalSkipped, Total in DB: ${articleRepository.count()}")
    }
}
