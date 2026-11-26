package com.gitgud.tunerack.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tags ffmpeg-made fixtures (regular MP4 with moov first and last, fragmented MP4 with and without
 * explicit base offsets). Outputs land in build/mp4tagger so they can also be checked with ffprobe.
 */
class Mp4TaggerTest {

    private val fixtures = File(javaClass.getResource("/mp4/faststart.m4a")!!.toURI()).parentFile!!
    private val out = File("build/mp4tagger").apply { mkdirs() }
    private val cover = File(fixtures, "cover.jpg").readBytes()

    private fun tag(name: String): ByteArray {
        val target = File(out, name)
        target.outputStream().use {
            Mp4Tagger.tag(File(fixtures, name), it, Mp4Tagger.Tags("Hymn for the Weekend", "Coldplay", cover, comment = "https://music.youtube.com/watch?v=abc123"))
        }
        return target.readBytes()
    }

    private fun ByteArray.contains(text: String): Boolean {
        val needle = text.toByteArray(Charsets.UTF_8)
        return (0..size - needle.size).any { i -> needle.indices.all { this[i + it] == needle[it] } }
    }

    private fun check(name: String) {
        val bytes = tag(name)
        assertTrue("$name: ilst missing", bytes.contains("ilst"))
        assertTrue("$name: title missing", bytes.contains("Hymn for the Weekend"))
        assertTrue("$name: artist missing", bytes.contains("Coldplay"))
        assertTrue("$name: cover missing", bytes.contains("covr"))

        val read = File(out, name).inputStream().use(Mp4Tagger::read)!!
        assertEquals("Hymn for the Weekend", read.title)
        assertEquals("Coldplay", read.artist)
        assertEquals("https://music.youtube.com/watch?v=abc123", read.comment)
    }

    @Test
    fun `reading an untagged file gives no values`() {
        val read = File(fixtures, "fragmented.m4a").inputStream().use(Mp4Tagger::read)!!
        assertEquals(null, read.comment)
    }

    @Test fun `regular mp4 with moov first`() = check("faststart.m4a")

    @Test fun `regular mp4 with moov last`() = check("moovlast.m4a")

    @Test fun `fragmented mp4 like youtube`() = check("fragmented.m4a")

    @Test fun `fragmented mp4 with absolute base offsets`() = check("fragmented_abs.m4a")
}
