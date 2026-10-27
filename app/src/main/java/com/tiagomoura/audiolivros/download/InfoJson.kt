package com.tiagomoura.audiolivros.download

import com.tiagomoura.audiolivros.chapters.ParsedChapter
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** O que interessa do `info.json` gravado pelo yt-dlp (seções 4.2 e 5, item 2). */
data class VideoInfo(
    val title: String?,
    val author: String?,
    val durationMs: Long?,
    val chapters: List<ParsedChapter>,
)

object InfoJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): VideoInfo {
        val root = json.parseToJsonElement(text).jsonObject
        val chapters = (root["chapters"] as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            val chapter = element as? JsonObject ?: return@mapIndexedNotNull null
            val start = chapter.number("start_time") ?: return@mapIndexedNotNull null
            ParsedChapter(
                title = chapter.string("title")?.trim().orEmpty().ifEmpty { "Capítulo ${index + 1}" },
                startMs = (start * 1000).roundToLong(),
            )
        }.sortedBy { it.startMs }
        return VideoInfo(
            title = root.string("title")?.trim()?.ifEmpty { null },
            author = (root.string("uploader") ?: root.string("channel") ?: root.string("creator"))?.trim()?.ifEmpty { null },
            durationMs = root.number("duration")?.let { (it * 1000).roundToLong() },
            chapters = chapters,
        )
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = primitive(key)?.doubleOrNull
}

/** Erros do yt-dlp que costumam indicar que ele está desatualizado. */
object DownloadErrors {
    private val outdatedHints = listOf(
        "please report this issue",
        "unable to extract",
        "nsig extraction failed",
        "signature extraction failed",
        "requested format is not available",
        "http error 403",
        "precondition check failed",
        "update to the latest version",
        "yt-dlp -u",
        "sign in to confirm",
    )

    fun suggestsUpdate(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return outdatedHints.any { it in text }
    }

    /** A última linha "ERROR: ..." é a mais útil para o usuário. */
    fun summarize(message: String?): String? {
        val lines = message?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        return (lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull())
            ?.removePrefix("ERROR:")?.trim()
    }
}
