/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Hilfe: zeigt die Schaltflächenleisten der App grafisch und beschreibt jede Schaltfläche.
 */
package com.codeforge.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

/** Eine Schaltfläche: Symbol, Name (wie im Tooltip) und Beschreibung. */
@Immutable
data class HelpEntry(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
)

/** Eine Leiste der App mit ihren Schaltflächen. */
@Immutable
data class HelpSection(
    @StringRes val titleRes: Int,
    val entries: List<HelpEntry>,
)

object HelpCatalog {
    val sections: List<HelpSection> = listOf(
        HelpSection(
            titleRes = R.string.help_s_header,
            entries = listOf(
                HelpEntry(Icons.Filled.Menu, R.string.app_menue_oeffnen, R.string.help_d_menu),
                HelpEntry(Icons.Filled.Transform, R.string.x2c_menu_convert_layouts, R.string.help_d_x2c),
                HelpEntry(Icons.Filled.Description, R.string.x2c_result_title, R.string.help_d_x2cres),
                HelpEntry(Icons.Filled.BugReport, R.string.debug_title, R.string.help_d_debugbar),
                HelpEntry(Icons.Filled.Build, R.string.build_bauen, R.string.help_d_build),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_edit,
            entries = listOf(
                HelpEntry(Icons.AutoMirrored.Filled.Undo, R.string.editor_rueckgaengig, R.string.help_d_undo),
                HelpEntry(Icons.AutoMirrored.Filled.Redo, R.string.editor_wiederholen, R.string.help_d_redo),
                HelpEntry(Icons.Filled.SelectAll, R.string.editor_alles_auswaehlen, R.string.help_d_selectall),
                HelpEntry(Icons.Filled.ContentCut, R.string.editor_ausschneiden, R.string.help_d_cut),
                HelpEntry(Icons.Filled.ContentCopy, R.string.editor_kopieren, R.string.help_d_copy),
                HelpEntry(Icons.Filled.ContentPaste, R.string.editor_einfuegen, R.string.help_d_paste),
                HelpEntry(Icons.Filled.Save, R.string.common_speichern, R.string.help_d_save),
                HelpEntry(Icons.Filled.AutoFixHigh, R.string.editor_format_document, R.string.help_d_format),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_code,
            entries = listOf(
                HelpEntry(Icons.AutoMirrored.Filled.Comment, R.string.editor_kommentar_umschalten, R.string.help_d_comment),
                HelpEntry(Icons.AutoMirrored.Filled.FormatIndentIncrease, R.string.editor_einruecken, R.string.help_d_indent),
                HelpEntry(Icons.AutoMirrored.Filled.FormatIndentDecrease, R.string.editor_ausruecken, R.string.help_d_outdent),
                HelpEntry(Icons.Filled.ControlPointDuplicate, R.string.editor_zeile_duplizieren, R.string.help_d_dup),
                HelpEntry(Icons.Filled.DeleteSweep, R.string.editor_zeile_loeschen, R.string.help_d_delline),
                HelpEntry(Icons.Filled.KeyboardArrowUp, R.string.editor_zeile_nach_oben, R.string.help_d_up),
                HelpEntry(Icons.Filled.KeyboardArrowDown, R.string.editor_zeile_nach_unten, R.string.help_d_down),
                HelpEntry(Icons.Filled.Numbers, R.string.editor_gehe_zu_zeile, R.string.help_d_goto),
                HelpEntry(Icons.Filled.Search, R.string.editor_suchen_ersetzen, R.string.help_d_search),
                HelpEntry(Icons.Filled.Share, R.string.editor_teilen, R.string.help_d_share),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_lsp,
            entries = listOf(
                HelpEntry(Icons.Filled.AccountTree, R.string.lsp_outline, R.string.help_d_outline),
                HelpEntry(Icons.Filled.Lightbulb, R.string.lsp_quick_fix, R.string.help_d_quickfix),
                HelpEntry(Icons.Filled.DriveFileRenameOutline, R.string.lsp_rename, R.string.help_d_rename),
                HelpEntry(Icons.AutoMirrored.Filled.OpenInNew, R.string.lsp_definition, R.string.help_d_definition),
                HelpEntry(Icons.Filled.FindInPage, R.string.lsp_references, R.string.help_d_references),
                HelpEntry(Icons.Filled.Info, R.string.lsp_more_actions, R.string.help_d_more),
                HelpEntry(Icons.Filled.Extension, R.string.editor_plugin_befehle, R.string.help_d_plugincmds),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_ai,
            entries = listOf(
                HelpEntry(Icons.Filled.AutoAwesome, R.string.ai_assistant_title, R.string.help_d_assistant),
                HelpEntry(Icons.Filled.Psychology, R.string.ai_quick_actions, R.string.help_d_aiquick),
                HelpEntry(Icons.Filled.Adjust, R.string.debug_tab_breakpoints, R.string.help_d_breakpoints),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_debug,
            entries = listOf(
                HelpEntry(Icons.Filled.PhoneAndroid, R.string.debug_action_attach_android, R.string.help_d_attach),
                HelpEntry(Icons.Filled.Tune, R.string.debug_action_configure_target, R.string.help_d_target),
                HelpEntry(Icons.Filled.PlayArrow, R.string.debug_action_start, R.string.help_d_start),
                HelpEntry(Icons.Filled.PlayArrow, R.string.debug_action_resume, R.string.help_d_resume),
                HelpEntry(Icons.Filled.Pause, R.string.debug_action_pause, R.string.help_d_pause),
                HelpEntry(Icons.Filled.SkipNext, R.string.debug_action_step_over, R.string.help_d_stepover),
                HelpEntry(Icons.Filled.South, R.string.debug_action_step_into, R.string.help_d_stepinto),
                HelpEntry(Icons.Filled.North, R.string.debug_action_step_out, R.string.help_d_stepout),
                HelpEntry(Icons.Filled.Stop, R.string.debug_action_stop, R.string.help_d_stop),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_git,
            entries = listOf(
                HelpEntry(Icons.Filled.Sync, R.string.git_fetch, R.string.help_d_fetch),
                HelpEntry(Icons.Filled.CloudDownload, R.string.git_pull, R.string.help_d_pull),
                HelpEntry(Icons.Filled.CloudUpload, R.string.git_push, R.string.help_d_push),
                HelpEntry(Icons.Filled.Refresh, R.string.common_aktualisieren, R.string.help_d_gitrefresh),
                HelpEntry(Icons.Filled.Settings, R.string.git_einstellungen, R.string.help_d_gitsettings),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_terminal,
            entries = listOf(
                HelpEntry(Icons.AutoMirrored.Filled.OpenInNew, R.string.terminal_termux_ui_oeffnen, R.string.help_d_termuxui),
                HelpEntry(Icons.Filled.Stop, R.string.terminal_sitzung_beenden, R.string.help_d_termstop),
                HelpEntry(Icons.Filled.PlayArrow, R.string.terminal_shell_starten, R.string.help_d_termstart),
                HelpEntry(Icons.Filled.ClearAll, R.string.terminal_ausgabe_loeschen, R.string.help_d_termclear),
                HelpEntry(Icons.AutoMirrored.Filled.Send, R.string.terminal_senden, R.string.help_d_termsend),
            )
        ),
        HelpSection(
            titleRes = R.string.help_s_tree,
            entries = listOf(
                HelpEntry(Icons.Filled.NoteAdd, R.string.filetree_neue_datei, R.string.help_d_newfile),
                HelpEntry(Icons.Filled.CreateNewFolder, R.string.filetree_neuer_ordner, R.string.help_d_newfolder),
                HelpEntry(Icons.Filled.Refresh, R.string.common_aktualisieren, R.string.help_d_treerefresh),
            )
        ),
    )
}

@Composable
fun HelpRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    HelpScreen(modifier = modifier, sections = HelpCatalog.sections, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    sections: List<HelpSection>,
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.help_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringRes(R.string.help_intro), style = MaterialTheme.typography.bodyMedium)
            }
            items(sections) { section ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringRes(section.titleRes), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Grafische Leiste: so sieht sie in der App aus
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FlowRow(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                section.entries.forEach { entry ->
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = stringRes(entry.labelRes),
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        section.entries.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(stringRes(entry.labelRes), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringRes(entry.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
