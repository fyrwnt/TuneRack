package com.gitgud.tunerack.data.charts

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

enum class ChartKind {
    SpotifyDaily,
    SpotifyWeekly,
    YouTube,
}

/** A chart page: kind plus Spotify country code ("global", "us", "id", ...). YouTube is global only. */
data class ChartSource(val kind: ChartKind, val country: String = GLOBAL) {
    val path: String
        get() = when (kind) {
            ChartKind.SpotifyDaily -> "spotify/country/${country}_daily.html"
            ChartKind.SpotifyWeekly -> "spotify/country/${country}_weekly.html"
            ChartKind.YouTube -> "youtube/"
        }

    val cacheName: String get() = if (kind == ChartKind.YouTube) kind.name else "${kind.name}-$country"

    companion object {
        const val GLOBAL = "global"
    }
}

data class ChartCountry(val code: String, val name: String)

/**
 * One chart position. Spotify entries carry a Spotify track id and need matching to YouTube Music;
 * YouTube entries carry the video id directly.
 */
data class ChartEntry(
    val rank: Int,
    /** kworb's movement column: "+3", "-1", "=", "NEW" or "RE". */
    val change: String,
    val title: String,
    val artist: String,
    val spotifyTrackId: String? = null,
    val videoId: String? = null,
    /** Daily/weekly streams or 24h views. */
    val plays: Long,
)

data class Chart(val source: ChartSource, val period: String, val entries: List<ChartEntry>)

/** Parses kworb.net chart tables. Rows that don't look like chart rows are skipped, not fatal. */
object KworbParser {

    fun parse(source: ChartSource, html: String): Chart = when (source.kind) {
        ChartKind.SpotifyDaily, ChartKind.SpotifyWeekly -> parseSpotify(source, html)
        ChartKind.YouTube -> parseYouTube(source, html)
    }

    private fun parseSpotify(source: ChartSource, html: String): Chart {
        val document = Jsoup.parse(html)
        // "Spotify Daily Chart - Global - 2026/11/24 | ..." -> "2026/11/24"
        val period = document.selectFirst("span.pagetitle")?.text()
            ?.substringBefore('|')?.substringAfterLast(" - ")?.trim().orEmpty()
        val entries = document.select("tbody > tr").mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 7) return@mapNotNull null
            val rank = cells[0].text().toIntOrNull() ?: return@mapNotNull null
            val text = cells[2]
            val track = text.selectFirst("a[href*=/track/]") ?: return@mapNotNull null
            val leadArtists = text.select("div > a[href*=/artist/]")
                .takeWhile { it.elementSiblingIndex() < track.elementSiblingIndex() }
                .joinToString(", ") { it.text() }
            ChartEntry(
                rank = rank,
                change = cells[1].text(),
                title = track.text(),
                artist = leadArtists.ifEmpty { text.text().substringBefore(" - ") },
                spotifyTrackId = track.attr("href").substringAfterLast('/').removeSuffix(".html"),
                plays = cells[6].number(),
            )
        }
        return Chart(source, period, entries)
    }

    private fun parseYouTube(source: ChartSource, html: String): Chart {
        val document = Jsoup.parse(html)
        // "Most viewed videos in the past 24 hours | 2026-11-25 22:15 EDT" -> "2026-11-25"
        val period = document.selectFirst("span.pagetitle")?.text()
            ?.substringAfter('|')?.trim()?.substringBefore(' ').orEmpty()
        val entries = document.select("tbody > tr").mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 4) return@mapNotNull null
            val rank = cells[0].text().toIntOrNull() ?: return@mapNotNull null
            val link = cells[2].selectFirst("a") ?: return@mapNotNull null
            val id = videoId(link.attr("href")) ?: return@mapNotNull null
            val (artist, title) = splitVideoTitle(link.text())
            ChartEntry(rank, cells[1].text(), title, artist, videoId = id, plays = cells[3].number())
        }
        return Chart(source, period, entries)
    }

    /** Countries with Spotify charts, from kworb's Spotify index page, Global first. */
    fun parseCountries(html: String): List<ChartCountry> {
        val countries = Jsoup.parse(html).select("tr").mapNotNull { row ->
            val link = row.selectFirst("a[href~=country/[a-z]+_daily\\.html]") ?: return@mapNotNull null
            val code = link.attr("href").substringAfter("country/").substringBefore("_daily")
            val name = row.selectFirst("td")?.text()?.trim().orEmpty()
            if (name.isEmpty()) null else ChartCountry(code, name)
        }.distinctBy { it.code }
        return countries.sortedWith(compareBy<ChartCountry> { it.code != ChartSource.GLOBAL }.thenBy { it.name })
    }

    /** Links are either kworb pages ("video/<id>.html") or YouTube URLs ("watch?v=<id>"). */
    private fun videoId(href: String): String? =
        Regex("video/([A-Za-z0-9_-]{11})\\.html").find(href)?.groupValues?.get(1)
            ?: Regex("[?&]v=([A-Za-z0-9_-]{11})").find(href)?.groupValues?.get(1)

    /**
     * "Shakira, Burna Boy - Dai Dai (Official Video)" -> ("Shakira, Burna Boy", "Dai Dai").
     * Titles with "|" credit lists ("Song - Film | Cast | Composer") don't follow Artist - Title,
     * so they keep the part before the first "|" as the title and no guessed artist.
     */
    internal fun splitVideoTitle(raw: String): Pair<String, String> {
        val tags = Regex("\\s*[(\\[](official|lyric|music|audio|visualizer|full)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
        if (raw.contains('|')) return "" to raw.substringBefore('|').replace(tags, "").trim()
        val cleaned = raw.replace(tags, "").trim()
        val parts = cleaned.split(" - ", limit = 2)
        return if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to cleaned
    }

    private fun Element.number(): Long = text().replace(",", "").toLongOrNull() ?: 0L
}
