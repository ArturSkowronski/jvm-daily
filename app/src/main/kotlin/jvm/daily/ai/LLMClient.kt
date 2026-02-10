package jvm.daily.ai

/**
 * Simple LLM client interface for AI workflows.
 * Abstracts away the underlying AI provider (Koog Agents, direct API calls, etc.)
 */
interface LLMClient {
    /**
     * Send a chat prompt and get a response.
     * @param prompt The full prompt (system + user context combined)
     * @return The LLM's text response
     */
    suspend fun chat(prompt: String): String
}
