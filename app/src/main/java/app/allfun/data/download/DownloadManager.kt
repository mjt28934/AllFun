package app.allfun.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.allfun.data.db.DownloadDao
import app.allfun.data.db.DownloadEntity
import app.allfun.data.db.DownloadStatus
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.MediaDao
import app.allfun.data.model.MediaKind
import app.allfun.data.net.Http
import app.allfun.data.net.ImageHeaders
import app.allfun.data.net.userMessage
import app.allfun.data.settings.SettingsStore
import app.allfun.data.source.SourceHub
import eu.kanade.tachiyomi.HostServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Queue orderings offered by the Downloads screen. Like Komikku, choosing one really
 * rewrites the queue instead of only changing how this screen displays it.
 */
enum class QueueOrder(val label: String) {
    CHAPTER_DESC("Chapter number, newest first"),
    CHAPTER_ASC("Chapter number, oldest first"),
    ADDED_NEWEST("Recently added first"),
    ADDED_OLDEST("Added longest ago first")
}

/**
 * Real offline downloads for content the provider actually serves over HTTP.
 *
 * Manga chapters are stored as an ordered folder of page images plus a `pages.json`
 * manifest; novel chapters as one HTML document; films and episodes as a single file.
 * A row is only marked COMPLETED after the bytes exist on disk and are non-empty, and
 * completed rows put the title into the Library. Torrent-only or browser-only streams are
 * rejected with the provider's real limitation instead of pretending to download.
 */
