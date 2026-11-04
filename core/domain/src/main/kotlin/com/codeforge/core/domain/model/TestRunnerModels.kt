// Modul: :core:domain
package com.codeforge.core.domain.model

/** Ergebnis eines Testfalls. */
enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

/** Details eines Testfehlers (Meldung, Typ, Stacktrace, Soll/Ist, Frames). */
data class TestFailureDetail(
    val message: String?,
    val type: String?,
    val stackTrace: String,
    val expected: String? = null,
    val actual: String? = null,
    val stackFrames: List<StackFrame> = emptyList()
) {
    /** Ob Soll- und Ist-Wert vorliegen (Vergleichsfehler). */
    val isComparisonFailure: Boolean get() = expected != null && actual != null
}

/** Ergebnis eines einzelnen Testfalls. */
data class TestCaseResult(
    val name: String,
    val className: String,
    val durationSeconds: Double,
    val status: TestStatus,
    val failureDetail: TestFailureDetail? = null,
    val stdout: String? = null,
    val stderr: String? = null
) {
    /** Ob der Test bestanden wurde. */
    val isSuccess: Boolean get() = status == TestStatus.PASSED
    /** Ob der Test fehlgeschlagen ist (Failure oder Error). */
    val isFailure: Boolean get() = status == TestStatus.FAILED || status == TestStatus.ERROR
    /** Ob der Test übersprungen wurde. */
    val isSkipped: Boolean get() = status == TestStatus.SKIPPED
}

/** Ergebnisse einer Testklasse. */
data class TestClassSuite(
    val className: String,
    val packageName: String,
    val cases: List<TestCaseResult> = emptyList()
) {
    /** Anzahl aller Tests. */
    val totalCount: Int get() = cases.size
    /** Anzahl bestandener Tests. */
    val passedCount: Int get() = cases.count { it.status == TestStatus.PASSED }
    /** Anzahl fehlgeschlagener Tests. */
    val failedCount: Int get() = cases.count { it.isFailure }
    /** Anzahl übersprungener Tests. */
    val skippedCount: Int get() = cases.count { it.status == TestStatus.SKIPPED }
    /** Gesamtdauer in Sekunden. */
    val durationSeconds: Double get() = cases.sumOf { it.durationSeconds }
    /** Klassenname ohne Package. */
    val simpleClassName: String get() = className.substringAfterLast('.')
}

/** Ergebnisse eines Moduls. */
data class TestModuleSuite(
    val moduleGradlePath: String,
    val moduleName: String,
    val classes: List<TestClassSuite> = emptyList()
) {
    /** Anzahl aller Tests. */
    val totalCount: Int get() = classes.sumOf { it.totalCount }
    /** Anzahl bestandener Tests. */
    val passedCount: Int get() = classes.sumOf { it.passedCount }
    /** Anzahl fehlgeschlagener Tests. */
    val failedCount: Int get() = classes.sumOf { it.failedCount }
    /** Anzahl übersprungener Tests. */
    val skippedCount: Int get() = classes.sumOf { it.skippedCount }
    /** Gesamtdauer in Sekunden. */
    val durationSeconds: Double get() = classes.sumOf { it.durationSeconds }
}

/** Gesamtergebnis eines Testlaufs über alle Module. */
data class TestRunResult(
    val moduleSuites: List<TestModuleSuite> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Anzahl aller Tests. */
    val totalCount: Int get() = moduleSuites.sumOf { it.totalCount }
    /** Anzahl bestandener Tests. */
    val passedCount: Int get() = moduleSuites.sumOf { it.passedCount }
    /** Anzahl fehlgeschlagener Tests. */
    val failedCount: Int get() = moduleSuites.sumOf { it.failedCount }
    /** Anzahl übersprungener Tests. */
    val skippedCount: Int get() = moduleSuites.sumOf { it.skippedCount }
    /** Gesamtdauer in Sekunden. */
    val durationSeconds: Double get() = moduleSuites.sumOf { it.durationSeconds }

    /** Alle fehlgeschlagenen Testfälle. */
    val allFailedCases: List<TestCaseResult>
        get() = moduleSuites.flatMap { it.classes }.flatMap { it.cases }.filter { it.isFailure }

    /** Ob mindestens ein Test fehlgeschlagen ist. */
    val hasFailures: Boolean get() = failedCount > 0
}

/** Filter der Testliste: alle Tests oder nur Fehlschläge. */
enum class TestFilterMode {
    ALL,
    FAILURES_ONLY
}
