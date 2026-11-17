package app.allfun.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `tags and bare versions are normalised`() {
        assertEquals("1.0.0", AppVersion.fromTag("v1.0.0"))
        assertEquals("1.0.0", AppVersion.fromTag("V1.0.0"))
        assertEquals("2.4.1", AppVersion.fromTag("2.4.1"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, AppVersion.compare("1.1", "1.1.0"))
        assertEquals(0, AppVersion.compare("v1", "1.0.0"))
    }

    @Test
    fun `numeric order is semantic rather than lexical`() {
        assertTrue(AppVersion.isNewer("1.0.10", "1.0.9"))
        assertTrue(AppVersion.isNewer("1.1.0", "1.0.99"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.99.99"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `build metadata and prerelease suffixes do not affect the core version`() {
        assertEquals(0, AppVersion.compare("1.2.3-beta.2", "1.2.3"))
        assertEquals(0, AppVersion.compare("1.2.3+45", "v1.2.3"))
    }

    @Test
    fun `version codes follow the documented scheme`() {
        assertEquals(10_000, AppVersion.toVersionCode("1.0.0"))
        assertEquals(10_001, AppVersion.toVersionCode("v1.0.1"))
        assertEquals(10_100, AppVersion.toVersionCode("1.1.0"))
        assertEquals(20_000, AppVersion.toVersionCode("2.0.0"))
        assertEquals(10_203, AppVersion.toVersionCode("1.2.3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-numeric versions are rejected`() {
        AppVersion.components("nightly")
    }
}
