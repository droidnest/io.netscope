package io.netscope.interceptor

import okio.Buffer
import okio.ForwardingSource
import okio.GzipSource
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the first [maxBytes] of what the app reads from a response body, and calls [onFinished]
 * with them and the total size once at the end of the body or when it is closed. The app never
 * waits for NetScope: a stream that stays open is captured as far as it was read.
 */
internal class CapturingSource(
    delegate: Source,
    private val maxBytes: Long,
    private val onFinished: (captured: Buffer, totalBytes: Long) -> Unit,
) : ForwardingSource(delegate) {

    private val captured = Buffer()
    private var totalBytes = 0L
    private val isFinished = AtomicBoolean(false)

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read == -1L) {
            finish()
            return read
        }
        totalBytes += read
        val toCapture = minOf(read, maxBytes - captured.size)
        if (toCapture > 0) sink.copyTo(captured, sink.size - read, toCapture)
        return read
    }

    override fun close() {
        try {
            super.close()
        } finally {
            finish()
        }
    }

    private fun finish() {
        if (isFinished.compareAndSet(false, true)) onFinished(captured, totalBytes)
    }
}

/** Keeps the first [maxBytes] written to it, then aborts the write with [CaptureFull]. */
private class CappedSink(private val maxBytes: Long) : Sink {

    val captured = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        val toCapture = minOf(byteCount, maxBytes - captured.size)
        captured.write(source, toCapture)
        source.skip(byteCount - toCapture)
        if (captured.size >= maxBytes) throw CaptureFull()
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private class CaptureFull : IOException()

/**
 * Returns the first [maxBytes] that [write] produces, without holding the rest in memory:
 * writing stops there.
 */
internal fun captureUpTo(maxBytes: Long, write: (okio.BufferedSink) -> Unit): Buffer {
    val capped = CappedSink(maxBytes = maxBytes)
    val sink = capped.buffer()
    try {
        write(sink)
        sink.flush()
    } catch (_: CaptureFull) {
        // Enough was captured
    }
    return capped.captured
}

/** Inflates this gzip data, up to [maxBytes]. A body cut by the capture limit inflates partly. */
internal fun Buffer.gunzipped(maxBytes: Long): Buffer {
    val out = Buffer()
    try {
        GzipSource(this).use { gzip ->
            while (out.size < maxBytes && gzip.read(out, GUNZIP_STEP_BYTES) != -1L) Unit
        }
    } catch (_: IOException) {
        // Keep what was inflated before the data ended
    }
    return out
}

private const val GUNZIP_STEP_BYTES = 8_192L
