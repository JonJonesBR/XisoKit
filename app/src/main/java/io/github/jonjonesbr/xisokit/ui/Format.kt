package io.github.jonjonesbr.xisokit.ui

import java.util.Locale

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = n.toDouble()
    var i = -1
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[i])
}

fun formatDuration(ms: Long): String {
    if (ms < 0) return "—"
    val s = ms / 1000
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min ${s % 60} s"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}

/** Nome de arquivo sugerido, sem caracteres problemáticos. */
fun safeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|()]"), "").trim().ifEmpty { "game" }
