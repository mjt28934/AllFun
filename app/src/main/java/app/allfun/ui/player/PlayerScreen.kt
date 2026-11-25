package app.allfun.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
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
import app.allfun.ui.glass.AFDivider
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFSpinner
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassIconButton
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassSlider
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The video canvas is neutral; the chrome is the only thing that glows. */
private val PlayerBlack = Color(0xFF05060A)

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
            .background(PlayerBlack)
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
            PlayerTopBar(
                title = state.title,
                subtitle = state.subtitle,
                showNext = state.episodeCount > 1,
                nextEnabled = state.episodeIndex < state.episodeCount - 1,
                onBack = onBack,
                onNext = vm::nextEpisode
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && !state.loading,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeekControl(
                    icon = AFIcons.Back,
                    label = "$seekSeconds s",
                    onClick = { seekBy(-seekSeconds * 1000L) }
                )
                GlassSurface(
                    modifier = Modifier.size(68.dp),
                    tier = GlassTier.CLEAR,
                    shape = CircleShape,
                    selected = true,
                    shadow = true,
                    onClick = {
                        player?.let { exo ->
                            if (exo.isPlaying) exo.pause() else exo.play()
                        }
                    },
                    onClickLabel = if (isPlaying) "Pause" else "Play"
                ) {
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        AFIcon(
                            icon = if (isPlaying) AFIcons.Pause else AFIcons.Play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = AFTheme.colors.onAccent,
                            size = 30.dp
                        )
                    }
                }
                SeekControl(
                    icon = AFIcons.Forward,
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
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassTier.FROSTED,
                    shape = RoundedCornerShape(GlassTokens.rPanel),
                    shadow = true
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        val shownMs =
                            if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AFText(
                                text = formatPlaybackTime(shownMs),
                                style = AFTheme.type.numeric,
                                color = AFTheme.colors.textHi,
                                onGlass = true
                            )
                            Spacer(Modifier.weight(1f))
                            AFText(
                                text = formatPlaybackTime(durationMs),
                                style = AFTheme.type.numeric,
                                color = AFTheme.colors.textLo,
                                onGlass = true
                            )
                        }
                        GlassSlider(
                            value = if (durationMs > 0) {
                                (if (scrubbing) scrubFraction
                                else positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            valueRange = 0f..1f,
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.streams.size > 1) {
                                AFTextButton(
                                    label = "Sources (${state.streams.size})",
                                    onClick = vm::chooseAnotherStream
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (state.episodeCount > 1) {
                                AFTextButton(
                                    label = "Next episode",
                                    onClick = vm::nextEpisode,
                                    enabled = state.episodeIndex < state.episodeCount - 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.loading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassSurface(
                    tier = GlassTier.FROSTED,
                    shape = RoundedCornerShape(GlassTokens.rPanel)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AFSpinner(size = 28.dp)
                        val progress = state.torrentStatus
                        if (progress != null) {
                            Spacer(Modifier.height(12.dp))
                            AFText(
                                text = progress,
                                style = AFTheme.type.body,
                                color = AFTheme.colors.textHi,
                                onGlass = true,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            AFText(
                                text = "Torrent streams start after the first pieces arrive.",
                                style = AFTheme.type.caption,
                                color = AFTheme.colors.textLo,
                                onGlass = true,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        val sourceError = state.error
        if (sourceError != null || playbackError != null) {
            var showDetails by remember { mutableStateOf(showTechnical) }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    tier = GlassTier.FROSTED,
                    shape = RoundedCornerShape(GlassTokens.rPanel),
                    shadow = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AFText(
                            text = "Cannot play this",
                            style = AFTheme.type.title,
                            color = AFTheme.colors.textHi,
                            onGlass = true
                        )
                        Spacer(Modifier.height(8.dp))
                        AFText(
                            text = sourceError?.message
                                ?: "The stream could not be played. The source may be offline, " +
                                "geo-blocked, or using a format this device cannot decode.",
                            style = AFTheme.type.body,
                            color = AFTheme.colors.textMid,
                            onGlass = true,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        GlassPrimaryAction(
                            label = "Retry",
                            onClick = {
                                playbackError = null
                                showDetails = false
                                showTechnical = false
                                vm.retry()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            label = "Back",
                            onClick = onBack,
                            icon = AFIcons.Back,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.streams.size > 1) {
                            AFTextButton(
                                label = "Choose another stream",
                                onClick = {
                                    playbackError = null
                                    showDetails = false
                                    showTechnical = false
                                    vm.chooseAnotherStream()
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        val technical = listOfNotNull(sourceError?.technical, playbackError)
                            .joinToString("\n\n")
                            .takeIf { it.isNotBlank() }
                        if (technical != null) {
                            AFTextButton(
                                label = if (showDetails) "Hide technical details"
                                else "Technical details",
                                onClick = {
                                    showDetails = !showDetails
                                    showTechnical = showDetails
                                },
                                color = AFTheme.colors.textLo
                            )
                            if (showDetails) {
                                AFText(
                                    text = technical,
                                    style = AFTheme.type.caption,
                                    color = AFTheme.colors.textLo,
                                    onGlass = true,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    showNext: Boolean,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(52.dp),
        tier = GlassTier.CLEAR,
        shape = RoundedCornerShape(26.dp),
        shadow = true
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AFIconButton(
                icon = AFIcons.Back,
                contentDescription = "Back",
                onClick = onBack
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp)
            ) {
                AFText(
                    text = title,
                    style = AFTheme.type.heading,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    AFText(
                        text = subtitle,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showNext) {
                AFIconButton(
                    icon = AFIcons.Forward,
                    contentDescription = "Next episode",
                    onClick = onNext,
                    enabled = nextEnabled
                )
            }
        }
    }
}

/**
 * A round glass seek button with the configured skip amount under it, so the player never
 * claims "10 seconds" when Settings is set to 30.
 */
@Composable
private fun SeekControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(
            icon = icon,
            contentDescription = "Skip $label",
            onClick = onClick,
            size = 54.dp
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = label,
            style = AFTheme.type.caption,
            color = AFTheme.colors.textMid,
            onGlass = true
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AFTheme.colors.bg0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                tier = GlassTier.CLEAR,
                shape = RoundedCornerShape(26.dp),
                shadow = true
            ) {
                Row(
                    modifier = Modifier
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AFIconButton(
                        icon = AFIcons.Back,
                        contentDescription = "Back",
                        onClick = onClose
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 4.dp)
                    ) {
                        AFText(
                            text = "Choose a stream",
                            style = AFTheme.type.heading,
                            color = AFTheme.colors.textHi,
                            onGlass = true,
                            maxLines = 1
                        )
                        AFText(
                            text = "${streams.size} options from your add-ons",
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            onGlass = true,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(streams, key = { index, stream -> "$index:${stream.url}" }) { index, stream ->
                    StreamRow(stream = stream, onClick = { onChoose(index) })
                    AFDivider()
                }
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
            .clickable(onClickLabel = "Play this stream", onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFText(
                text = stream.name ?: if (torrent != null) "Torrent" else "Direct stream",
                style = AFTheme.type.body,
                color = AFTheme.colors.textHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AFText(
                text = if (torrent != null) "Torrent" else "Direct",
                style = AFTheme.type.caption,
                color = if (torrent != null) AFTheme.colors.accent else AFTheme.colors.ok
            )
        }
        stream.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            AFText(
                text = detail.replace('\n', ' ').trim(),
                style = AFTheme.type.bodySmall,
                color = AFTheme.colors.textMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        val meta = buildList {
            stream.addonName?.takeIf { it.isNotBlank() }?.let(::add)
            torrent?.seeds?.takeIf { it > 0 }?.let { add("$it seeds") }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            AFText(
                text = meta,
                style = AFTheme.type.caption,
                color = AFTheme.colors.textLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
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
