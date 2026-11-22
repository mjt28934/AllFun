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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.net.UserError
import app.allfun.data.settings.NovelTheme
import app.allfun.ui.components.ChapterPickerDialog
import app.allfun.ui.components.rememberViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                        ScrollBridge { fraction -> vm.onScroll(fraction) },
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
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) }
        )

        LaunchedEffect(state.fontSize, state.lineSpacing, state.theme) {
            webView?.let { view ->
                injectReaderTheme(view, state.fontSize, state.lineSpacing, state.theme, 0f)
            }
        }

        // Thin reading progress bar at the very bottom edge.
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
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
            Surface(color = Color(0xE60B0B10), contentColor = Color.White) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = vm::previousChapter,
                            enabled = state.index > 0
                        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous chapter") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Chapter ${state.index + 1} of ${state.chapters.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(state.fraction.coerceIn(0f, 1f) * 100).roundToInt()}% read",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                        IconButton(
                            onClick = vm::nextChapter,
                            enabled = state.index < state.chapters.lastIndex
                        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter") }
                    }
                }
            }
        }

        // The WebView shows its own "Loading chapter…" page, so no separate spinner.

        if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(24.dp)
                ) {
                    ChapterError(
                        error = state.error ?: UserError("Could not load this chapter", null),
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
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChapters: () -> Unit
) {
    Surface(color = Color(0xE60B0B10), contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Reader settings")
            }
            IconButton(onClick = onOpenChapters) {
                Icon(
                    Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "Choose chapter"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fontValue by remember(theme, fontSize) { mutableStateOf(fontSize.toFloat()) }
    var spacingValue by remember(theme, lineSpacing) { mutableStateOf(lineSpacing) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Reader settings", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()

            NovelSettingsGroup("Theme") {
                NovelTheme.entries.forEach { option ->
                    NovelChoice(
                        label = option.label,
                        selected = theme == option,
                        onClick = { onSetTheme(option) }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))

            NovelSettingsGroup("Text size") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = fontValue,
                        onValueChange = {
                            fontValue = it
                            onSetFontSize(it.roundToInt())
                        },
                        valueRange = 70f..180f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${fontValue.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))

            NovelSettingsGroup("Line spacing") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = spacingValue,
                        onValueChange = {
                            spacingValue = it
                            onSetLineSpacing(it)
                        },
                        valueRange = 1.2f..2.4f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${(spacingValue * 10).roundToInt() / 10f}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NovelSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun NovelChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChapterError(error: UserError, onBack: () -> Unit, onRetry: () -> Unit) {
    var showTechnical by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Could not load this chapter",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
        val technical = error.technical
        if (!technical.isNullOrBlank()) {
            TextButton(onClick = { showTechnical = !showTechnical }) {
                Text(if (showTechnical) "Hide technical details" else "Technical details")
            }
            if (showTechnical) {
                Text(
                    text = technical,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private class ScrollBridge(private val onScroll: (Float) -> Unit) {
    @JavascriptInterface
    fun onScroll(fraction: String) {
        onScroll(fraction.toFloatOrNull() ?: 0f)
    }
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
            window.addEventListener('scroll', function() {
              var m = document.documentElement.scrollHeight - window.innerHeight;
              var f = m > 0 ? (window.scrollY / m) : 0;
              if (window.AllFun) { window.AllFun.onScroll(String(f)); }
            }, { passive: true });
          }
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}