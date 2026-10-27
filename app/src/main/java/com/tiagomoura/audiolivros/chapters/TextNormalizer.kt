package com.tiagomoura.audiolivros.chapters

import java.text.Normalizer

/** Minúsculas, sem acentos e com espaços simples: "Capítulo  Três" → "capitulo tres". */
internal fun normalizeText(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
