package app.allfun.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression cover for the MangaDex "HTTP error 400" report: extensions are 1-based,
 * AllFun is 0-based, and passing 0 through made them request a negative offset.
 */
class KomikkuPageTest {

    @Test
    fun `the first AllFun page is page one for an extension`() {
        assertEquals(1, tachiyomiPage(0))
        assertEquals(2, tachiyomiPage(1))
        assertEquals(11, tachiyomiPage(10))
    }

    @Test
    fun `a negative page never becomes a negative extension offset`() {
        assertEquals(1, tachiyomiPage(-1))
        assertEquals(1, tachiyomiPage(-40))
    }
}
