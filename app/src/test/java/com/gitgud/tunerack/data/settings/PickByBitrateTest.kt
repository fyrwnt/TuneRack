package com.gitgud.tunerack.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PickByBitrateTest {

    private data class S(val kbps: Int, val opus: Boolean)

    // What YouTube usually offers for a song.
    private val all = listOf(S(160, true), S(128, false), S(70, true), S(50, true), S(48, false))

    private fun pick(list: List<S>, max: Int) = pickByBitrate(list, max, { it.kbps }, { it.opus })

    @Test fun `high takes the best stream`() = assertEquals(S(160, true), pick(all, StreamQuality.High.maxKbps))

    @Test fun `normal caps at 128`() = assertEquals(S(128, false), pick(all, StreamQuality.Normal.maxKbps))

    @Test fun `data saver caps at 70`() = assertEquals(S(70, true), pick(all, StreamQuality.Low.maxKbps))

    @Test fun `opus wins at equal bitrate`() = assertEquals(S(128, true), pick(listOf(S(128, false), S(128, true)), 128))

    @Test fun `falls back to the smallest when everything is above the cap`() =
        assertEquals(S(128, false), pick(listOf(S(160, true), S(128, false)), 70))

    @Test fun `download data saver takes AAC 48`() {
        val aac = all.filter { !it.opus }
        assertEquals(S(48, false), pick(aac, DownloadQuality.Low.maxKbps))
        assertEquals(S(128, false), pick(aac, DownloadQuality.High.maxKbps))
    }
}
