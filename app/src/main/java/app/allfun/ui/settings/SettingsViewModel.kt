package app.allfun.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.BuildConfig
import app.allfun.data.AppContainer
import app.allfun.data.backup.BackupFormatException
import app.allfun.data.extension.ExtensionRepository
import app.allfun.data.net.toUserError
import app.allfun.data.settings.HomeSection
import app.allfun.data.settings.LibrarySort
import app.allfun.data.settings.LibraryView
import app.allfun.data.settings.MangaFit
import app.allfun.data.settings.NovelTheme
import app.allfun.data.settings.ReaderMode
import app.allfun.data.settings.ReadingDirection
import app.allfun.data.settings.RepositoryKind
import app.allfun.data.settings.Settings
import app.allfun.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings actions are split into three kinds: preference writes (instant), local
 * maintenance (storage stats/clear) and network calls (repository test, backup file I/O).
 * The last two run off the main thread and report through [messages] so nothing silently
 * fails.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class StorageStats(
        val cacheBytes: Long = 0L,
        val extensionBytes: Long = 0L,
        val databaseBytes: Long = 0L
    ) {
        val totalBytes: Long get() = cacheBytes + extensionBytes + databaseBytes
    }

    val settings = container.settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings()
    )

    private val _storage = MutableStateFlow<StorageStats?>(null)
    val storage = _storage.asStateFlow()

    /** Non-null while a slow action runs; the screen shows it and disables repeat taps. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refreshStorage()
    }

    // ---- Appearance -------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = launchSettings { setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launchSettings { setDynamicColor(enabled) }

    fun setReduceMotion(enabled: Boolean) = launchSettings { setReduceMotion(enabled) }

    // ---- Reading ----------------------------------------------------------------------

    fun setReaderMode(mode: ReaderMode) = launchSettings { setReaderMode(mode) }

    fun setFontSize(size: Int) = launchSettings { setNovelFontSize(size) }

    fun setLineSpacing(value: Float) = launchSettings { setNovelLineSpacing(value) }

    fun setNovelTheme(theme: NovelTheme) = launchSettings { setNovelTheme(theme) }

    fun setMangaFit(fit: MangaFit) = launchSettings { setMangaFit(fit) }

    fun setReadingDirection(direction: ReadingDirection) =
        launchSettings { setReadingDirection(direction) }

    // ---- Library ----------------------------------------------------------------------

    fun setLibraryView(view: LibraryView) = launchSettings { setLibraryView(view) }

    fun setLibrarySort(sort: LibrarySort) = launchSettings { setLibrarySort(sort) }

    /**
     * Turns one home shelf on or off. The stored list drives Home directly, so toggling
     * here is visible immediately and survives a restart.
     */
    fun setHomeSection(section: HomeSection, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.settings.current().homeSections
            val next = if (enabled) {
                (current + section).distinct()
            } else {
                current - section
            }
            container.settings.setHomeSections(next)
        }
    }

    // ---- Playback ---------------------------------------------------------------------

    fun setResumePlayback(enabled: Boolean) = launchSettings { setResumePlayback(enabled) }

    fun setSubtitlesEnabled(enabled: Boolean) = launchSettings { setSubtitlesEnabled(enabled) }

    // ---- Storage ----------------------------------------------------------------------

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                StorageStats(
                    cacheBytes = sizeOf(container.cacheDir),
                    extensionBytes = sizeOf(File(container.filesDir, "extensions")),
                    databaseBytes = sizeOf(File(container.filesDir, "databases"))
                )
            }
        }
    }

    fun clearCache() {
        if (_busy.value != null) return
        _busy.value = "Clearing cache"
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val before = sizeOf(container.cacheDir)
                container.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                before - sizeOf(container.cacheDir)
            }
            refreshStorage()
            _messages.tryEmit(
                if (freed > 0L) {
                    "Cleared ${formatBytes(freed)} of temporary files. Installed extensions " +
                        "and your library were not touched."
                } else {
                    "There was no cached data to clear."
                }
            )
            _busy.value = null
        }
    }

    // ---- Network ----------------------------------------------------------------------

    fun testConnection() {
        if (_busy.value != null) return
        _busy.value = "Testing connection"
        viewModelScope.launch {
            val repo = ExtensionRepository.BUILT_IN.first { it.kind == RepositoryKind.MANGA }
            val started = System.currentTimeMillis()
            runCatching { container.extensionRepo.load(repo.kind, repo.url) }
                .onSuccess { contents ->
                    val elapsed = System.currentTimeMillis() - started
                    _messages.emit(
                        "Connection works. Keiyoushi listed ${contents.extensions.size} " +
                            "extensions in $elapsed ms."
                    )
                }
                .onFailure { error ->
                    val message = if (error is IllegalArgumentException) {
                        error.message ?: "That repository URL is not valid."
                    } else {
                        error.toUserError("the extension repository").message
                    }
                    _messages.emit("Repository test failed. $message")
                }
            _busy.value = null
        }
    }

    // ---- Backup -----------------------------------------------------------------------

    fun exportBackup(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = "Writing backup"
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = container.backups.export(BuildConfig.VERSION_NAME)
                    val resolver = container.appContext.contentResolver
                    val stream = resolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("That location could not be opened for writing.")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    text.length
                }
            }.onSuccess {
                _messages.emit(
                    "Backup written. It contains settings, repository URLs, your library, " +
                        "chapters/episodes and progress."
                )
            }.onFailure { error ->
                _messages.emit(
                    "Could not write the backup. " +
                        (error.message ?: "The selected location is not writable.")
                )
            }
            _busy.value = null
        }
    }

    fun importBackup(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = "Reading backup"
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = container.appContext.contentResolver
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("That file could not be opened.")
                    if (text.length > MAX_BACKUP_CHARS) {
                        throw BackupFormatException(
                            "That file is too large to be an AllFun backup."
                        )
                    }
                    container.backups.import(text)
                }
            }
            result.onSuccess { summary ->
                refreshStorage()
                _messages.emit("${summary.describe()} Extensions and account credentials are " +
                    "not part of a backup, so reinstall them from Extensions.")
            }.onFailure { error ->
                val message = when (error) {
                    is BackupFormatException -> error.message
                    else -> error.toUserError("that backup").message
                }
                _messages.emit("Could not restore this backup. $message")
            }
            _busy.value = null
        }
    }

    private fun launchSettings(write: suspend app.allfun.data.settings.SettingsStore.() -> Unit) {
        viewModelScope.launch { container.settings.run { write() } }
    }

    private fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    companion object {
        /** ~32 MB of JSON is far beyond a real library export; refuse instead of OOMing. */
        private const val MAX_BACKUP_CHARS = 32 * 1024 * 1024

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(java.util.Locale.US, "%.0f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
            return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        }
    }
}
