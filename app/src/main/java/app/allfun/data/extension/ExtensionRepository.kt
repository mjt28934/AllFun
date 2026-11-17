package app.allfun.data.extension

import app.allfun.data.net.Http
import app.allfun.data.net.arr
import app.allfun.data.net.int
import app.allfun.data.net.obj
import app.allfun.data.net.str
import app.allfun.data.settings.RepositoryConfig
import app.allfun.data.settings.RepositoryKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URI

/** One installable extension advertised by a repository index. */
data class RepoExtension(
    val key: String,
    val kind: RepositoryKind,
    val name: String,
    val version: String?,
    val description: String?,
    val developer: String?,
    val iconUrl: String?,
    val downloadUrl: String,
    val packageName: String? = null,
    val pluginId: String? = null,
    val site: String? = null,
    val language: String? = null,
    val categories: List<String> = emptyList(),
    val nsfw: Boolean = false,
    val sourceCount: Int = 0,
    val repoUrl: String
)

data class RepositoryContents(
    val name: String?,
    val signingKey: String?,
    val extensions: List<RepoExtension>
)

/**
 * Reads the two repository formats AllFun actually supports:
 *
 *  - Mihon/Komikku/Tachiyomi JSON (`index.json` with `extensionList.extensions`, or the
 *    legacy top-level array) for manga extensions.
 *  - LNReader `plugins.min.json` (a flat array) for novel plugins.
 *
 * An index is parsed only after the payload validates as JSON of the right shape. A
 * repository that serves HTML or an empty list is rejected instead of producing a screen
 * full of entries that cannot install.
 */
class ExtensionRepository(private val http: Http) {

    suspend fun load(kind: RepositoryKind, url: String): RepositoryContents {
        requireHttpUrl(url)
        return when (kind) {
            RepositoryKind.MANGA -> loadManga(url)
            RepositoryKind.NOVEL -> loadNovel(url)
        }
    }

    private suspend fun loadManga(url: String): RepositoryContents {
        val root = http.getJson(url)
        return when (root) {
            is JsonObject -> {
                val list = root.obj("extensionList")?.arr("extensions")
                    ?: root.arr("extensions")
                    ?: JsonArray(emptyList())
                RepositoryContents(
                    name = root.str("name"),
                    signingKey = root.str("signingKey"),
                    extensions = list.mapNotNull { element ->
                        parseModernManga(element, url)
                    }
                )
            }

            is JsonArray -> RepositoryContents(
                name = null,
                signingKey = null,
                extensions = root.mapNotNull { element -> parseLegacyManga(element, url) }
            )

            else -> error("The repository index is neither an object nor an array")
        }.also { contents ->
            if (contents.extensions.isEmpty()) {
                error("That repository contains no extensions AllFun can install")
            }
        }
    }

    private suspend fun loadNovel(url: String): RepositoryContents {
        val root = http.getJson(url)
        val array = root as? JsonArray
            ?: error("The novel repository index must be a JSON array")
        val extensions = array.mapNotNull { element -> parseNovel(element, url) }
        if (extensions.isEmpty()) {
            error("That repository contains no readable LNReader plugins")
        }
        return RepositoryContents(name = null, signingKey = null, extensions = extensions)
    }

    private fun parseModernManga(element: JsonElement, repoUrl: String): RepoExtension? {
        val manifest = element as? JsonObject ?: return null
        val packageName = manifest.str("packageName") ?: return null
        val name = manifest.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val resources = manifest.obj("resources")
        val downloadUrl = resources?.str("apkUrl")
            ?: manifest.str("apkUrl")
            ?: return null
        if (!isHttp(downloadUrl)) return null
        val language = manifest.str("lang")
            ?: manifest.arr("sources")
                ?.mapNotNull { (it as? JsonObject)?.str("language") }
                ?.distinct()
                ?.take(3)
                ?.joinToString(", ")
        val contentWarning = manifest.str("contentWarning")
        val sources = manifest.arr("sources")?.size ?: 0
        return RepoExtension(
            key = "manga:$packageName",
            kind = RepositoryKind.MANGA,
            name = name,
            version = manifest.str("versionName") ?: manifest.str("version"),
            description = contentWarning.takeUnless { it.isNullOrBlank() || it == "CONTENT_WARNING_NONE" }
                ?.replace('_', ' ')
                ?.lowercase()
                ?.replaceFirstChar { it.uppercase() }
                ?.let { "$it · ${sources} source${if (sources == 1) "" else "s"}" },
            developer = if (repoUrl.contains("keiyoushi", ignoreCase = true)) "Keiyoushi" else null,
            iconUrl = resources?.str("iconUrl"),
            downloadUrl = downloadUrl,
            packageName = packageName,
            language = language,
            categories = listOfNotNull("Manga", language?.takeIf { it.isNotBlank() }),
            nsfw = contentWarning?.contains("NSFW", ignoreCase = true) == true,
            sourceCount = sources,
            repoUrl = repoUrl
        )
    }

