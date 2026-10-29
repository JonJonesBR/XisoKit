package io.github.jonjonesbr.xisokit.core

import java.io.InputStream
import java.security.MessageDigest

/** Dados lidos do cabeçalho do executável do Xbox (default.xbe). */
data class XbeInfo(
    val titleName: String,
    val titleId: Long,
    val regionFlags: Long,
    val version: Long,
    val discNumber: Long,
) {
    val titleIdText: String
        get() {
            val a = ((titleId shr 24) and 0xFF).toInt()
            val b = ((titleId shr 16) and 0xFF).toInt()
            val hex = "%08X".format(titleId)
            return if (a in 0x20..0x7E && b in 0x20..0x7E) {
                "%c%c-%03d (%s)".format(a.toChar(), b.toChar(), titleId and 0xFFFF, hex)
            } else hex
        }

    val isNorthAmerica: Boolean get() = regionFlags and REGION_NORTH_AMERICA != 0L
    val isJapan: Boolean get() = regionFlags and REGION_JAPAN != 0L
    val isRestOfWorld: Boolean get() = regionFlags and REGION_REST_OF_WORLD != 0L
    val isManufacturing: Boolean get() = regionFlags and REGION_MANUFACTURING != 0L

    companion object {
        const val REGION_NORTH_AMERICA = 0x1L
        const val REGION_JAPAN = 0x2L
        const val REGION_REST_OF_WORLD = 0x4L
        const val REGION_MANUFACTURING = 0x80000000L

        /** Lê os primeiros bytes do .xbe (o cabeçalho cabe em bem menos de 64 KB). */
        fun parse(header: ByteArray): XbeInfo? {
            if (header.size < 0x178) return null
            if (header[0] != 'X'.code.toByte() || header[1] != 'B'.code.toByte() ||
                header[2] != 'E'.code.toByte() || header[3] != 'H'.code.toByte()
            ) return null
            val base = Xdvdfs.u32(header, 0x104)
            val certAddr = Xdvdfs.u32(header, 0x118)
            val cert = (certAddr - base)
            if (cert < 0 || cert + 0xB0 > header.size) return null
            val c = cert.toInt()
            val nameBytes = header.copyOfRange(c + 0x0C, c + 0x0C + 0x50)
            var name = String(nameBytes, Charsets.UTF_16LE)
            val nul = name.indexOf('\u0000')
            if (nul >= 0) name = name.substring(0, nul)
            return XbeInfo(
                titleName = name.trim(),
                titleId = Xdvdfs.u32(header, c + 0x08),
                regionFlags = Xdvdfs.u32(header, c + 0xA0),
                discNumber = Xdvdfs.u32(header, c + 0xA8),
                version = Xdvdfs.u32(header, c + 0xAC),
            )
        }

        /** Lê o cabeçalho e calcula o SHA-256 em uma única passada. */
        fun analyze(input: InputStream): Pair<XbeInfo?, String> {
            val md = MessageDigest.getInstance("SHA-256")
            val head = ByteArray(64 * 1024)
            var headLen = 0
            val buf = ByteArray(256 * 1024)
            input.use {
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    if (headLen < head.size) {
                        val k = minOf(n, head.size - headLen)
                        System.arraycopy(buf, 0, head, headLen, k)
                        headLen += k
                    }
                }
            }
            val info = parse(head.copyOf(headLen))
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            return info to hex
        }
    }
}

/** Compatibilidade com o port nativo Takedawn (Burnout 3, NTSC-U). */
object Takedawn {
    const val NTSC_U_SHA256 = "9f497acd82adb8edfb72cc31aeeb35010b45d32b13a3f1fc6540717ff24d13f9"
    const val BURNOUT3_TITLE_ID = 0x4541005BL

    enum class Status { COMPATIBLE, MODIFIED_NTSC_U, OTHER_REGION, OTHER_GAME, NOT_XBE }

    fun evaluate(info: XbeInfo?, sha256: String): Status = when {
        sha256.equals(NTSC_U_SHA256, ignoreCase = true) -> Status.COMPATIBLE
        info == null -> Status.NOT_XBE
        info.titleId != BURNOUT3_TITLE_ID -> Status.OTHER_GAME
        info.regionFlags and 0x1L != 0L -> Status.MODIFIED_NTSC_U
        else -> Status.OTHER_REGION
    }

}
