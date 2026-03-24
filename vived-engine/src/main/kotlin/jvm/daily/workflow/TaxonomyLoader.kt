package jvm.daily.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Loads and exposes the JVM Daily taxonomy for article classification.
 */
class TaxonomyLoader private constructor(
    private val areaNodes: List<TaxonomyNode>,
    private val impactTagNodes: List<TaxonomyTag>,
) {
    fun areas(): List<TaxonomyNode> = areaNodes

    fun subAreasFor(areaSlug: String): List<TaxonomyNode> =
        areaNodes.find { it.slug == areaSlug }?.children ?: emptyList()

    fun impactTags(): List<TaxonomyTag> = impactTagNodes

    fun isValidArea(slug: String): Boolean = areaNodes.any { it.slug == slug }

    fun isValidSubArea(areaSlug: String, subAreaSlug: String): Boolean =
        subAreasFor(areaSlug).any { it.slug == subAreaSlug }

    fun isValidImpactTag(slug: String): Boolean = impactTagNodes.any { it.slug == slug }

    /**
     * Builds a prompt fragment listing all areas with their classification hints.
     */
    fun buildAreasPrompt(): String = buildString {
        appendLine("Areas:")
        for (area in areaNodes) {
            appendLine("- ${area.slug}: ${area.title} — ${area.hintSummary}")
            for (child in area.children) {
                appendLine("  - ${child.slug}: ${child.title} — ${child.hintSummary}")
            }
        }
        appendLine()
        appendLine("Impact tags (zero or more):")
        for (tag in impactTagNodes) {
            appendLine("- ${tag.slug}: ${tag.description}")
        }
    }

    companion object {
        fun loadFromClasspath(): TaxonomyLoader {
            val text = TaxonomyLoader::class.java.classLoader
                .getResource("taxonomy.json")
                ?.readText()
                ?: throw IllegalStateException("taxonomy.json not found on classpath")
            return loadFromString(text)
        }

        fun loadFromString(jsonText: String): TaxonomyLoader {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonText).jsonObject

            val nodes = root["nodes"]?.jsonArray ?: error("Missing 'nodes' in taxonomy")
            val areaNodes = nodes.map { parseNode(it.jsonObject) }

            val tagSets = root["tag_sets"]?.jsonObject
            val impactArray = tagSets?.get("impact")?.jsonArray ?: JsonArray(emptyList())
            val impactTags = impactArray.map { parseTag(it.jsonObject) }

            return TaxonomyLoader(areaNodes, impactTags)
        }

        private fun parseNode(obj: JsonObject): TaxonomyNode {
            val slug = obj["slug"]!!.jsonPrimitive.content
            val title = obj["title"]!!.jsonPrimitive.content
            val hints = obj["classification_hints"]?.jsonObject
            val summary = hints?.get("summary")?.jsonPrimitive?.content ?: ""
            val children = obj["children"]?.jsonArray?.map { parseNode(it.jsonObject) } ?: emptyList()
            return TaxonomyNode(slug, title, summary, children)
        }

        private fun parseTag(obj: JsonObject): TaxonomyTag {
            val slug = obj["slug"]!!.jsonPrimitive.content
            val name = obj["name"]!!.jsonPrimitive.content
            val hints = obj["classification_hints"]?.jsonObject
            val description = hints?.get("description")?.jsonPrimitive?.content ?: ""
            return TaxonomyTag(slug, name, description)
        }
    }
}

data class TaxonomyNode(
    val slug: String,
    val title: String,
    val hintSummary: String,
    val children: List<TaxonomyNode> = emptyList(),
)

data class TaxonomyTag(
    val slug: String,
    val name: String,
    val description: String,
)
