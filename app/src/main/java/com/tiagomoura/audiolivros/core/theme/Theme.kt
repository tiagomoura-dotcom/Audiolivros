package com.tiagomoura.audiolivros.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val Esquema = darkColorScheme(
    primary = Destaque,
    onPrimary = TextoSobreDestaque,
    primaryContainer = Superficie2,
    onPrimaryContainer = Texto,
    secondary = Destaque,
    onSecondary = TextoSobreDestaque,
    secondaryContainer = Superficie2,
    onSecondaryContainer = Texto,
    tertiary = Destaque,
    onTertiary = TextoSobreDestaque,
    background = Fundo,
    onBackground = Texto,
    surface = Fundo,
    onSurface = Texto,
    surfaceVariant = Superficie2,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = Fundo,
    surfaceContainerLow = Superficie,
    surfaceContainer = Superficie,
    surfaceContainerHigh = Superficie2,
    surfaceContainerHighest = Superficie2,
    inverseSurface = Texto,
    inverseOnSurface = Fundo,
    outline = Borda,
    outlineVariant = Divisoria,
)

/** Raios: cartões 20 dp, botões 14 dp, capas 8 a 12 dp. */
object Raios {
    val Cartao = RoundedCornerShape(20.dp)
    val Botao = RoundedCornerShape(14.dp)
    val CapaPequena = RoundedCornerShape(8.dp)
    val CapaGrande = RoundedCornerShape(12.dp)
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = Raios.Botao,
    medium = Raios.Botao,
    large = Raios.Cartao,
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AudiolivrosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Esquema,
        typography = AudiolivrosTypography,
        shapes = Formas,
        content = content,
    )
}
