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
        assertEquals("ColdplayVEVO", YouTubeAccount.cleanArtist("ColdplayVEVO"))
    }
}
