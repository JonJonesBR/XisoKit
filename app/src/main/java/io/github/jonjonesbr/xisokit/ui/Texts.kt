package io.github.jonjonesbr.xisokit.ui

import android.content.res.Resources
import io.github.jonjonesbr.xisokit.R
import io.github.jonjonesbr.xisokit.core.ImageLayout
import io.github.jonjonesbr.xisokit.core.Problem
import io.github.jonjonesbr.xisokit.core.ProblemKind
import io.github.jonjonesbr.xisokit.core.Takedawn
import io.github.jonjonesbr.xisokit.core.XbeInfo
import io.github.jonjonesbr.xisokit.core.XisoException

/** Traduz os códigos do núcleo para o idioma do app. */
fun Resources.problemText(p: Problem): String {
    fun arg(i: Int) = p.args.getOrElse(i) { "" }
    return when (p.kind) {
        ProblemKind.NOT_XBOX_IMAGE -> getString(R.string.problem_not_xbox)
        ProblemKind.CORRUPT_IMAGE -> getString(R.string.problem_corrupt, arg(0))
        ProblemKind.DUPLICATE_NAME -> getString(R.string.problem_duplicate, arg(0), arg(1))
        ProblemKind.FILE_TOO_LARGE -> getString(R.string.problem_too_large, arg(0))
        ProblemKind.INVALID_CHARACTERS -> getString(R.string.problem_invalid_chars, arg(0))
        ProblemKind.NAME_TOO_LONG -> getString(R.string.problem_name_too_long, arg(0))
        ProblemKind.EMPTY_NAME -> getString(R.string.problem_empty_name, arg(0))
        ProblemKind.TOO_MANY_ENTRIES -> getString(R.string.problem_too_many, arg(0))
        ProblemKind.FILE_CHANGED -> getString(R.string.problem_file_changed, arg(0))
        ProblemKind.TOO_DEEP -> getString(R.string.problem_too_deep, arg(0))
        ProblemKind.ACCENTED_NAME -> getString(R.string.problem_accented, arg(0))
        ProblemKind.LONG_NAME -> getString(R.string.problem_long_name, arg(0))
        ProblemKind.CANNOT_LIST_FOLDER -> getString(R.string.problem_cannot_list)
        ProblemKind.CANNOT_OPEN_FILE -> getString(R.string.problem_cannot_open)
        ProblemKind.CANNOT_WRITE_DESTINATION -> getString(R.string.problem_cannot_write)
        ProblemKind.CANNOT_CREATE -> getString(R.string.problem_cannot_create, arg(0))
    }
}

/** Texto de qualquer erro: problemas conhecidos traduzidos, o resto com a mensagem do sistema. */
fun Resources.errorText(t: Throwable): String {
    if (t is XisoException) {
        val lines = t.problems.take(8).map { problemText(it) }
        val more = if (t.problems.size > 8) "\n" + getString(R.string.and_more, t.problems.size - 8) else ""
        return if (t.problems.size == 1) lines.first()
        else getString(R.string.cannot_build) + "\n" + lines.joinToString("\n") { "• $it" } + more
    }
    return getString(R.string.error_generic, t.message ?: t.javaClass.simpleName)
}

fun Resources.layoutText(layout: ImageLayout): String = when (layout) {
    ImageLayout.XISO -> getString(R.string.layout_xiso)
    ImageLayout.XGD1 -> getString(R.string.layout_xgd1)
    ImageLayout.XGD2 -> getString(R.string.layout_xgd2)
    ImageLayout.XGD3 -> getString(R.string.layout_xgd3)
}

fun Resources.regionText(info: XbeInfo): String {
    val parts = ArrayList<String>()
    if (info.isNorthAmerica) parts.add(getString(R.string.region_na))
    if (info.isJapan) parts.add(getString(R.string.region_jp))
    if (info.isRestOfWorld) parts.add(getString(R.string.region_pal))
    if (info.isManufacturing) parts.add(getString(R.string.region_debug))
    return if (parts.isEmpty()) getString(R.string.region_unknown, "0x%08X".format(info.regionFlags))
    else parts.joinToString(", ")
}

fun Resources.takedawnText(status: Takedawn.Status): String = when (status) {
    Takedawn.Status.COMPATIBLE -> getString(R.string.takedawn_compatible)
    Takedawn.Status.MODIFIED_NTSC_U -> getString(R.string.takedawn_modified)
    Takedawn.Status.OTHER_REGION -> getString(R.string.takedawn_other_region)
    Takedawn.Status.OTHER_GAME -> getString(R.string.takedawn_other_game)
    Takedawn.Status.NOT_XBE -> getString(R.string.takedawn_not_xbe)
}
