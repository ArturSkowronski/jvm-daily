package jvm.daily

import dev.vived.engine.ai.OpenAiCompatibleLLMClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AppLlmClientTest {

    @Test
    fun `default model is OpenAI Luna`() {
        assertEquals("gpt-6-luna", DEFAULT_LLM_MODEL)
    }

    @Test
    fun `openai provider builds OpenAI-compatible client`() {
        assertIs<OpenAiCompatibleLLMClient>(createLLMClient("openai", "sk-test", DEFAULT_LLM_MODEL))
    }

    @Test
    fun `unknown provider fails`() {
        assertFailsWith<IllegalStateException> { createLLMClient("nope", null, DEFAULT_LLM_MODEL) }
    }
}
