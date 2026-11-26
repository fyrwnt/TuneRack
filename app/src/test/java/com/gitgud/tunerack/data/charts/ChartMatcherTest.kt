package com.gitgud.tunerack.data.charts

import com.gitgud.tunerack.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartMatcherTest {

    private val entry = ChartEntry(1, "=", "Stop The Wedding!", "Ashe", spotifyTrackId = "x", plays = 1)
    private fun track(id: String, title: String, artist: String) = Track(id, title, artist, 200, null)

    @Test
    fun `prefers the real song over karaoke and covers`() {
        val results = listOf(
            track("karaoke", "Stop The Wedding (Karaoke Version)", "Sing King"),
            track("cover", "Stop the Wedding", "Some Cover Band"),
            track("real", "Stop The Wedding!", "Ashe"),
        )
        assertEquals("real", ChartMatcher.best(entry, results)?.id)
    }

    @Test
    fun `ignores featuring credits and brackets`() {
        val feat = ChartEntry(1, "=", "Flower In A Hurricane", "Justice", spotifyTrackId = "y", plays = 1)
        val results = listOf(track("a", "Flower In A Hurricane (feat. Tame Impala)", "Justice & Tame Impala"))
        assertEquals("a", ChartMatcher.best(feat, results)?.id)
    }

    @Test
    fun `falls back to the top result when nothing fits`() {
        val results = listOf(track("first", "Something Else", "Nobody"), track("second", "Other", "Nobody"))
        assertEquals("first", ChartMatcher.best(entry, results)?.id)
    }

    @Test
    fun `no results gives no match`() = assertNull(ChartMatcher.best(entry, emptyList()))
}
