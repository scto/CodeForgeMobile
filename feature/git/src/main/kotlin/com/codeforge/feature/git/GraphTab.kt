// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GraphEdge
import com.codeforge.core.domain.model.GraphRow
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.text.DateFormat
import java.util.Date

private val LANE_COLORS = listOf(
    Color(0xFF4C8DFF), Color(0xFF2E9E4F), Color(0xFFE07B39), Color(0xFFB45EE5),
    Color(0xFFD64545), Color(0xFF1FB5B5), Color(0xFFD9A400), Color(0xFF8A8F98),
)
private val ROW_HEIGHT = 52.dp
private val LANE_WIDTH = 14.dp
private const val MAX_VISIBLE_LANES = 6

@Composable
internal fun GraphTab(state: GitUiState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.graph.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringRes(R.string.git_noch_keine_commits), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val lanes = state.graph.maxOf { it.laneCount }.coerceIn(1, MAX_VISIBLE_LANES)
    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    LazyColumn(modifier) {
        items(state.graph, key = { it.commit.hash }) { row ->
            Row(
                Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable { onEvent(GitUiEvent.OpenCommit(row.commit.hash)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GraphLanes(row, lanes)
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.commit.refs.take(3).forEach { RefChip(it) }
                        Text(
                            row.commit.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        "${row.commit.shortHash} · ${row.commit.authorName} · ${dateFormat.format(Date(row.commit.timestampEpochMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefChip(label: String) {
    val isHead = label.startsWith("HEAD")
    val color = when {
        isHead -> MaterialTheme.colorScheme.primary
        label.startsWith("tag:") -> Color(0xFFD9A400)
        label.contains('/') -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp).width(refChipWidth(label)),
        )
    }
}

/** Begrenzt sehr lange Ref-Namen, ohne kurze aufzublähen. */
private fun refChipWidth(label: String): Dp = (label.length * 6.5f).coerceIn(24f, 120f).dp

@Composable
private fun GraphLanes(row: GraphRow, visibleLanes: Int) {
    Canvas(Modifier.width(LANE_WIDTH * visibleLanes + 8.dp).height(ROW_HEIGHT)) {
        val laneW = LANE_WIDTH.toPx()
        val h = size.height
        val stroke = 2.dp.toPx()
        fun x(lane: Int) = laneW * (lane.coerceAtMost(MAX_VISIBLE_LANES - 1)) + laneW / 2f + 4.dp.toPx()
        fun color(lane: Int) = LANE_COLORS[lane % LANE_COLORS.size]

        row.edges.forEach { e ->
            val c = color(e.colorLane)
            when (e.kind) {
                GraphEdge.Kind.THROUGH -> drawLine(c, Offset(x(e.fromLane), 0f), Offset(x(e.toLane), h), stroke, StrokeCap.Round)
                GraphEdge.Kind.IN -> drawLine(c, Offset(x(e.fromLane), 0f), Offset(x(row.lane), h / 2f), stroke, StrokeCap.Round)
                GraphEdge.Kind.OUT -> drawLine(c, Offset(x(row.lane), h / 2f), Offset(x(e.toLane), h), stroke, StrokeCap.Round)
            }
        }
        val isMerge = row.commit.parents.size > 1
        val nodeColor = color(row.lane)
        drawCircle(nodeColor, radius = if (isMerge) 4.dp.toPx() else 5.dp.toPx(), center = Offset(x(row.lane), h / 2f))
        if (isMerge) drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(x(row.lane), h / 2f))
    }
}
