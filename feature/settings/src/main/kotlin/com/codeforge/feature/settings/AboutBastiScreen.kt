/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * „Über Basti“: Widmung des Projekts an Thomas' Golden Retriever. Große animierte Pixelfigur (heute oder als Welpe),
 * beim Antippen ein „Wuff!“, dazu der Widmungstext.
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeforge.core.designsystem.BastiSprite
import com.codeforge.core.designsystem.BastiVariant
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.launch

/** Route „Über Basti“ (rein darstellend, ohne Zustand aus dem ViewModel). */
@Composable
fun AboutBastiRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    AboutBastiScreen(modifier = modifier, onBack = onBack)
}

/**
 * Bildschirm „Über Basti“ mit Figur, Umschalter Heute/Welpe und Widmung.
 *
 * Die Auswahl der Fassung ist reiner Darstellungszustand und übersteht Drehen des Geräts.
 *
 * @param modifier Modifier.
 * @param onBack Zurück-Aktion.
 * @param initialVariant Fassung, mit der der Bildschirm startet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBastiScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    initialVariant: BastiVariant = BastiVariant.ADULT,
) {
    var variant by rememberSaveable { mutableStateOf(initialVariant) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val woof = stringRes(R.string.basti_woof)
    val variants = listOf(
        BastiVariant.ADULT to R.string.basti_variant_adult,
        BastiVariant.PUPPY to R.string.basti_variant_puppy,
    )

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.basti_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BastiSprite(
                        modifier = Modifier.clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(woof)
                            }
                        },
                        variant = variant,
                        size = 192.dp,
                        contentDescription = stringRes(R.string.basti_sprite_cd),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringRes(R.string.basti_name),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringRes(R.string.basti_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow {
                        variants.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = variant == value,
                                onClick = { variant = value },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = variants.size),
                            ) { Text(stringRes(label)) }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringRes(R.string.basti_dedication_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringRes(R.string.basti_dedication_text), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringRes(R.string.basti_signature),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
