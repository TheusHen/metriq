package com.metriq.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class FoodPackEntry(
    val id: String,
    val file: String,
    val size: Long?,
    val count: Int?,
    val sha256: String?,
)

data class FoodPackManifest(val version: Int, val packs: List<FoodPackEntry>) {
    companion object {
        fun fromJson(json: String): FoodPackManifest {
            val obj = JSONObject(json)
            val version = obj.optInt("version", -1)
            require(version > 0) { "Unsupported food pack manifest version" }
            val array = obj.optJSONArray("packs") ?: error("Manifest is missing 'packs'")
            val packs = (0 until array.length()).mapNotNull { index ->
                val node: JSONObject = array.optJSONObject(index) ?: return@mapNotNull null
                val id = node.optString("id")
                val file = node.optString("file")
                if (id.isBlank() || file.isBlank()) return@mapNotNull null
                FoodPackEntry(
                    id = id,
                    file = file,
                    size = node.optLong("size", -1L).takeIf { it > 0 },
                    count = node.optInt("count", -1).takeIf { it >= 0 },
                    sha256 = node.optString("sha256").ifBlank { null },
                )
            }
            require(packs.isNotEmpty()) { "Manifest has no usable packs" }
            return FoodPackManifest(version, packs)
        }
    }
}

sealed class PackValidation {
    data class Valid(val rowCount: Int, val hasHeader: Boolean) : PackValidation()
    data class Invalid(val reason: String) : PackValidation()
}

/** Pure, network-free checks for the 26-column pack TSV format. */
object FoodPackValidator {

    const val COLUMN_COUNT = 26

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** The header is optional: "name" in column 0 or a non-numeric kcal in column 4. */
    fun hasHeaderRow(columns: List<String>): Boolean {
        if (columns.firstOrNull().orEmpty().trim().equals("name", ignoreCase = true)) return true
        val kcal = columns.getOrNull(4)?.trim().orEmpty()
        return kcal.isNotEmpty() && kcal.toDoubleOrNull() == null
    }

    fun validate(
        tsv: String,
        expectedCount: Int? = null,
        expectedSha256: String? = null,
    ): PackValidation {
        if (tsv.isBlank()) return PackValidation.Invalid("Pack is empty")
        if (!expectedSha256.isNullOrBlank()) {
            val actual = sha256(tsv.toByteArray(Charsets.UTF_8))
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                return PackValidation.Invalid("Checksum mismatch: expected $expectedSha256, got $actual")
            }
        }
        var hasHeader = false
        var dataRows = 0
        val lines = tsv.lineSequence().filter { it.isNotBlank() }.toList()
        lines.forEachIndexed { index, line ->
            val columns = line.split('\t')
            if (columns.size != COLUMN_COUNT) {
                return PackValidation.Invalid("Row $index has ${columns.size} columns; expected $COLUMN_COUNT")
            }
            if (index == 0 && hasHeaderRow(columns)) {
                hasHeader = true
                return@forEachIndexed
            }
            if (columns[0].isBlank()) return PackValidation.Invalid("Row $index has no name")
            if (columns[4].isNotBlank() && columns[4].toDoubleOrNull() == null) {
                return PackValidation.Invalid("Row $index has an invalid kcal value")
            }
            dataRows++
        }
        if (dataRows == 0) return PackValidation.Invalid("Pack has no data rows")
        if (expectedCount != null && dataRows != expectedCount) {
            return PackValidation.Invalid("Row count mismatch: expected $expectedCount, found $dataRows")
        }
        return PackValidation.Valid(dataRows, hasHeader)
    }
}

data class InstalledFoodPack(
    val id: String,
    val version: String,
    val sha256: String?,
    val file: String?,
)

sealed class PackInstallResult {
    data class Success(
        val packId: String,
        val version: String,
        val inserted: Int,
        val total: Int,
    ) : PackInstallResult()

    data class Failure(val reason: String) : PackInstallResult()

    val ok: Boolean get() = this is Success
}

