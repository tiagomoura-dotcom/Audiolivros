package com.tiagomoura.audiolivros.covers

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.tiagomoura.audiolivros.core.database.BookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Folha "Foto de capa" (seção 2.4): galeria, câmera, miniatura do vídeo ou remover. */
@Singleton
class CoverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val storage: CoverStorage,
) {
    private val cameraFile: File get() = File(context.cacheDir, "camera/capa.jpg")

    /** Destino da foto do TakePicture, compartilhado pelo FileProvider. */
    fun cameraUri(): Uri {
        cameraFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(context, "${context.packageName}.arquivos", cameraFile)
    }

    suspend fun setFromUri(bookId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
        save(bookId, bytes)
    }

    suspend fun setFromCamera(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val file = cameraFile
        if (!file.exists()) return@withContext false
        save(bookId, file.readBytes()).also { file.delete() }
    }

    suspend fun setFromThumbnail(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val path = bookDao.get(bookId)?.thumbnailPath ?: return@withContext false
        val file = File(path).takeIf { it.exists() } ?: return@withContext false
        save(bookId, file.readBytes())
    }

    suspend fun remove(bookId: String) = withContext(Dispatchers.IO) {
        storage.delete(bookId)
        bookDao.updateCover(bookId, null)
    }

    private suspend fun save(bookId: String, bytes: ByteArray): Boolean {
        val path = storage.save(bookId, bytes) ?: return false
        // Grava nulo e depois o caminho, para as telas recarregarem a imagem nova.
        bookDao.updateCover(bookId, null)
        bookDao.updateCover(bookId, path)
        return true
    }
}
