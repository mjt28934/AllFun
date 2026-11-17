package app.allfun.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.db.EpisodeEntity
import app.allfun.data.db.MediaEntity
import app.allfun.data.model.MediaKind
import app.allfun.ui.components.CoverImage
import app.allfun.ui.components.SectionHeader
import app.allfun.ui.components.accentColor
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.countLabel
import app.allfun.ui.formatDuration
import app.allfun.ui.formatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    mediaId: String,
    onBack: () -> Unit,
    onOpen: (mediaId: String, episodeId: String?, kind: MediaKind) -> Unit
) {
    val vm = rememberViewModel { DetailViewModel(mediaId, container) }
    val media by vm.media.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = media?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val saved = media?.inLibrary == true
                    val favorite = media?.favorite == true
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (favorite) {
                                "Remove from favorites"
                            } else {
                                "Add to favorites"
                            },
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { vm.toggleLibrary() }) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (saved) "Remove from library" else "Add to library",
                            tint = if (saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        val item = media
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val resume = episodes.firstOrNull { it.id == progress?.episodeId }
            ?: episodes.firstOrNull()
        val isCurrent = progress?.episodeId == resume?.id

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { DetailHeader(item) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onOpen(mediaId, resume?.id, item.kind) },
                        enabled = resume != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (item.kind.isVideo) Icons.Filled.PlayArrow
                            else Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isCurrent -> "Continue"
                                item.kind.isVideo -> "Play"
                                else -> "Read"
                            }
                        )
                    }
                    OutlinedButton(onClick = { vm.toggleLibrary() }) {
                        Icon(
                            imageVector = if (item.inLibrary) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.inLibrary) "Saved" else "Save")
                    }
                }
            }

            if (!item.description.isNullOrBlank()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = item.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        if ((item.description?.length ?: 0) > 260) {
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Less" else "More")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            item {
                SectionHeader(
                    title = if (item.kind == MediaKind.SERIES) "Episodes" else "${item.unitLabel}s",
                    actionLabel = "${episodes.size}",
                    onAction = null
                )
            }

            if (episodes.isEmpty()) {
                item {
                    Text(
                        text = "No ${item.unitLabel.lowercase()}s yet. Pull a refresh from the source by reopening this page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else if (item.kind == MediaKind.SERIES) {
                episodes.groupBy { it.season }.forEach { (season, list) ->
                    if (season > 0) {
                        item("season-$season") {
                            Text(
                                text = "Season $season",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(list, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            kind = item.kind,
                            position = progress?.takeIf { it.episodeId == episode.id }?.position,
                            total = progress?.takeIf { it.episodeId == episode.id }?.total,
                            onClick = { onOpen(mediaId, episode.id, item.kind) }
                        )
                    }
                }
            } else {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        kind = item.kind,
                        position = progress?.takeIf { it.episodeId == episode.id }?.position,
                        total = progress?.takeIf { it.episodeId == episode.id }?.total,
                        onClick = { onOpen(mediaId, episode.id, item.kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(media: MediaEntity) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            media.kind.accentColor().copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(126.dp)
                    .height(189.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CoverImage(
                    url = media.coverUrl,
                    title = media.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val meta = listOfNotNull(
                    media.author,
                    media.year?.toString(),
                    media.status
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(media.kind.accentColor().copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = media.kind.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = media.kind.accentColor()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = countLabel(media.totalUnits, media.unitLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (media.genreList.isNotEmpty()) {
                    Text(
                        text = media.genreList.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    kind: MediaKind,
    position: Long?,
    total: Long?,
    onClick: () -> Unit
) {
    val fraction = if (total != null && total > 0L && position != null) {
        (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (fraction > 0f) kind.accentColor().copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (kind.isVideo && episode.number <= 0.0) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (fraction > 0f) kind.accentColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = formatNumber(episode.number),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fraction > 0f) kind.accentColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                kind.isVideo && episode.durationMs != null -> formatDuration(episode.durationMs)
                episode.pageCount != null -> "${episode.pageCount} pages"
                else -> ""
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (fraction > 0f) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = "Continue",
                tint = kind.accentColor()
            )
        }
    }
}
