package app.allfun.data.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stremio splits catalogues from metadata: an addon may publish `catalog` and nothing
 * else. Asking such an addon for `meta` returned an HTML page, which the app reported as
 * "This provider returned a web page instead of data". These cases pin the rule that a
 * resource is only requested from an addon that declares it.
 */
class StremioManifestTest {

    private fun manifest(json: String) = Json.parseToJsonElement(json).jsonObject

    /** The manifest of the catalogue-only addon that triggered the bug. */
    private val catalogOnly = manifest(
        """
        {
          "id": "org.stremio.streamingcatalogs",
          "name": "Streaming Catalogs",
          "resources": ["catalog"],
          "types": ["movie", "series"],
          "idPrefixes": ["tt"],
          "catalogs": [{ "type": "movie", "id": "nfx", "name": "Netflix" }]
        }
        """.trimIndent()
    )

    private val withMeta = manifest(
        """
        {
          "id": "com.linvo.cinemeta",
          "name": "Cinemeta",
          "resources": [
            "catalog",
            { "name": "meta", "types": ["movie", "series"], "idPrefixes": ["tt"] }
          ],
          "types": ["movie", "series"],
          "idPrefixes": ["tt"]
        }
        """.trimIndent()
    )

    private val streamOnly = manifest(
        """
        {
          "id": "org.stremio.torrentio",
          "name": "Torrentio",
          "resources": ["stream"],
          "types": ["movie", "series"],
          "idPrefixes": ["tt"]
        }
        """.trimIndent()
    )

    @Test
    fun `catalog only addon is never asked for metadata`() {
        assertFalse(StremioManifest.supports(catalogOnly, "meta"))
        assertFalse(StremioManifest.accepts(catalogOnly, "meta", "movie", "tt0111161"))
        assertNull(StremioManifest.resource(catalogOnly, "meta"))
        // It still declares the catalogue it really serves.
        assertTrue(StremioManifest.supports(catalogOnly, "catalog"))
    }

    @Test
    fun `addon declaring meta answers only the types and prefixes it lists`() {
        assertTrue(StremioManifest.supports(withMeta, "meta"))
        assertTrue(StremioManifest.accepts(withMeta, "meta", "movie", "tt0111161"))
        assertTrue(StremioManifest.accepts(withMeta, "meta", "series", "tt0944947"))
        // A non-imdb id is outside the declared prefix, so it is not guessed at.
        assertFalse(StremioManifest.accepts(withMeta, "meta", "movie", "kitsu:123"))
        assertFalse(StremioManifest.accepts(withMeta, "meta", "channel", "tt0111161"))
    }

    @Test
    fun `stream only addon has no metadata but does stream`() {
        assertFalse(StremioManifest.supports(streamOnly, "meta"))
        assertTrue(StremioManifest.supports(streamOnly, "stream"))
        assertTrue(StremioManifest.accepts(streamOnly, "stream", "series", "tt0944947"))
    }

    @Test
    fun `bare string resource means every type and prefix`() {
        val bare = manifest("""{ "resources": ["meta"] }""")
        assertTrue(StremioManifest.accepts(bare, "meta", "movie", "anything"))
        assertEquals(emptySet<String>(), StremioManifest.resource(bare, "meta")!!.types)
    }

    @Test
    fun `legacy addon without a stored manifest keeps working`() {
        assertTrue(StremioManifest.supports(null, "meta"))
        assertTrue(StremioManifest.accepts(null, "meta", "movie", "tt0111161"))
    }
}
