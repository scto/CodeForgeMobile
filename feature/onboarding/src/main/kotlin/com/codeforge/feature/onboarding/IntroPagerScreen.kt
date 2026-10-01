@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class IntroPage(val title: String, val description: String, val icon: ImageVector)

private val introPages = listOf(
    IntroPage(
        title = "Willkommen bei CodeForgeMobile",
        description = "Ihre Smartphone IDE für Unterwegs",
        icon = Icons.Filled.Code
    ),
    IntroPage(
        title = "Code direkt auf dem Smartphone",
        description = "Vollwertiger Kotlin/Java-Editor mit LSP-Unterstützung, Syntax-Highlighting und Auto-Completion.",
        icon = Icons.Filled.Code
    ),
    IntroPage(
        title = "Echtes Linux-Terminal",
        description = "Alpine, Ubuntu oder Debian per PRoot – Shell, Gradle und Build-Tools direkt auf dem Gerät.",
        icon = Icons.Filled.Terminal
    ),
    IntroPage(
        title = "Individuell anpassbar",
        description = "Themes, Editor-Konfiguration und Plugins nach deinen Wünschen.",
        icon = Icons.Filled.SettingsSuggest
    ),
    IntroPage(
        title = "Erweiterbar per Plugin-API",
        description = "Installiere Plugins oder entwickle eigene über das offene Plugin-API-Surface.",
        icon = Icons.Filled.Extension
    )
)

@Composable
fun IntroPagerScreen(
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = currentPage) { introPages.size }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            IntroPageContent(page = introPages[page], isFirstPage = page == 0)
        }

        PagerIndicator(pagerState = pagerState, pageCount = introPages.size)

        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (pagerState.currentPage == introPages.lastIndex) {
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("Loslegen")
                }
            } else {
                TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("Überspringen")
                }
            }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage, isFirstPage: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isFirstPage) {
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = "CodeForge App Logo",
                modifier = Modifier
                    .size(140.dp)
                    .padding(bottom = 24.dp)
            )
        } else {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PagerIndicator(pagerState: PagerState, pageCount: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val selected = pagerState.currentPage == index
            val dotSize = if (selected) 10.dp else 8.dp
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(dotSize)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}
