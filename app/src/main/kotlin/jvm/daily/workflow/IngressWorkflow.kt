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

        var totalIngested = 0
        for (source in sources) {
            val articles = source.fetch()
            println("[ingress] Fetched ${articles.size} article(s) from ${source.sourceType}")
            articleRepository.saveAll(articles)
            totalIngested += articles.size
        }

        println("[ingress] Ingress complete. Total articles ingested: $totalIngested")
    }
}
