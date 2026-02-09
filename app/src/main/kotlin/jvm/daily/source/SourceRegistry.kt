package jvm.daily.source

class SourceRegistry {
    private val sources = mutableListOf<Source>()

    fun register(source: Source) {
        sources.add(source)
    }

    fun all(): List<Source> = sources.toList()
}
