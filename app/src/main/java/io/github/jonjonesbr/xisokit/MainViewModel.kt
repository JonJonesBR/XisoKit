package io.github.jonjonesbr.xisokit

import android.app.Application
import android.content.res.Resources
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jonjonesbr.xisokit.core.ImageLayout
import io.github.jonjonesbr.xisokit.core.Problem
import io.github.jonjonesbr.xisokit.core.ProblemKind
import io.github.jonjonesbr.xisokit.core.Takedawn
import io.github.jonjonesbr.xisokit.core.XbeInfo
import io.github.jonjonesbr.xisokit.core.XisoEntry
import io.github.jonjonesbr.xisokit.core.XisoException
import io.github.jonjonesbr.xisokit.core.XisoImage
import io.github.jonjonesbr.xisokit.core.XisoPlan
import io.github.jonjonesbr.xisokit.core.XisoWriter
import io.github.jonjonesbr.xisokit.storage.FolderScan
import io.github.jonjonesbr.xisokit.storage.SafStorage
import io.github.jonjonesbr.xisokit.ui.errorText
import io.github.jonjonesbr.xisokit.ui.formatBytes
import io.github.jonjonesbr.xisokit.ui.formatDuration
import io.github.jonjonesbr.xisokit.ui.layoutText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

data class XbeReport(val info: XbeInfo?, val sha256: String, val status: Takedawn.Status)

data class FolderInfo(
    val name: String,
    val files: Int,
    val dirs: Int,
    val bytes: Long,
    val estimatedSize: Long?,
    val xbe: XbeReport?,
    val warnings: List<Problem>,
    val errors: List<Problem>,
    val skippedHidden: Int,
)

data class ImageInfo(
    val name: String,
    val fileSize: Long,
    val layout: ImageLayout,
    val files: Int,
    val dirs: Int,
    val bytes: Long,
    val optimizedSize: Long?,
    val xbe: XbeReport?,
    val entries: List<XisoEntry>,
) {
    val isFullDisc: Boolean get() = layout != ImageLayout.XISO
}

data class JobState(
    val title: String,
    val done: Long,
    val total: Long,
    val current: String,
    val startedAt: Long,
)

data class UserMessage(val text: String, val isError: Boolean)

data class UiState(
    val folder: FolderInfo? = null,
    val image: ImageInfo? = null,
    val busyText: String? = null,
    val job: JobState? = null,
    val message: UserMessage? = null,
    val log: List<String> = emptyList(),
) {
    val working: Boolean get() = busyText != null || job != null
}

/** Cancelamento pedido pelo usuário, com o texto a mostrar. */
private class UserCancel(val text: String) : CancellationException(text)

