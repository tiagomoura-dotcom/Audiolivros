package com.tiagomoura.audiolivros.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arquivo de posição automático (seção 9): um `<título>.posicao.json` por livro na
 * pasta escolhida, no mesmo formato do backup com um único livro. Atualizado ao
 * pausar e no máximo uma vez por minuto durante a reprodução.
 */
@Singleton
class PositionFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val bookDao: BookDao,
) {
    private val lastWrite = ConcurrentHashMap<String, Long>()

    suspend fun write(bookId: String, force: Boolean) {
        val folder = settings.currentPositionFolder() ?: return
        val now = System.currentTimeMillis()
        if (!force && now - (lastWrite[bookId] ?: 0L) < MIN_INTERVAL_MS) return
        lastWrite[bookId] = now
        try {
            val book = bookDao.get(bookId) ?: return
            val text = backup.exportJson(bookId)
            val tree = Uri.parse(folder)
            val name = positionFileName(book.title)
            val document = findChild(tree, name) ?: DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
                "application/json",
                name,
            ) ?: return
            context.contentResolver.openOutputStream(document, "wt")?.use { it.write(text.toByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível gravar o arquivo de posição", e)
        }
    }

    private fun findChild(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return null
    }

    private companion object {
        const val TAG = "PositionFileWriter"
        const val MIN_INTERVAL_MS = 60_000L
    }
}
