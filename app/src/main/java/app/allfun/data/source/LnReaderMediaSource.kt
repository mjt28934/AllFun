package app.allfun.data.source

import app.allfun.data.db.SourceEntity
import app.allfun.data.extension.NovelPluginHost
import app.allfun.data.model.Catalog
import app.allfun.data.model.MediaDetails
import app.allfun.data.model.MediaKind
import app.allfun.data.model.MediaStub
import app.allfun.data.model.SourceType
import app.allfun.data.model.UnitStub
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Adapter that presents one LNReader plugin as an AllFun novel source.
 *
 * The plugin itself lives in `filesDir/lnreader/<id>.js` and is executed by
 * [NovelPluginHost]. Everything here is just shape conversion: LNReader's
 * `NovelItem`/`SourceNovel`/chapter paths become [MediaStub], [MediaDetails] and
 * [UnitStub] values.
 */
class LnReaderMediaSource(
    private val entity: SourceEntity,
    private val host: NovelPluginHost,
    private val pluginFile: File,
    private val pluginId: String
) : MediaSource() {

    override val id = entity.id
    override val name = entity.name
    override val type = SourceType.NOVEL
    override val chaptersAreRemote = true

    private val site = (entity.baseUrl ?: "").trimEnd('/')
    private val loadLock = Mutex()

    override fun catalogs(): List<Catalog> = listOf(
        Catalog("popular", "Popular"),
        Catalog("latest", "Latest")
    )

    private suspend fun ensureLoaded() {
        if (host.loadedPluginId == pluginId) return
        loadLock.withLock {
            if (host.loadedPluginId == pluginId) return
            if (!pluginFile.exists()) error("Plugin file is missing")
            host.loadPlugin(pluginFile.readText())
        }
    }

    override suspend fun browse(catalog: String?, page: Int): List<MediaStub> {
        ensureLoaded()
        val raw = host.call("popular", "[${page.coerceAtLeast(1)}]")
        return novels(raw)
    }

    override suspend fun search(query: String, page: Int): List<MediaStub> {
        ensureLoaded()
        val args = JSONArray().put(query).put(page.coerceAtLeast(1)).toString()
        return novels(host.call("search", args))
    }

    override suspend fun details(key: String): MediaDetails {
        ensureLoaded()
        val novel = host.toJsonObject(host.call("novel", JSONArray().put(key).toString()))
        val chapters = novel.optJSONArray("chapters") ?: JSONArray()
        return MediaDetails(
            key = key,
            title = novel.optString("name").ifBlank { "Untitled" },
            kind = MediaKind.NOVEL,
            author = novel.optString("author").takeIf { it.isNotBlank() },
            description = novel.optString("summary").takeIf { it.isNotBlank() },
            coverUrl = absolute(novel.optString("cover").takeIf { it.isNotBlank() }),
            status = novel.optString("status").takeIf { it.isNotBlank() },
            genres = novel.optString("genres")
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            totalUnits = chapters.length(),
            unitLabel = "Chapter"
        )
    }

    override suspend fun units(key: String): List<UnitStub> {
        ensureLoaded()
        val novel = host.toJsonObject(host.call("novel", JSONArray().put(key).toString()))
        val chapters = novel.optJSONArray("chapters") ?: return emptyList()
        val total = chapters.length()
        return (0 until total).mapNotNull { index ->
            val chapter = chapters.optJSONObject(index) ?: return@mapNotNull null
            val path = chapter.optString("path")
            if (path.isBlank()) return@mapNotNull null
            val number = if (chapter.has("chapterNumber") && !chapter.isNull("chapterNumber")) {
                chapter.optDouble("chapterNumber")
            } else {
                (total - index).toDouble()
            }
            UnitStub(
                id = path,
                index = index,
                title = chapter.optString("name").ifBlank { "Chapter ${index + 1}" },
                number = number,
                url = path
            )
        }
    }

    override suspend fun chapterText(key: String, unitId: String): String? {
        val html = chapterBody(key, unitId) ?: return null
        return Jsoup.parse(html).text()
    }

    /**
     * LNReader's `parseChapter` returns HTML, which is what the reader should render.
     * Everything executable is stripped and relative links are made absolute before the
     * fragment reaches the WebView.
     */
    override suspend fun chapterBody(key: String, unitId: String): String? {
        ensureLoaded()
        val raw = host.call("chapter", JSONArray().put(unitId).toString())
        val html = runCatching { JSONArray("[$raw]").optString(0) }.getOrElse { raw }
        if (html.isBlank()) return null
        return sanitize(html)
    }

    private fun sanitize(raw: String): String {
        val doc = Jsoup.parseBodyFragment(raw)
        doc.select("script, style, noscript, iframe, object, embed").remove()
        doc.select("*").forEach { element ->
            element.attributes().asList().toList().forEach { attribute ->
                val name = attribute.key
                if (name.startsWith("on", ignoreCase = true)) {
                    element.removeAttr(name)
                    return@forEach
                }
                if (name == "href" || name == "src") {
                    if (attribute.value.startsWith("javascript:", ignoreCase = true)) {
                        element.removeAttr(name)
                    } else {
                        absolute(attribute.value)?.let { element.attr(name, it) }
                    }
                }
            }
        }
        return doc.body().html()
    }

    private fun novels(raw: String): List<MediaStub> {
        val array = host.toJsonArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val path = item.optString("path").ifBlank { return@mapNotNull null }
            MediaStub(
                key = path,
                title = item.optString("name").ifBlank { "Untitled" },
                coverUrl = absolute(item.optString("cover").takeIf { it.isNotBlank() })
            )
        }
    }

    private fun absolute(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("data:")) return path
        if (site.isEmpty()) return path
        return if (path.startsWith("/")) site + path else "$site/$path"
    }
}