/** Erro de uso (por exemplo, nada escolhido), com o texto já traduzido. */
private class UserError(val text: String) : Exception(text)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = SafStorage(app.contentResolver)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile
    private var cancelRequested = false
    private var folderScan: FolderScan? = null
    private var imageUri: Uri? = null
    private var lastProgress = 0L

    private val res: Resources get() = getApplication<Application>().resources
    private fun str(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)
    private fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        res.getQuantityString(id, count, *args)

    fun cancel() {
        cancelRequested = true
    }

    override fun onCleared() {
        // A escrita continua em segundo plano mesmo quando a tela é fechada; isto a interrompe.
        cancelRequested = true
        super.onCleared()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- pasta

    fun selectFolder(uri: Uri) = runTask(str(R.string.busy_reading_folder)) {
        val scan = storage.scanFolder(uri) { cancelRequested }
        var estimated: Long? = null
        var warnings = emptyList<Problem>()
        var errors = emptyList<Problem>()
        try {
            val plan = XisoWriter.plan(scan.root) { cancelRequested }
            estimated = plan.imageSize
            warnings = plan.warnings
        } catch (e: XisoException) {
            errors = e.problems
        }
        setBusy(str(R.string.busy_checking_xbe))
        var xbe: XbeReport? = null
        scan.xbeUri?.let { xbeUri ->
            try {
                xbe = analyzeXbe(storage.openInput(xbeUri)) { cancelRequested }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // O jogo continua utilizável; só não dá para mostrar o cabeçalho.
                log(str(R.string.log_error, res.errorText(e).lineSequence().first()))
            }
        }
        folderScan = scan
        _state.update {
            it.copy(folder = FolderInfo(scan.name, scan.files, scan.dirs, scan.bytes, estimated, xbe, warnings, errors, scan.skippedHidden))
        }
        log(plural(R.plurals.log_folder, scan.files, scan.name, scan.files, formatBytes(scan.bytes)))
    }

    fun createFromFolder(output: Uri) = runTask(str(R.string.busy_preparing)) {
        val scan = folderScan ?: throw UserError(str(R.string.need_folder))
        val plan = XisoWriter.plan(scan.root) { cancelRequested }
        val name = storage.displayName(output) ?: "image.iso"
        writeImage(plan, output, str(R.string.job_creating, name))
        val check = verifyWritten(output, plan)
        message(str(R.string.msg_xiso_created, name, formatBytes(plan.imageSize), check), isError = false)
    }

    // ---------------------------------------------------------------- imagem

    fun selectImage(uri: Uri) = runTask(str(R.string.busy_reading_image)) {
        storage.openImage(uri).use { src ->
            val img = XisoImage.open(src)
            val root = img.readTree { cancelRequested }
            val all = XisoImage.flatten(root)
            val files = all.filter { !it.isDirectory }
            val xbeEntry = XisoImage.findInRoot(root, "default.xbe")
            setBusy(str(R.string.busy_checking_xbe))
            var xbe: XbeReport? = null
            xbeEntry?.let { entry ->
                try {
                    xbe = analyzeXbe(img.openFile(entry)) { cancelRequested }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(str(R.string.log_error, res.errorText(e).lineSequence().first()))
                }
            }
            val optimized = try {
                val node = img.toNode(root, isCancelled = { cancelRequested })
                XisoWriter.plan(node, isCancelled = { cancelRequested }).imageSize
            } catch (e: XisoException) {
                null
            }
            val name = storage.displayName(uri) ?: "image.iso"
            imageUri = uri
            _state.update {
                it.copy(
                    image = ImageInfo(
                        name, src.length, img.layout, files.size, all.size - files.size,
                        files.sumOf { f -> f.size }, optimized, xbe, all,
                    ),
                )
            }
            log(plural(R.plurals.log_image, files.size, name, res.layoutText(img.layout), files.size))
        }
    }

    fun convertImage(output: Uri) = runTask(str(R.string.busy_preparing)) {
        val uri = imageUri ?: throw UserError(str(R.string.need_image))
        if (storage.sameDocument(uri, output)) throw UserError(str(R.string.need_other_output))
        storage.openImage(uri).use { src ->
            val img = XisoImage.open(src)
            val root = img.readTree { cancelRequested }
            val plan = XisoWriter.plan(img.toNode(root, isCancelled = { cancelRequested }), isCancelled = { cancelRequested })
            val name = storage.displayName(output) ?: "image.iso"
            writeImage(plan, output, str(R.string.job_generating, name))
            val check = verifyWritten(output, plan)
            message(str(R.string.msg_xiso_optimized, name, formatBytes(plan.imageSize), check), isError = false)
        }
    }

    fun extractImage(tree: Uri) = runTask(str(R.string.busy_preparing)) {
        val uri = imageUri ?: throw UserError(str(R.string.need_image))
        storage.openImage(uri).use { src ->
            val img = XisoImage.open(src)
            val root = img.readTree { cancelRequested }
            val all = XisoImage.flatten(root)
            val fileCount = all.count { !it.isDirectory }
            val total = all.filter { !it.isDirectory }.sumOf { it.size }
            val baseName = (storage.displayName(uri) ?: "game").substringBeforeLast('.').ifEmpty { "game" }
            startJob(str(R.string.job_extracting, baseName), total)
            val target = storage.createDirectory(storage.treeRoot(tree), baseName)
            val targetName = storage.displayName(target) ?: baseName
            val buf = ByteArray(1 shl 20)
            var done = 0L
            val cancelText = str(R.string.extract_cancelled, targetName)

            fun extractDir(dir: XisoEntry, parent: Uri) {
                for (entry in dir.children) {
                    if (cancelRequested) throw UserCancel(cancelText)
                    // Nomes vindos de uma imagem podem ser hostis; não deixa virar caminho no destino.
                    if (entry.name.isEmpty() || entry.name == "." || entry.name == ".." ||
                        entry.name.any { it == '/' || it == '\\' || it.code < 0x20 }
                    ) {
                        throw XisoException(ProblemKind.INVALID_CHARACTERS, entry.path)
                    }
                    if (entry.isDirectory) {
                        extractDir(entry, storage.createDirectory(parent, entry.name))
                        continue
                    }
                    val file = storage.createFile(parent, entry.name)
                    img.openFile(entry).use { input ->
                        storage.openOutput(file).use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                progress(done, total, entry.path)
                                if (cancelRequested) throw UserCancel(cancelText)
                            }
                        }
                    }
                }
            }

            try {
                extractDir(root, target)
            } catch (e: Throwable) {
                // Cancelou ou falhou no meio: não deixa uma pasta pela metade no destino.
                val removed = storage.delete(target)
                if (e is UserCancel) {
                    throw UserCancel(str(if (removed) R.string.extract_cancelled else R.string.extract_cancelled_kept, targetName))
                }
                throw e
            }
            message(plural(R.plurals.msg_extracted, fileCount, targetName, fileCount, formatBytes(total)), isError = false)
        }
    }

    fun verifyImage() = runTask(str(R.string.busy_preparing)) {
        val uri = imageUri ?: throw UserError(str(R.string.need_image))
        storage.openImage(uri).use { src ->
            val img = XisoImage.open(src)
            val files = XisoImage.flatten(img.readTree { cancelRequested }).filter { !it.isDirectory }
            val total = files.sumOf { it.size }
            startJob(str(R.string.job_verifying), total)
            val problems = ArrayList<String>()
            val buf = ByteArray(1 shl 20)
            val cancelText = str(R.string.verify_cancelled)
            var done = 0L
            for (entry in files) {
                if (cancelRequested) throw UserCancel(cancelText)
                if (!img.isInsideImage(entry)) {
                    problems.add(str(R.string.verify_beyond_end, entry.path))
                    done += entry.size
                    progress(done, total, entry.path)
                    continue
                }
                try {
                    img.openFile(entry).use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            progress(done, total, entry.path)
                            if (cancelRequested) throw UserCancel(cancelText)
                        }
                    }
                } catch (e: IOException) {
                    problems.add(str(R.string.verify_read_error, entry.path, e.message ?: e.javaClass.simpleName))
                }
            }
            if (problems.isEmpty()) {
                message(plural(R.plurals.verify_ok, files.size, files.size, formatBytes(total)), isError = false)
            } else {
                val more = if (problems.size > 10) "\n" + str(R.string.and_more, problems.size - 10) else ""
                message(plural(R.plurals.verify_problems, problems.size, problems.size) + "\n" + problems.take(10).joinToString("\n") + more, isError = true)
            }
        }
    }

    /** Lista de conteúdo como texto, para compartilhar. */
    fun contentsAsText(): String {
        val image = _state.value.image ?: return ""
        val sb = StringBuilder()
        sb.append(image.name).append(" — ").append(res.layoutText(image.layout)).append('\n')
        sb.append(
            str(
                R.string.contents_summary,
                plural(R.plurals.files_count, image.files, image.files),
                plural(R.plurals.folders_count, image.dirs, image.dirs),
                formatBytes(image.bytes),
            ),
        ).append("\n\n")
        for (e in image.entries.take(20_000)) {
            if (e.isDirectory) sb.append(e.path).append("/\n")
            else sb.append(e.path).append("  (").append(e.size).append(" bytes)\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- apoio

    private fun runTask(busy: String, block: suspend () -> Unit) {
        if (_state.value.working) return
        cancelRequested = false
        _state.update { it.copy(busyText = busy, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: UserCancel) {
                log(e.text)
                message(e.text, isError = false)
            } catch (e: UserError) {
                message(e.text, isError = true)
            } catch (e: CancellationException) {
                val text = str(R.string.op_cancelled)
                log(text)
                message(text, isError = false)
            } catch (e: Throwable) {
                val text = res.errorText(e)
                log(str(R.string.log_error, text.lineSequence().first()))
                message(text, isError = true)
            } finally {
                _state.update { it.copy(busyText = null, job = null) }
            }
        }
    }

    private fun setBusy(text: String) = _state.update { it.copy(busyText = text) }

    private fun startJob(title: String, total: Long) {
        lastProgress = 0L
        _state.update { it.copy(job = JobState(title, 0, total, "", SystemClock.elapsedRealtime())) }
    }

    private fun progress(done: Long, total: Long, path: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgress < 150 && done < total) return
        lastProgress = now
        _state.update { it.copy(job = it.job?.copy(done = done, total = total, current = path)) }
    }

    private fun writeImage(plan: XisoPlan, output: Uri, title: String) {
        startJob(title, plan.dataBytes)
        val started = SystemClock.elapsedRealtime()
        try {
            storage.openOutput(output).use { os ->
                XisoWriter.write(plan, os) { done, total, path ->
                    progress(done, total, path)
                    !cancelRequested
                }
            }
        } catch (e: Throwable) {
            storage.delete(output)
            throw e
        }
        log(str(R.string.log_done, title, formatDuration(SystemClock.elapsedRealtime() - started)))
    }

    private fun verifyWritten(uri: Uri, plan: XisoPlan): String = try {
        _state.update { it.copy(job = null, busyText = str(R.string.busy_checking_output)) }
        storage.openImage(uri).use { src ->
            val img = XisoImage.open(src)
            // Confere nome, tamanho e se cada arquivo cabe na imagem, não só a quantidade.
            val bad = plan.mismatches(img)
            if (bad.isEmpty()) plural(R.plurals.check_ok, plan.fileCount, plan.fileCount)
            else plural(R.plurals.check_mismatch, plan.fileCount, bad.size, plan.fileCount)
        }
    } catch (e: CancellationException) {
        str(R.string.op_cancelled)
    } catch (e: Exception) {
        str(R.string.check_failed, res.errorText(e))
    }

    private fun analyzeXbe(input: InputStream, isCancelled: () -> Boolean = { false }): XbeReport {
        val (info, sha) = XbeInfo.analyze(input, isCancelled)
        return XbeReport(info, sha, Takedawn.evaluate(info, sha))
    }

    private fun message(text: String, isError: Boolean) =
        _state.update { it.copy(message = UserMessage(text, isError)) }

    private fun log(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        _state.update { it.copy(log = (listOf("$time  $text") + it.log).take(40)) }
    }
}
