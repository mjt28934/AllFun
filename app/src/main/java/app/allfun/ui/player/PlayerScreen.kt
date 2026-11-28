package app.allfun.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import app.allfun.ui.components.AllFunButton
import app.allfun.ui.components.AllFunButtonVariant
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
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
    val autoPlayNext = rememberUpdatedState(settings.autoPlayNext)
    val context = LocalContext.current
    val view = LocalView.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showTechnical by remember { mutableStateOf(false) }

    // Custom controls, laid out like a desktop player: tap to toggle the chrome, and the
    // middle row keeps play/pause and the seek buttons one thumb-reach away.
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val seekSeconds = settings.seekStep.seconds
    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        val end = exo.duration.takeIf { it > 0 } ?: return
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, end))
        positionMs = exo.currentPosition
    }

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
                        // Settings decides whether the next episode starts on its own; the
                        // player does not override that choice.
                        if (autoPlayNext.value) vm.nextEpisode()
                    }
                    isPlaying = exo.isPlaying
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
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

    // Position and duration refresh quickly enough for a smooth bar without waking the
    // player on every frame.
    LaunchedEffect(player) {
        val exo = player ?: return@LaunchedEffect
        while (isActive) {
            durationMs = exo.duration.coerceAtLeast(0L)
            if (!scrubbing) positionMs = exo.currentPosition.coerceAtLeast(0L)
            isPlaying = exo.isPlaying
            delay(250)
        }
    }

    // Chrome fades out while the film plays, and comes back the moment it pauses or the
    // viewer taps the picture.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
        if (!isPlaying) controlsVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setKeepScreenOn(true)
                }
            },
            update = { playerView ->
                playerView.player = player
                // Controls are drawn by Compose so they can match the rest of the app;
                // PlayerView keeps only the surface and the buffering spinner.
                playerView.useController = false
                val hasError = state.error != null || playbackError != null
                playerView.setShowBuffering(
                    if (hasError) PlayerView.SHOW_BUFFERING_NEVER
                    else PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap toggles the chrome; a double tap on either half skips like the buttons do.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(seekSeconds) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val delta = seekSeconds * 1000L
                            seekBy(if (offset.x < size.width / 2) -delta else delta)
                        }
                    )
                }
        )

        val chromeVisible = controlsVisible && state.error == null && playbackError == null

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AllFun.colors.bg.copy(alpha = 0.95f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = AllFun.colors.textPrimary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            color = AllFun.colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.subtitle,
                            color = AllFun.colors.textSecondary,
                            style = AllFunType.mono,
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
                                tint = if (state.episodeIndex < state.episodeCount - 1) {
                                    AllFun.colors.textPrimary
                                } else {
                                    AllFun.colors.textTertiary
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = AllFun.colors.hairline)
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !state.loading,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeekControl(
                    icon = Icons.Outlined.FastRewind,
                    label = "$seekSeconds s",
                    onClick = { seekBy(-seekSeconds * 1000L) }
                )
                RoundControl(
                    icon = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    filled = true,
                    size = 64.dp,
                    onClick = {
                        player?.let { exo ->
                            if (exo.isPlaying) exo.pause() else exo.play()
                        }
                    }
                )
                SeekControl(
                    icon = Icons.Outlined.FastForward,
                    label = "$seekSeconds s",
                    onClick = { seekBy(seekSeconds * 1000L) }
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AllFun.colors.bg.copy(alpha = 0.95f))
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(thickness = 1.dp, color = AllFun.colors.hairline)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val shownMs = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlaybackTime(shownMs),
                            color = AllFun.colors.textPrimary,
                            style = AllFunType.mono
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatPlaybackTime(durationMs),
                            color = AllFun.colors.textTertiary,
                            style = AllFunType.mono
                        )
                    }
                    Slider(
                        value = if (durationMs > 0) {
                            (if (scrubbing) scrubFraction else positionMs.toFloat() / durationMs)
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = {
                            scrubbing = true
                            scrubFraction = it
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) {
                                player?.seekTo((scrubFraction * durationMs).toLong())
                                positionMs = (scrubFraction * durationMs).toLong()
                            }
                            scrubbing = false
                        },
                        enabled = durationMs > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = AllFun.colors.accent,
                            activeTrackColor = AllFun.colors.accent,
                            inactiveTrackColor = AllFun.colors.hairline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        if (state.streams.size > 1) {
                            ChromeTextAction(
                                text = "Sources (${state.streams.size})",
                                icon = Icons.Outlined.FormatListBulleted,
                                onClick = vm::chooseAnotherStream
                            )
                        }
                        if (state.episodeCount > 1) {
                            ChromeTextAction(
                                text = "Next episode",
                                icon = Icons.Outlined.SkipNext,
                                enabled = state.episodeIndex < state.episodeCount - 1,
                                onClick = vm::nextEpisode
                            )
                        }
                    }
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
                CircularProgressIndicator(color = AllFun.colors.accent)
                val progress = state.torrentStatus
                if (progress != null) {
                    Spacer(Modifier.padding(top = 16.dp))
                    Text(
                        text = progress,
                        color = AllFun.colors.textPrimary,
                        style = AllFunType.mono
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = "Torrent streams start after the first pieces arrive.",
                        color = AllFun.colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall
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
                    color = AllFun.colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = sourceError?.message
                        ?: "The stream could not be played. The source may be offline, " +
                            "geo-blocked, or using a format this device cannot decode.",
                    color = AllFun.colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.padding(top = 20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChromeButton(text = "Back", primary = false, onClick = onBack)
                    ChromeButton(
                        text = "Retry",
                        primary = true,
                        onClick = {
                            playbackError = null
                            showDetails = false
                            showTechnical = false
                            vm.retry()
                        }
                    )
                }
                if (state.streams.size > 1) {
                    ChromeTextAction(
                        text = "Choose another stream",
                        onClick = {
                            playbackError = null
                            showDetails = false
                            showTechnical = false
                            vm.chooseAnotherStream()
                        }
                    )
                }
                val technical = listOfNotNull(sourceError?.technical, playbackError)
                    .joinToString("\n\n")
                    .takeIf { it.isNotBlank() }
                if (technical != null) {
                    ChromeTextAction(
                        text = if (showDetails) "Hide technical details" else "Technical details",
                        muted = true,
                        onClick = {
                            showDetails = !showDetails
                            showTechnical = showDetails
                        }
                    )
                    if (showDetails) {
                        Text(
                            text = technical,
                            color = AllFun.colors.textTertiary,
                            style = AllFunType.mono
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

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    filled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(AllFun.colors.accent)
                else Modifier.background(AllFun.colors.surface2).border(1.dp, AllFun.colors.hairline, CircleShape)
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) AllFun.colors.onAccent else AllFun.colors.textPrimary,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
private fun ChromeButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(AllFun.colors.accent)
                else Modifier.border(1.dp, AllFun.colors.hairline, shape)
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) AllFun.colors.onAccent else AllFun.colors.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun ChromeTextAction(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    muted: Boolean = false
) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (!enabled) AllFun.colors.textTertiary else if (muted) AllFun.colors.textSecondary else AllFun.colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> AllFun.colors.textTertiary
                muted -> AllFun.colors.textSecondary
                else -> AllFun.colors.accent
            },
            maxLines = 1
        )
    }
}