class DownloadManager(
    context: Context,
    private val http: Http,
    private val dao: DownloadDao,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val sourceHub: SourceHub,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    /** App-private storage: removed with the app and needs no storage permission. */
    val root: File = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "downloads"
    )

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    private val running = ConcurrentHashMap<String, Job>()
    private val pumpLock = Mutex()

    /** Serialises queue reordering so two quick menu taps cannot interleave writes. */
    private val orderLock = Mutex()

    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    init {
        // Wi-Fi-only is a real preference: when the network changes, queued rows get a
        // fresh chance to start instead of waiting for the next app launch.
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { pump() }
                }

                override fun onLost(network: Network) = Unit
            })
        }
    }

    suspend fun restore() {
        dao.all().forEach { row ->
            when (row.status) {
                DownloadStatus.RUNNING -> dao.update(
                    key = row.key,
                    status = DownloadStatus.QUEUED,
                    progress = row.progress,
                    bytes = row.downloadedBytes,
                    total = row.totalBytes,
                    path = row.path,
                    error = null,
                    note = "Resuming after restart",
                    completedAt = null
                )
                DownloadStatus.COMPLETED -> {
                    val file = row.path?.let(::File)
                    val ok = file != null && file.exists() &&
                        (if (file.isDirectory) file.listFiles()?.any() == true else file.length() > 0)
                    if (!ok) {
                        dao.update(
                            key = row.key,
                            status = DownloadStatus.FAILED,
                            progress = row.progress,
                            bytes = row.downloadedBytes,
                            total = row.totalBytes,
                            path = null,
                            error = "The downloaded file is missing. Download it again.",
                            note = null,
                            completedAt = row.completedAt
                        )
                    }
                }
                else -> Unit
            }
        }
        pump()
    }

    /** Rows for one title, so a detail screen can show real per-chapter state. */
    fun forMedia(mediaId: String): Flow<List<DownloadEntity>> =
        dao.observeAll().map { rows -> rows.filter { it.mediaId == mediaId } }

    suspend fun forMediaOnce(mediaId: String): List<DownloadEntity> = dao.forMedia(mediaId)

    /** Re-evaluates the queue; used after a setting or network change. */
    suspend fun kick() = pump()

    suspend fun enqueue(mediaId: String, unitIds: List<String>): Int {
        val media = mediaDao.byId(mediaId) ?: return 0
        val provider = sourceHub.sourceFor(media.sourceId)
        if (provider == null || !provider.supportsDownloads) return 0
        val episodes = episodeDao.forMedia(mediaId).associateBy { it.id }
        // Komikku queues the newest chapters first (sortedByDescending sourceOrder); a
        // batch download therefore starts with what the user most likely wants to read.
        val wanted = unitIds.distinct()
            .mapNotNull { episodes[it] }
            .sortedByDescending { it.orderIndex }
        var order = dao.maxQueueOrder()
        var queued = 0
        wanted.forEach { episode ->
            val key = key(mediaId, episode.id)
            val existing = dao.one(mediaId, episode.id)
            if (existing != null && (
                    existing.status == DownloadStatus.COMPLETED ||
                        existing.status == DownloadStatus.RUNNING ||
                        existing.status == DownloadStatus.QUEUED ||
                        existing.status == DownloadStatus.WAITING
                    )
            ) {
                return@forEach
            }
            order++
            dao.upsert(
                DownloadEntity(
                    key = key,
                    mediaId = mediaId,
                    unitId = episode.id,
                    kind = media.kind,
                    mediaTitle = media.title,
                    unitTitle = episode.title,
                    status = DownloadStatus.QUEUED,
                    progress = 0,
                    note = wifiBlockedNote(),
                    unitNumber = episode.number,
                    queueOrder = order
                )
            )
            queued++
        }
        if (queued > 0) pump()
        return queued
    }

    // -------------------------------------------------------------- queue order

    /** Writes an explicit order for the active queue, keeping saved rows untouched. */
    suspend fun reorder(keys: List<String>) = orderLock.withLock {
        keys.distinct().forEachIndexed { index, key ->
            dao.setQueueOrder(key, (index + 1).toLong())
        }
    }

    suspend fun moveToTop(key: String) = moveOne(key, toTop = true)

    suspend fun moveToBottom(key: String) = moveOne(key, toTop = false)

    private suspend fun moveOne(key: String, toTop: Boolean) = orderLock.withLock {
        val active = dao.active()
        val item = active.firstOrNull { it.key == key } ?: return@withLock
        val reordered = active.filterNot { it.key == key }.toMutableList()
        if (toTop) reordered.add(0, item) else reordered.add(item)
        reordered.forEachIndexed { index, row ->
            dao.setQueueOrder(row.key, (index + 1).toLong())
        }
        pump()
    }

    /** Moves every active unit of one title together, like "move all for series". */
    suspend fun moveGroup(mediaId: String, toTop: Boolean) = orderLock.withLock {
        val active = dao.active()
        val group = active.filter { it.mediaId == mediaId }
        if (group.isEmpty()) return@withLock
        val others = active.filterNot { it.mediaId == mediaId }
        val reordered = if (toTop) group + others else others + group
        reordered.forEachIndexed { index, row ->
            dao.setQueueOrder(row.key, (index + 1).toLong())
        }
        pump()
    }

    suspend fun sortQueue(order: QueueOrder) = orderLock.withLock {
        val active = dao.active()
        val sorted = when (order) {
            QueueOrder.CHAPTER_DESC ->
                active.sortedWith(
                    compareByDescending<DownloadEntity> { it.unitNumber }
                        .thenByDescending { it.createdAt }
                )
            QueueOrder.CHAPTER_ASC ->
                active.sortedWith(
                    compareBy<DownloadEntity> { it.unitNumber }
                        .thenBy { it.createdAt }
                )
            QueueOrder.ADDED_NEWEST -> active.sortedByDescending { it.createdAt }
            QueueOrder.ADDED_OLDEST -> active.sortedBy { it.createdAt }
        }
        sorted.forEachIndexed { index, row ->
            dao.setQueueOrder(row.key, (index + 1).toLong())
        }
    }

    /** Stops the whole queue; queued rows keep their place and partial files. */
    suspend fun pauseAll() {
        settings.setDownloadQueuePaused(true)
        dao.active()
            .filter { running.containsKey(it.key) || it.status == DownloadStatus.RUNNING }
            .forEach { row ->
                running.remove(row.key)?.cancelAndJoin()
                val current = dao.one(row.mediaId, row.unitId) ?: row
                dao.update(
                    key = row.key,
                    status = DownloadStatus.QUEUED,
                    progress = current.progress,
                    bytes = current.downloadedBytes,
                    total = current.totalBytes,
                    path = current.path,
                    error = null,
                    note = "Paused",
                    completedAt = null
                )
            }
    }

    /** Resumes the queue and retries rows that failed, like Komikku's start action. */
    suspend fun startAll() {
        settings.setDownloadQueuePaused(false)
        dao.all()
            .filter { it.status == DownloadStatus.FAILED }
            .forEach { row ->
                dao.update(
                    key = row.key,
                    status = DownloadStatus.QUEUED,
                    progress = row.progress,
                    bytes = row.downloadedBytes,
                    total = row.totalBytes,
                    path = row.path,
                    error = null,
                    note = wifiBlockedNote(),
                    completedAt = null
                )
            }
        // Clear the paused marker so the rows stopped by the pause action really restart.
        dao.active()
            .filter { it.status == DownloadStatus.QUEUED && it.note == "Paused" }
            .forEach { row ->
                dao.update(
                    key = row.key,
                    status = DownloadStatus.QUEUED,
                    progress = row.progress,
                    bytes = row.downloadedBytes,
                    total = row.totalBytes,
                    path = row.path,
                    error = null,
                    note = wifiBlockedNote(),
                    completedAt = null
                )
            }
        pump()
    }

    /**
     * Cancels everything still queued and deletes its partial files. Completed downloads
     * stay: they are real files on disk and the Library's offline filter depends on them.
     */
    suspend fun clearQueue(): Int {
        val active = dao.active()
        active.forEach { cancel(it.key) }
        dao.all()
            .filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
            .forEach { dao.delete(it.key) }
        return active.size
    }

    suspend fun cancel(key: String) {
        // Wait for the worker to really stop, so its last progress write cannot land after
        // the CANCELED status and leave a row that looks active forever.
        running.remove(key)?.cancelAndJoin()
        val row = dao.all().firstOrNull { it.key == key } ?: return
        deleteFiles(row)
        dao.update(
            key = key,
            status = DownloadStatus.CANCELED,
            progress = 0,
            bytes = 0,
            total = 0,
            path = null,
            error = null,
            note = "Cancelled",
            completedAt = null
        )
    }

    suspend fun pause(key: String) {
        running.remove(key)?.cancelAndJoin()
        val row = dao.all().firstOrNull { it.key == key } ?: return
        if (row.status != DownloadStatus.RUNNING && row.status != DownloadStatus.QUEUED) return
        dao.update(
            key = key,
            status = DownloadStatus.WAITING,
            progress = row.progress,
            bytes = row.downloadedBytes,
            total = row.totalBytes,
            path = row.path,
            error = null,
            note = "Paused",
            completedAt = null
        )
    }

    suspend fun resume(key: String) {
        val row = dao.all().firstOrNull { it.key == key } ?: return
        if (row.status == DownloadStatus.COMPLETED) return
        dao.update(
            key = key,
            status = DownloadStatus.QUEUED,
            progress = row.progress,
            bytes = row.downloadedBytes,
            total = row.totalBytes,
            path = row.path,
            error = null,
            note = wifiBlockedNote(),
            completedAt = null
        )
        pump()
    }

    suspend fun retry(key: String) = resume(key)

    /** Removes the row and any files it owns. Confirmation is the caller's job. */
    suspend fun remove(key: String) {
        running.remove(key)?.cancelAndJoin()
        val row = dao.all().firstOrNull { it.key == key } ?: return
        deleteFiles(row)
        dao.delete(key)
    }

    suspend fun clearFinished() {
        dao.all().filter { it.status == DownloadStatus.COMPLETED ||
            it.status == DownloadStatus.FAILED ||
            it.status == DownloadStatus.CANCELED }
            .forEach { row ->
                deleteFiles(row)
                dao.delete(row.key)
            }
    }

    /** Bytes actually on disk for every completed row. */
    suspend fun usedBytes(): Long = dao.all().sumOf { row ->
        row.path?.let(::File)?.let { file ->
            if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            else file.length()
        } ?: 0L
    }

    // ------------------------------------------------------------------ offline reads

    suspend fun localPages(mediaId: String, unitId: String): List<String> {
        val row = dao.one(mediaId, unitId) ?: return emptyList()
        if (row.status != DownloadStatus.COMPLETED) return emptyList()
        val directory = row.path?.let(::File)?.takeIf { it.isDirectory } ?: return emptyList()
        val manifest = File(directory, PAGES_MANIFEST)
        if (!manifest.isFile) return emptyList()
        val names = runCatching {
            json.parseToJsonElement(manifest.readText())
                .let { element -> (element as? kotlinx.serialization.json.JsonArray) }
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull().orEmpty()
        val files = names.map { File(directory, it) }.filter { it.isFile && it.length() > 0 }
        return if (files.size == names.size) files.map { it.toURI().toString() } else emptyList()
    }

    suspend fun localChapter(mediaId: String, unitId: String): String? {
        val row = dao.one(mediaId, unitId) ?: return null
        if (row.status != DownloadStatus.COMPLETED) return null
        val file = row.path?.let(::File)?.takeIf { it.isFile } ?: return null
        return file.takeIf { it.length() > 0 }?.readText()
    }

    suspend fun localStream(mediaId: String, unitId: String): String? {
        val row = dao.one(mediaId, unitId) ?: return null
        if (row.status != DownloadStatus.COMPLETED) return null
        val file = row.path?.let(::File)?.takeIf { it.isFile && it.length() > 0 } ?: return null
        return file.toURI().toString()
    }

    // ---------------------------------------------------------------------- queue

    private suspend fun pump() {
        pumpLock.withLock {
            val settingsNow = settings.current()
            // A paused queue is explicit: nothing starts until the user taps Resume.
            if (settingsNow.downloadQueuePaused) return@withLock
            val blocked = wifiBlockedNote()
            val rows = dao.active().filter {
                it.status != DownloadStatus.RUNNING || !running.containsKey(it.key)
            }
            if (blocked != null) {
                // A paused row is the user's choice and is not the same as Wi-Fi blocking.
                rows.filter { it.status == DownloadStatus.QUEUED && it.note != "Paused" }
                    .forEach { row ->
                        dao.update(
                            key = row.key,
                            status = DownloadStatus.WAITING,
                            progress = row.progress,
                            bytes = row.downloadedBytes,
                            total = row.totalBytes,
                            path = row.path,
                            error = null,
                            note = blocked,
                            completedAt = null
                        )
                    }
                return@withLock
            }
            val limit = settingsNow.concurrentDownloads.coerceIn(1, 4)
            rows.filter { it.status == DownloadStatus.WAITING && it.note == "Waiting for Wi-Fi" }
                .forEach { row ->
                    dao.update(
                        key = row.key,
                        status = DownloadStatus.QUEUED,
                        progress = row.progress,
                        bytes = row.downloadedBytes,
                        total = row.totalBytes,
                        path = row.path,
                        error = null,
                        note = null,
                        completedAt = null
                    )
                }
            val capacity = (limit - running.size).coerceAtLeast(0)
            if (capacity == 0) return
            dao.active()
                // A row the user paused individually stays parked even when others resume.
                .filter {
                    it.status == DownloadStatus.QUEUED &&
                        it.note != "Paused" &&
                        !running.containsKey(it.key)
                }
                .take(capacity)
                .forEach { row ->
                    // Start lazily so the job is in the map before it can possibly finish.
                    // Without this, a fast failure could complete before the assignment and
                    // leave a dead job counted against the concurrency limit forever.
                    val job = scope.launch(start = CoroutineStart.LAZY) { run(row) }
                    running[row.key] = job
                    job.invokeOnCompletion {
                        running.remove(row.key)
                        // A finished worker frees a slot. Re-pumping here is what keeps a
                        // queue of more than `concurrentDownloads` rows moving; before, only
                        // enqueue/network events called pump(), so the fifth row never started.
                        scope.launch { pump() }
                    }
                    job.start()
                }
        }
    }

    private suspend fun run(row: DownloadEntity) {
        val media = mediaDao.byId(row.mediaId)
        if (media == null) {
            fail(row, "This title is no longer in the library.")
            return
        }
        val episode = episodeDao.one(row.mediaId, row.unitId)
        if (episode == null) {
            fail(row, "That chapter or episode no longer exists.")
            return
        }
        val source = sourceHub.sourceFor(media.sourceId)
        if (source == null) {
            fail(row, "Install the extension that provides this title to download it.")
            return
        }
        markRunning(row)
        try {
            when (row.kind) {
                MediaKind.MANGA -> downloadManga(row, media.sourceKey, episode, source)
                MediaKind.NOVEL -> downloadNovel(row, media.sourceKey, episode, source)
                MediaKind.MOVIE, MediaKind.SERIES ->
                    downloadVideo(row, media.sourceKey, episode, source)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            fail(row, error.userMessage("this download"))
        }
    }

    private suspend fun downloadManga(
        row: DownloadEntity,
        sourceKey: String,
        episode: app.allfun.data.db.EpisodeEntity,
        source: app.allfun.data.source.MediaSource
    ) {
        val pages = source.pages(sourceKey, episode.sourceUnitId ?: episode.id)
        if (pages.isEmpty()) throw IOException("This provider returned no pages for that chapter.")
        val directory = File(root, safe(row.mediaTitle) + "/" + safe(row.unitTitle))
        directory.mkdirs()
        val names = mutableListOf<String>()
        var done = 0
        var bytes = 0L
        pages.forEachIndexed { index, url ->
            val name = "page_%03d%s".format(index + 1, extensionFor(url, "jpg"))
            val target = File(directory, name)
            // Reuse a page already written by an interrupted run; only fetch what is missing.
            if (!target.isFile || target.length() == 0L) {
                val data = fetchBytes(url)
                target.writeBytes(data)
            }
            names += name
            bytes += target.length()
            done++
            publish(row, (done * 100) / pages.size, done, bytes, directory.path)
        }
        val manifest = File(directory, PAGES_MANIFEST)
        manifest.writeText(
            buildJsonArray { names.forEach { add(it) } }.toString()
        )
        complete(row, directory.path, bytes)
    }

    private suspend fun downloadNovel(
        row: DownloadEntity,
        sourceKey: String,
        episode: app.allfun.data.db.EpisodeEntity,
        source: app.allfun.data.source.MediaSource
    ) {
        val body = source.chapterBody(sourceKey, episode.sourceUnitId ?: episode.id)
        val text = body ?: source.chapterText(sourceKey, episode.sourceUnitId ?: episode.id)
        if (text.isNullOrBlank()) {
            throw IOException("This provider returned no text for that chapter.")
        }
        val directory = File(root, safe(row.mediaTitle) + "/" + safe(row.unitTitle))
        directory.mkdirs()
        val file = File(directory, "chapter.html")
        val html = if (body != null) text else text
            .split(Regex("\n\\s*\n"))
            .joinToString("\n") { "<p>" + it.replace("&", "&amp;").replace("<", "&lt;") + "</p>" }
        file.writeText(html)
        complete(row, file.path, file.length())
    }

    private suspend fun downloadVideo(
        row: DownloadEntity,
        sourceKey: String,
        episode: app.allfun.data.db.EpisodeEntity,
        source: app.allfun.data.source.MediaSource
    ) {
        val stream = sourceHub.streamFor(
            originSourceId = source.id,
            key = sourceKey,
            unitId = episode.sourceUnitId ?: episode.id,
            payload = episode.payload
        ) ?: throw IOException(
            "This provider offers no downloadable file. Browser-only streams cannot " +
                "be saved by AllFun."
        )
        if (stream.torrent != null) {
            // The player streams torrents through libtorrent, but writing a complete
            // torrent to offline storage is not implemented. Say so instead of attempting
            // an HTTP request for a magnet link and failing with a network error.
            throw IOException(
                "This release is torrent-only. AllFun can stream it, but saving torrents " +
                    "for offline playback is not supported yet. Pick a direct stream to download."
            )
        }
        if (!stream.url.startsWith("http://", true) && !stream.url.startsWith("https://", true)) {
            throw IOException("This provider returned a link AllFun cannot download.")
        }
        val lowerUrl = stream.url.substringBefore('?').lowercase()
        if (lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".mpd")) {
            throw IOException(
                "This provider streams in segments (HLS/DASH), so there is no single file to " +
                    "save. Stream it instead; AllFun will not store an unplayable playlist."
            )
        }
        val directory = File(root, safe(row.mediaTitle))
        directory.mkdirs()
        val target = File(directory, safe(row.unitTitle) + extensionFor(stream.url, "mp4"))
        retryTransfer {
            val request = okhttp3.Request.Builder()
                .url(stream.url)
                .apply { stream.headers.forEach { (key, value) -> header(key, value) } }
                .build()
            http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} while downloading this file")
                }
                val body = response.body ?: throw IOException("The provider sent an empty file")
                val total = body.contentLength()
                val input = body.byteStream()
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val percent = if (total > 0) ((written * 100) / total).toInt() else -1
                        publish(row, percent, 100, written, target.path, total)
                    }
                }
            }
        }
        if (target.length() <= 0) throw IOException("The downloaded file is empty")
        complete(row, target.path, target.length())
    }

    // ------------------------------------------------------------------- helpers

    /**
     * Komikku retries a failed transfer three times, waiting 2, 4 and 8 seconds. Doing the
     * same here keeps a flaky host from failing a whole chapter on one bad response.
     */
    private suspend fun <T> retryTransfer(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (attempt >= MAX_RETRIES) throw error
                delay((2L shl attempt) * 1_000L)
                attempt++
            }
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray = retryTransfer {
        val builder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", HostServices.userAgent)
        ImageHeaders.forUrl(url).forEach { (key, value) -> builder.header(key, value) }
        http.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading a page")
            }
            response.body?.bytes() ?: throw IOException("Empty page response")
        }
    }

    private suspend fun markRunning(row: DownloadEntity) {
        dao.update(
            key = row.key,
            status = DownloadStatus.RUNNING,
            progress = row.progress,
            bytes = row.downloadedBytes,
            total = row.totalBytes,
            path = row.path,
            error = null,
            note = null,
            completedAt = null
        )
    }

    private suspend fun publish(
        row: DownloadEntity,
        progress: Int,
        @Suppress("UNUSED_PARAMETER") steps: Int,
        bytes: Long,
        path: String?,
        total: Long = 0
    ) {
        dao.update(
            key = row.key,
            status = DownloadStatus.RUNNING,
            progress = progress,
            bytes = bytes,
            total = total,
            path = path,
            error = null,
            note = null,
            completedAt = null
        )
    }

    private suspend fun complete(row: DownloadEntity, path: String, bytes: Long) {
        val now = System.currentTimeMillis()
        dao.update(
            key = row.key,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            bytes = bytes,
            total = bytes,
            path = path,
            error = null,
            note = null,
            completedAt = now
        )
        // A completed download is part of the user's collection; the Library filter reads
        // the downloads table, and inLibrary keeps the title visible everywhere else too.
        mediaDao.setInLibrary(row.mediaId, true, now)
    }

    private suspend fun fail(row: DownloadEntity, message: String) {
        dao.update(
            key = row.key,
            status = DownloadStatus.FAILED,
            progress = row.progress,
            bytes = row.downloadedBytes,
            total = row.totalBytes,
            path = row.path,
            error = message,
            note = null,
            completedAt = null
        )
    }

    private fun deleteFiles(row: DownloadEntity) {
        val path = row.path?.let(::File) ?: return
        runCatching {
            if (path.isDirectory) path.deleteRecursively() else path.delete()
        }
    }

    private suspend fun wifiBlockedNote(): String? {
        val settingsNow = settings.current()
        if (!settingsNow.downloadWifiOnly) return null
        val manager = connectivity ?: return null
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "Waiting for Wi-Fi"
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            null
        } else {
            "Waiting for Wi-Fi"
        }
    }

    private fun safe(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(60)
            .ifBlank { "item" }

    private fun extensionFor(url: String, fallback: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
        return "." + (ext ?: fallback)
    }

    private fun key(mediaId: String, unitId: String) = "$mediaId|$unitId"

    companion object {
        private const val PAGES_MANIFEST = "pages.json"

        /** Extra attempts after a transfer fails: Komikku waits 2, 4 then 8 seconds. */
        private const val MAX_RETRIES = 3
    }
}
