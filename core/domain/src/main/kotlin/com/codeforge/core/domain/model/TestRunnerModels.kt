// Modul: :core:domain
package com.codeforge.core.domain.model

enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

data class TestFailureDetail(
    val message: String?,
    val type: String?,
    val stackTrace: String,
    val expected: String? = null,
    val actual: String? = null,
    val stackFrames: List<StackFrame> = emptyList()
) {
    val isComparisonFailure: Boolean get() = expected != null && actual != null
}

data class TestCaseResult(
    val name: String,
    val className: String,
    val durationSeconds: Double,
    val status: TestStatus,
    val failureDetail: TestFailureDetail? = null,
    val stdout: String? = null,
    val stderr: String? = null
) {
    val isSuccess: Boolean get() = status == TestStatus.PASSED
    val isFailure: Boolean get() = status == TestStatus.FAILED || status == TestStatus.ERROR
    val isSkipped: Boolean get() = status == TestStatus.SKIPPED
}

data class TestClassSuite(
    val className: String,
    val packageName: String,
    val cases: List<TestCaseResult> = emptyList()
) {
    val totalCount: Int get() = cases.size
    val passedCount: Int get() = cases.count { it.status == TestStatus.PASSED }
    val failedCount: Int get() = cases.count { it.isFailure }
    val skippedCount: Int get() = cases.count { it.status == TestStatus.SKIPPED }
    val durationSeconds: Double get() = cases.sumOf { it.durationSeconds }
    val simpleClassName: String get() = className.substringAfterLast('.')
}

data class TestModuleSuite(
    val moduleGradlePath: String,
    val moduleName: String,
    val classes: List<TestClassSuite> = emptyList()
) {
    val totalCount: Int get() = classes.sumOf { it.totalCount }
    val passedCount: Int get() = classes.sumOf { it.passedCount }
    val failedCount: Int get() = classes.sumOf { it.failedCount }
    val skippedCount: Int get() = classes.sumOf { it.skippedCount }
    val durationSeconds: Double get() = classes.sumOf { it.durationSeconds }
}

data class TestRunResult(
    val moduleSuites: List<TestModuleSuite> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalCount: Int get() = moduleSuites.sumOf { it.totalCount }
    val passedCount: Int get() = moduleSuites.sumOf { it.passedCount }
    val failedCount: Int get() = moduleSuites.sumOf { it.failedCount }
    val skippedCount: Int get() = moduleSuites.sumOf { it.skippedCount }
    val durationSeconds: Double get() = moduleSuites.sumOf { it.durationSeconds }

    val allFailedCases: List<TestCaseResult>
        get() = moduleSuites.flatMap { it.classes }.flatMap { it.cases }.filter { it.isFailure }

    val hasFailures: Boolean get() = failedCount > 0
}

enum class TestFilterMode {
    ALL,
    FAILURES_ONLY
}
