package app.allfun.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
        onDispose { view.keepScreenOn = false }
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
            // content:// (SAF imports) and file:// need the default resolver; http(s) goes
            // through the configured factory so Jellyfin/Stremio headers still apply.
            val dataSource = DefaultDataSource.Factory(context, httpDataSource)
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
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
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
