package com.metriq.app.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages optional, user-supplied model files under the app's private files directory.
 *
 * There are no bundled binaries and no hardcoded URLs. This class only downloads a file from
 * a URL the user provides, stores it under a stable id, and reports what is actually on disk.
 * Installing a file does NOT make it the active engine — that requires an explicit selection.
 */
class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    /** Stable, filesystem-safe file name for an id. */
    private fun fileNameFor(id: String): String = buildString {
        append("model_")
        for (ch in id.trim()) {
            append(if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_')
        }
        append(".bin")
    }

    /** Absolute path for a model id. */
    fun pathFor(id: String): File = File(modelsDir, fileNameFor(id))

    /** Ids of model files that actually exist and are non-empty. */
    fun installed(): List<String> = modelsDir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.length() > 0L && it.name.endsWith(".bin") }
        .map { it.name.removePrefix("model_").removeSuffix(".bin") }
        .sorted()

    /**
     * Streams [url] to a temp file, verifies it is non-empty, then atomically installs it.
     * Progress is reported in 0..1. Failures are returned as a [Result] with a clear message.
     */
    suspend fun download(
        id: String,
        url: String,
        budgetMb: Int,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (id.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Model id is empty."))
        }
        val address = url.trim()
        DownloadPolicy.urlError(address)?.let {
            return@withContext Result.failure(IllegalArgumentException(it))
        }
        val budgetBytes = DownloadPolicy.budgetBytes(budgetMb)
        if (budgetBytes <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Orçamento de armazenamento desligado — define um limite primeiro."),
            )
        }
        fun budgetError(): IOException = IOException(
            "Ficheiro excede o orçamento de $budgetMb MB e não foi guardado.",
        )

        val finalFile = pathFor(id)
        val tempFile = File(modelsDir, fileNameFor(id).removeSuffix(".bin") + ".part")
        var connection: HttpURLConnection? = null
        try {
            onProgress(0f)
            connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("Falha no download (HTTP $code)."))
            }

            val contentLength = connection.contentLength // -1 when unknown
            if (contentLength > 0 && DownloadPolicy.exceeds(contentLength.toLong(), budgetBytes)) {
                return@withContext Result.failure(budgetError())
            }
            var total = 0L
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        ensureActive() // cancellation must reach the blocking read loop
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (DownloadPolicy.exceeds(total, budgetBytes)) {
                            tempFile.delete()
                            return@withContext Result.failure(budgetError())
                        }
                        output.write(buffer, 0, read)
                        val fraction = if (contentLength > 0) {
                            total.toFloat() / contentLength.toFloat()
                        } else {
                            // Unknown length: saturating estimate, never claims done early.
                            (total.toFloat() / (total + 512 * 1024f)).coerceAtMost(0.95f)
                        }
                        onProgress(fraction.coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }

            if (total <= 0L || !tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return@withContext Result.failure(IOException("Downloaded file is empty."))
            }

            Files.move(
                tempFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            onProgress(1f)
            Result.success(finalFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName.orEmpty()
            Result.failure(IOException("Falha no download: $detail"))
        } finally {
            connection?.disconnect()
        }
    }

    /** Removes the installed file (and any leftover partial) for an id. */
    fun remove(id: String) {
        pathFor(id).delete()
        File(modelsDir, fileNameFor(id).removeSuffix(".bin") + ".part").delete()
    }
}
