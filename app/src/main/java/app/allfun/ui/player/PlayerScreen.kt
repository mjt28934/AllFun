package app.allfun.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.allfun.data.AppContainer
import app.allfun.data.model.StreamInfo
import app.allfun.data.settings.Settings
import app.allfun.data.torrent.TorrentDataSourceFactory
import app.allfun.ui.components.rememberViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    container: AppContainer,
    mediaId: String,
    startEpisodeId: String?,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { PlayerViewModel(container, mediaId, startEpisodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val resumePlayback = rememberUpdatedState(settings.resumePlayback)
    val subtitlesEnabled = rememberUpdatedState(settings.subtitlesEnabled)
    val context = LocalContext.current
    val view = LocalView.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showTechnical by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            vm.stopTorrentPlayback()
        }
    }

    DisposableEffect(state.stream) {
        val stream = state.stream
        if (stream == null) {
            onDispose { }
        } else {
            playbackError = null
            val httpDataSource = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(stream.headers)
                .setAllowCrossProtocolRedirects(true)
            // content:// (SAF imports), file:// and http(s) keep the default resolver,
            // while torrent:// reads come from the active libtorrent file. The factory
            // routes each request by scheme so subtitles and Jellyfin headers still work.
            val dataSource = TorrentDataSourceFactory(context, container.torrents, httpDataSource)
            val exo = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .build()
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = buildString {
                        append(error.errorCodeName)
                        error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        vm.saveProgress(exo.duration, exo.duration)
                    }
                }
            })
            exo.setMediaItem(mediaItemFor(stream))
            exo.prepare()
            exo.seekTo(if (resumePlayback.value) state.startPositionMs else 0L)
            exo.playWhenReady = true
            player = exo
            onDispose {
                vm.saveProgress(exo.currentPosition, exo.duration)
                exo.release()
                player = null
            }
        }
    }

    // Subtitle preference is a live track selection rather than a restart: the sidecar
    // configuration is always attached, and this disables the text track type when off.
    LaunchedEffect(player, settings.subtitlesEnabled) {
        val exo = player ?: return@LaunchedEffect
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled.value)
            .build()
    }

    LaunchedEffect(player) {
        val exo = player ?: return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            if (exo.isPlaying) vm.saveProgress(exo.currentPosition, exo.duration)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setKeepScreenOn(true)
                }
            },
            update = { playerView ->
                playerView.player = player
                // A player error keeps PlayerView's own overlay (spinner, control bar) on top
                // of our message. Hide the controls and the buffering spinner while the
                // error column is showing so nothing is drawn over it.
                val hasError = state.error != null || playbackError != null
                playerView.useController = !hasError
                playerView.setShowBuffering(
                    if (hasError) PlayerView.SHOW_BUFFERING_NEVER
                    else PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.episodeCount > 1) {
                IconButton(
                    onClick = vm::nextEpisode,
                    enabled = state.episodeIndex < state.episodeCount - 1
                ) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = "Next episode",
                        tint = Color.White
                    )
                }
            }
        }

        if (state.loading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                val progress = state.torrentStatus
                if (progress != null) {
                    Spacer(Modifier.padding(top = 16.dp))
                    Text(
                        text = progress,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = "Torrent streams start after the first pieces arrive.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        val sourceError = state.error
        if (sourceError != null || playbackError != null) {
            var showDetails by remember { mutableStateOf(showTechnical) }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cannot play this",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = sourceError?.message
                        ?: "The stream could not be played. The source may be offline, " +
                            "geo-blocked, or using a format this device cannot decode.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.padding(top = 20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBack) { Text("Back") }
                    OutlinedButton(onClick = {
                        playbackError = null
                        showDetails = false
                        showTechnical = false
                        vm.retry()
                    }) { Text("Retry") }
                }
                if (state.streams.size > 1) {
                    TextButton(onClick = {
                        playbackError = null
                        showDetails = false
                        showTechnical = false
                        vm.chooseAnotherStream()
                    }) {
                        Text("Choose another stream", color = Color.White)
                    }
                }
                val technical = listOfNotNull(sourceError?.technical, playbackError)
                    .joinToString("\n\n")
                    .takeIf { it.isNotBlank() }
                if (technical != null) {
                    TextButton(onClick = {
                        showDetails = !showDetails
                        showTechnical = showDetails
                    }) {
                        Text(
                            text = if (showDetails) "Hide technical details" else "Technical details",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (showDetails) {
                        Text(
                            text = technical,
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        if (state.showStreamPicker) {
            StreamPicker(
                streams = state.streams,
                onChoose = vm::chooseStream,
                onClose = onBack
            )
        }
    }
}

/**
 * Lists every release the installed addons returned. Torrent-only addons can answer a
 * popular film with dozens of files; the viewer needs to be able to move on when the
 * first pick has no reachable peers.
 */
@Composable
private fun StreamPicker(
    streams: List<StreamInfo>,
    onChoose: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Choose a stream",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${streams.size} options from your add-ons",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(streams, key = { index, stream -> "$index:${stream.url}" }) { index, stream ->
                StreamRow(stream = stream, onClick = { onChoose(index) })
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun StreamRow(stream: StreamInfo, onClick: () -> Unit) {
    val torrent = stream.torrent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stream.name ?: if (torrent != null) "Torrent" else "Direct stream",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (torrent != null) "Torrent" else "Direct",
                color = if (torrent != null) Color(0xFF9CC7FF) else Color(0xFF9BE7B0),
                style = MaterialTheme.typography.labelSmall
            )
        }
        stream.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = detail.replace('\n', ' ').trim(),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val meta = buildList {
            stream.addonName?.takeIf { it.isNotBlank() }?.let(::add)
            torrent?.seeds?.takeIf { it > 0 }?.let { add("$it seeds") }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = meta,
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Builds the player item, attaching a sidecar subtitle track when the addon supplied
 * one. Stremio subtitles are plain WebVTT or SubRip files; Media3 renders both.
 */
private fun mediaItemFor(stream: StreamInfo): MediaItem {
    val builder = MediaItem.Builder().setUri(stream.url)
    val subtitle = stream.subtitleUrl
    if (subtitle != null) {
        val mime = when {
            subtitle.substringBefore('?').endsWith(".srt", ignoreCase = true) ->
                MimeTypes.APPLICATION_SUBRIP
            subtitle.substringBefore('?').endsWith(".ass", ignoreCase = true) ->
                MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle))
                    .setMimeType(mime)
                    .setLanguage("und")
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
    }
    return builder.build()
}
