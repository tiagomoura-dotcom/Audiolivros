package com.tiagomoura.audiolivros.chapters

import androidx.annotation.StringRes
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.database.ChapterSource

/** Origem curta, para a lista da biblioteca: "148 capítulos · da descrição". */
@StringRes
fun ChapterSource.shortLabel(): Int = when (this) {
    ChapterSource.EMBEDDED -> R.string.origem_curta_embedded
    ChapterSource.INFO_JSON -> R.string.origem_curta_info_json
    ChapterSource.DESCRIPTION -> R.string.origem_curta_description
    ChapterSource.SUBTITLE -> R.string.origem_curta_subtitle
    ChapterSource.SILENCE -> R.string.origem_curta_silence
    ChapterSource.MANUAL -> R.string.origem_curta_manual
}

/** Origem completa, para o cabeçalho da aba Capítulos: "Detectados pela descrição do vídeo". */
@StringRes
fun ChapterSource.longLabel(): Int = when (this) {
    ChapterSource.EMBEDDED -> R.string.origem_embedded
    ChapterSource.INFO_JSON -> R.string.origem_info_json
    ChapterSource.DESCRIPTION -> R.string.origem_description
    ChapterSource.SUBTITLE -> R.string.origem_subtitle
    ChapterSource.SILENCE -> R.string.origem_silence
    ChapterSource.MANUAL -> R.string.origem_manual
}
