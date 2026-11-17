package app.allfun.data.update

import app.allfun.data.net.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Where releases live. A fork only has to change these two strings. */
object UpdateSource {
    const val OWNER = "mjt28934"
    const val REPO = "AllFun"

    val releaseApiUrl: String = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    val releasesUrl: String = "https://github.com/$OWNER/$REPO/releases"
}

/**
 * One published GitHub Release, reduced to what the updater needs.
 *
 * @param checksumSha256 lowercase hex digest of the APK, from the asset's `digest` field
 *   (`sha256:…`) or from the release's `.sha256` asset.
 */
@Serializable
data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkAssetName: String,
    val apkUrl: String,
    val apkSize: Long,
    val checksumUrl: String? = null,
    val checksumSha256: String? = null,
    val releaseUrl: String = ""
)

class NoReleasePublishedException :
    Exception("No stable release has been published yet.")

/**
 * Reads GitHub Releases. GitHub only returns published, non-draft releases from the
 * `latest` endpoint, which is the point: a commit is not an update, a release is.
 */
class UpdateApi(private val http: Http) {

    suspend fun latestRelease(): ReleaseInfo {
        val body = http.get(
            UpdateSource.releaseApiUrl,
            mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to "AllFun-Android"
            )
        )
        val release = parseRelease(body)
        if (release.checksumSha256 != null || release.checksumUrl == null) return release

        // Older releases carry only the .sha256 asset. Fetch it now so the download can
        // validate while it writes rather than trusting an unverified file.
        val checksum = runCatching {
            parseChecksumFile(http.get(release.checksumUrl, mapOf("User-Agent" to "AllFun-Android")))
        }.getOrNull()
        return release.copy(checksumSha256 = checksum)
    }

    companion object {

        /** Pulls an APK and its checksum out of a release payload. */
        fun parseRelease(json: String): ReleaseInfo {
            val root = try {
                Json.parseToJsonElement(json).jsonObject
            } catch (error: Exception) {
                throw IllegalStateException("GitHub returned something that is not a release.", error)
            }

            if (root.bool("draft") == true || root.bool("prerelease") == true) {
                throw NoReleasePublishedException()
            }

            val tag = root.str("tag_name")
                ?: throw IllegalStateException("The release has no tag.")
            val assets = root["assets"]?.jsonArray
                ?.map { it.jsonObject }
                .orEmpty()

            val apk = assets.firstOrNull {
                it.str("name").orEmpty().endsWith(".apk", ignoreCase = true)
            } ?: throw IllegalStateException("The latest release has no APK attached.")

            val checksumAsset = assets.firstOrNull {
                it.str("name").orEmpty().endsWith(".sha256", ignoreCase = true)
            }

            val digest = apk.str("digest")
                ?.substringAfter("sha256:", "")
                ?.takeIf { it.length == 64 }
                ?.lowercase()

            return ReleaseInfo(
                tag = tag,
                versionName = AppVersion.fromTag(tag),
                title = root.str("name") ?: tag,
                notes = root.str("body").orEmpty().trim(),
                publishedAt = root.str("published_at").orEmpty(),
                apkAssetName = apk.str("name").orEmpty(),
                apkUrl = apk.str("browser_download_url")
                    ?: throw IllegalStateException("The APK asset has no download URL."),
                apkSize = apk.long("size") ?: 0L,
                checksumUrl = checksumAsset?.str("browser_download_url"),
                checksumSha256 = digest,
                releaseUrl = root.str("html_url").orEmpty()
            )
        }

        /** Reads a checksum file: `<hex>  <file>` or a bare hex string. */
        fun parseChecksumFile(text: String): String? =
            text.split(Regex("\\s+"))
                .firstOrNull { token ->
                    token.length == 64 &&
                        token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                }
                ?.lowercase()

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.bool(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.booleanOrNull

        private fun JsonObject.long(key: String): Long? =
            (this[key] as? JsonPrimitive)?.longOrNull
    }
}
