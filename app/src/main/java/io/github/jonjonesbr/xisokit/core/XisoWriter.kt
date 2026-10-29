package io.github.jonjonesbr.xisokit.core

import io.github.jonjonesbr.xisokit.core.Xdvdfs.SECTOR
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

/** Árvore de entrada para gravar uma XISO: venha de uma pasta ou de outra imagem. */
class XisoNode private constructor(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val open: (() -> InputStream)?,
) {
    val children: MutableList<XisoNode> = ArrayList()

    companion object {
        fun directory(name: String) = XisoNode(name, true, 0, null)
        fun file(name: String, size: Long, open: () -> InputStream) = XisoNode(name, false, size, open)
    }
}

/** Layout calculado antes de gravar: tamanho final, contagens e avisos. */
class XisoPlan internal constructor(
    internal val root: PlanDir,
    val totalSectors: Long,
    val fileCount: Int,
    val dirCount: Int,
    val dataBytes: Long,
    val warnings: List<Problem>,
) {
    val imageSize: Long get() = totalSectors * SECTOR
}

internal class PlanEntry(val node: XisoNode, val nameBytes: ByteArray, val path: String) {
    var left: PlanEntry? = null
    var right: PlanEntry? = null
    var offset = 0
    var sector = 0L
    var dir: PlanDir? = null
}

internal class PlanDir(val path: String, val preorder: List<PlanEntry>, val tableSize: Long) {
    var sector = 0L
    val isEmpty: Boolean get() = preorder.isEmpty()
    /** Bytes ocupados pela tabela (pasta vazia ocupa um setor só de 0xFF). */
    val tableBytes: Long get() = if (isEmpty) SECTOR.toLong() else Xdvdfs.alignSector(tableSize)
    /** Tamanho anotado na entrada da pasta-mãe (arredondado ao setor). */
    val entrySize: Long get() = tableBytes
}

object XisoWriter {

    fun interface Listener {
        /** Chamado durante a gravação. Devolva false para cancelar. */
        fun onProgress(doneBytes: Long, totalBytes: Long, currentPath: String): Boolean
    }

    /** Calcula o layout. Lança XisoException se algo impede a gravação. */
    fun plan(root: XisoNode): XisoPlan {
        val warnings = ArrayList<Problem>()
        val errors = ArrayList<Problem>()
        var files = 0
        var dirs = 0
        var bytes = 0L

        fun buildDir(node: XisoNode, path: String): PlanDir {
            val entries = ArrayList<PlanEntry>()
            for (child in node.children) {
                val childPath = if (path.isEmpty()) "/${child.name}" else "$path/${child.name}"
                val nameBytes = encodeName(child.name, childPath, warnings, errors) ?: continue
                if (!child.isDirectory && child.size > Xdvdfs.MAX_FILE_SIZE) {
                    errors.add(problem(ProblemKind.FILE_TOO_LARGE, childPath))
                    continue
                }
                entries.add(PlanEntry(child, nameBytes, childPath))
            }
            entries.sortWith { a, b -> Xdvdfs.compareNames(a.nameBytes, b.nameBytes) }
            for (i in 1 until entries.size) {
                if (Xdvdfs.compareNames(entries[i - 1].nameBytes, entries[i].nameBytes) == 0) {
                    errors.add(problem(ProblemKind.DUPLICATE_NAME, entries[i - 1].path, entries[i].path))
                }
            }

            fun bst(lo: Int, hi: Int): PlanEntry? {
                if (lo > hi) return null
                val mid = (lo + hi) ushr 1
                val e = entries[mid]
                e.left = bst(lo, mid - 1)
                e.right = bst(mid + 1, hi)
                return e
            }

            val top = bst(0, entries.size - 1)
            val preorder = ArrayList<PlanEntry>(entries.size)
            fun pre(e: PlanEntry?) {
                if (e == null) return
                preorder.add(e); pre(e.left); pre(e.right)
            }
            pre(top)

            var size = 0L
            for (e in preorder) {
                val len = Xdvdfs.align4(Xdvdfs.ENTRY_HEADER + e.nameBytes.size).toLong()
                if (Xdvdfs.sectors(size + len) > Xdvdfs.sectors(size)) size = Xdvdfs.alignSector(size)
                if (size / 4 > 0xFFFF) {
                    errors.add(problem(ProblemKind.TOO_MANY_ENTRIES, path.ifEmpty { "/" }))
                    break
                }
                e.offset = size.toInt()
                size += len
            }

            for (e in preorder) {
                if (e.node.isDirectory) {
                    dirs++
                    e.dir = buildDir(e.node, e.path)
                } else {
                    files++
                    bytes += e.node.size
                }
            }
            return PlanDir(path, preorder, size)
        }

        val rootDir = buildDir(root, "")
        if (errors.isNotEmpty()) throw XisoException(errors)

        var current = Xdvdfs.ROOT_SECTOR
        fun assign(dir: PlanDir) {
            dir.sector = current
            current += dir.tableBytes / SECTOR
            for (e in dir.preorder) if (!e.node.isDirectory) {
                e.sector = current
                current += Xdvdfs.sectors(e.node.size)
            }
            for (e in dir.preorder) if (e.node.isDirectory) {
                assign(e.dir!!)
                e.sector = e.dir!!.sector
            }
        }
        assign(rootDir)
        val perModulus = Xdvdfs.FILE_MODULUS / SECTOR
        val total = (current + perModulus - 1) / perModulus * perModulus
        return XisoPlan(rootDir, total, files, dirs, bytes, warnings)
    }

