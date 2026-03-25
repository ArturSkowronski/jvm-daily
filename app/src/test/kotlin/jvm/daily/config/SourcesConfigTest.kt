package jvm.daily.config

import com.charleskorn.kaml.Yaml
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmSourcesConfigTest {

    @Test
    fun `jep config parses from yaml`() {
        val yaml = """
            jep:
              enabled: true
              initialSeed: false
              activeStatuses: [Targeted, Integrated]
        """.trimIndent()
        val config = Yaml.default.decodeFromString(JvmSourcesConfig.serializer(), yaml)
        assertEquals(true, config.jep?.enabled)
        assertEquals(listOf("Targeted", "Integrated"), config.jep?.activeStatuses)
    }

    @Test
    fun `jep config defaults to null when missing`() {
        val config = Yaml.default.decodeFromString(JvmSourcesConfig.serializer(), "{}")
        assertNull(config.jep)
    }
}
