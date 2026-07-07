package com.beakoninc.locusnotes.data.takeout

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a Google Maps place URL (which carries only an opaque feature id)
 * to exact coordinates by fetching the map page and reading the coordinates
 * Google embeds in it. This is the same pin the user saved, so it beats
 * geocoding by name — the caller should fall back to geocoding only when
 * this returns null.
 */
@Singleton
class MapsUrlResolver @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(url: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val httpUrl = url.toHttpUrlOrNull() ?: return@withContext null
            val request = Request.Builder()
                .url(httpUrl.newBuilder().setQueryParameter("hl", "en").build())
                // A browser UA and pre-set consent cookie keep Google from
                // serving an interstitial instead of the map page
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "CONSENT=YES+; SOCS=CAISAiAD")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Maps page returned ${response.code} for $url")
                    return@withContext null
                }
                extractFromHtml(response.body?.string() ?: return@withContext null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve $url", e)
            null
        }
    }

    /** Exposed for tests. */
    fun extractFromHtml(html: String): Pair<Double, Double>? {
        // og:image static-map: ...staticmap?center=36.955011%2C-111.893296&...
        ogImageCenter.find(html)?.let { m ->
            validated(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        // window.APP_INITIALIZATION_STATE=[[[zoom,LNG,LAT],...
        appInitState.find(html)?.let { m ->
            validated(lat = m.groupValues[2], lon = m.groupValues[1])?.let { return it }
        }
        // Canonical /maps/place/.../@LAT,LNG,15z links inside the page
        atPath.find(html)?.let { m ->
            validated(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        return null
    }

    private fun validated(lat: String, lon: String): Pair<Double, Double>? {
        val latD = lat.toDoubleOrNull() ?: return null
        val lonD = lon.toDoubleOrNull() ?: return null
        return if (latD in -90.0..90.0 && lonD in -180.0..180.0) latD to lonD else null
    }

    companion object {
        private const val TAG = "MapsUrlResolver"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val ogImageCenter = Regex("""center=(-?\d{1,3}\.\d+)%2C(-?\d{1,3}\.\d+)""")
        private val appInitState =
            Regex("""APP_INITIALIZATION_STATE=\[\[\[-?[\d.]+,(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
        private val atPath = Regex("""/@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    }
}
