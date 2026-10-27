package com.tiagomoura.audiolivros.backup

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** "Impressão digital" do arquivo: nome + tamanho + duração (seção 9). */
data class Fingerprint(val fileName: String, val fileSizeBytes: Long, val durationMs: Long) {
    /** Mesmo nome e tamanho, e duração com tolerância de 1 s. */
    fun matches(other: Fingerprint): Boolean =
        fileName == other.fileName &&
            fileSizeBytes == other.fileSizeBytes &&
            kotlin.math.abs(durationMs - other.durationMs) <= DURATION_TOLERANCE_MS

    companion object {
        const val DURATION_TOLERANCE_MS = 1_000L
    }
}

data class BackupChapter(val index: Int, val title: String, val startMs: Long, val source: String)

data class BackupBookmark(val startMs: Long, val endMs: Long?, val note: String?, val createdAt: Long)

data class BackupBook(
    val id: String,
    val title: String,
    val author: String?,
    val fingerprint: Fingerprint,
    val positionMs: Long?,
    val speed: Float,
    val playbackUpdatedAt: Long?,
    val finished: Boolean,
    val chapterSource: String?,
    val chapters: List<BackupChapter>,
    val bookmarks: List<BackupBookmark>,
)

data class Backup(val exportedAt: Long, val books: List<BackupBook>)

/** Lê e grava o formato de `docs/exemplo-backup.json` (versão 1). */
object BackupFormat {
    const val FORMAT = "audiolivros-backup"
    const val VERSION = 1

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun write(backup: Backup, zone: ZoneId = ZoneId.systemDefault()): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", iso(backup.exportedAt, zone))
            putJsonArray("books") {
                backup.books.forEach { book ->
                    addJsonObject {
                        put("id", book.id)
                        put("title", book.title)
                        put("author", book.author)
                        putJsonObject("fingerprint") {
                            put("fileName", book.fingerprint.fileName)
                            put("fileSizeBytes", book.fingerprint.fileSizeBytes)
                            put("durationMs", book.fingerprint.durationMs)
                        }
                        putJsonObject("playback") {
                            put("positionMs", book.positionMs ?: 0L)
                            put("speed", book.speed)
                            put("updatedAt", book.playbackUpdatedAt?.let { iso(it, zone) })
                            put("finished", book.finished)
                        }
                        put("chapterSource", book.chapterSource)
                        putJsonArray("chapters") {
                            book.chapters.forEach { chapter ->
                                addJsonObject {
                                    put("index", chapter.index)
                                    put("title", chapter.title)
                                    put("startMs", chapter.startMs)
                                    put("source", chapter.source)
                                }
                            }
                        }
                        putJsonArray("bookmarks") {
                            book.bookmarks.forEach { bookmark ->
                                addJsonObject {
                                    put("type", if (bookmark.endMs == null) "point" else "range")
                                    put("startMs", bookmark.startMs)
                                    put("endMs", bookmark.endMs)
                                    put("note", bookmark.note)
                                    put("createdAt", iso(bookmark.createdAt, zone))
                                }
                            }
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Lê um backup; lança [IllegalArgumentException] se não for um arquivo do app. */
    fun read(text: String): Backup {
        val root = json.parseToJsonElement(text).jsonObject
        require(root.string("format") == FORMAT) { "Este arquivo não é um backup do Audiolivros" }
        val version = root.long("version") ?: 0
        require(version in 1..VERSION) { "Versão de backup não suportada: $version" }
        val books = (root["books"] as? JsonArray).orEmpty().mapNotNull { element ->
            val book = element as? JsonObject ?: return@mapNotNull null
            val fingerprint = book.obj("fingerprint") ?: return@mapNotNull null
            val playback = book.obj("playback")
            BackupBook(
                id = book.string("id") ?: return@mapNotNull null,
                title = book.string("title").orEmpty(),
                author = book.string("author"),
                fingerprint = Fingerprint(
                    fileName = fingerprint.string("fileName").orEmpty(),
                    fileSizeBytes = fingerprint.long("fileSizeBytes") ?: -1,
                    durationMs = fingerprint.long("durationMs") ?: 0,
                ),
                positionMs = playback?.long("positionMs"),
                speed = playback?.double("speed")?.toFloat() ?: 1f,
                playbackUpdatedAt = playback?.string("updatedAt")?.let(::parseIso),
                finished = playback?.boolean("finished") ?: false,
                chapterSource = book.string("chapterSource"),
                chapters = (book["chapters"] as? JsonArray).orEmpty().mapIndexedNotNull { index, item ->
                    val chapter = item as? JsonObject ?: return@mapIndexedNotNull null
                    BackupChapter(
                        index = chapter.long("index")?.toInt() ?: index,
                        title = chapter.string("title").orEmpty(),
                        startMs = chapter.long("startMs") ?: return@mapIndexedNotNull null,
                        source = chapter.string("source") ?: "MANUAL",
                    )
                },
                bookmarks = (book["bookmarks"] as? JsonArray).orEmpty().mapNotNull { item ->
                    val bookmark = item as? JsonObject ?: return@mapNotNull null
                    val start = bookmark.long("startMs") ?: return@mapNotNull null
                    BackupBookmark(
                        startMs = start,
                        endMs = if (bookmark.string("type") == "point") null else bookmark.long("endMs"),
                        note = bookmark.string("note"),
                        createdAt = bookmark.string("createdAt")?.let(::parseIso) ?: 0L,
                    )
                },
            )
        }
        return Backup(exportedAt = root.string("exportedAt")?.let(::parseIso) ?: 0L, books = books)
    }

    fun iso(epochMs: Long, zone: ZoneId): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone).withNano(0).format(isoFormatter)

    fun parseIso(text: String): Long? = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String): Long? = primitive(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull
    private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.booleanOrNull
}

/** Nome seguro para "<título>.posicao.json" no arquivo de posição automático. */
fun positionFileName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifEmpty { "livro" } + ".posicao.json"
