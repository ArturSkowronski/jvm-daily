package jvm.daily.source

class SourceRegistry {
    private val sources = mutableListOf<Source>()

    fun register(source: Source) {
        require(source.sourceType.isNotBlank()) { "sourceType must not be blank" }
        require(sources.none { it.sourceType == source.sourceType }) {
            "Source already registered: ${source.sourceType}"
        }
        sources.add(source)
    }

    fun all(): List<Source> = sources.toList()
}
