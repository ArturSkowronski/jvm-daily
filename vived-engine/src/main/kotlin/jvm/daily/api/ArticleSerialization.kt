package jvm.daily.api

import jvm.daily.model.Article
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

fun parseIngestPayload(json: String): List<Article> {
    val array = Json.parseToJsonElement(json).jsonArray
    return array.map { elem ->
        val obj = elem.jsonObject
        Article(
            id = obj["id"]!!.jsonPrimitive.content,
            title = obj["title"]!!.jsonPrimitive.content,
            content = obj["content"]?.jsonPrimitive?.content ?: "",
            sourceType = obj["sourceType"]!!.jsonPrimitive.content,
            sourceId = obj["sourceId"]!!.jsonPrimitive.content,
            url = obj["url"]?.jsonPrimitive?.contentOrNull,
            author = obj["author"]?.jsonPrimitive?.contentOrNull,
            comments = obj["comments"]?.jsonPrimitive?.contentOrNull,
            ingestedAt = Instant.parse(obj["ingestedAt"]!!.jsonPrimitive.content),
        )
    }
}

fun serializeArticles(articles: List<Article>): String {
    val elements = articles.map { a ->
        buildJsonObject {
            put("id", a.id)
            put("title", a.title)
            put("content", a.content)
            put("sourceType", a.sourceType)
            put("sourceId", a.sourceId)
            a.url?.let { put("url", it) }
            a.author?.let { put("author", it) }
            a.comments?.let { put("comments", it) }
            put("ingestedAt", a.ingestedAt.toString())
        }
    }
    return JsonArray(elements).toString()
}
