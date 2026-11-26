package com.gitgud.tunerack.data.download

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Writes iTunes-style tags (title, artist, cover art) into an MP4/M4A file, so downloads show up
 * properly in any music player.
 *
 * Only the `moov` box changes: any existing `moov/udta` is replaced by one holding `meta/ilst`.
 * Because that changes `moov`'s size, absolute file offsets after it are corrected: chunk offsets
 * (`stco`/`co64`) for regular MP4 and `tfhd` base offsets for fragmented MP4 (YouTube's format).
 * The optional `mfra` random-access index, which also stores absolute offsets, is dropped.
 * Audio data is copied byte for byte.
 */
object Mp4Tagger {

    class Tags(
        val title: String,
        val artist: String,
        val cover: ByteArray?,
        val coverIsPng: Boolean = false,
        /** Stored in the comment field; TuneRack puts the song's YouTube Music link here. */
        val comment: String? = null,
    )

    class ReadTags(val title: String?, val artist: String?, val comment: String?)

    /**
     * Reads title, artist and comment from an MP4 stream without loading the audio: everything but
     * `moov` is skipped. Returns null when the stream is not MP4.
     */
    fun read(input: InputStream): ReadTags? {
        val data = DataInputStream(input)
        val header = ByteArray(8)
        while (true) {
            if (!data.readFullyOrEnd(header)) return null
            var size = ByteBuffer.wrap(header, 0, 4).int.toLong() and 0xFFFFFFFFL
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8L
            if (size == 1L) { size = data.readLong(); headerSize = 16 }
            if (size == 0L || size < headerSize) return null
            if (type == "moov") {
                if (size > 64L * 1024 * 1024) return null
                val moov = ByteArray(size.toInt())
                System.arraycopy(header, 0, moov, 0, 8)
                data.readFully(moov, headerSize.toInt(), (size - headerSize).toInt())
                return readIlst(moov)
            }
            data.skipFully(size - headerSize)
        }
    }

    private fun readIlst(moov: ByteArray): ReadTags {
        val values = mutableMapOf<String, String>()
        forEachChild(moov, 8, moov.size) { type, start, end ->
            if (type != "udta") return@forEachChild
            forEachChild(moov, start + 8, end) { metaType, metaStart, metaEnd ->
                if (metaType != "meta") return@forEachChild
                forEachChild(moov, metaStart + 12, metaEnd) { ilstType, ilstStart, ilstEnd ->
                    if (ilstType != "ilst") return@forEachChild
                    forEachChild(moov, ilstStart + 8, ilstEnd) { item, itemStart, itemEnd ->
                        forEachChild(moov, itemStart + 8, itemEnd) { dataType, dataStart, dataEnd ->
                            if (dataType == "data" && dataEnd - dataStart > 16) {
                                values[item] = String(moov, dataStart + 16, dataEnd - dataStart - 16, Charsets.UTF_8)
                            }
                        }
                    }
                }
            }
        }
        return ReadTags(values["©nam"], values["©ART"], values["©cmt"])
    }

    private fun DataInputStream.readFullyOrEnd(buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun DataInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException()
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    fun tag(input: File, output: OutputStream, tags: Tags) {
        RandomAccessFile(input, "r").use { file ->
            val boxes = topLevelBoxes(file)
            val moov = boxes.firstOrNull { it.type == "moov" } ?: error("Not an MP4 file: no moov box")
            val oldMoov = file.read(moov.offset, moov.size)
            val newMoov = rebuildMoov(oldMoov, tags)
            val delta = (newMoov.size - oldMoov.size).toLong()

            for (box in boxes) {
                val after = box.offset > moov.offset
                when {
                    box === moov -> output.write(patchChunkOffsets(newMoov, delta, moovBeforeData = boxes.any { it.type == "mdat" && it.offset > moov.offset }))
                    box.type == "mfra" && delta != 0L -> Unit
                    box.type == "moof" && after && delta != 0L -> output.write(patchFragmentOffsets(file.read(box.offset, box.size), delta))
                    else -> file.copyTo(output, box.offset, box.size)
                }
            }
        }
    }

    private class Box(val type: String, val offset: Long, val size: Long)

    private fun topLevelBoxes(file: RandomAccessFile): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = 0L
        val length = file.length()
        while (offset + 8 <= length) {
            file.seek(offset)
            var size = file.readInt().toLong() and 0xFFFFFFFFL
            val type = String(ByteArray(4).also(file::readFully), Charsets.ISO_8859_1)
            if (size == 1L) size = file.readLong()
            if (size == 0L) size = length - offset
            require(size >= 8 && offset + size <= length) { "Corrupt MP4 box '$type' at $offset" }
            boxes += Box(type, offset, size)
            offset += size
        }
        return boxes
    }

    private fun RandomAccessFile.read(offset: Long, size: Long): ByteArray {
        require(size <= Int.MAX_VALUE) { "Box too large" }
        seek(offset)
        return ByteArray(size.toInt()).also(::readFully)
    }

    private fun RandomAccessFile.copyTo(output: OutputStream, offset: Long, size: Long) {
        seek(offset)
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Unexpected end of file")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    // ----- moov rewriting -----

    private fun rebuildMoov(moov: ByteArray, tags: Tags): ByteArray {
        val children = ByteArrayOutputStream()
        forEachChild(moov, 8, moov.size) { type, start, end ->
            if (type != "udta") children.write(moov, start, end - start)
        }
        children.write(udta(tags))
        return box("moov", children.toByteArray())
    }

    private fun udta(tags: Tags): ByteArray {
        val items = ByteArrayOutputStream()
        items.write(textItem("©nam", tags.title))
        if (tags.artist.isNotBlank()) items.write(textItem("©ART", tags.artist))
        tags.comment?.let { items.write(textItem("©cmt", it)) }
        tags.cover?.let { items.write(box("covr", dataAtom(if (tags.coverIsPng) 14 else 13, it))) }
        val hdlr = box("hdlr", int(0) + int(0) + "mdir".latin1() + "appl".latin1() + int(0) + int(0) + byteArrayOf(0))
        val meta = box("meta", int(0) + hdlr + box("ilst", items.toByteArray()))
        return box("udta", meta)
    }

    private fun textItem(type: String, value: String) = box(type, dataAtom(1, value.toByteArray(Charsets.UTF_8)))

    private fun dataAtom(typeIndicator: Int, payload: ByteArray) = box("data", int(typeIndicator) + int(0) + payload)

    // ----- offset patching -----

    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "moof", "traf")

    /** Shifts every stco/co64 entry by [delta] when moov sits in front of the media data. */
    private fun patchChunkOffsets(moov: ByteArray, delta: Long, moovBeforeData: Boolean): ByteArray {
        if (delta == 0L || !moovBeforeData) return moov
        val buffer = ByteBuffer.wrap(moov)
        walk(moov, 8, moov.size) { type, start, _ ->
            when (type) {
                "stco" -> {
                    val count = buffer.getInt(start + 12)
                    repeat(count) { i ->
                        val at = start + 16 + i * 4
                        buffer.putInt(at, ((buffer.getInt(at).toLong() and 0xFFFFFFFFL) + delta).toInt())
                    }
                }
                "co64" -> {
                    val count = buffer.getInt(start + 12)
                    repeat(count) { i ->
                        val at = start + 16 + i * 8
                        buffer.putLong(at, buffer.getLong(at) + delta)
                    }
                }
            }
        }
        return moov
    }

    /** Shifts explicit tfhd base-data-offsets (flag 0x1) inside a moof that follows moov. */
    private fun patchFragmentOffsets(moof: ByteArray, delta: Long): ByteArray {
        val buffer = ByteBuffer.wrap(moof)
        walk(moof, 8, moof.size) { type, start, _ ->
            if (type == "tfhd") {
                val flags = buffer.getInt(start + 8) and 0xFFFFFF
                if (flags and 0x1 != 0) {
                    val at = start + 16 // after header, version/flags and track_ID
                    buffer.putLong(at, buffer.getLong(at) + delta)
                }
            }
        }
        return moof
    }

    private fun walk(bytes: ByteArray, from: Int, to: Int, visit: (type: String, start: Int, end: Int) -> Unit) {
        forEachChild(bytes, from, to) { type, start, end ->
            visit(type, start, end)
            if (type in containers) walk(bytes, start + 8, end, visit)
        }
    }

    private fun forEachChild(bytes: ByteArray, from: Int, to: Int, visit: (type: String, start: Int, end: Int) -> Unit) {
        val buffer = ByteBuffer.wrap(bytes)
        var offset = from
        while (offset + 8 <= to) {
            val size = buffer.getInt(offset).toLong() and 0xFFFFFFFFL
            val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
            val end = if (size == 0L) to else (offset + size).toInt()
            require(size == 0L || size >= 8 && end <= to) { "Corrupt box '$type' inside container" }
            visit(type, offset, end)
            offset = end
        }
    }

    // ----- byte helpers -----

    private fun box(type: String, payload: ByteArray): ByteArray =
        int(8 + payload.size) + type.latin1() + payload

    private fun int(value: Int) = ByteBuffer.allocate(4).putInt(value).array()

    private fun String.latin1() = toByteArray(Charsets.ISO_8859_1)
}
