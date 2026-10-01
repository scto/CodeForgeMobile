package com.codeforge.core.domain.repository

data class ComposableFunctionInfo(val functionName: String)

interface ComposeSourceAnalyzer {
    fun findComposables(content: String): List<ComposableFunctionInfo>
}
