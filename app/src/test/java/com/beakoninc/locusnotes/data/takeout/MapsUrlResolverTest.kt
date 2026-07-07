package com.beakoninc.locusnotes.data.takeout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsUrlResolverTest {

    private val resolver = MapsUrlResolver()

    @Test
    fun `extracts coordinates from og image static map`() {
        val html = """
            <meta property="og:image" content="https://maps.google.com/maps/api/staticmap?center=36.229668%2C-111.850145&amp;zoom=13&amp;size=900x900">
        """.trimIndent()
        assertEquals(36.229668 to -111.850145, resolver.extractFromHtml(html))
    }

    @Test
    fun `extracts coordinates from app initialization state`() {
        // Format is [[[zoom, LONGITUDE, LATITUDE]...
        val html = """window.APP_INITIALIZATION_STATE=[[[8000.0,-111.850145,36.229668],[...]]"""
        assertEquals(36.229668 to -111.850145, resolver.extractFromHtml(html))
    }

    @Test
    fun `extracts coordinates from at-path canonical link`() {
        val html = """<link rel="canonical" href="https://www.google.com/maps/place/Badwater/@36.2296,-111.8501,15z/">"""
        assertEquals(36.2296 to -111.8501, resolver.extractFromHtml(html))
    }

    @Test
    fun `prefers og image over other markers`() {
        val html = """
            <meta property="og:image" content="staticmap?center=10.5%2C20.5&zoom=13">
            window.APP_INITIALIZATION_STATE=[[[8000.0,-1.0,-2.0]
        """.trimIndent()
        assertEquals(10.5 to 20.5, resolver.extractFromHtml(html))
    }

    @Test
    fun `returns null for pages without coordinates`() {
        assertNull(resolver.extractFromHtml("<html><body>Before you continue to Google</body></html>"))
        assertNull(resolver.extractFromHtml(""))
    }

    @Test
    fun `rejects out of range values`() {
        assertNull(resolver.extractFromHtml("""staticmap?center=99999.0%2C-111.85&zoom"""))
    }
}
