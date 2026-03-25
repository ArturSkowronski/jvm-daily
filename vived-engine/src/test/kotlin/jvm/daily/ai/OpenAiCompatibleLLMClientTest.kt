package jvm.daily.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiCompatibleLLMClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `chat returns content from OpenAI-compatible response`() = runTest {
        server.createContext("/v1/chat/completions") { exchange ->
            val responseJson = """
                {"choices":[{"message":{"role":"assistant","content":"Hello from LLM"}}]}
            """.trimIndent()
            exchange.sendResponseHeaders(200, responseJson.length.toLong())
            exchange.responseBody.use { it.write(responseJson.toByteArray()) }
        }
        server.start()

        val client = OpenAiCompatibleLLMClient(
            apiKey = "test-key",
            model = "test-model",
            baseUrl = "http://localhost:$port/v1",
        )

        val result = client.chat("Say hello")
        assertEquals("Hello from LLM", result)
    }

    @Test
    fun `chat throws on non-2xx status`() = runTest {
        server.createContext("/v1/chat/completions") { exchange ->
            val body = """{"error":{"message":"rate limited"}}"""
            exchange.sendResponseHeaders(429, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        val client = OpenAiCompatibleLLMClient(
            apiKey = "key",
            model = "model",
            baseUrl = "http://localhost:$port/v1",
        )

        val ex = assertFailsWith<IllegalStateException> { client.chat("test") }
        assertContains(ex.message!!, "429")
    }

    @Test
    fun `chat works without API key for local providers`() = runTest {
        server.createContext("/v1/chat/completions") { exchange ->
            // Verify no Authorization header
            val authHeader = exchange.requestHeaders["Authorization"]
            val hasAuth = authHeader != null && authHeader.isNotEmpty()

            val responseJson = """
                {"choices":[{"message":{"role":"assistant","content":"no-auth-ok=$hasAuth"}}]}
            """.trimIndent()
            exchange.sendResponseHeaders(200, responseJson.length.toLong())
            exchange.responseBody.use { it.write(responseJson.toByteArray()) }
        }
        server.start()

        val client = OpenAiCompatibleLLMClient(
            apiKey = null,
            model = "llama3",
            baseUrl = "http://localhost:$port/v1",
        )

        val result = client.chat("test")
        assertEquals("no-auth-ok=false", result)
    }
}
