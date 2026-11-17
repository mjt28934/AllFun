package app.allfun.ui.reader

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.allfun.data.AppContainer
import app.allfun.data.net.UserError
import app.allfun.data.settings.NovelTheme
import app.allfun.ui.components.ChapterPickerDialog
import app.allfun.ui.components.rememberViewModel

@Composable
fun NovelReaderScreen(
    container: AppContainer,
    mediaId: String,
    startEpisodeId: String?,
    onBack: () -> Unit
) {
    val vm = rememberViewModel { NovelReaderViewModel(container, mediaId, startEpisodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    val fontSize = rememberUpdatedState(state.fontSize)
    val lineSpacing = rememberUpdatedState(state.lineSpacing)
    val theme = rememberUpdatedState(state.theme)
    var chrome by remember { mutableStateOf(true) }
    var showChapters by remember { mutableStateOf(false) }

    val palette = paletteFor(state.theme)

    Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(palette.background)))) {
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
                val uri = state.chapterUri
                val html = state.chapterHtml
                val chapterTag = "html:" + (state.chapter?.id ?: "")
                when {
                    uri != null && view.tag != uri -> {
                        view.tag = uri
                        view.loadUrl(uri)
                    }
                    html != null && view.tag != chapterTag -> {
                        view.tag = chapterTag
                        // Remote chapters arrive as text, so render them directly. A null
                        // base URL keeps relative links from resolving to local files.
                        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
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

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            NovelTopBar(
                title = state.title,
                chapterTitle = state.chapter?.title.orEmpty(),
                onBack = onBack,
                onOpenChapters = { showChapters = true }
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(color = Color(0xE60B0B10), contentColor = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = vm::previousChapter,
                        enabled = state.index > 0
                    ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous chapter") }
                    Text(
                        text = "Chapter ${state.index + 1} of ${state.chapters.size}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(
                        onClick = vm::nextChapter,
                        enabled = state.index < state.chapters.lastIndex
                    ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next chapter") }
                }
            }
        }

        if (state.loading && state.chapterUri == null && state.chapterHtml == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF9C8BFF)
            )
        }

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
                        onRetry = { vm.openChapter(state.index) }
                    )
                }
            }
        }
    }

    if (showChapters) {
        ChapterPickerDialog(
            chapters = state.chapters,
            selectedIndex = state.index,
            onSelect = { vm.openChapter(it) },
            onDismiss = { showChapters = false }
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
    onOpenChapters: () -> Unit
) {
    Surface(color = Color(0xE60B0B10), contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
        // The reader's own bar floats over the WebView, so the document needs top
        // padding tall enough to clear it; 132px works at this bar height on phones.
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
