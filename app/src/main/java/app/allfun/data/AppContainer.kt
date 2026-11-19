package app.allfun.data

import android.content.Context
import app.allfun.BuildConfig
import app.allfun.data.backup.BackupManager
import app.allfun.data.content.ContentRepository
import app.allfun.data.db.AppDatabase
import app.allfun.data.download.DownloadManager
import app.allfun.data.extension.ExtensionManager
import app.allfun.data.extension.ExtensionRepository
import app.allfun.data.extension.NovelPluginHost
import app.allfun.data.net.Http
import app.allfun.data.repo.LibraryRepository
import app.allfun.data.repo.ProgressRepository
import app.allfun.data.repo.SourceRepository
import app.allfun.data.settings.SettingsStore
import app.allfun.data.source.SourceHub
import app.allfun.data.source.SourceIds
import app.allfun.data.update.ApkInspector
import app.allfun.data.update.ApkInstaller
import app.allfun.data.update.UpdateApi
import app.allfun.data.update.UpdateDownloader
import app.allfun.data.update.UpdateNotifier
import app.allfun.data.update.UpdateRepository
import app.allfun.data.update.UpdateScheduler
import app.allfun.data.update.UpdateStore
import app.allfun.data.update.AppVersion
import eu.kanade.tachiyomi.HostServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Source id used by the demo manga builds up to 0.2 shipped. Only referenced to clean up. */
private const val DEMO_SOURCE_ID = "demo-manga"

/**
 * Hand-wired dependency graph. Small enough to keep in one place; swapping in Hilt later
 * means moving these same bindings into a module.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Kept for settings that need to touch files, e.g. storage stats and cache clearing. */
    val appContext: Context = app

    /** Lives as long as the process: used for writes that must survive a screen closing. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = AppDatabase.build(app)
    val http = Http()
    val settings = SettingsStore(app)

    /** Scratch space for repository downloads; cleaned up immediately after install. */
    val cacheDir: File = app.cacheDir

    /** Installed extension APKs and downloaded plugin scripts. */
    val filesDir: File = app.filesDir

    /** JSON export/import of settings, repositories, library and progress. */
    val backups = BackupManager(
        mediaDao = database.mediaDao(),
        episodeDao = database.episodeDao(),
        progressDao = database.progressDao(),
        settingsStore = settings
    )

    /** Reads the manga/novel repository indexes the Extensions screen discovers. */
    val extensionRepo = ExtensionRepository(http)

    val library = LibraryRepository(database.mediaDao(), database.episodeDao())
    val progress = ProgressRepository(database.progressDao())
    val sources = SourceRepository(database.sourceDao())

    val extensions: ExtensionManager

    val novelHost: NovelPluginHost

    val sourceHub: SourceHub

    /** The three-category aggregation layer that all content screens use. */
    val content: ContentRepository

    /** Real offline downloads: queue, files, offline reads and Library integration. */
    val downloads: DownloadManager

    init {
        // Extensions link against the eu.kanade.tachiyomi.* API implemented by this app,
        // so the host services must exist before any extension class is instantiated.
        HostServices.install(app, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        extensions = ExtensionManager(app)
        novelHost = NovelPluginHost(app, http)
        sourceHub = SourceHub(
            context = app,
            http = http,
            mediaDao = database.mediaDao(),
            episodeDao = database.episodeDao(),
            sourceDao = database.sourceDao(),
            extensions = extensions,
            novelHost = novelHost
        )
        content = ContentRepository(
            sourceHub = sourceHub,
            sourceDao = database.sourceDao(),
            settings = settings
        )
        downloads = DownloadManager(
            context = app,
            http = http,
            dao = database.downloadDao(),
            mediaDao = database.mediaDao(),
            episodeDao = database.episodeDao(),
            sourceHub = sourceHub,
            settings = settings,
            scope = appScope
        )
    }

    /** GitHub Releases-based updater; see data/update. */
    val updates = UpdateRepository(UpdateApi(http), UpdateStore(app))
    val updateDownloader = UpdateDownloader(app, http)
    val apkInspector = ApkInspector(app)
    val apkInstaller = ApkInstaller(app)
    val updateNotifier = UpdateNotifier(app)

    fun bootstrap() {
        scope.launch {
            sourceHub.ensureSeeded()
            runCatching { sourceHub.reconcileInstalledExtensions() }

            // 0.7 removed local media browsing. Delete the source rows older builds seeded
            // and the media/progress that only those sources could open, then remove the
            // extracted EPUB copies. Downloaded AllFun content is untouched.
            runCatching {
                database.mediaDao().deleteBySource(SourceIds.LOCAL_VIDEO)
                database.mediaDao().deleteBySource(SourceIds.LOCAL_NOVEL)
                database.sourceDao().delete(SourceIds.LOCAL_VIDEO)
                database.sourceDao().delete(SourceIds.LOCAL_NOVEL)
                File(filesDir, "books").deleteRecursively()
            }

            runCatching { downloads.restore() }

            // 0.3 removed the bundled demo content. Earlier builds seeded a manga and an
            // EPUB into the library; drop both so an upgrade does not keep titles whose
            // source no longer exists. The manga is unambiguous (its source id is gone);
            // the demo book went through the normal import path, so it can only be
            // recognised by the exact title/author it was shipped with.
            runCatching {
                database.mediaDao().deleteBySource(DEMO_SOURCE_ID)
                database.sourceDao().delete(DEMO_SOURCE_ID)
            }

            // Update checks: schedule the periodic job and, at most once per cooldown,
            // ask GitHub on launch. A failure here must never affect the library.
            runCatching {
                val current = settings.current()
                UpdateScheduler.sync(app, current.autoCheckUpdates)
                if (!current.autoCheckUpdates) return@runCatching

                val release = updates.checkIfDue() ?: return@runCatching
                if (!AppVersion.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                    return@runCatching
                }
                if (current.notifyUpdates && updates.notifiedTag() != release.tag) {
                    updateNotifier.notifyAvailable(release)
                    updates.setNotifiedTag(release.tag)
                }
            }
        }
    }
}
