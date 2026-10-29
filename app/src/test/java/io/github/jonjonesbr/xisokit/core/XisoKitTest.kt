package io.github.jonjonesbr.xisokit.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Imagem em memória; com offset, simula uma imagem completa do disco sem ocupar memória. */
private class MemorySource(private val data: ByteArray, private val offset: Long = 0) : ImageSource {
    override val length: Long get() = offset + data.size
    override fun read(position: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (position >= length) return -1
        val n = minOf(len.toLong(), length - position).toInt()
        for (i in 0 until n) {
            val p = position + i
            buf[off + i] = if (p < offset) 0 else data[(p - offset).toInt()]
        }
        return n
    }
    override fun close() {}
}

class XisoKitTest {

    private val random = Random(42)
    private val contents = HashMap<String, ByteArray>()

    private fun file(dir: XisoNode, path: String, name: String, size: Int) {
        val bytes = random.nextBytes(size)
        contents["$path/$name"] = bytes
        dir.children.add(XisoNode.file(name, size.toLong()) { ByteArrayInputStream(bytes) })
    }

    private fun sampleTree(): XisoNode {
        val root = XisoNode.directory("")
        file(root, "", "default.xbe", 5000)
        file(root, "", "empty.bin", 0)
        file(root, "", "exact.bin", 2048)
        val tracks = XisoNode.directory("Tracks").also { root.children.add(it) }
        // Nomes longos e muitos arquivos: a tabela da pasta ocupa vários setores.
        for (i in 0 until 250) file(tracks, "/Tracks", "Track_${"abCD"[i % 4]}$i" + "x".repeat(i % 60) + ".bin", random.nextInt(0, 6000))
        root.children.add(XisoNode.directory("vazia"))
        val deep = XisoNode.directory("Data").also { root.children.add(it) }
        val sub = XisoNode.directory("Sub").also { deep.children.add(it) }
        file(sub, "/Data/Sub", "fundo.dat", 70_000)
        return root
    }

    private fun build(root: XisoNode): ByteArray {
        val plan = XisoWriter.plan(root)
        val out = ByteArrayOutputStream()
        XisoWriter.write(plan, out) { _, _, _ -> true }
        assertEquals(plan.imageSize, out.size().toLong())
        return out.toByteArray()
    }

    private fun checkImage(img: XisoImage) {
        val files = XisoImage.flatten(img.readTree()).filter { !it.isDirectory }
        assertEquals(contents.size, files.size)
        for (entry in files) {
            val expected = contents[entry.path] ?: run { fail("Arquivo inesperado: ${entry.path}"); return }
            assertArrayEquals(entry.path, expected, img.openFile(entry).readBytes())
        }
    }

    @Test
    fun roundTrip() {
        val bytes = build(sampleTree())
        assertEquals(0L, bytes.size % Xdvdfs.FILE_MODULUS)
        checkImage(XisoImage.open(MemorySource(bytes)))
    }

    @Test
    fun fullDiscImageIsDetectedAndConverted() {
        val bytes = build(sampleTree())
        val full = XisoImage.open(MemorySource(bytes, offset = 0x18300000L))
        assertEquals(ImageLayout.XGD1, full.layout)
        val rebuilt = build(full.toNode(full.readTree()))
        val img = XisoImage.open(MemorySource(rebuilt))
        assertTrue(!img.isFullDiscImage)
        checkImage(img)
    }

    @Test
    fun duplicateNamesAreRejected() {
        val root = XisoNode.directory("")
        file(root, "", "ABC", 1)
        file(root, "", "abc", 1)
        try {
            XisoWriter.plan(root)
            fail("Deveria recusar nomes iguais")
        } catch (e: XisoException) {
            assertTrue(e.problems.any { it.kind == ProblemKind.DUPLICATE_NAME })
        }
    }

    @Test
    fun nameOrderMatchesXbox() {
        // Sem diferenciar maiúsculas, prefixo antes, e "_" depois de "Z" (ordem ASCII em maiúsculas).
        val names = listOf("b", "A", "_x", "a1", "Zeta", "a").map { it.toByteArray() }
        val sorted = names.sortedWith { a, b -> Xdvdfs.compareNames(a, b) }.map { String(it) }
        assertEquals(listOf("A", "a", "a1", "b", "Zeta", "_x"), sorted)
    }

    @Test
    fun plainIsoIsRejected() {
        try {
            XisoImage.open(MemorySource(ByteArray(0x20000)))
            fail("Deveria recusar imagem sem XDVDFS")
        } catch (e: XisoException) {
            assertEquals(ProblemKind.NOT_XBOX_IMAGE, e.problems.first().kind)
        }
    }

    @Test
    fun xbeHeaderIsParsed() {
        val b = ByteArray(0x1000)
        "XBEH".toByteArray().copyInto(b)
        Xdvdfs.put32(b, 0x104, 0x10000)
        Xdvdfs.put32(b, 0x118, 0x10400)
        val c = 0x400
        Xdvdfs.put32(b, c + 0x08, Takedawn.BURNOUT3_TITLE_ID)
        "Burnout 3".toByteArray(Charsets.UTF_16LE).copyInto(b, c + 0x0C)
        Xdvdfs.put32(b, c + 0xA0, 4)
        val (info, sha) = XbeInfo.analyze(ByteArrayInputStream(b))
        assertNotNull(info)
        assertEquals("Burnout 3", info!!.titleName)
        assertEquals("EA-091 (4541005B)", info.titleIdText)
        assertEquals(Takedawn.Status.OTHER_REGION, Takedawn.evaluate(info, sha))
    }
}