/**
 * Downloads and installs optional food packs. All network work is on [Dispatchers.IO];
 * validation and the DB import are synchronous and can be tested without network.
 *
 * Installed pack metadata is stored in [settings] under the PACK_*_KEY constants so
 * the UI can show the current pack without a network call.
 */
class FoodPackManager(
    private val foods: FoodRepository,
    private val settings: SettingsRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val maxTsvBytes: Long = DEFAULT_MAX_TSV_BYTES,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://github.com/TheusHen/metriq/releases/download/packs-2026-11"
        const val PACK_ID_KEY = "foodPack.id"
        const val PACK_VERSION_KEY = "foodPack.version"
        const val PACK_SHA_KEY = "foodPack.sha256"
        const val PACK_FILE_KEY = "foodPack.file"

        const val DEFAULT_MAX_TSV_BYTES = 32L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }

    suspend fun fetchManifest(): FoodPackManifest = withContext(Dispatchers.IO) {
        val bytes = download("manifest.json", MAX_MANIFEST_BYTES)
        FoodPackManifest.fromJson(bytes.toString(Charsets.UTF_8))
    }

    /** Fetch -> validate -> import. A rejected pack never touches food/settings. */
    suspend fun install(packId: String): PackInstallResult = withContext(Dispatchers.IO) {
        try {
            val manifest = FoodPackManifest.fromJson(
                download("manifest.json", MAX_MANIFEST_BYTES).toString(Charsets.UTF_8),
            )
            val entry = manifest.packs.firstOrNull { it.id == packId }
                ?: return@withContext PackInstallResult.Failure("Pack '$packId' is not in the manifest")
            val cap = minOf(maxTsvBytes, entry.size ?: maxTsvBytes)
            val tsv = download(entry.file, cap).toString(Charsets.UTF_8)
            importValidatedPack(
                tsv = tsv,
                packId = entry.id,
                version = manifest.version.toString(),
                sha256 = entry.sha256,
                expectedCount = entry.count,
                file = entry.file,
            )
        } catch (e: Exception) {
            PackInstallResult.Failure("Install failed: ${e.message ?: e::class.simpleName}")
        }
    }

    /** Pure DB half: validates first, then adds rows and writes metadata atomically. */
    fun importValidatedPack(
        tsv: String,
        packId: String,
        version: String,
        sha256: String?,
        expectedCount: Int? = null,
        file: String? = null,
    ): PackInstallResult {
        when (val validation = FoodPackValidator.validate(tsv, expectedCount, sha256)) {
            is PackValidation.Invalid -> return PackInstallResult.Failure(validation.reason)
            is PackValidation.Valid -> Unit
        }
        return try {
            val inserted = foods.importPackTsv(tsv) {
                settings.set(PACK_ID_KEY, packId)
                settings.set(PACK_VERSION_KEY, version)
                settings.set(PACK_SHA_KEY, sha256.orEmpty())
                settings.set(PACK_FILE_KEY, file.orEmpty())
            }
            PackInstallResult.Success(packId, version, inserted, foods.count().toInt())
        } catch (e: Exception) {
            PackInstallResult.Failure("Import failed: ${e.message ?: e::class.simpleName}")
        }
    }

    fun installedPack(): InstalledFoodPack? {
        val id = settings.get(PACK_ID_KEY) ?: return null
        return InstalledFoodPack(
            id = id,
            version = settings.get(PACK_VERSION_KEY).orEmpty(),
            sha256 = settings.get(PACK_SHA_KEY)?.ifBlank { null },
            file = settings.get(PACK_FILE_KEY)?.ifBlank { null },
        )
    }

    private fun download(path: String, maxBytes: Long): ByteArray {
        val url = URL(baseUrl.trimEnd('/') + "/" + path)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code while fetching $path")
            val declared = connection.contentLengthLong
            if (declared > maxBytes) throw IOException("$path is too large ($declared bytes > $maxBytes)")
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("$path exceeds the $maxBytes byte cap")
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
