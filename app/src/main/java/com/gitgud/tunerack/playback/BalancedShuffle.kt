package com.gitgud.tunerack.playback

import java.util.PriorityQueue
import kotlin.random.Random

/**
 * Spotify-style "balanced" shuffle.
 *
 * Plain random shuffles cluster: three songs by one artist in a row feels broken even though it is
 * random. Like Spotify's published approach, each artist's songs are spread evenly across the
 * playlist (with a random offset and jitter), and then any remaining same-artist neighbours are
 * repaired. Whenever it is possible at all, no two consecutive songs share an artist.
 */
object BalancedShuffle {

    /**
     * Returns a play order over `artists.indices`. [first], if given, stays first (the song
     * already playing when shuffle is switched on).
     */
    fun order(artists: List<String>, first: Int? = null, random: Random = Random.Default): IntArray {
        val n = artists.size
        if (n <= 1) return IntArray(n) { it }
        val keys = artists.map(::artistKey)

        val rest = (0 until n).filter { it != first }
        val dithered = dither(rest, keys, random)
        val order = if (first != null) listOf(first) + dithered else dithered

        val repaired = repair(order.toMutableList(), keys, pinFirst = first != null)
        val result = if (hasAdjacentRepeats(repaired, keys) && isSeparable(rest, keys, first?.let { keys[it] })) {
            constructive(rest, keys, first, random)
        } else {
            repaired
        }
        return result.toIntArray()
    }

    /** Collapses "Artist feat. X", "Artist, X", "Artist & X" to the lead artist. */
    fun artistKey(artist: String): String =
        artist.lowercase()
            .split(" feat. ", " feat ", " ft. ", " ft ", ",", " & ", " x ", " with ")
            .first()
            .trim()

    /** Spread each artist's songs evenly over [0, 1), then sort everything by position. */
    private fun dither(indices: List<Int>, keys: List<String>, random: Random): List<Int> {
        val positioned = mutableListOf<Pair<Double, Int>>()
        indices.groupBy { keys[it] }.values.forEach { group ->
            val songs = group.shuffled(random)
            val step = 1.0 / songs.size
            val offset = random.nextDouble() * step
            songs.forEachIndexed { i, index ->
                val jitter = (random.nextDouble() - 0.5) * step * 0.2
                positioned += (offset + i * step + jitter) to index
            }
        }
        return positioned.sortedBy { it.first }.map { it.second }
    }

    /** Greedy pass: when a song repeats the previous artist, swap in the next song that doesn't. */
    private fun repair(order: MutableList<Int>, keys: List<String>, pinFirst: Boolean): List<Int> {
        for (i in 1 until order.size) {
            if (keys[order[i]] != keys[order[i - 1]]) continue
            val swapWith = (i + 1 until order.size).firstOrNull { j ->
                keys[order[j]] != keys[order[i - 1]] &&
                    (j + 1 >= order.size || keys[order[i]] != keys[order[j + 1]] || j == i + 1)
            } ?: (i + 1 until order.size).firstOrNull { keys[order[it]] != keys[order[i - 1]] }
            if (swapWith != null) {
                order[i] = order[swapWith].also { order[swapWith] = order[i] }
            } else if (!pinFirst || i > 1) {
                // Nothing later fits: try moving this song earlier, between two other artists.
                val target = (1 until i - 1).firstOrNull { j ->
                    keys[order[j - 1]] != keys[order[i]] && keys[order[j]] != keys[order[i]]
                }
                if (target != null) order.add(target, order.removeAt(i))
            }
        }
        return order
    }

    private fun hasAdjacentRepeats(order: List<Int>, keys: List<String>) =
        order.zipWithNext().any { (a, b) -> keys[a] == keys[b] }

    /** True when some order of [rest] (after [lead]) can avoid adjacent same-artist songs. */
    private fun isSeparable(rest: List<Int>, keys: List<String>, lead: String?): Boolean {
        val counts = rest.groupingBy { keys[it] }.eachCount()
        val total = rest.size
        return counts.all { (key, count) ->
            val limit = if (key == lead) total / 2 else (total + 1) / 2
            count <= limit
        }
    }

    /**
     * Fallback that always succeeds when separation is possible: repeatedly take the artist with
     * the most songs left that differs from the previous one (random tie-breaks).
     */
    private fun constructive(rest: List<Int>, keys: List<String>, first: Int?, random: Random): List<Int> {
        val buckets = rest.groupBy { keys[it] }.mapValues { it.value.shuffled(random).toMutableList() }
        val tieBreak = buckets.keys.associateWith { random.nextInt() }
        val heap = PriorityQueue<String>(
            compareByDescending<String> { buckets.getValue(it).size }.thenBy { tieBreak.getValue(it) },
        )
        heap.addAll(buckets.keys)

        val result = mutableListOf<Int>()
        first?.let { result += it }
        var previous = first?.let { keys[it] }
        while (heap.isNotEmpty()) {
            val top = heap.poll()!!
            val pick = if (top == previous && heap.isNotEmpty()) heap.poll()!!.also { heap.add(top) } else top
            val bucket = buckets.getValue(pick)
            result += bucket.removeAt(bucket.lastIndex)
            previous = pick
            if (bucket.isNotEmpty()) heap.add(pick)
        }
        return result
    }
}
