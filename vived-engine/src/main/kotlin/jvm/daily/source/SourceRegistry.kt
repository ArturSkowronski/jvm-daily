package jvm.daily.source

/**
 * Connector boundary for onboarding: new connectors should only need to
 * implement [Source] and register once here; workflow orchestration remains unchanged.
 */
class SourceRegistry {
    private val sources = mutableListOf<Source>()

    fun register(source: Source) {
        val type = source.sourceType
        require(type.isNotBlank()) { "sourceType must not be blank" }
        require(type == type.trim()) { "sourceType must not have leading/trailing whitespace: '$type'" }
        require(sources.none { it.sourceType.equals(type, ignoreCase = true) }) {
            "Source already registered (case-insensitive): $type"
        }
        sources.add(source)
    }

    fun all(): List<Source> = sources.toList()
}
