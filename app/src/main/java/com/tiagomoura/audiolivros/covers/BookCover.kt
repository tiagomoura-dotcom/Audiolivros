package com.tiagomoura.audiolivros.covers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.theme.Literata
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.Texto
import java.io.File

/** Capa 3:4: a foto salva ou a capa provisória com as iniciais do título. */
@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    placeholderColor: Int,
    modifier: Modifier = Modifier,
    shape: Shape = Raios.CapaPequena,
    initialsSize: TextUnit = 16.sp,
    initialsPadding: Dp = 6.dp,
) {
    val description = stringResource(R.string.capa_de, title)
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(shape)
            .background(Color(placeholderColor)),
    ) {
        if (coverPath != null) {
            val context = LocalContext.current
            val file = File(coverPath)
            // A chave inclui a data do arquivo: trocar a capa mostra a imagem nova na hora.
            val request = remember(coverPath, file.lastModified()) {
                ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("$coverPath:${file.lastModified()}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = PlaceholderCover.initials(title),
                style = TextStyle(
                    fontFamily = Literata,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = initialsSize,
                    color = Texto,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(initialsPadding),
            )
        }
    }
}
