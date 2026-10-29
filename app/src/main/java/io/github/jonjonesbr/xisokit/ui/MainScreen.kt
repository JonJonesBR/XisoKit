package io.github.jonjonesbr.xisokit.ui

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jonjonesbr.xisokit.FolderInfo
import io.github.jonjonesbr.xisokit.ImageInfo
import io.github.jonjonesbr.xisokit.JobState
import io.github.jonjonesbr.xisokit.MainViewModel
import io.github.jonjonesbr.xisokit.R
import io.github.jonjonesbr.xisokit.UserMessage
import io.github.jonjonesbr.xisokit.XbeReport
import io.github.jonjonesbr.xisokit.core.Problem
import io.github.jonjonesbr.xisokit.core.Takedawn
import io.github.jonjonesbr.xisokit.storage.SafStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val working = state.working

    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.selectFolder(uri)
    }
    val saveFromFolder = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.createFromFolder(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.selectImage(uri)
    }
    val saveFromImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.convertImage(uri)
    }
    val pickExtractTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.extractImage(uri)
    }

    var showContents by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = { LanguageMenu() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val job = state.job
            val busyText = state.busyText
            val message = state.message
            if (job != null) {
                item { JobCard(job, onCancel = vm::cancel) }
            } else if (busyText != null) {
                item { BusyCard(busyText, onCancel = vm::cancel) }
            }
            if (message != null) {
                item { MessageCard(message, onDismiss = vm::dismissMessage) }
            }
            item {
                FolderCard(
                    folder = state.folder,
                    enabled = !working,
                    onPick = { pickFolder.launch(SafStorage.DOWNLOAD_URI) },
                    onCreate = { name -> saveFromFolder.launch(name) },
                )
            }
            item {
                ImageCard(
                    image = state.image,
                    enabled = !working,
                    onPick = { pickImage.launch(arrayOf("*/*")) },
                    onShowContents = { showContents = true },
                    onExtract = { pickExtractTarget.launch(SafStorage.DOWNLOAD_URI) },
                    onConvert = { name -> saveFromImage.launch(name) },
                    onVerify = vm::verifyImage,
                )
            }
            item { TipsCard() }
            if (state.log.isNotEmpty()) item { LogCard(state.log) }
        }
    }

    val image = state.image
    if (showContents && image != null) {
        val shareTitle = stringResource(R.string.share_list)
        val shareSubject = stringResource(R.string.share_subject, image.name)
        ContentsDialog(
            image = image,
            onDismiss = { showContents = false },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                    putExtra(Intent.EXTRA_TEXT, vm.contentsAsText())
                }
                context.startActivity(Intent.createChooser(send, shareTitle))
            },
        )
    }
}

/** Seletor de idioma do app (Android 13 ou mais novo). Nos anteriores, o app segue o idioma do sistema. */
@Composable
private fun LanguageMenu() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        "" to stringResource(R.string.language_system),
        "pt-BR" to "Português",
        "en" to "English",
        "es" to "Español",
    )
    Box {
        TextButton(onClick = { open = true }) { Text("🌐 " + stringResource(R.string.language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (tag, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        setAppLanguage(context, tag)
                    },
                )
            }
        }
    }
}

private fun setAppLanguage(context: Context, tag: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WideButton(text: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
    }
}

@Composable
private fun FolderCard(folder: FolderInfo?, enabled: Boolean, onPick: () -> Unit, onCreate: (String) -> Unit) {
    SectionCard(stringResource(R.string.folder_title), stringResource(R.string.folder_subtitle)) {
        if (folder != null) {
            InfoRow(stringResource(R.string.label_folder), folder.name)
            InfoRow(
                stringResource(R.string.label_contents),
                stringResource(R.string.contents_summary, folder.files, folder.dirs, formatBytes(folder.bytes)),
            )
            folder.estimatedSize?.let {
                InfoRow(stringResource(R.string.label_final_xiso), stringResource(R.string.approx_size, formatBytes(it)))
            }
            if (folder.skippedHidden > 0) {
                Text(stringResource(R.string.hidden_skipped, folder.skippedHidden), style = MaterialTheme.typography.bodySmall)
            }
            if (folder.errors.isNotEmpty()) ProblemsText(folder.errors, isError = true)
            if (folder.warnings.isNotEmpty()) ProblemsText(folder.warnings, isError = false)
            if (folder.xbe != null) XbeBlock(folder.xbe)
            else ErrorText(stringResource(R.string.no_xbe_folder))
        }
        WideButton(
            stringResource(if (folder == null) R.string.choose_folder else R.string.change_folder),
            enabled,
            primary = folder == null,
            onClick = onPick,
        )
        if (folder != null) {
            WideButton(stringResource(R.string.create_xiso), enabled && folder.errors.isEmpty(), primary = true) {
                onCreate(safeFileName(folder.name) + ".iso")
            }
        }
    }
}

