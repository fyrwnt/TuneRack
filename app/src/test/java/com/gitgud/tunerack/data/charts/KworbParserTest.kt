package com.gitgud.tunerack.data.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs the parser over trimmed copies of real kworb pages (first rows of each chart). */
class KworbParserTest {

    private fun fixture(name: String) = javaClass.getResource("/kworb/$name")!!.readText()

    @Test
    fun `spotify daily`() {
        val chart = KworbParser.parse(ChartSource(ChartKind.SpotifyDaily), fixture("global_daily.html"))
        assertEquals("2026/11/24", chart.period)
        assertEquals(6, chart.entries.size)
        val first = chart.entries.first()
        assertEquals(1, first.rank)
        assertEquals("=", first.change)
        assertEquals("Flower In A Hurricane", first.title)
        // Featured artists in "(w/ ...)" are not part of the lead artist.
        assertEquals("Justice", first.artist)
        assertEquals("4HCOHRCSQelz2GPX8iTamO", first.spotifyTrackId)
        assertEquals(4_979_768L, first.plays)
        assertNull(first.videoId)
        assertEquals((1..6).toList(), chart.entries.map { it.rank })
    }

    @Test
    fun `spotify weekly`() {
        val chart = KworbParser.parse(ChartSource(ChartKind.SpotifyWeekly), fixture("global_weekly.html"))
        assertEquals("2026/11/19", chart.period)
        val second = chart.entries[1]
        assertEquals("Ashe", second.artist)
        assertEquals("Stop The Wedding!", second.title)
        assertEquals("+1", second.change)
        assertEquals(30_901_084L, second.plays)
    }

    @Test
    fun `youtube daily handles both link styles`() {
        val chart = KworbParser.parse(ChartSource(ChartKind.YouTube), fixture("youtube.html"))
        assertEquals("2026-11-25", chart.period)
        assertEquals(6, chart.entries.size)
        assertEquals("jZEA2mMwL1k", chart.entries[0].videoId)
        // Row 4 links straight to youtube.com/watch?v=...
        assertEquals("qA_doR1lYe8", chart.entries[3].videoId)
        assertEquals("NEW", chart.entries[3].change)
        assertEquals(12_544_133L, chart.entries[0].plays)
        val shakira = chart.entries[4]
        assertEquals("Shakira, Burna Boy", shakira.artist)
        assertEquals("Dai Dai", shakira.title)
    }

    @Test
    fun `video titles without an artist keep the whole title`() {
        assertEquals("Artist" to "Song", KworbParser.splitVideoTitle("Artist - Song [Official Lyric Video]"))
        assertEquals("" to "Plain Title", KworbParser.splitVideoTitle("Plain Title"))
    }

    @Test
    fun `credit list titles are not split into a guessed artist`() {
        assertEquals("" to "Mutta Kalakki Music Video", KworbParser.splitVideoTitle("Mutta Kalakki Music Video | Youth | Ken Karunaas"))
        assertEquals(
            "" to "Diviki Diviki - January 12 Vidudala",
            KworbParser.splitVideoTitle("Diviki Diviki - January 12 Vidudala| Venkatesh,Kalyan Ram"),
        )
        assertEquals("" to "Ammiye NIJJAR", KworbParser.splitVideoTitle("Ammiye (Official Music Video) NIJJAR | Dr Zeus | Autobiography"))
    }

    @Test
    fun `country list`() {
        val html = """<table><tr><td class="mp text">Indonesia</td><td><a href="country/id_daily.html">Daily</a></td></tr>
            <tr><td class="mp text">Global</td><td><a href="country/global_daily.html">Daily</a></td></tr>
            <tr><td class="mp text">Argentina</td><td><a href="country/ar_daily.html">Daily</a></td></tr></table>"""
        val countries = KworbParser.parseCountries(html)
        assertEquals(listOf("global", "ar", "id"), countries.map { it.code })
        assertEquals("Indonesia", countries.last().name)
    }
}
