package com.tiagomoura.audiolivros.chapters

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Decodifica o áudio em blocos com MediaExtractor + MediaCodec, mistura para mono
 * e calcula o envelope de volume (seção 5.1). Só um bloco de PCM fica na memória
 * de cada vez, então livros de mais de 10 horas não causam falta de memória.
 */
class AudioEnvelopeDecoder(private val context: Context) {

    /** @param onProgress porcentagem de 0 a 100, pela posição decodificada. */
    suspend fun decode(uri: Uri, onProgress: suspend (Int) -> Unit): Envelope {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("O arquivo não tem faixa de áudio")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var channels = inputFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var sampleRate = inputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var floatPcm = false
            var builder: EnvelopeBuilder? = null
            var mono = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var lastPercent = -1

            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        channels = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        sampleRate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        floatPcm = format.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                            AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outputIndex >= 0 -> {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            output.order(ByteOrder.nativeOrder())
                            val envelope = builder ?: EnvelopeBuilder(sampleRate).also { builder = it }
                            val frames: Int
                            if (floatPcm) {
                                val samples = output.asFloatBuffer()
                                frames = samples.remaining() / channels
                                if (mono.size < frames) mono = FloatArray(frames)
                                for (frame in 0 until frames) {
                                    var sum = 0f
                                    for (channel in 0 until channels) sum += samples.get()
                                    mono[frame] = sum / channels
                                }
                            } else {
                                val samples = output.asShortBuffer()
                                frames = samples.remaining() / channels
                                if (mono.size < frames) mono = FloatArray(frames)
                                for (frame in 0 until frames) {
                                    var sum = 0f
                                    for (channel in 0 until channels) sum += samples.get() / 32768f
                                    mono[frame] = sum / channels
                                }
                            }
                            envelope.add(mono, frames)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)

                        if (durationUs > 0) {
                            val percent = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 99)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            onProgress(100)
            return (builder ?: EnvelopeBuilder(sampleRate)).finish()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
