package com.codeforge.core.data.repository

import com.codeforge.core.domain.repository.ComposableFunctionInfo
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComposeSourceAnalyzerImpl @Inject constructor() : ComposeSourceAnalyzer {
    override fun findComposables(content: String): List<ComposableFunctionInfo> {
        val regex = Regex("""@Composable\s+fun\s+([A-Za-z0-9_]+)""")
        return regex.findAll(content).map { ComposableFunctionInfo(it.groupValues[1]) }.toList()
    }
}
