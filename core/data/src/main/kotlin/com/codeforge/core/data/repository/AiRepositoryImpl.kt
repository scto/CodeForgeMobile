// Modul: :core:data
package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.data.security.KeyValueStore
import com.codeforge.core.data.security.SharedPreferencesStore
import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.ai.AiRole
import com.codeforge.core.domain.ai.AiUsage
import com.codeforge.core.domain.repository.AiRepository
import com.codeforge.core.domain.security.SecretStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Singleton
class AiRepositoryImpl internal constructor(
    private val store: KeyValueStore,
    private val historyDir: File,
    private val secretStorage: SecretStorage
) : AiRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        secretStorage: SecretStorage
    ) : this(
        SharedPreferencesStore(context.getSharedPreferences("codeforge_ai_settings", Context.MODE_PRIVATE)),
        File(context.filesDir, "ai_history").apply { mkdirs() },
        secretStorage
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Mutex()

    private val _optionsFlow = MutableStateFlow(readOptionsSync())

    init {
        // Der API-Schlüssel liegt im SecretStorage und wurde nie zurückgelesen: nach jedem Neustart war `apiKey` leer,
        // obwohl die Einstellungen einen Schlüssel zeigten bzw. gespeichert hatten.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { restoreStoredKey() }
    }

    private suspend fun restoreStoredKey() {
        val provider = _optionsFlow.value.provider
        val key = try {
            secretStorage.getSecret(SECRET_KEY_PREFIX + provider.id)
        } catch (_: Exception) {
            null
        }
        if (key.isNullOrBlank()) return
        lock.withLock {
            // Nur ergänzen, wenn der Benutzer inzwischen nichts anderes gespeichert hat.
            _optionsFlow.update { current ->
                if (current.provider == provider && current.apiKey.isBlank()) current.copy(apiKey = key) else current
            }
        }
    }
    private val _privacyFlow = MutableStateFlow(readPrivacyPolicySync())
    private val _dailyLimitFlow = MutableStateFlow(store.get(KEY_DAILY_LIMIT)?.toIntOrNull() ?: 50)
    private val _requestCountFlow = MutableStateFlow(readRequestCountSync())

    private val historyFlows = mutableMapOf<String, MutableStateFlow<List<AiChatMessage>>>()

    override fun getAiOptions(): Flow<AiChatOptions> = _optionsFlow.asStateFlow()

    override suspend fun saveAiOptions(options: AiChatOptions) {
        lock.withLock {
            store.put(KEY_PROVIDER, options.provider.id)
            store.put(KEY_MODEL_PREFIX + options.provider.id, options.model)
            options.baseUrl?.let { store.put(KEY_BASE_URL_PREFIX + options.provider.id, it) }

            if (options.apiKey.isNotBlank()) {
                secretStorage.storeSecret(SECRET_KEY_PREFIX + options.provider.id, options.apiKey)
            } else {
                secretStorage.removeSecret(SECRET_KEY_PREFIX + options.provider.id)
            }

            _optionsFlow.value = options
        }
    }

    override fun getPrivacyPolicy(): Flow<AiPrivacyPolicy> = _privacyFlow.asStateFlow()

    override suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy) {
        lock.withLock {
            store.put(KEY_PRIVACY_POLICY, policy.name)
            _privacyFlow.value = policy
        }
    }

    override fun getDailyLimit(): Flow<Int> = _dailyLimitFlow.asStateFlow()

    override suspend fun saveDailyLimit(limit: Int) {
        lock.withLock {
            store.put(KEY_DAILY_LIMIT, limit.toString())
            _dailyLimitFlow.value = limit
        }
    }

    override fun getTodayRequestCount(): Flow<Int> = _requestCountFlow.asStateFlow()

    override suspend fun incrementTodayRequestCount() {
        lock.withLock {
            val today = currentDayString()
            val savedDate = store.get(KEY_REQUEST_DATE) ?: ""
            val currentCount = store.get(KEY_REQUEST_COUNT)?.toIntOrNull() ?: 0
            val newCount = if (savedDate == today) {
                currentCount + 1
            } else {
                1
            }
            store.put(KEY_REQUEST_DATE, today)
            store.put(KEY_REQUEST_COUNT, newCount.toString())
            _requestCountFlow.value = newCount
        }
    }

    override fun getHistory(projectId: String): Flow<List<AiChatMessage>> {
        return synchronized(historyFlows) {
            historyFlows.getOrPut(projectId) {
                MutableStateFlow(loadHistoryFromFile(projectId))
            }.asStateFlow()
        }
    }

    override suspend fun saveHistory(projectId: String, history: List<AiChatMessage>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = historyFileFor(projectId)
            val jsonArray = buildJsonArray {
                for (msg in history) {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(msg.id))
                            put("role", JsonPrimitive(msg.role.name))
                            put("content", JsonPrimitive(msg.content))
                            put("timestamp", JsonPrimitive(msg.timestamp))
                            msg.usage?.let { u ->
                                put(
                                    "usage",
                                    buildJsonObject {
                                        put("prompt", JsonPrimitive(u.promptTokens))
                                        put("completion", JsonPrimitive(u.completionTokens))
                                        put("total", JsonPrimitive(u.totalTokens))
                                    }
                                )
                            }
                        }
                    )
                }
            }
            file.writeText(json.encodeToString(JsonArray.serializer(), jsonArray))
            synchronized(historyFlows) {
                historyFlows[projectId]?.value = history
            }
        }
    }

    override suspend fun clearHistory(projectId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = historyFileFor(projectId)
            if (file.exists()) {
                file.delete()
            }
            synchronized(historyFlows) {
                historyFlows[projectId]?.value = emptyList()
            }
        }
    }

    private fun historyFileFor(projectId: String): File {
        val safeName = projectId.hashCode().toUInt().toString(16) + ".json"
        return File(historyDir, safeName)
    }

    private fun loadHistoryFromFile(projectId: String): List<AiChatMessage> {
        val file = historyFileFor(projectId)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            val array = json.parseToJsonElement(content).jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val roleStr = obj["role"]?.jsonPrimitive?.content ?: "USER"
                val role = try { AiRole.valueOf(roleStr) } catch (_: Exception) { AiRole.USER }
                val text = obj["content"]?.jsonPrimitive?.content ?: ""
                val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val usageObj = obj["usage"]?.jsonObject
                val usage = usageObj?.let {
                    AiUsage(
                        promptTokens = it["prompt"]?.jsonPrimitive?.intOrNull ?: 0,
                        completionTokens = it["completion"]?.jsonPrimitive?.intOrNull ?: 0,
                        totalTokens = it["total"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                }
                AiChatMessage(id = id, role = role, content = text, timestamp = timestamp, usage = usage)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readPrivacyPolicySync(): AiPrivacyPolicy {
        val name = store.get(KEY_PRIVACY_POLICY) ?: AiPrivacyPolicy.ALWAYS_ASK.name
        return try {
            AiPrivacyPolicy.valueOf(name)
        } catch (_: Exception) {
            AiPrivacyPolicy.ALWAYS_ASK
        }
    }

    private fun readRequestCountSync(): Int {
        val today = currentDayString()
        val savedDate = store.get(KEY_REQUEST_DATE) ?: ""
        return if (savedDate == today) {
            store.get(KEY_REQUEST_COUNT)?.toIntOrNull() ?: 0
        } else {
            0
        }
    }

    private fun readOptionsSync(): AiChatOptions {
        val providerId = store.get(KEY_PROVIDER) ?: AiProviderType.OPENAI.id
        val provider = AiProviderType.fromId(providerId)
        val model = store.get(KEY_MODEL_PREFIX + provider.id) ?: provider.defaultModel
        val baseUrl = store.get(KEY_BASE_URL_PREFIX + provider.id) ?: provider.defaultBaseUrl

        return AiChatOptions(
            provider = provider,
            model = model,
            baseUrl = baseUrl,
            apiKey = ""
        )
    }

    private fun currentDayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    companion object {
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_MODEL_PREFIX = "ai_model_"
        private const val KEY_BASE_URL_PREFIX = "ai_base_url_"
        private const val KEY_PRIVACY_POLICY = "ai_privacy_policy"
        private const val KEY_DAILY_LIMIT = "ai_daily_limit"
        private const val KEY_REQUEST_DATE = "ai_request_date"
        private const val KEY_REQUEST_COUNT = "ai_request_count"
        private const val SECRET_KEY_PREFIX = "ai_key_"
    }
}
