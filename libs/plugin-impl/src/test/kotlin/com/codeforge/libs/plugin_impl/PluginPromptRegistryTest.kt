package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.PluginPromptRegistry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginPromptRegistryTest {

    @Test
    fun answerResumesAndRemoves() = runTest {
        val result = async { PluginPromptRegistry.ask("a", "T", "M", listOf("x", "y")) }
        runCurrent()
        val prompt = PluginPromptRegistry.prompts.value.single { it.pluginId == "a" }
        PluginPromptRegistry.answer(prompt.id, 1)
        assertEquals(1, result.await())
        assertTrue(PluginPromptRegistry.prompts.value.none { it.pluginId == "a" })
    }

    @Test
    fun outOfRangeAnswerIsCancel() = runTest {
        val result = async { PluginPromptRegistry.ask("b", "T", "M", listOf("ok")) }
        runCurrent()
        PluginPromptRegistry.answer(PluginPromptRegistry.prompts.value.single { it.pluginId == "b" }.id, 5)
        assertNull(result.await())
    }

    @Test
    fun cancelAllAnswersNull() = runTest {
        val result = async { PluginPromptRegistry.ask("c", "T", "M", listOf("ok")) }
        runCurrent()
        PluginPromptRegistry.cancelAll("c")
        assertNull(result.await())
        assertTrue(PluginPromptRegistry.prompts.value.none { it.pluginId == "c" })
    }

    @Test
    fun cancelledCallerRemovesPrompt() = runTest {
        val result = async { PluginPromptRegistry.ask("d", "T", "M", listOf("ok")) }
        runCurrent()
        result.cancel()
        runCurrent()
        assertTrue(PluginPromptRegistry.prompts.value.none { it.pluginId == "d" })
    }

    @Test
    fun limitsPendingPerPlugin() = runTest {
        val pending = List(3) { async { PluginPromptRegistry.ask("e", "T", "M", listOf("ok")) } }
        runCurrent()
        val fourth = runCatching { PluginPromptRegistry.ask("e", "T", "M", listOf("ok")) }
        assertTrue(fourth.isFailure)
        PluginPromptRegistry.cancelAll("e")
        pending.forEach { assertNull(it.await()) }
    }
}
