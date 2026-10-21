// Modul: :core:navigation
package com.codeforge.core.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anforderung zum Starten eines Tests (oder einer Test-Suite) aus dem Editor oder anderen Modulen.
 */
data class TestRunRequest(
    val moduleGradlePath: String? = null,
    val testFilter: String? = null
)

interface TestRunRequestBridge {
    val runRequests: SharedFlow<TestRunRequest>
    suspend fun requestRunTest(moduleGradlePath: String? = null, testFilter: String? = null)
}

@Singleton
class TestRunRequestBridgeImpl @Inject constructor() : TestRunRequestBridge {
    private val _runRequests = MutableSharedFlow<TestRunRequest>(extraBufferCapacity = 4)
    override val runRequests: SharedFlow<TestRunRequest> = _runRequests.asSharedFlow()

    override suspend fun requestRunTest(moduleGradlePath: String?, testFilter: String?) {
        _runRequests.emit(TestRunRequest(moduleGradlePath, testFilter))
    }
}
