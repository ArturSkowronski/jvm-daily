package jvm.daily.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * OpenAI-compatible LLM client.
 *
 * Works with: OpenAI, Groq, Together, Ollama, vLLM, LM Studio, and any
 * provider that implements the /v1/chat/completions endpoint.
 *
 * Env vars:
 *   LLM_API_KEY   — Bearer token (optional for local providers like Ollama)
 *   LLM_MODEL     — Model name (e.g. "gpt-4", "llama3", "mixtral-8x7b")
 *   LLM_BASE_URL  — API base URL (default: https://api.openai.com/v1)
 */
class OpenAiCompatibleLLMClient(
    private val apiKey: String?,
    private val model: String,
    private val baseUrl: String = "https://api.openai.com/v1",
) : LLMClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newHttpClient()

    override suspend fun chat(prompt: String): String {
        val requestBody = ChatRequest(
            model = model,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
        )

        val builder = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))

        if (!apiKey.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            error("LLM API error ${response.statusCode()}: ${response.body().take(500)}")
        }

        val chatResponse = json.decodeFromString<ChatResponse>(response.body())
        return chatResponse.choices.firstOrNull()?.message?.content
            ?: error("LLM returned empty response")
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice>,
    )

    @Serializable
    private data class Choice(
        val message: ChatMessage,
    )
}