/**
 * A round, translucent seek button with the configured skip amount under it, so the
 * player never claims "10 seconds" when Settings is set to 30.
 */
@Composable
private fun SeekControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundControl(
            icon = icon,
            contentDescription = "Skip $label",
            filled = false,
            size = 52.dp,
            onClick = onClick
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = AllFun.colors.textTertiary,
            style = AllFunType.mono
        )
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
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
            .background(AllFun.colors.bg.copy(alpha = 0.95f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = AllFun.colors.textPrimary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Choose a stream",
                    color = AllFun.colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "${streams.size} options from your add-ons",
                    color = AllFun.colors.textSecondary,
                    style = AllFunType.mono
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(streams, key = { index, stream -> "$index:${stream.url}" }) { index, stream ->
                StreamRow(stream = stream, onClick = { onChoose(index) })
                HorizontalDivider(color = AllFun.colors.hairline)
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
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stream.name ?: if (torrent != null) "Torrent" else "Direct stream",
                color = AllFun.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (torrent != null) "Torrent" else "Direct",
                color = if (torrent != null) Color(0xFF86A8C4) else Color(0xFF8FB573),
                style = AllFunType.mono
            )
        }
        stream.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail.replace('\n', ' ').trim(),
                color = AllFun.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val meta = buildList {
            stream.addonName?.takeIf { it.isNotBlank() }?.let(::add)
            torrent?.seeds?.takeIf { it > 0 }?.let { add("$it seeds") }
        }.joinToString(" \u00B7 ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                color = AllFun.colors.textTertiary,
                style = AllFunType.mono,
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
