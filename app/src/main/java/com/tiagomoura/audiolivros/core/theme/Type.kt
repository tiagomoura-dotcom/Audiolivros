package com.tiagomoura.audiolivros.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tiagomoura.audiolivros.R

val Literata = FontFamily(Font(R.font.literata_semibold, FontWeight.SemiBold))

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private val Titulo = TextStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold, color = Texto)
private val Corpo = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, color = Texto)

val AudiolivrosTypography = Typography(
    displaySmall = Titulo.copy(fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = Titulo.copy(fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = Titulo.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = Titulo.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = Titulo.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = Corpo.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Corpo.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Corpo.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Corpo.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Corpo.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Corpo.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Corpo.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = Corpo.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Rótulo de seção: caixa alta, 12 sp, espaçamento de 0,08 em, cor secundária. */
val RotuloSecao = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 0.08.em,
    color = TextoSecundario,
)
