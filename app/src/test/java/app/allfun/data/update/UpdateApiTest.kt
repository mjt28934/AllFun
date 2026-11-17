package app.allfun.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApiTest {

    private val digest = "9f2c" + "a".repeat(60)

    private val apkUrl = "https://github.com/mjt28934/AllFun/releases/download/" +
        "v1.0.0/AllFun-1.0.0-release.apk"

    private fun apkAsset(includeDigest: Boolean): String {
        val digestField = if (includeDigest) ""","digest":"sha256:$digest"""" else ""
        return """
            {
              "name": "AllFun-1.0.0-release.apk",
              "size": 10856817,
              "browser_download_url": "$apkUrl"
              $digestField
            }
        """.trimIndent()
    }

    private val checksumAsset = """
        {
          "name": "AllFun-1.0.0-release.apk.sha256",
          "size": 96,
          "browser_download_url": "$apkUrl.sha256"
        }
    """.trimIndent()

    private fun releaseJson(
        draft: Boolean = false,
        prerelease: Boolean = false,
        includeApk: Boolean = true,
        includeDigest: Boolean = true,
        includeChecksumAsset: Boolean = false
    ): String {
        val assets = buildList {
            if (includeApk) add(apkAsset(includeDigest))
            if (includeChecksumAsset) add(checksumAsset)
        }.joinToString(",")

        return """
            {
              "tag_name": "v1.0.0",
              "name": "AllFun 1.0.0",
              "body": "  First public release.\n- GitHub-based updates  ",
              "published_at": "2026-11-17T09:30:00Z",
              "draft": $draft,
              "prerelease": $prerelease,
              "html_url": "https://github.com/mjt28934/AllFun/releases/tag/v1.0.0",
              "assets": [$assets]
            }
        """.trimIndent()
    }

    @Test
    fun `published release exposes the APK and its digest`() {
        val release = UpdateApi.parseRelease(releaseJson())

        assertEquals("v1.0.0", release.tag)
        assertEquals("1.0.0", release.versionName)
        assertEquals("AllFun 1.0.0", release.title)
        assertEquals("First public release.\n- GitHub-based updates", release.notes)
        assertEquals("AllFun-1.0.0-release.apk", release.apkAssetName)
        assertEquals(10_856_817L, release.apkSize)
        assertEquals(apkUrl, release.apkUrl)
        assertEquals(digest, release.checksumSha256)
        assertNull(release.checksumUrl)
        assertEquals("https://github.com/mjt28934/AllFun/releases/tag/v1.0.0", release.releaseUrl)
    }

    @Test
    fun `checksum asset is used when the API has no digest`() {
        val release = UpdateApi.parseRelease(
            releaseJson(includeDigest = false, includeChecksumAsset = true)
        )

        assertNull(release.checksumSha256)
        assertTrue(release.checksumUrl.orEmpty().endsWith(".sha256"))
    }

    @Test(expected = NoReleasePublishedException::class)
    fun `drafts are not updates`() {
        UpdateApi.parseRelease(releaseJson(draft = true))
    }

    @Test(expected = NoReleasePublishedException::class)
    fun `prereleases are not stable updates`() {
        UpdateApi.parseRelease(releaseJson(prerelease = true))
    }

    @Test(expected = IllegalStateException::class)
    fun `release without an APK is rejected`() {
        UpdateApi.parseRelease(releaseJson(includeApk = false))
    }

    @Test(expected = IllegalStateException::class)
    fun `non JSON payload is rejected`() {
        UpdateApi.parseRelease("<html>rate limited</html>")
    }

    @Test
    fun `checksum files are read in both common formats`() {
        val expected = "0123456789abcdef".repeat(4)

        assertEquals(expected, UpdateApi.parseChecksumFile("$expected  AllFun-1.0.0-release.apk\n"))
        assertEquals(expected, UpdateApi.parseChecksumFile(expected.uppercase()))
        assertEquals(expected, UpdateApi.parseChecksumFile("$expected *AllFun-1.0.0-release.apk"))
        assertNull(UpdateApi.parseChecksumFile("not a checksum"))
        assertNull(UpdateApi.parseChecksumFile("abc123"))
    }
}
