package com.gitgud.tunerack.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BalancedShuffleTest {

    private fun adjacentRepeats(order: IntArray, artists: List<String>): Int =
        order.toList().zipWithNext().count { (a, b) ->
            BalancedShuffle.artistKey(artists[a]) == BalancedShuffle.artistKey(artists[b])
        }

    private fun separable(artists: List<String>, first: Int?): Boolean {
        val keys = artists.map(BalancedShuffle::artistKey)
        val rest = artists.indices.filter { it != first }
        val total = rest.size
        return rest.groupingBy { keys[it] }.eachCount().all { (key, count) ->
            count <= if (first != null && key == keys[first]) total / 2 else (total + 1) / 2
        }
    }

    @Test
    fun `order is a permutation`() {
        val artists = List(40) { "Artist ${it % 7}" }
        val order = BalancedShuffle.order(artists, random = Random(1))
        assertEquals(artists.indices.toList(), order.sorted())
    }

    @Test
    fun `pinned first song stays first`() {
        val artists = List(25) { "Artist ${it % 4}" }
        repeat(50) { seed ->
            val order = BalancedShuffle.order(artists, first = 9, random = Random(seed))
            assertEquals(9, order.first())
            assertEquals(artists.indices.toList(), order.sorted())
        }
    }

    @Test
    fun `never repeats an artist back to back when avoidable`() {
        // Thousands of random playlists with skewed artist counts, with and without a pinned song.
        val random = Random(42)
        var checked = 0
        repeat(5000) {
            val size = random.nextInt(2, 60)
            val artistCount = random.nextInt(1, 12)
            val weights = List(artistCount) { random.nextDouble(0.2, 3.0) }
            val artists = List(size) {
                var r = random.nextDouble() * weights.sum()
                val pick = weights.indexOfFirst { w -> r -= w; r <= 0 }.let { i -> if (i < 0) artistCount - 1 else i }
                "Artist $pick"
            }
            val first = if (random.nextBoolean()) random.nextInt(size) else null
            if (!separable(artists, first)) return@repeat
            val order = BalancedShuffle.order(artists, first, Random(random.nextLong()))
            assertEquals(0, adjacentRepeats(order, artists))
            checked++
        }
        assertTrue("too few separable cases generated: $checked", checked > 1000)
    }

    @Test
    fun `featured and joint credits count as the lead artist`() {
        assertEquals("queen", BalancedShuffle.artistKey("Queen"))
        assertEquals("queen", BalancedShuffle.artistKey("Queen & David Bowie"))
        assertEquals("drake", BalancedShuffle.artistKey("Drake feat. Rihanna"))
        assertEquals("drake", BalancedShuffle.artistKey("Drake, Future"))
    }

    @Test
    fun `different seeds give different orders`() {
        val artists = List(30) { "Artist ${it % 6}" }
        val a = BalancedShuffle.order(artists, random = Random(1)).toList()
        val b = BalancedShuffle.order(artists, random = Random(2)).toList()
        assertFalse(a == b)
    }
}
