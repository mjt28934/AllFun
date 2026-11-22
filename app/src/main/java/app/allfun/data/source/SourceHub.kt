package app.allfun.data.source

import android.content.Context
import android.net.Uri
import app.allfun.BuildConfig
import app.allfun.data.db.EpisodeDao
import app.allfun.data.db.MediaDao
import app.allfun.data.db.SourceDao
import app.allfun.data.db.SourceEntity
import app.allfun.data.extension.ExtensionManager
import app.allfun.data.extension.NovelPluginHost
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.SourceType
import app.allfun.data.model.StreamInfo
import app.allfun.data.model.UnitStub
import app.allfun.data.net.Http
import app.allfun.data.net.arr
import app.allfun.data.net.obj
import app.allfun.data.net.str
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates live [MediaSource] instances from stored rows and installs new sources.
 *
 * Adding a source is deliberately data-driven: a Stremio addon is a URL, a Jellyfin server
 * is a URL + credentials. Nothing executable is downloaded.
 */
class SourceHub(
    context: Context,
    private val http: Http,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val sourceDao: SourceDao,
    private val extensions: ExtensionManager,
    private val novelHost: NovelPluginHost
) {
    private val appContext = context.applicationContext

    /**
     * 0.7 removed local media browsing: AllFun is an entertainment client for installed
     * extensions, not a file browser. The built-in source rows are therefore gone; the
     * cleanup in [AppContainer.bootstrap] removes the rows older versions seeded.
     */
    fun builtIns(): List<SourceEntity> = emptyList()

    suspend fun ensureSeeded() = Unit

    /**
     * Re-registers sources for extension APKs that are on disk but missing from the
     * database. That happens after restoring a backup onto a device that already had
     * extensions installed, or after a database reset: the Extensions screen listed the
     * APKs while the category screens still said "no source". Loading is best-effort so
     * one incompatible extension cannot stop startup.
     */
    suspend fun reconcileInstalledExtensions() {
        val registered = sourceDao.all().mapNotNull { entity ->
            runCatching {
                Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject.str("apk")
            }.getOrNull()
        }.toSet()
        extensions.installed()
            .filter { it.apk.name !in registered }
            .forEach { info ->
                runCatching { registerExtension(info, repoUrl = null, version = info.versionName) }
            }
    }

    fun instance(entity: SourceEntity): MediaSource? {
        val kind = runCatching {
            Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject.str("kind")
        }.getOrNull()
        return when (kind) {
            "stremio" -> StremioSource(entity, http)
            "jellyfin" -> JellyfinSource(entity, http)
            "lnreader" -> {
                val config = runCatching {
                    Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
                }.getOrNull() ?: return null
                val pluginFile = config.str("plugin") ?: return null
                val pluginId = config.str("pluginId") ?: return null
                val file = File(File(appContext.filesDir, "lnreader"), pluginFile)
                if (!file.exists()) return null
                LnReaderMediaSource(entity, novelHost, file, pluginId)
            }
            "komikku" -> {
                val config = runCatching {
                    Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
                }.getOrNull() ?: return null
                val apk = config.str("apk") ?: return null
                val sourceId = config["sourceId"]?.jsonPrimitive?.longOrNull
                val catalogue = extensions.catalogue(apk, sourceId) ?: return null
                KomikkuMediaSource(entity, catalogue, mediaDao, episodeDao)
            }
            else -> null
        }
    }

    /**
     * Installs a Tachiyomi/Komikku extension APK and registers every manga source it
     * exposes. The APK stays in private storage; only metadata goes into the database.
     */
    suspend fun addExtension(
        uri: Uri,
        repoUrl: String? = null,
        version: String? = null
    ): Result<List<SourceEntity>> = runCatching {
        val info = extensions.install(uri).getOrThrow()
        registerExtension(info, repoUrl, version)
    }

    /** Installs an already-downloaded APK (used by the repository installer). */
    suspend fun addExtension(
        file: File,
        repoUrl: String? = null,
        version: String? = null,
        expectedSigningKey: String? = null
    ): Result<List<SourceEntity>> = runCatching {
        val info = extensions.installFile(file, expectedSigningKey).getOrThrow()
        registerExtension(info, repoUrl, version)
    }

    private suspend fun registerExtension(
        info: ExtensionManager.ExtensionInfo,
        repoUrl: String?,
        version: String?
    ): List<SourceEntity> {
        val loaded = extensions.load(info)
        val entities = loaded.sources.mapNotNull { source ->
            val catalogue = source as? CatalogueSource ?: return@mapNotNull null
            val entity = SourceEntity(
                id = "komikku:" + catalogue.id,
                type = SourceType.MANGA,
                name = catalogue.name,
                baseUrl = (catalogue as? HttpSource)?.baseUrl,
                configJson = buildJsonObject {
                    put("kind", "komikku")
                    put("apk", info.apk.name)
                    put("sourceId", catalogue.id)
                    put("className", info.className)
                }.toString(),
                builtIn = false,
                repoUrl = repoUrl,
                version = version ?: info.versionName
            )
            sourceDao.upsert(entity)
            entity
        }
        if (entities.isEmpty()) error("That extension contains no manga sources")
        return entities
    }

    suspend fun sourceFor(sourceId: String): MediaSource? =
        sourceDao.byId(sourceId)?.let { instance(it) }

    /**
     * A Stremio addon with no catalogs is a stream provider (Torrentio and similar). It
     * is installed and queried by [streamFor], but it must not appear in a category feed
     * or be reported as a broken catalogue.
     */
    fun isStreamProviderOnly(entity: SourceEntity): Boolean {
        if (entity.type != SourceType.VIDEO) return false
        val config = runCatching {
            Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
        }.getOrNull() ?: return false
        if (config.str("kind") != "stremio") return false
        val manifest = config.obj("manifest") ?: return false
        return manifest.arr("catalogs").isNullOrEmpty() &&
            StremioManifest.supports(manifest, "stream")
    }

    /**
     * Resolves a video stream across every installed Stremio addon that accepts the
     * content id. Catalog addons supply the metadata; stream addons such as Torrentio
     * answer requests for another addon's ids, so asking only the item's origin addon
     * would hide every torrent source.
     */
    suspend fun streamFor(
        originSourceId: String,
        key: String,
        unitId: String,
        payload: String?
    ): StreamInfo? = streamsFor(originSourceId, key, unitId, payload).firstOrNull()

    /**
     * Every playable option for a title. Direct HTTP streams come first because they
     * start instantly; torrents follow, preferring 1080p/720p then seed count. The player
     * shows this list when more than one addon or release answers, so an unreachable
     * tracker never leaves the viewer with no alternative.
     */
    suspend fun streamsFor(
        originSourceId: String,
        key: String,
        unitId: String,
        payload: String?
    ): List<StreamInfo> {
        val origin = sourceDao.byId(originSourceId) ?: return emptyList()
        if (origin.type != SourceType.VIDEO) return emptyList()
        val originSource = runCatching { instance(origin) }.getOrNull() ?: return emptyList()

        // Jellyfin and other non-Stremio sources use their own internal ids; querying an
        // IMDb-keyed addon with those would always fail.
        if (originSource !is StremioSource) {
            return listOfNotNull(originSource.stream(key, unitId, payload))
        }

        val type = key.substringBefore('|', "movie")
        val streamId = payload?.takeIf { it.isNotBlank() } ?: unitId
        val addons = sourceDao.all()
            .filter { it.enabled && it.type == SourceType.VIDEO }
            .mapNotNull { entity ->
                runCatching { instance(entity) }.getOrNull() as? StremioSource
            }
            .filter { it.supportsStreams && it.acceptsStream(type, streamId) }

        val perAddon = coroutineScope {
            addons.map { addon ->
                async {
                    withTimeoutOrNull(STREAM_LOOKUP_TIMEOUT_MS) {
                        try {
                            addon.streams(key, unitId, payload)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    } ?: emptyList()
                }
            }.awaitAll()
        }

        val unique = perAddon.flatten().distinctBy(::streamIdentity)
        val direct = unique.filter { it.torrent == null }
        val torrents = unique.filter { it.torrent != null }
            .sortedWith(
                compareByDescending<StreamInfo> { StremioTorrents.qualityRank(it) }
                    .thenByDescending { it.torrent?.seeds ?: 0 }
            )
        return (direct + torrents).take(MAX_STREAMS)
    }

    // ------------------------------------------------------------------ metadata

    /** A detail page and its chapter/episode list, resolved in one lookup. */
    data class DetailsBundle(val details: MediaDetails, val units: List<UnitStub>)

    private data class CachedDetails(val value: DetailsBundle, val at: Long)
    private val detailsCache = ConcurrentHashMap<String, CachedDetails>()

    /**
     * Loads a title's metadata and units from the source that owns it, fanning out across
     * every installed Stremio addon that declares the `meta` resource.
     *
     * Stremio separates catalogues from metadata. A catalogue-only addon (Netflix, HBO Max
     * and similar aggregators) answers `catalog` but not `meta`, so asking only the addon
     * that supplied the card returned an HTML error page. Cinemeta or another meta-capable
     * addon supplies the real detail page in that case, exactly as Stremio itself does.
     * Returns null only when no installed addon can answer; callers keep the cached row.
     */
    suspend fun detailsWithUnits(sourceId: String, key: String): DetailsBundle? {
        val cacheKey = "$sourceId|$key"
        detailsCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.at <= DETAILS_TTL_MS }
            ?.let { return it.value }

        val origin = sourceDao.byId(sourceId) ?: return null
        val source = runCatching { instance(origin) }.getOrNull() ?: return null
        val bundle = if (source !is StremioSource) {
            val details = runCatching { source.details(key) }.getOrNull() ?: return null
            DetailsBundle(details, runCatching { source.units(key) }.getOrDefault(emptyList()))
        } else {
            stremioDetails(source, sourceId, key) ?: return null
        }
        detailsCache[cacheKey] = CachedDetails(bundle, System.currentTimeMillis())
        return bundle
    }

    /** Forgets a cached detail page, e.g. after the user removes the addon that served it. */
    fun forgetDetails(sourceId: String) {
        detailsCache.keys.removeAll { it.startsWith("$sourceId|") }
    }

    private suspend fun stremioDetails(
        origin: StremioSource,
        originId: String,
        key: String
    ): DetailsBundle? {
        val type = key.substringBefore('|', "movie")
        val metaId = key.substringAfter('|', key)
        val candidates = buildList {
            if (origin.supportsMeta && origin.acceptsMeta(type, metaId)) add(origin)
            sourceDao.all()
                .filter { it.enabled && it.type == SourceType.VIDEO && it.id != originId }
                .mapNotNull { entity ->
                    runCatching { instance(entity) }.getOrNull() as? StremioSource
                }
                .filter { it.supportsMeta && it.acceptsMeta(type, metaId) }
                .forEach(::add)
        }
        if (candidates.isEmpty()) return null

        val answers = coroutineScope {
            candidates.map { addon ->
                async {
                    withTimeoutOrNull(DETAILS_LOOKUP_TIMEOUT_MS) {
                        runCatching { addon.metaBundle(key) }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        // Prefer an answer with an episode list: a film from Cinemeta always includes its
        // single video, while a bare stub would leave the detail screen without a player.
        val best = answers.firstOrNull { it != null && it.hasVideos }
            ?: answers.firstNotNullOfOrNull { it }
            ?: return null
        return DetailsBundle(best.details, best.units)
    }

    private fun streamIdentity(stream: StreamInfo): String {
        val hash = stream.torrent?.magnet
            ?.substringAfter("xt=urn:btih:", "")
            ?.substringBefore('&')
        return if (hash.isNullOrBlank()) stream.url else "btih:$hash"
    }

    /** Whether the files an extension-backed source needs are still on disk. */
    fun isAvailable(entity: SourceEntity): Boolean {
        val config = runCatching {
            Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
        }.getOrNull() ?: return true
        return when (config.str("kind")) {
            "lnreader" -> {
                val plugin = config.str("plugin") ?: return false
                File(File(appContext.filesDir, "lnreader"), plugin).isFile
            }
            "komikku" -> {
                val apk = config.str("apk") ?: return false
                extensions.installed().any { it.apk.name == apk }
            }
            else -> true
        }
    }

    /**
     * Imports an LNReader plugin (a single `.js` file) and registers it as a novel
     * source. The script stays in private storage and runs in [NovelPluginHost].
     */
    suspend fun addLnReader(uri: Uri): Result<SourceEntity> = runCatching {
        val source = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: error("Could not read that plugin file")
        installLnReader(source, repoUrl = null, version = null)
    }

    /**
     * Installs LNReader plugin source text that was already fetched (from a repository or
     * a file picker). The plugin is validated by the sandbox host before anything is
     * written, so a broken or unsupported script never becomes a source row.
     */
    suspend fun installLnReader(
        source: String,
        repoUrl: String? = null,
        version: String? = null
    ): SourceEntity {
        if (source.isBlank()) error("That plugin file is empty")
        val info = novelHost.loadPlugin(source)
        val pluginId = info.optString("id").takeIf { it.isNotBlank() }
            ?: error("That plugin has no id")
        val pluginName = info.optString("name").takeIf { it.isNotBlank() } ?: pluginId
        val site = info.optString("site").trimEnd('/')
        val pluginVersion = version?.takeIf { it.isNotBlank() }
            ?: info.optString("version").takeIf { it.isNotBlank() }

        val directory = File(appContext.filesDir, "lnreader").apply { mkdirs() }
        val fileName = pluginId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".js"
        File(directory, fileName).writeText(source)

        val config = buildJsonObject {
            put("kind", "lnreader")
            put("plugin", fileName)
            put("pluginId", pluginId)
            put("site", site)
            put("version", pluginVersion)
            put("lang", info.optString("lang"))
        }.toString()

        val entity = SourceEntity(
            id = "lnreader:$pluginId",
            type = SourceType.NOVEL,
            name = pluginName,
            baseUrl = site.ifBlank { null },
            configJson = config,
            builtIn = false,
            repoUrl = repoUrl,
            version = pluginVersion
        )
        sourceDao.upsert(entity)
        return entity
    }

    /** Installs a Stremio addon from its manifest URL (or the addon's base URL). */
    suspend fun addStremio(rawUrl: String): Result<SourceEntity> = runCatching {
        val base = normalize(rawUrl)
        val manifest = http.getJson("$base/manifest.json").jsonObject
        val name = manifest.str("name") ?: error("That addon has no manifest name")
        val catalogs = manifest.arr("catalogs").orEmpty()
        // Stream-only addons (Torrentio, media fusion) are valid: they answer stream
        // requests for ids supplied by Cinemeta or another catalog addon. Catalog-only
        // aggregators are valid too: cards open through any installed addon with `meta`.
        // Rejecting either kind was why those addons appeared to install and then vanished.
        if (catalogs.isEmpty() && !StremioManifest.supports(manifest, "stream")) {
            error("That addon exposes neither catalogs nor streams")
        }

        // Keep the whole manifest: catalog `extra` declarations decide which request
        // shapes the addon accepts (search, skip, genre), and the stream/subtitle
        // resources decide what the player can do. Flattening loses all of that.
        val config = buildJsonObject {
            put("kind", "stremio")
            put("manifest", manifest)
            put("catalogs", buildJsonArray {
                catalogs.forEach { element ->
                    val cat = element.jsonObject
                    val type = cat.str("type") ?: return@forEach
                    val id = cat.str("id") ?: return@forEach
                    addJsonObject {
                        put("type", type)
                        put("id", id)
                        put("name", cat.str("name") ?: "$type · $id")
                    }
                }
            })
        }.toString()

        val entity = SourceEntity(
            id = "stremio:" + Integer.toHexString(base.hashCode()),
            type = SourceType.VIDEO,
            name = name,
            baseUrl = base,
            configJson = config,
            builtIn = false
        )
        sourceDao.upsert(entity)
        entity
    }

    /** Authenticates against Jellyfin and stores the resulting access token. */
    suspend fun addJellyfin(
        rawUrl: String,
        username: String,
        password: String
    ): Result<SourceEntity> = runCatching {
        val base = normalize(rawUrl)
        val authHeader =
            """MediaBrowser Client="AllFun", Device="Android", DeviceId="allfun-android", Version="${BuildConfig.VERSION_NAME}""""
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString()

        val response = http.postJson(
            "$base/Users/AuthenticateByName",
            body,
            mapOf("X-Emby-Authorization" to authHeader)
        ).jsonObject

        val token = response.str("AccessToken") ?: error("Jellyfin rejected those credentials")
        val userId = response.obj("User")?.str("Id") ?: error("No user returned by Jellyfin")
        val serverName = runCatching {
            http.getJson("$base/System/Info/Public").jsonObject.str("ServerName")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Jellyfin"

        val config = buildJsonObject {
            put("kind", "jellyfin")
            put("base", base)
            put("token", token)
            put("userId", userId)
        }.toString()

        val entity = SourceEntity(
            id = "jellyfin:" + Integer.toHexString(base.hashCode()),
            type = SourceType.VIDEO,
            name = serverName,
            baseUrl = base,
            configJson = config,
            builtIn = false
        )
        sourceDao.upsert(entity)
        entity
    }

    suspend fun remove(sourceId: String) {
        if (builtIns().any { it.id == sourceId }) return
        val entity = sourceDao.byId(sourceId) ?: return
        sourceDao.delete(sourceId)
        forgetDetails(sourceId)
        val config = runCatching {
            Json.parseToJsonElement(entity.configJson ?: "{}").jsonObject
        }.getOrNull() ?: return
        if (config.str("kind") == "komikku") {
            val apk = config.str("apk") ?: return
            val stillUsed = sourceDao.all().any { it.configJson?.contains("\"apk\":\"$apk\"") == true }
            if (!stillUsed) extensions.remove(apk)
        } else if (config.str("kind") == "lnreader") {
            val plugin = config.str("plugin") ?: return
            val stillUsed = sourceDao.all().any { it.configJson?.contains("\"plugin\":\"$plugin\"") == true }
            if (!stillUsed) File(File(appContext.filesDir, "lnreader"), plugin).delete()
        }
    }

    private fun normalize(raw: String): String {
        var value = raw.trim().trimEnd('/')
        // Stremio install links look like stremio://host/manifest.json or
        // stremio:https://host/manifest.json; both point at an HTTPS addon.
        value = value.removePrefix("stremio:").removePrefix("stremio://")
        if (value.startsWith("//")) value = "https:$value"
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://$value"
        }
        if (value.endsWith("/manifest.json")) value = value.removeSuffix("/manifest.json")
        return value
    }

    private companion object {
        const val STREAM_LOOKUP_TIMEOUT_MS = 20_000L

        /** Metadata addons answer in a second or two; do not hold the screen on a dead one. */
        const val DETAILS_LOOKUP_TIMEOUT_MS = 12_000L
        const val DETAILS_TTL_MS = 5 * 60_000L

        /** Enough alternatives to escape a dead tracker without an endless picker. */
        const val MAX_STREAMS = 60
    }
}
