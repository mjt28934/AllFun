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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.net.UserError
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.Settings
import app.allfun.ui.components.AllFunChip
import app.allfun.ui.components.AllFunSheet
import app.allfun.ui.components.ChapterPickerDialog
import app.allfun.ui.components.Overline
import app.allfun.ui.components.rememberViewModel
import app.allfun.ui.theme.AllFun
import app.allfun.ui.theme.AllFunType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Chrome derived from the reader palette, so the bars belong to the page they frame. */
private data class NovelChrome(
    val bg: Color,
    val text: Color,
    val secondary: Color,
    val hairline: Color,
    val accent: Color
)

private fun novelChrome(theme: NovelTheme): NovelChrome {
    val palette = paletteFor(theme)
    val text = Color(android.graphics.Color.parseColor(palette.text))
    val background = Color(android.graphics.Color.parseColor(palette.background))
    return NovelChrome(
        bg = background.copy(alpha = 0.96f),
        text = text,
        secondary = text.copy(alpha = 0.68f),
        hairline = text.copy(alpha = 0.14f),
        accent = Color(android.graphics.Color.parseColor(palette.link))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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

        // Thin reading progress bar at the very bottom edge.
        val chromeColors = novelChrome(state.theme)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(chromeColors.hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = state.fraction.coerceIn(0f, 1f))
                    .background(chromeColors.accent)
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
                theme = state.theme,
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
            Surface(color = chromeColors.bg, contentColor = chromeColors.text) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = chromeColors.hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = vm::previousChapter,
                            enabled = state.index > 0
                        ) {
                            Icon(
                                Icons.Outlined.ChevronLeft,
                                contentDescription = "Previous chapter",
                                tint = if (state.index > 0) chromeColors.text
                                else chromeColors.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Chapter ${state.index + 1} of ${state.chapters.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = chromeColors.text
                            )
                            Text(
                                text = "${(state.fraction.coerceIn(0f, 1f) * 100).roundToInt()}% read",
                                style = MaterialTheme.typography.labelSmall,
                                color = chromeColors.secondary
                            )
                        }
                        IconButton(
                            onClick = vm::nextChapter,
                            enabled = state.index < state.chapters.lastIndex
                        ) {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "Next chapter",
                                tint = if (state.index < state.chapters.lastIndex) chromeColors.text
                                else chromeColors.secondary
                            )
                        }
                    }
                }
            }
        }

        // The WebView shows its own "Loading chapter…" page, so no separate spinner.

        if (state.error != null) {
            val errorChrome = novelChrome(state.theme)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(errorChrome.bg)
                        .border(
                            1.dp,
                            errorChrome.hairline,
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(24.dp)
                ) {
                    ChapterError(
                        error = state.error ?: UserError("Could not load this chapter", null),
                        chrome = errorChrome,
                        onBack = onBack,
                        onRetry = { vm.openChapter(state.index, state.fraction) }
                    )
                }
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
    theme: NovelTheme,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChapters: () -> Unit
) {
    val chromeColors = novelChrome(theme)
    Surface(color = chromeColors.bg, contentColor = chromeColors.text) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = chromeColors.text
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = chromeColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chapterTitle.isNotBlank()) {
                        Text(
                            text = chapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = chromeColors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Reader settings",
                        tint = chromeColors.text
                    )
                }
                IconButton(onClick = onOpenChapters) {
                    Icon(
                        Icons.AutoMirrored.Outlined.FormatListBulleted,
                        contentDescription = "Choose chapter",
                        tint = chromeColors.text
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = chromeColors.hairline)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    var fontValue by remember(theme, fontSize) { mutableStateOf(fontSize.toFloat()) }
    var spacingValue by remember(theme, lineSpacing) { mutableStateOf(lineSpacing) }

    AllFunSheet(title = "Reader settings", onDismiss = onDismiss) {
        Overline("Theme")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        ) {
            NovelTheme.entries.forEach { option ->
                AllFunChip(
                    label = option.label,
                    selected = theme == option,
                    onClick = { onSetTheme(option) }
                )
            }
        }
        Overline("Text size")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = fontValue,
                onValueChange = {
                    fontValue = it
                    onSetFontSize(it.roundToInt())
                },
                valueRange = 70f..180f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = AllFun.colors.accent,
                    activeTrackColor = AllFun.colors.accent,
                    inactiveTrackColor = AllFun.colors.hairline
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${fontValue.roundToInt()}%",
                style = AllFunType.mono,
                color = AllFun.colors.textSecondary,
                modifier = Modifier.width(48.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Overline("Line spacing")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = spacingValue,
                onValueChange = {
                    spacingValue = it
                    onSetLineSpacing(it)
                },
                valueRange = 1.2f..2.4f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = AllFun.colors.accent,
                    activeTrackColor = AllFun.colors.accent,
                    inactiveTrackColor = AllFun.colors.hairline
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${(spacingValue * 10).roundToInt() / 10f}\u00D7",
                style = AllFunType.mono,
                color = AllFun.colors.textSecondary,
                modifier = Modifier.width(48.dp)
            )
        }
    }
}

@Composable
private fun ChapterError(
    error: UserError,
    chrome: NovelChrome,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    var showTechnical by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Could not load this chapter",
            style = MaterialTheme.typography.headlineSmall,
            color = chrome.text
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyLarge,
            color = chrome.secondary
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NovelReaderButton(text = "Back", chrome = chrome, primary = false, onClick = onBack)
            NovelReaderButton(text = "Retry", chrome = chrome, primary = true, onClick = onRetry)
        }
        val technical = error.technical
        if (!technical.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { showTechnical = !showTechnical }
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showTechnical) "Hide technical details" else "Technical details",
                    style = MaterialTheme.typography.labelLarge,
                    color = chrome.secondary
                )
            }
            if (showTechnical) {
                Text(
                    text = technical,
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome.secondary
                )
            }
        }
    }
}

@Composable
private fun NovelReaderButton(
    text: String,
    chrome: NovelChrome,
    primary: Boolean,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(chrome.accent)
                else Modifier.border(1.dp, chrome.hairline, shape)
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) chrome.bg else chrome.text,
            maxLines = 1
        )
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