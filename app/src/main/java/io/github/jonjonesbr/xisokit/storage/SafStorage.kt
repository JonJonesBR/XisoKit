package io.github.jonjonesbr.xisokit.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import io.github.jonjonesbr.xisokit.core.ChannelImageSource
import io.github.jonjonesbr.xisokit.core.ImageSource
import io.github.jonjonesbr.xisokit.core.ProblemKind
import io.github.jonjonesbr.xisokit.core.XisoException
import io.github.jonjonesbr.xisokit.core.XisoNode
import java.io.Closeable
import java.io.FileInputStream
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
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        node.children.add(XisoNode.file(childName, size) { openInput(uri) })
                        files++
                        bytes += size
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
        val fis = FileInputStream(pfd.fileDescriptor)
        return ChannelImageSource(fis.channel, Closeable {
            fis.close()
            pfd.close()
        })
    }

    fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun delete(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            // Sem permissão para apagar: o arquivo incompleto fica, e a mensagem de erro avisa.
        }
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
