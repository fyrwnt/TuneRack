package com.gitgud.tunerack.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeAccountTest {

    @Test
    fun `parses YouTube durations`() {
        assertEquals(259L, YouTubeAccount.parseDuration("PT4M19S"))
        assertEquals(3720L, YouTubeAccount.parseDuration("PT1H2M"))
        assertEquals(45L, YouTubeAccount.parseDuration("PT45S"))
        assertEquals(0L, YouTubeAccount.parseDuration("P1D"))
        assertEquals(0L, YouTubeAccount.parseDuration(null))
    }

    @Test
    fun `auto-generated topic channels become the artist name`() {
        assertEquals("Coldplay", YouTubeAccount.cleanArtist("Coldplay - Topic"))
        assertEquals("Coldplay", YouTubeAccount.cleanArtist("ColdplayVEVO"))
    }

    @Test
    fun `liked videos become clean songs`() {
        val owl = YouTubeAccount.songFromVideo("a", "Owl City - To The Sky (Official Music Video)", "OwlCityVEVO", 219, null)!!
        assertEquals("Owl City", owl.artist)
        assertEquals("To The Sky", owl.title)
        val topic = YouTubeAccount.songFromVideo("b", "Hymn for the Weekend", "Coldplay - Topic", 259, null)!!
        assertEquals("Coldplay", topic.artist)
        assertEquals("Hymn for the Weekend", topic.title)
        val plain = YouTubeAccount.songFromVideo("c", "MEME TILL YOU'RE DEAD", "Cyranek", 103, null)!!
        assertEquals("Cyranek", plain.artist)
    }

    @Test
    fun `invisible titles are skipped`() {
        assertEquals(null, YouTubeAccount.songFromVideo("d", "\u3164\u3164 \u3164", "someone", 100, null))
    }
}
