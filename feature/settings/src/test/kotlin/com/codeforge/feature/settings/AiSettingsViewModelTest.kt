package com.codeforge.feature.settings

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiEvent
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProvider
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeRepo()
    private val provider = FakeProvider()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AiSettingsViewModel(repo, provider)

    @Test
    fun `Schluessel wird pro Anbieter gespeichert und aktiver Anbieter bleibt`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onEvent(AiSettingsUiEvent.ApiKeyChanged(AiProviderType.GEMINI, "  AIzaKey  "))
        assertTrue(vm.uiState.value.providers.first { it.provider == AiProviderType.GEMINI }.dirty)
        vm.onEvent(AiSettingsUiEvent.Save(AiProviderType.GEMINI))
        advanceUntilIdle()
        assertEquals("AIzaKey", repo.stored.getValue(AiProviderType.GEMINI).apiKey)
        assertEquals(AiProviderType.OPENAI, repo.active.provider)
        assertFalse(vm.uiState.value.providers.first { it.provider == AiProviderType.GEMINI }.dirty)
    }

    @Test
    fun `Anbieter aktivieren speichert ihn mit Schluessel`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onEvent(AiSettingsUiEvent.ApiKeyChanged(AiProviderType.ANTHROPIC, "sk-ant-x"))
        vm.onEvent(AiSettingsUiEvent.SetActive(AiProviderType.ANTHROPIC))
        advanceUntilIdle()
        assertEquals(AiProviderType.ANTHROPIC, repo.active.provider)
        assertEquals("sk-ant-x", repo.active.apiKey)
        assertEquals(AiProviderType.ANTHROPIC, vm.uiState.value.activeProvider)
    }

    @Test
    fun `Verbindungstest meldet Erfolg und Fehler`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onEvent(AiSettingsUiEvent.ApiKeyChanged(AiProviderType.GEMINI, "AIzaKey"))
        vm.onEvent(AiSettingsUiEvent.TestConnection(AiProviderType.GEMINI))
        advanceUntilIdle()
        assertEquals(AiCheckState.Success("OK"), vm.uiState.value.providers.first { it.provider == AiProviderType.GEMINI }.test)
        provider.fail = true
        vm.onEvent(AiSettingsUiEvent.TestConnection(AiProviderType.GEMINI))
        advanceUntilIdle()
        val failure = vm.uiState.value.providers.first { it.provider == AiProviderType.GEMINI }.test
        assertTrue(failure is AiCheckState.Failure && failure.message == "401")
    }

    @Test
    fun `Live-Modelle stehen vor den Vorschlaegen ohne Duplikate`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onEvent(AiSettingsUiEvent.ApiKeyChanged(AiProviderType.GEMINI, "AIzaKey"))
        vm.onEvent(AiSettingsUiEvent.FetchModels(AiProviderType.GEMINI))
        advanceUntilIdle()
        val models = vm.uiState.value.providers.first { it.provider == AiProviderType.GEMINI }.models
        assertEquals("live-model", models.first())
        assertEquals(models.size, models.toSet().size)
        assertTrue(AiProviderType.GEMINI.defaultModel in models)
    }

    @Test
    fun `Tageslimit wird begrenzt`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onEvent(AiSettingsUiEvent.DailyLimitChanged(-5))
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.dailyLimit)
    }

    @Test
    fun `Standardmodell ist in den Vorschlaegen enthalten`() {
        AiProviderType.entries.forEach {
            assertTrue(it.id, it.defaultModel in it.suggestedModels)
            assertEquals(it.id, it.suggestedModels.size, it.suggestedModels.toSet().size)
        }
    }

    private class FakeProvider : AiProvider {
        var fail = false
        override fun streamChat(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent> = emptyFlow()
        override suspend fun testConnection(options: AiChatOptions): Result<String> =
            if (fail) Result.failure(IllegalStateException("401")) else Result.success("OK")
        override suspend fun fetchAvailableModels(options: AiChatOptions): Result<List<String>> =
            Result.success(listOf("live-model", options.provider.defaultModel))
    }

    private class FakeRepo : AiRepository {
        val stored = mutableMapOf<AiProviderType, AiChatOptions>()
        var active = AiChatOptions(AiProviderType.OPENAI, AiProviderType.OPENAI.defaultModel)
        private val policy = MutableStateFlow(AiPrivacyPolicy.ONLY_SELECTED)
        private val limit = MutableStateFlow(50)
        private val count = MutableStateFlow(0)

        override fun getAiOptions(): Flow<AiChatOptions> = MutableStateFlow(active)
        override suspend fun saveAiOptions(options: AiChatOptions) { active = options; stored[options.provider] = options }
        override suspend fun getProviderOptions(provider: AiProviderType) =
            stored[provider] ?: AiChatOptions(provider, provider.defaultModel, provider.defaultBaseUrl)
        override suspend fun saveProviderOptions(options: AiChatOptions) { stored[options.provider] = options }
        override fun getPrivacyPolicy(): Flow<AiPrivacyPolicy> = policy
        override suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy) { this.policy.value = policy }
        override fun getDailyLimit(): Flow<Int> = limit
        override suspend fun saveDailyLimit(limit: Int) { this.limit.value = limit }
        override fun getTodayRequestCount(): Flow<Int> = count
        override suspend fun incrementTodayRequestCount() { count.value += 1 }
        override fun getHistory(projectId: String): Flow<List<AiChatMessage>> = MutableStateFlow(emptyList())
        override suspend fun saveHistory(projectId: String, history: List<AiChatMessage>) {}
        override suspend fun clearHistory(projectId: String) {}
    }
}