    /** Grava a XISO em sequência (serve para documentos do Android, que não aceitam seek). */
    fun write(plan: XisoPlan, output: OutputStream, listener: Listener) {
        val out = Counter(BufferedOutputStream(output, 1 shl 20))
        val total = plan.dataBytes
        var done = 0L

        out.write(buildHeader(plan))
        out.write(buildVolumeDescriptor(plan))
        out.zeros(Xdvdfs.ROOT_SECTOR * SECTOR - out.count)

        val buf = ByteArray(1 shl 20)
        fun check(path: String) {
            if (!listener.onProgress(done, total, path)) throw CancellationException("Cancelled")
        }

        fun writeDir(dir: PlanDir) {
            expect(out, dir.sector)
            out.write(buildTable(dir))
            for (e in dir.preorder) {
                if (e.node.isDirectory) continue
                expect(out, e.sector)
                check(e.path)
                var left = e.node.size
                if (left > 0) {
                    e.node.open!!.invoke().use { input ->
                        while (left > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw XisoException(ProblemKind.FILE_CHANGED, e.path)
                            out.write(buf, 0, n)
                            left -= n
                            done += n
                            check(e.path)
                        }
                    }
                }
                val pad = Xdvdfs.alignSector(e.node.size) - e.node.size
                out.fill(pad, Xdvdfs.PAD)
            }
            for (e in dir.preorder) if (e.node.isDirectory) writeDir(e.dir!!)
        }

        writeDir(plan.root)
        out.zeros(plan.imageSize - out.count)
        out.flush()
        listener.onProgress(done, total, "")
    }

    private fun expect(out: Counter, sector: Long) {
        check(out.count == sector * SECTOR) { "Internal layout error: ${out.count} != ${sector * SECTOR}" }
    }

    private fun encodeName(name: String, path: String, warnings: MutableList<Problem>, errors: MutableList<Problem>): ByteArray? {
        if (name.isEmpty()) {
            errors.add(problem(ProblemKind.EMPTY_NAME, path)); return null
        }
        val encoder = Charsets.ISO_8859_1.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val bytes = try {
            val bb = encoder.encode(java.nio.CharBuffer.wrap(name))
            ByteArray(bb.remaining()).also { bb.get(it) }
        } catch (e: Exception) {
            errors.add(problem(ProblemKind.INVALID_CHARACTERS, path))
            return null
        }
        if (bytes.size > Xdvdfs.MAX_NAME_BYTES) {
            errors.add(problem(ProblemKind.NAME_TOO_LONG, path)); return null
        }
        if (bytes.any { (it.toInt() and 0xFF) > 0x7E }) {
            warnings.add(problem(ProblemKind.ACCENTED_NAME, path))
        }
        if (bytes.size > 42) warnings.add(problem(ProblemKind.LONG_NAME, path))
        return bytes
    }

