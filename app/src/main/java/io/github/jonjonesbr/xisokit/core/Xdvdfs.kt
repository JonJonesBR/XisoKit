package io.github.jonjonesbr.xisokit.core

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Constantes e utilitários do sistema de arquivos XDVDFS (disco do Xbox original). */
object Xdvdfs {
    const val SECTOR = 2048
    const val HEADER_OFFSET = 0x10000L
    const val ROOT_SECTOR = 0x108L
    const val FILE_MODULUS = 0x10000L
    const val ATTR_DIR = 0x10
    const val ATTR_ARCHIVE = 0x20
    const val PAD: Byte = 0xFF.toByte()
    const val MAX_NAME_BYTES = 255
    const val MAX_FILE_SIZE = 0xFFFFFFFFL
    const val ENTRY_HEADER = 14
    val MAGIC: ByteArray = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)

    fun sectors(size: Long): Long = (size + SECTOR - 1) / SECTOR
    fun alignSector(size: Long): Long = sectors(size) * SECTOR
    fun align4(n: Int): Int = (n + 3) and 3.inv()

    /** Mesma ordem usada pelo Xbox e pelo extract-xiso: ASCII sem diferenciar maiúsculas. */
    fun compareNames(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = upper(a[i])
            val y = upper(b[i])
            if (x != y) return if (x < y) -1 else 1
        }
        return a.size.compareTo(b.size)
    }

    private fun upper(b: Byte): Int {
        val v = b.toInt()
        return if (v in 'a'.code..'z'.code) v - 32 else v
    }

    fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    fun u32(b: ByteArray, o: Int): Long =
        (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)

    fun put16(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte()
    }

    fun put32(b: ByteArray, o: Int, v: Long) {
        for (i in 0 until 4) b[o + i] = (v shr (8 * i)).toByte()
    }

    fun put32be(b: ByteArray, o: Int, v: Long) {
        for (i in 0 until 4) b[o + i] = (v shr (8 * (3 - i))).toByte()
    }
}

/** Onde a partição de jogo começa em cada tipo de imagem (na ordem em que são testadas). */
enum class ImageLayout(val partitionOffset: Long) {
    XISO(0L),
    XGD1(0x18300000L),
    XGD2(0x0FD90000L),
    XGD3(0x02080000L),
}

/** Tipos de problema. O texto de cada um fica na interface, traduzido. */
enum class ProblemKind {
    NOT_XBOX_IMAGE,
    CORRUPT_IMAGE,
    DUPLICATE_NAME,
    FILE_TOO_LARGE,
    INVALID_CHARACTERS,
    NAME_TOO_LONG,
    EMPTY_NAME,
    TOO_MANY_ENTRIES,
    FILE_CHANGED,
    TOO_DEEP,
    ACCENTED_NAME,
    LONG_NAME,
    CANNOT_LIST_FOLDER,
    CANNOT_OPEN_FILE,
    CANNOT_WRITE_DESTINATION,
    CANNOT_CREATE,
}

data class Problem(val kind: ProblemKind, val args: List<String>) {
    override fun toString(): String = if (args.isEmpty()) kind.name else "${kind.name}: ${args.joinToString(", ")}"
}

fun problem(kind: ProblemKind, vararg args: String) = Problem(kind, args.toList())

class XisoException(val problems: List<Problem>) : IOException(problems.joinToString("; ")) {
    constructor(kind: ProblemKind, vararg args: String) : this(listOf(Problem(kind, args.toList())))
}

/** Acesso aleatório de leitura a uma imagem (arquivo local ou documento do Android). */
interface ImageSource : Closeable {
    val length: Long
    fun read(position: Long, buf: ByteArray, off: Int, len: Int): Int
}

fun ImageSource.readFully(position: Long, buf: ByteArray, off: Int = 0, len: Int = buf.size) {
    var done = 0
    while (done < len) {
        val r = read(position + done, buf, off + done, len - done)
        if (r <= 0) throw EOFException("Unexpected end of image at offset ${position + done}")
        done += r
    }
}

class ChannelImageSource(
    private val channel: FileChannel,
    private val closer: Closeable = channel,
) : ImageSource {
    override val length: Long get() = channel.size()
    override fun read(position: Long, buf: ByteArray, off: Int, len: Int): Int =
        channel.read(ByteBuffer.wrap(buf, off, len), position)

    override fun close() = closer.close()
}

/** Lê um trecho da imagem como se fosse um arquivo. */
class RangeInputStream(
    private val source: ImageSource,
    private val start: Long,
    private val size: Long,
) : InputStream() {
    private var pos = 0L

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == 1) one[0].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val remaining = size - pos
        if (remaining <= 0) return -1
        val n = source.read(start + pos, b, off, minOf(len.toLong(), remaining).toInt())
        if (n <= 0) throw EOFException("The image ended before the end of the file")
        pos += n
        return n
    }

    override fun available(): Int = minOf(size - pos, Int.MAX_VALUE.toLong()).toInt()
}
