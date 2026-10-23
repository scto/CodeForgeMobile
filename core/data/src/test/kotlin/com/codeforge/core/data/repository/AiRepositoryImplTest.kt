// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.data.security.KeyValueStore
import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.ai.AiRole
import com.codeforge.core.domain.security.SecretStorage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mapStore: MapStore
    private lateinit var fakeSecretStorage: FakeSecretStorage
    private lateinit var repository: AiRepositoryImpl

    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class FakeSecretStorage : SecretStorage {
        val secrets = mutableMapOf<String, String>()
        override suspend fun getSecret(key: String): String? = secrets[key]
        override suspend fun storeSecret(key: String, secret: String) { secrets[key] = secret }
        override suspend fun removeSecret(key: String) { secrets.remove(key) }
    }

    @Before
    fun setUp() {
        mapStore = MapStore()
        fakeSecretStorage = FakeSecretStorage()
        val historyDir = File(tempFolder.root, "ai_history").apply { mkdirs() }
        repository = AiRepositoryImpl(mapStore, historyDir, fakeSecretStorage)
    }

    @Test
    fun testOptionsAndSecretStorage() = runBlocking {
        val options = AiChatOptions(
            provider = AiProviderType.ANTHROPIC,
            model = "claude-3-7-sonnet-latest",
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant-test-key"
        )
        repository.saveAiOptions(options)

        assertEquals("sk-ant-test-key", fakeSecretStorage.secrets["ai_key_anthropic"])
        val loaded = repository.getAiOptions().first()
        assertEquals(AiProviderType.ANTHROPIC, loaded.provider)
        assertEquals("claude-3-7-sonnet-latest", loaded.model)
    }

    @Test
    fun testHistorySaveAndClear() = runBlocking {
        val projectId = "project_test_123"
        val msg1 = AiChatMessage(id = "1", role = AiRole.USER, content = "Hello")
        val msg2 = AiChatMessage(id = "2", role = AiRole.ASSISTANT, content = "Hi there!")

        repository.saveHistory(projectId, listOf(msg1, msg2))
        val history = repository.getHistory(projectId).first()
        assertEquals(2, history.size)
        assertEquals("Hello", history[0].content)
        assertEquals("Hi there!", history[1].content)

        repository.clearHistory(projectId)
        val cleared = repository.getHistory(projectId).first()
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun testDailyLimitAndCounter() = runBlocking {
        repository.saveDailyLimit(25)
        assertEquals(25, repository.getDailyLimit().first())

        repository.incrementTodayRequestCount()
        repository.incrementTodayRequestCount()
        assertEquals(2, repository.getTodayRequestCount().first())
    }

    @Test
    fun testPrivacyPolicy() = runBlocking {
        repository.savePrivacyPolicy(AiPrivacyPolicy.ONLY_SELECTED)
        assertEquals(AiPrivacyPolicy.ONLY_SELECTED, repository.getPrivacyPolicy().first())
    }
}