    private fun buildHeader(plan: XisoPlan): ByteArray {
        val h = ByteArray(Xdvdfs.HEADER_OFFSET.toInt())
        // Descritores ISO 9660 mínimos, iguais aos do extract-xiso (ajudam outros programas a reconhecer o tamanho).
        val base = 0x8000
        val pvd = byteArrayOf(1, 'C'.code.toByte(), 'D'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), 1)
        pvd.copyInto(h, base)
        Xdvdfs.put32(h, base + 80, plan.totalSectors)
        Xdvdfs.put32be(h, base + 84, plan.totalSectors)
        byteArrayOf(1, 0, 0, 1, 1, 0, 0, 1, 0, 8, 8, 0).copyInto(h, base + 120)
        for (i in base + 190 until base + 813) h[i] = 0x20
        val date = "0000000000000000".toByteArray(Charsets.US_ASCII)
        var p = base + 813
        repeat(4) { date.copyInto(h, p); p += 17 }
        h[p] = 1
        val term = byteArrayOf(0xFF.toByte(), 'C'.code.toByte(), 'D'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), 1)
        term.copyInto(h, base + SECTOR)
        val tag = "in!xisokit".toByteArray(Charsets.US_ASCII)
        tag.copyInto(h, 31337)
        return h
    }

    private fun buildVolumeDescriptor(plan: XisoPlan): ByteArray {
        val v = ByteArray(SECTOR)
        Xdvdfs.MAGIC.copyInto(v, 0)
        Xdvdfs.put32(v, 20, plan.root.sector)
        Xdvdfs.put32(v, 24, if (plan.root.isEmpty) SECTOR.toLong() else plan.root.tableSize)
        val filetime = (System.currentTimeMillis() + 11_644_473_600_000L) * 10_000L
        for (i in 0 until 8) v[28 + i] = (filetime shr (8 * i)).toByte()
        Xdvdfs.MAGIC.copyInto(v, SECTOR - Xdvdfs.MAGIC.size)
        return v
    }

    private fun buildTable(dir: PlanDir): ByteArray {
        val t = ByteArray(dir.tableBytes.toInt())
        t.fill(Xdvdfs.PAD)
        for (e in dir.preorder) {
            val o = e.offset
            Xdvdfs.put16(t, o, (e.left?.offset ?: 0) / 4)
            Xdvdfs.put16(t, o + 2, (e.right?.offset ?: 0) / 4)
            Xdvdfs.put32(t, o + 4, e.sector)
            val size = if (e.node.isDirectory) e.dir!!.entrySize else e.node.size
            Xdvdfs.put32(t, o + 8, size)
            t[o + 12] = (if (e.node.isDirectory) Xdvdfs.ATTR_DIR else Xdvdfs.ATTR_ARCHIVE).toByte()
            t[o + 13] = e.nameBytes.size.toByte()
            e.nameBytes.copyInto(t, o + Xdvdfs.ENTRY_HEADER)
        }
        return t
    }

    private class Counter(private val out: OutputStream) {
        var count = 0L
            private set
        private val zero = ByteArray(64 * 1024)
        private val pad = ByteArray(SECTOR) { Xdvdfs.PAD }

        fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
            out.write(b, off, len); count += len
        }

        fun zeros(n: Long) {
            var left = n
            while (left > 0) {
                val k = minOf(left, zero.size.toLong()).toInt()
                write(zero, 0, k); left -= k
            }
        }

        fun fill(n: Long, @Suppress("UNUSED_PARAMETER") value: Byte) {
            var left = n
            while (left > 0) {
                val k = minOf(left, pad.size.toLong()).toInt()
                write(pad, 0, k); left -= k
            }
        }

        fun flush() = out.flush()
    }
}
