package io.github.jonjonesbr.xisokit.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import io.github.jonjonesbr.xisokit.core.ChannelImageSource
import io.github.jonjonesbr.xisokit.core.ImageSource
import io.github.jonjonesbr.xisokit.core.ProblemKind
import io.github.jonjonesbr.xisokit.core.XisoException
import io.github.jonjonesbr.xisokit.core.XisoNode
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

class FolderScan(
    val name: String,
    val root: XisoNode,
    val files: Int,
    val dirs: Int,
    val bytes: Long,
    val xbeUri: Uri?,
    val skippedHidden: Int,
)

/** Acesso a pastas e arquivos pelo seletor do Android (Storage Access Framework). */
class SafStorage(private val resolver: ContentResolver) {

    fun scanFolder(treeUri: Uri, isCancelled: () -> Boolean): FolderScan {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val name = displayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)) ?: "game"
        val root = XisoNode.directory("")
        var files = 0
        var dirs = 0
        var bytes = 0L
        var xbe: Uri? = null
        var hidden = 0

        fun walk(docId: String, node: XisoNode, depth: Int) {
            if (isCancelled()) throw CancellationException("Cancelled")
            if (depth > 64) throw XisoException(ProblemKind.TOO_DEEP, name)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = resolver.query(childrenUri, PROJECTION, null, null, null)
                ?: throw XisoException(ProblemKind.CANNOT_LIST_FOLDER)
            val subdirs = ArrayList<Pair<String, XisoNode>>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    if (isCancelled()) throw CancellationException("Cancelled")
                    val id = c.getString(0) ?: continue
                    val childName = c.getString(1) ?: continue
                    if (childName.startsWith(".")) {
                        hidden++
                        continue
                    }
                    val mime = c.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        val dir = XisoNode.directory(childName)
                        node.children.add(dir)
                        subdirs.add(id to dir)
                        dirs++
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        // Alguns provedores não informam o tamanho; sem ele o arquivo sairia vazio.
                        val size = if (!c.isNull(3)) c.getLong(3) else -1L
                        val realSize = if (size >= 0) size else lengthOf(uri)
                        if (realSize < 0) throw XisoException(ProblemKind.UNKNOWN_SIZE, childName)
                        node.children.add(XisoNode.file(childName, realSize) { openInput(uri) })
                        files++
                        bytes += realSize
                        if (depth == 0 && childName.equals("default.xbe", ignoreCase = true)) xbe = uri
                    }
                }
            }
            for ((id, dir) in subdirs) walk(id, dir, depth + 1)
        }

        walk(rootId, root, 0)
        return FolderScan(name, root, files, dirs, bytes, xbe, hidden)
    }

    fun openInput(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw XisoException(ProblemKind.CANNOT_OPEN_FILE)

    fun openOutput(uri: Uri): OutputStream {
        val truncating = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            null
        }
        return truncating ?: resolver.openOutputStream(uri, "w")
            ?: throw XisoException(ProblemKind.CANNOT_WRITE_DESTINATION)
    }

    fun openImage(uri: Uri): ImageSource {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw XisoException(ProblemKind.CANNOT_OPEN_FILE)
        // AutoCloseInputStream fecha o descritor uma única vez, junto com o stream.
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        return ChannelImageSource(stream.channel, stream)
    }

    /** Tamanho informado pelo provedor, ou -1 quando ele não sabe. */
    private fun lengthOf(uri: Uri): Long {
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) return afd.length
            }
        } catch (e: Exception) {
            // Tenta pelo descritor abaixo.
        }
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /** Diz se duas URIs apontam para o mesmo documento (o seletor pode devolver URIs diferentes). */
    fun sameDocument(a: Uri, b: Uri): Boolean = try {
        a.authority == b.authority && DocumentsContract.getDocumentId(a) == DocumentsContract.getDocumentId(b)
    } catch (e: Exception) {
        a == b
    }

    fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Apaga um documento (ou uma pasta, com o conteúdo). False se o provedor não permitir. */
    fun delete(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (e: Exception) {
        // Sem permissão para apagar: quem chamou decide o que avisar.
        false
    }

    fun treeRoot(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    fun createDirectory(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
            ?: throw XisoException(ProblemKind.CANNOT_CREATE, name)

    fun createFile(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: throw XisoException(ProblemKind.CANNOT_CREATE, name)

    companion object {
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )

        /** Faz o seletor abrir já na pasta Download. */
        val DOWNLOAD_URI: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download",
        )
    }
}