@Composable
private fun ImageCard(
    image: ImageInfo?,
    enabled: Boolean,
    onPick: () -> Unit,
    onShowContents: () -> Unit,
    onExtract: () -> Unit,
    onConvert: (String) -> Unit,
    onVerify: () -> Unit,
) {
    val res = LocalContext.current.resources
    SectionCard(stringResource(R.string.image_title), stringResource(R.string.image_subtitle)) {
        if (image != null) {
            InfoRow(stringResource(R.string.label_file), image.name)
            InfoRow(stringResource(R.string.label_format), res.layoutText(image.layout))
            InfoRow(stringResource(R.string.label_size), formatBytes(image.fileSize))
            InfoRow(
                stringResource(R.string.label_contents),
                stringResource(R.string.contents_summary, image.files, image.dirs, formatBytes(image.bytes)),
            )
            image.optimizedSize?.let { opt ->
                val saving = image.fileSize - opt
                val text = if (saving > 1024 * 1024) stringResource(R.string.as_xiso_with_saving, formatBytes(opt), formatBytes(saving))
                else stringResource(R.string.approx_size, formatBytes(opt))
                InfoRow(stringResource(R.string.label_as_xiso), text)
            }
            if (image.xbe != null) XbeBlock(image.xbe)
            else ErrorText(stringResource(R.string.no_xbe_image))
        }
        WideButton(
            stringResource(if (image == null) R.string.open_image else R.string.change_image),
            enabled,
            primary = image == null,
            onClick = onPick,
        )
        if (image != null) {
            WideButton(stringResource(R.string.view_contents), enabled, onClick = onShowContents)
            WideButton(stringResource(R.string.extract_to_folder), enabled, onClick = onExtract)
            val base = safeFileName(image.name.substringBeforeLast('.'))
            WideButton(
                stringResource(if (image.isFullDisc) R.string.convert_to_xiso else R.string.rewrite_xiso),
                enabled && image.optimizedSize != null,
                primary = image.isFullDisc,
            ) { onConvert("$base-xiso.iso") }
            WideButton(stringResource(R.string.verify_integrity), enabled, onClick = onVerify)
        }
    }
}

@Composable
private fun XbeBlock(report: XbeReport) {
    val clipboard = LocalClipboardManager.current
    val res = LocalContext.current.resources
    val colors = MaterialTheme.colorScheme
    val info = report.info
    // O veredito do Takedawn só aparece quando o jogo é o Burnout 3.
    val isBurnout3 = report.status == Takedawn.Status.COMPATIBLE || info?.titleId == Takedawn.BURNOUT3_TITLE_ID
    val background = when {
        info == null -> colors.errorContainer
        !isBurnout3 -> colors.surfaceVariant
        report.status == Takedawn.Status.COMPATIBLE -> colors.primaryContainer
        else -> colors.errorContainer
    }
    Surface(color = background, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.xbe_title), style = MaterialTheme.typography.titleSmall)
            if (info != null) {
                InfoRow(stringResource(R.string.label_game), info.titleName.ifEmpty { "—" })
                InfoRow(stringResource(R.string.label_title_id), info.titleIdText)
                InfoRow(stringResource(R.string.label_region), res.regionText(info))
                InfoRow(stringResource(R.string.label_version), "${info.version}")
                if (info.discNumber > 0) InfoRow(stringResource(R.string.label_disc), "${info.discNumber}")
            } else {
                Text(stringResource(R.string.xbe_invalid), style = MaterialTheme.typography.bodyMedium)
            }
            Text("SHA-256", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(report.sha256, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(report.sha256)) }) {
                Text(stringResource(R.string.copy_sha))
            }
            if (isBurnout3) {
                HorizontalDivider()
                Text(stringResource(R.string.takedawn_prefix, res.takedawnText(report.status)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ProblemsText(problems: List<Problem>, isError: Boolean) {
    val res = LocalContext.current.resources
    var expanded by remember { mutableStateOf(isError) }
    Column {
        if (isError) ErrorText(stringResource(R.string.cannot_build))
        else Text(stringResource(R.string.warnings_count, problems.size), style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            problems.take(30).forEach {
                val line = "• " + res.problemText(it)
                if (isError) ErrorText(line) else Text(line, style = MaterialTheme.typography.bodySmall)
            }
            if (problems.size > 30) Text(stringResource(R.string.and_more, problems.size - 30), style = MaterialTheme.typography.bodySmall)
        }
        if (!isError) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.hide_warnings else R.string.show_warnings))
            }
        }
    }
}

@Composable
private fun JobCard(job: JobState, onCancel: () -> Unit) {
    val fraction = if (job.total > 0) (job.done.toFloat() / job.total).coerceIn(0f, 1f) else 0f
    val elapsed = (SystemClock.elapsedRealtime() - job.startedAt).coerceAtLeast(1L)
    val speed = job.done * 1000 / elapsed
    val eta = if (speed > 0) (job.total - job.done) * 1000 / speed else -1L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.job_progress, (fraction * 100).toInt(), formatBytes(job.done), formatBytes(job.total)))
            Text(
                stringResource(R.string.job_speed, formatBytes(speed), formatDuration(eta)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (job.current.isNotEmpty()) {
                Text(job.current, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.keep_open), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun BusyCard(text: String, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(text, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun MessageCard(message: UserMessage, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isError) colors.errorContainer else colors.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            SelectionContainer { Text(message.text) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ok)) }
        }
    }
}

@Composable
private fun TipsCard() {
    SectionCard(stringResource(R.string.tips_title), stringResource(R.string.tips_subtitle)) {
        listOf(R.string.tip_any_game, R.string.tip_folder, R.string.tip_plain_iso, R.string.tip_takedawn).forEach {
            Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    SectionCard(stringResource(R.string.log_title), stringResource(R.string.log_subtitle)) {
        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ContentsDialog(image: ImageInfo, onDismiss: () -> Unit, onShare: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, image) {
        if (query.isBlank()) image.entries
        else image.entries.filter { it.path.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contents_title, image.files), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.filter_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.items_count, filtered.size), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered) { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                (if (entry.isDirectory) "📁 " else "📄 ") + entry.path,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (!entry.isDirectory) {
                                Spacer(Modifier.width(8.dp))
                                Text(formatBytes(entry.size), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onShare) { Text(stringResource(R.string.share_list)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
