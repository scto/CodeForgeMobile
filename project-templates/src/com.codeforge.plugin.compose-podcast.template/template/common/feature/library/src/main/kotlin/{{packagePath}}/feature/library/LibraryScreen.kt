package {{packageName}}.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.EmptyState
import {{packageName}}.core.designsystem.component.GradientCover
import {{packageName}}.core.designsystem.component.GradientHeader
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun LibraryRoute(onOpenPodcast: (Long) -> Unit, modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(state, onOpenPodcast, modifier)
}

/** Bibliothek: abonnierte Podcasts als Cover-Raster. */
@Composable
fun LibraryScreen(state: LibraryUiState, onOpenPodcast: (Long) -> Unit, modifier: Modifier = Modifier) {
    val spacing = AppTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GradientHeader(
                title = stringResource(R.string.library_title),
                subtitle = pluralStringResource(R.plurals.library_count, state.podcasts.size, state.podcasts.size)
            )
        }
        if (state.podcasts.isEmpty() && !state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(AppIcons.Headphones, stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_text))
            }
        }
        items(state.podcasts, key = { it.id }) { podcast ->
            Column(
                Modifier
                    .padding(horizontal = spacing.l)
                    .clickable { onOpenPodcast(podcast.id) }
            ) {
                GradientCover(seed = podcast.seed, label = podcast.title, size = 150.dp, modifier = Modifier.fillMaxWidth())
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.s)
                )
                Text(
                    podcast.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Preview
@Composable
private fun LibraryScreenPreview() {
    AppTheme {
        LibraryScreen(
            state = LibraryUiState(
                listOf(
                    Podcast(1, "Compose Kitchen", "Lena", Category.TECH, "", 0),
                    Podcast(2, "Sternenstaub", "Jonas", Category.SCIENCE, "", 4)
                ),
                false
            ),
            onOpenPodcast = {}
        )
    }
}