    private fun parseLegacyManga(element: JsonElement, repoUrl: String): RepoExtension? {
        val manifest = element as? JsonObject ?: return null
        val packageName = manifest.str("pkg") ?: return null
        val name = manifest.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val apk = manifest.str("apk") ?: return null
        val downloadUrl = manifest.str("apkUrl")
            ?: (repoUrl.substringBeforeLast('/') + "/apk/" + apk)
        if (!isHttp(downloadUrl)) return null
        val sources = manifest.arr("sources")?.size ?: 0
        return RepoExtension(
            key = "manga:$packageName",
            kind = RepositoryKind.MANGA,
            name = name,
            version = manifest.str("version"),
            description = if (sources > 0) {
                "$sources source${if (sources == 1) "" else "s"}"
            } else null,
            developer = null,
            iconUrl = manifest.str("icon"),
            downloadUrl = downloadUrl,
            packageName = packageName,
            language = manifest.str("lang"),
            categories = listOfNotNull("Manga", manifest.str("lang")),
            nsfw = (manifest.int("nsfw") ?: 0) != 0,
            sourceCount = sources,
            repoUrl = repoUrl
        )
    }

    private fun parseNovel(element: JsonElement, repoUrl: String): RepoExtension? {
        val plugin = element as? JsonObject ?: return null
        val id = plugin.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = plugin.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val downloadUrl = plugin.str("url") ?: return null
        if (!isHttp(downloadUrl)) return null
        val language = plugin.str("lang")
        val site = plugin.str("site")
        return RepoExtension(
            key = "novel:$id",
            kind = RepositoryKind.NOVEL,
            name = name,
            version = plugin.str("version"),
            description = site?.let { hostOf(it) }?.let { "$it · LNReader plugin" },
            developer = site?.let(::hostOf),
            iconUrl = plugin.str("iconUrl"),
            downloadUrl = downloadUrl,
            pluginId = id,
            site = site,
            language = language,
            categories = listOfNotNull("Novels", language?.takeIf { it.isNotBlank() }),
            repoUrl = repoUrl
        )
    }

    private fun hostOf(url: String): String? = runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun isHttp(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private fun requireHttpUrl(url: String) {
        require(isHttp(url)) { "Repository URLs must start with http:// or https://" }
    }

    companion object {
        /** Two repositories AllFun ships with; both are real, public indexes. */
        val BUILT_IN = listOf(
            RepositoryConfig(
                kind = RepositoryKind.MANGA,
                url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json"
            ),
            RepositoryConfig(
                kind = RepositoryKind.NOVEL,
                url = "https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"
            )
        )

        fun builtInName(config: RepositoryConfig): String = when (config.kind) {
            RepositoryKind.MANGA -> "Keiyoushi extensions"
            RepositoryKind.NOVEL -> "LNReader plugins"
        }

        fun builtInDeveloper(config: RepositoryConfig): String? = when (config.kind) {
            RepositoryKind.MANGA -> "Keiyoushi"
            RepositoryKind.NOVEL -> "LNReader"
        }

        /** Numeric-aware comparison: 1.10.0 is newer than 1.9.4. */
        fun isNewer(remote: String?, local: String?): Boolean {
            if (remote.isNullOrBlank()) return false
            if (local.isNullOrBlank()) return true
            val a = remote.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
            val b = local.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
            for (index in 0 until maxOf(a.size, b.size)) {
                val left = a.getOrElse(index) { 0 }
                val right = b.getOrElse(index) { 0 }
                if (left != right) return left > right
            }
            return false
        }
    }
}
