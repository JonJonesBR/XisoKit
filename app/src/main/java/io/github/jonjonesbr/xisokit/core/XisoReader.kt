package io.github.jonjonesbr.xisokit.core

import io.github.jonjonesbr.xisokit.core.Xdvdfs.SECTOR
import java.io.InputStream

/** Um arquivo ou pasta dentro da imagem. */
class XisoEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sector: Long,
    val size: Long,
    val attributes: Int,
) {
    val children: MutableList<XisoEntry> = ArrayList()
}

/** Imagem de disco do Xbox aberta para leitura (XISO ou imagem completa). */
class XisoImage private constructor(
    val source: ImageSource,
    val layout: ImageLayout,
    val rootSector: Long,
    val rootSize: Long,
) {
    val partitionOffset: Long get() = layout.partitionOffset
    val isFullDiscImage: Boolean get() = layout != ImageLayout.XISO

    fun absolute(sector: Long): Long = partitionOffset + sector * SECTOR

    /** Lê toda a árvore de pastas. A raiz devolvida tem path "/". */
    fun readTree(): XisoEntry {
        val root = XisoEntry("", "/", true, rootSector, rootSize, Xdvdfs.ATTR_DIR)
        val visited = HashSet<Long>()
        var count = 0

        fun walk(dir: XisoEntry, depth: Int) {
            if (dir.size == 0L) return
            if (depth > 64) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
            if (!visited.add(dir.sector)) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
            val tableLen = Xdvdfs.alignSector(dir.size)
            if (tableLen > 64L * 1024 * 1024 || absolute(dir.sector) + tableLen > source.length) {
                throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
            }
            val table = ByteArray(tableLen.toInt())
            source.readFully(absolute(dir.sector), table)

            val pending = ArrayDeque<Int>()
            val seen = HashSet<Int>()
            pending.addLast(0)
            while (pending.isNotEmpty()) {
                val off = pending.removeLast()
                if (!seen.add(off)) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
                if (off + Xdvdfs.ENTRY_HEADER > table.size) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
                val left = Xdvdfs.u16(table, off)
                if (left == 0xFFFF) {
                    if (off == 0) break // pasta vazia
                    throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
                }
                val right = Xdvdfs.u16(table, off + 2)
                val start = Xdvdfs.u32(table, off + 4)
                val size = Xdvdfs.u32(table, off + 8)
                val attr = table[off + 12].toInt() and 0xFF
                val nameLen = table[off + 13].toInt() and 0xFF
                if (off + Xdvdfs.ENTRY_HEADER + nameLen > table.size) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
                val name = String(table, off + Xdvdfs.ENTRY_HEADER, nameLen, Charsets.ISO_8859_1)
                val path = if (dir.path == "/") "/$name" else "${dir.path}/$name"
                val isDir = (attr and Xdvdfs.ATTR_DIR) != 0
                dir.children.add(XisoEntry(name, path, isDir, start, size, attr))
                if (++count > 1_000_000) throw XisoException(ProblemKind.CORRUPT_IMAGE, dir.path)
                if (left != 0) pending.addLast(left * 4)
                if (right != 0) pending.addLast(right * 4)
            }
            dir.children.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            for (child in dir.children) if (child.isDirectory) walk(child, depth + 1)
        }

        walk(root, 0)
        return root
    }

    fun openFile(entry: XisoEntry): InputStream = RangeInputStream(source, absolute(entry.sector), entry.size)

    fun isInsideImage(entry: XisoEntry): Boolean = absolute(entry.sector) + entry.size <= source.length

    /** Converte a árvore lida em nós prontos para gravar uma nova XISO. */
    fun toNode(entry: XisoEntry): XisoNode {
        val node = if (entry.isDirectory) XisoNode.directory(entry.name)
        else XisoNode.file(entry.name, entry.size) { openFile(entry) }
        if (entry.isDirectory) entry.children.forEach { node.children.add(toNode(it)) }
        return node
    }

    companion object {
        fun open(source: ImageSource): XisoImage {
            val header = ByteArray(SECTOR)
            for (layout in ImageLayout.values()) {
                val pos = layout.partitionOffset + Xdvdfs.HEADER_OFFSET
                if (pos + SECTOR > source.length) continue
                source.readFully(pos, header)
                if (!startsWithMagic(header, 0) || !startsWithMagic(header, SECTOR - Xdvdfs.MAGIC.size)) continue
                return XisoImage(source, layout, Xdvdfs.u32(header, 20), Xdvdfs.u32(header, 24))
            }
            throw XisoException(ProblemKind.NOT_XBOX_IMAGE)
        }

        private fun startsWithMagic(b: ByteArray, at: Int): Boolean {
            for (i in Xdvdfs.MAGIC.indices) if (b[at + i] != Xdvdfs.MAGIC[i]) return false
            return true
        }

        fun flatten(root: XisoEntry): List<XisoEntry> {
            val out = ArrayList<XisoEntry>()
            fun rec(e: XisoEntry) {
                for (c in e.children) {
                    out.add(c)
                    if (c.isDirectory) rec(c)
                }
            }
            rec(root)
            return out
        }

        /** Procura um arquivo pelo nome na raiz, sem diferenciar maiúsculas. */
        fun findInRoot(root: XisoEntry, name: String): XisoEntry? =
            root.children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
    }
}
