package app.allfun.ui.reader

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.net.UserError
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.Settings
import app.allfun.ui.components.ChapterPickerDialog
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.glass.AFIcon
import app.allfun.ui.glass.AFIconButton
import app.allfun.ui.glass.AFIcons
import app.allfun.ui.glass.AFOverline
import app.allfun.ui.glass.AFText
import app.allfun.ui.glass.AFTextButton
import app.allfun.ui.glass.AFTheme
import app.allfun.ui.glass.GlassButton
import app.allfun.ui.glass.GlassPrimaryAction
import app.allfun.ui.glass.GlassProgress
import app.allfun.ui.glass.GlassSegmented
import app.allfun.ui.glass.GlassSheet
import app.allfun.ui.glass.GlassSlider
import app.allfun.ui.glass.GlassSurface
import app.allfun.ui.glass.GlassTier
import app.allfun.ui.glass.GlassTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NovelReaderScreen(
    container: AppContainer,
    mediaId: String,
    startEpisodeId: String?,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { NovelReaderViewModel(container, mediaId, startEpisodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by container.settings.settings
        .collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val fontSize = rememberUpdatedState(state.fontSize)
    val lineSpacing = rememberUpdatedState(state.lineSpacing)
    val theme = rememberUpdatedState(state.theme)
    var chrome by rememberSaveable { mutableStateOf(true) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Reading is a foreground activity; by default the screen should not dim mid-chapter.
    val rootView = LocalView.current
    DisposableEffect(rootView, appSettings.readerKeepScreenOn) {
        val previous = rootView.keepScreenOn
        rootView.keepScreenOn = appSettings.readerKeepScreenOn
        onDispose { rootView.keepScreenOn = previous }
    }

    val palette = paletteFor(state.theme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(android.graphics.Color.parseColor(palette.background)))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.VolumeUp -> {
                        webView?.evaluateJavascript("window.scrollBy(0, -Math.round(window.innerHeight * 0.75));", null)
                        true
                    }
                    Key.VolumeDown -> {
                        webView?.evaluateJavascript("window.scrollBy(0, Math.round(window.innerHeight * 0.75));", null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.parseColor(palette.background))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.defaultTextEncodingName = "utf-8"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            // The loading page is not the chapter; only wire up the theme
                            // and scroll bridge once the chapter's own document is in.
                            if (view.tag != "html:" + (vm.state.value.chapter?.id ?: "")) return
                            injectReaderTheme(
                                view = view,
                                fontSize = fontSize.value,
                                lineSpacing = lineSpacing.value,
                                theme = theme.value,
                                restore = vm.consumeRestore()
                            )
                        }
                    }
                    addJavascriptInterface(
                        ScrollBridge(
                            scrollHandler = { fraction -> vm.onScroll(fraction) },
                            tapHandler = { scope.launch { chrome = !chrome } },
                            scrollingHandler = { scope.launch { chrome = false } }
                        ),
                        "AllFun"
                    )
                    webView = this
                }
            },
            update = { view ->
                view.setBackgroundColor(android.graphics.Color.parseColor(palette.background))
                val chapterId = state.chapter?.id.orEmpty()
                val html = state.chapterHtml
                if (chapterId.isEmpty()) return@AndroidView
                if (html != null) {
                    val tag = "html:$chapterId"
                    if (view.tag != tag) {
                        view.tag = tag
                        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                } else {
                    // Replace the chapter we are leaving straight away: leaving its text on
                    // screen under the new chapter's name reads as the wrong content, and
                    // its scroll events would report the new chapter's position.
                    val tag = "pending:$chapterId"
                    if (view.tag != tag) {
                        view.tag = tag
                        view.loadDataWithBaseURL(
                            null,
                            loadingDocument(palette),
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(state.fontSize, state.lineSpacing, state.theme) {
            webView?.let { view ->
                injectReaderTheme(view, state.fontSize, state.lineSpacing, state.theme, 0f)
            }
        }

        // Thin reading progress line at the bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = state.fraction.coerceIn(0f, 1f))
                    .background(AFTheme.colors.accent.copy(alpha = 0.7f))
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            NovelTopBar(
                title = state.title,
                chapterTitle = state.chapter?.title.orEmpty(),
                onBack = onBack,
                onOpenSettings = { showSettings = true },
                onOpenChapters = { showChapters = true }
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NovelBottomBar(
                hasPrevious = state.index > 0,
                hasNext = state.index < state.chapters.lastIndex,
                label = "Chapter ${state.index + 1} of ${state.chapters.size}",
                fraction = state.fraction.coerceIn(0f, 1f),
                onPrevious = vm::previousChapter,
                onNext = vm::nextChapter
            )
        }

        // The WebView shows its own "Loading chapter…" page, so no separate spinner.

        if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChapterError(
                    error = state.error ?: UserError("Could not load this chapter", null),
                    onBack = onBack,
                    onRetry = { vm.openChapter(state.index, state.fraction) }
                )
            }
        }
    }

    if (showChapters) {
        ChapterPickerDialog(
            chapters = state.chapters,
            selectedIndex = state.index,
            onSelect = { vm.goToChapter(it) },
            onDismiss = { showChapters = false }
        )
    }

    if (showSettings) {
        NovelReaderSettingsSheet(
            theme = state.theme,
            fontSize = state.fontSize,
            lineSpacing = state.lineSpacing,
            onSetTheme = { value -> scope.launch { container.settings.setNovelTheme(value) } },
            onSetFontSize = { value -> scope.launch { container.settings.setNovelFontSize(value) } },
            onSetLineSpacing = { value -> scope.launch { container.settings.setNovelLineSpacing(value) } },
            onDismiss = { showSettings = false }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.flush()
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun NovelTopBar(
    title: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChapters: () -> Unit
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
                if (chapterTitle.isNotBlank()) {
                    AFText(
                        text = chapterTitle,
                        style = AFTheme.type.caption,
                        color = AFTheme.colors.textLo,
                        onGlass = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AFIconButton(
                icon = AFIcons.Sliders,
                contentDescription = "Reader settings",
                onClick = onOpenSettings
            )
            AFIconButton(
                icon = AFIcons.List,
                contentDescription = "Choose chapter",
                onClick = onOpenChapters
            )
        }
    }
}

@Composable
private fun NovelBottomBar(
    hasPrevious: Boolean,
    hasNext: Boolean,
    label: String,
    fraction: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit
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
                GlassProgress(
                    progress = fraction,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AFIconButton(
                        icon = AFIcons.Back,
                        contentDescription = "Previous chapter",
                        onClick = onPrevious,
                        enabled = hasPrevious
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AFText(
                            text = label,
                            style = AFTheme.type.label,
                            color = AFTheme.colors.textHi,
                            onGlass = true
                        )
                        AFText(
                            text = "${(fraction * 100).roundToInt()}% read",
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            onGlass = true
                        )
                    }
                    AFIconButton(
                        icon = AFIcons.Forward,
                        contentDescription = "Next chapter",
                        onClick = onNext,
                        enabled = hasNext
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelReaderSettingsSheet(
    theme: NovelTheme,
    fontSize: Int,
    lineSpacing: Float,
    onSetTheme: (NovelTheme) -> Unit,
    onSetFontSize: (Int) -> Unit,
    onSetLineSpacing: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var fontValue by remember(fontSize) { mutableStateOf(fontSize.toFloat()) }
    var spacingValue by remember(lineSpacing) { mutableStateOf(lineSpacing) }
    val themes = NovelTheme.entries

    GlassSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFIcon(
                icon = AFIcons.Book,
                contentDescription = null,
                tint = AFTheme.colors.accent,
                size = 20.dp
            )
            Spacer(Modifier.width(10.dp))
            AFText(
                text = "Reader settings",
                style = AFTheme.type.title,
                color = AFTheme.colors.textHi
            )
        }
        Spacer(Modifier.height(18.dp))
        AFOverline("Theme")
        Spacer(Modifier.height(8.dp))
        GlassSegmented(
            options = themes.map { it.label },
            selectedIndex = themes.indexOf(theme).coerceAtLeast(0),
            onSelect = { onSetTheme(themes[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFOverline("Text size", Modifier.weight(1f))
            AFText(
                text = "${fontValue.roundToInt()}%",
                style = AFTheme.type.numeric,
                color = AFTheme.colors.textMid
            )
        }
        GlassSlider(
            value = fontValue,
            valueRange = 70f..180f,
            onValueChange = {
                fontValue = it
                onSetFontSize(it.roundToInt())
            },
            onValueChangeFinished = {},
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AFOverline("Line spacing", Modifier.weight(1f))
            AFText(
                text = "${(spacingValue * 10).roundToInt() / 10f}×",
                style = AFTheme.type.numeric,
                color = AFTheme.colors.textMid
            )
        }
        GlassSlider(
            value = spacingValue,
            valueRange = 1.2f..2.4f,
            onValueChange = {
                spacingValue = it
                onSetLineSpacing(it)
            },
            onValueChangeFinished = {},
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        AFText(
            text = "The chapter text itself uses the theme you pick; these bars stay glass.",
            style = AFTheme.type.caption,
            color = AFTheme.colors.textLo
        )
    }
}

@Composable
private fun ChapterError(error: UserError, onBack: () -> Unit, onRetry: () -> Unit) {
    var showTechnical by remember { mutableStateOf(false) }
    Box(Modifier.padding(24.dp)) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassTier.FROSTED,
            shape = RoundedCornerShape(GlassTokens.rPanel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AFText(
                    text = "Could not load this chapter",
                    style = AFTheme.type.title,
                    color = AFTheme.colors.textHi,
                    onGlass = true,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                AFText(
                    text = error.message,
                    style = AFTheme.type.body,
                    color = AFTheme.colors.textMid,
                    onGlass = true,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                GlassPrimaryAction(
                    label = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    label = "Back",
                    onClick = onBack,
                    icon = AFIcons.Back,
                    modifier = Modifier.fillMaxWidth()
                )
                val technical = error.technical
                if (!technical.isNullOrBlank()) {
                    AFTextButton(
                        label = if (showTechnical) "Hide technical details" else "Technical details",
                        onClick = { showTechnical = !showTechnical },
                        color = AFTheme.colors.textLo,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (showTechnical) {
                        AFText(
                            text = technical,
                            style = AFTheme.type.caption,
                            color = AFTheme.colors.textLo,
                            onGlass = true
                        )
                    }
                }
            }
        }
    }
}

private class ScrollBridge(
    private val scrollHandler: (Float) -> Unit,
    private val tapHandler: () -> Unit,
    private val scrollingHandler: () -> Unit
) {
    @JavascriptInterface
    fun onScroll(fraction: String) {
        scrollHandler(fraction.toFloatOrNull() ?: 0f)
    }

    @JavascriptInterface
    fun onTap() = tapHandler()

    @JavascriptInterface
    fun onScrolling() = scrollingHandler()
}

private data class ReaderPalette(
    val background: String,
    val text: String,
    val heading: String,
    val link: String,
    val quote: String
)

private fun loadingDocument(palette: ReaderPalette): String = """
    <!doctype html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      html,body{background:${palette.background};color:${palette.text};margin:0;height:100%;}
      body{display:flex;align-items:center;justify-content:center;font-family:sans-serif;
        font-size:15px;opacity:.55;}
    </style></head>
    <body>Loading chapter…</body></html>
""".trimIndent()

private fun paletteFor(theme: NovelTheme): ReaderPalette = when (theme) {
    NovelTheme.DARK -> ReaderPalette(
        background = "#0B0B10",
        text = "#E9E7F3",
        heading = "#FFFFFF",
        link = "#9C8BFF",
        quote = "#BDBBCB"
    )
    NovelTheme.SEPIA -> ReaderPalette(
        background = "#F4ECD8",
        text = "#4A3728",
        heading = "#2B1D12",
        link = "#8A5A2B",
        quote = "#6B5844"
    )
    NovelTheme.LIGHT -> ReaderPalette(
        background = "#FDFCFF",
        text = "#23222B",
        heading = "#000000",
        link = "#5B3FE0",
        quote = "#4C4A59"
    )
}

private fun injectReaderTheme(
    view: WebView,
    fontSize: Int,
    lineSpacing: Float,
    theme: NovelTheme,
    restore: Float
) {
    val palette = paletteFor(theme)
    val css = "html,body{background:${palette.background} !important;" +
        "color:${palette.text} !important;}" +
        "body{font-size:${fontSize}% !important;line-height:${lineSpacing} !important;" +
        // The reader's own bars float over the WebView, so the document needs top and
        // bottom padding tall enough to clear them; 132px/96px work at this bar height.
        "padding:132px 20px 96px !important;max-width:none !important;margin:0 !important;}" +
        "p{margin:0 0 1.2em !important;color:${palette.text} !important;}" +
        "h1,h2,h3,h4{color:${palette.heading} !important;line-height:1.3 !important;}" +
        "a{color:${palette.link} !important;}" +
        "img{max-width:100% !important;height:auto !important;}" +
        "blockquote{border-left:3px solid ${palette.link} !important;margin-left:0 !important;" +
        "padding-left:14px !important;color:${palette.quote} !important;}"

    val js = """
        (function() {
          var style = document.getElementById('allfun-reader-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'allfun-reader-style';
            (document.head || document.documentElement).appendChild(style);
          }
          style.innerHTML = '$css';
          var max = document.documentElement.scrollHeight - window.innerHeight;
          if (max > 0 && $restore > 0) { window.scrollTo(0, max * $restore); }
          if (!window.allfunHooked) {
            window.allfunHooked = true;
            var scrollTimer = null;
            var lastScroll = 0;
            window.addEventListener('scroll', function() {
              var m = document.documentElement.scrollHeight - window.innerHeight;
              var f = m > 0 ? (window.scrollY / m) : 0;
              if (window.AllFun) { window.AllFun.onScroll(String(f)); }
              lastScroll = Date.now();
              // Only a real finger scroll hides the bars; the restore scroll above and
              // chapter loads must not make the chrome flash away on open.
              if (window.allfunUserTouched && !window.allfunScrolling) {
                window.allfunScrolling = true;
                if (window.AllFun) { window.AllFun.onScrolling(); }
              }
              clearTimeout(scrollTimer);
              scrollTimer = setTimeout(function(){ window.allfunScrolling = false; }, 450);
            }, { passive: true });

            var sx = 0, sy = 0, st = 0, moved = false;
            window.addEventListener('touchstart', function(e) {
              window.allfunUserTouched = true;
              if (e.touches.length !== 1) { st = 0; return; }
              var t = e.touches[0];
              sx = t.clientX; sy = t.clientY; st = Date.now(); moved = false;
            }, { passive: true });
            window.addEventListener('touchmove', function(e) {
              if (e.touches.length !== 1) return;
              var t = e.touches[0];
              if (Math.abs(t.clientX - sx) > 12 || Math.abs(t.clientY - sy) > 12) {
                moved = true;
              }
            }, { passive: true });
            window.addEventListener('touchend', function(e) {
              var wasTap = st > 0 && !moved && Date.now() - st < 350 &&
                Date.now() - lastScroll > 300;
              st = 0;
              if (!wasTap) return;
              var target = e.target;
              if (target && target.closest && target.closest('a')) return;
              if (window.AllFun) { window.AllFun.onTap(); }
            }, { passive: true });
          }
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}
