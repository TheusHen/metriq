package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Outcome of [BackupRepository.importJson]. Never partially applied. */
sealed class ImportResult {
    data class Success(val schemaVersion: Long, val rowCounts: Map<String, Int>) : ImportResult()
    data class Failure(val reason: String) : ImportResult()

    val ok: Boolean get() = this is Success
}

/**
 * Full-device JSON backup. Tables are discovered from sqlite_master and dumped
 * generically, so future additive tables/fields are included without code changes.
 *
 * Values keep their SQLite storage class (integer / real / text / blob / null) via
 * `typeof()`, which makes a restore lossless even for nullable columns.
 */
class BackupRepository(
    private val driver: SqlDriver,
    private val notifier: DataChangeNotifier? = null,
    private val schemaVersion: Long = MetriqMigrations.VERSION,
) {
    companion object {
        const val FORMAT = "metriq-backup"
        const val BACKUP_VERSION = 1
        private const val BLOB_KEY = "__blob__"
    }

    fun exportJson(): String {
        val tables = JSONObject()
        val rowCounts = JSONObject()
        userTables().forEach { table ->
            val columns = columnsOf(table)
            val select = buildString {
                append("SELECT ")
                columns.forEachIndexed { index, column ->
                    if (index > 0) append(", ")
                    append("typeof(").append(q(column)).append(") AS t").append(index)
                    append(", ").append(q(column)).append(" AS v").append(index)
                }
                append(" FROM ").append(q(table))
            }
            val rows = query(select, 0) { cursor ->
                val out = JSONArray()
                while (cursor.next().value) {
                    val row = JSONObject()
                    columns.forEachIndexed { index, column ->
                        row.put(column, readValue(cursor, index * 2, index * 2 + 1))
                    }
                    out.put(row)
                }
                QueryResult.Value(out)
            }
            tables.put(table, JSONObject().put("columns", JSONArray(columns)).put("rows", rows))
            rowCounts.put(table, rows.length())
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("backupVersion", BACKUP_VERSION)
            .put("schemaVersion", schemaVersion)
            .put("rowCounts", rowCounts)
            .put("tables", tables)
            .toString()
    }

    /**
     * Validates the entire document first, then replaces every user table inside a
     * single transaction. Foreign keys are disabled only around that transaction
     * (SQLite ignores PRAGMA changes made *inside* one) and always restored after.
     */
    fun importJson(json: String): ImportResult {
        if (driver.currentTransaction() != null) {
            return ImportResult.Failure("Backup import cannot run inside an existing database transaction")
        }
        val parsed = parse(json) ?: return ImportResult.Failure("Malformed backup document")

        val localTables = userTables()
        val version = parsed.optLong("schemaVersion", -1L)
        if (version != schemaVersion) {
            return ImportResult.Failure("Schema version mismatch: backup has $version, app expects $schemaVersion")
        }
        val backupTables = parsed.optJSONObject("tables")
            ?: return ImportResult.Failure("Backup is missing 'tables'")
        val declaredCounts = parsed.optJSONObject("rowCounts")
            ?: return ImportResult.Failure("Backup is missing 'rowCounts'")

        val names = backupTables.keys().asSequence().toList()
        if (names.toSet() != localTables.toSet()) {
            val missing = localTables - names.toSet()
            val extra = names.toSet() - localTables.toSet()
            return ImportResult.Failure("Table set mismatch (missing=$missing, unknown=$extra)")
        }

        val data = LinkedHashMap<String, TableData>()
        for (table in localTables) {
            val node = backupTables.optJSONObject(table)
                ?: return ImportResult.Failure("Backup is missing table $table")
            val columns = node.optJSONArray("columns")
                ?: return ImportResult.Failure("Table $table has no columns")
            val columnNames = (0 until columns.length()).map { columns.optString(it) }
            if (columnNames.isEmpty() || columnNames.distinct().size != columnNames.size ||
                columnNames.toSet() != columnsOf(table).toSet()
            ) {
                return ImportResult.Failure("Column mismatch for table $table")
            }
            val rows = node.optJSONArray("rows")
                ?: return ImportResult.Failure("Table $table has no rows")
            val values = ArrayList<List<Any>>(rows.length())
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i)
                    ?: return ImportResult.Failure("Row $i of $table is not an object")
                if (row.keys().asSequence().toSet() != columnNames.toSet()) {
                    return ImportResult.Failure("Row $i of $table has unexpected fields")
                }
                values.add(columnNames.map { column ->
                    val decoded = decodeValue(row.get(column))
                    if (decoded === INVALID) return ImportResult.Failure("Unsupported value in $table.$column")
                    decoded
                })
            }
            if (!declaredCounts.has(table) || declaredCounts.optInt(table, -1) != values.size) {
                return ImportResult.Failure("Row count mismatch for table $table")
            }
            data[table] = TableData(columnNames, values)
        }

        val order = dependencyOrder(localTables)
        val previousFk = ForeignKeys.isEnabled(driver)
        var committed = false
        try {
            ForeignKeys.setEnabled(driver, false)
            driver.execute(null, "BEGIN", 0)
            order.asReversed().forEach { table ->
                driver.execute(null, "DELETE FROM ${q(table)}", 0)
            }
            val counts = LinkedHashMap<String, Int>()
            order.forEach { table ->
                val tableData = data.getValue(table)
                val sql = "INSERT INTO ${q(table)} (" +
                    tableData.columns.joinToString(",") { q(it) } + ") VALUES (" +
                    tableData.columns.joinToString(",") { "?" } + ")"
                tableData.rows.forEach { row ->
                    val bindRow: (app.cash.sqldelight.db.SqlPreparedStatement) -> Unit = { prepared ->
                        for (i in row.indices) bind(prepared, i, row[i])
                    }
                    driver.execute(null, sql, row.size, bindRow)
                }
                counts[table] = tableData.rows.size
            }
            driver.execute(null, "COMMIT", 0)
            committed = true
            DataType.entries.forEach { notifier?.notify(it) }
            return ImportResult.Success(version, counts)
        } catch (e: Exception) {
            return ImportResult.Failure("Import failed: ${e.message ?: e::class.simpleName}")
        } finally {
            if (!committed) runCatching { driver.execute(null, "ROLLBACK", 0) }
            runCatching { ForeignKeys.setEnabled(driver, previousFk) }
        }
    }

    // --- parsing -----------------------------------------------------------

    private data class TableData(val columns: List<String>, val rows: List<List<Any>>)

    private fun parse(json: String): JSONObject? = try {
        val obj = JSONObject(json)
        if (obj.optString("format") != FORMAT || obj.optInt("backupVersion", -1) != BACKUP_VERSION) null else obj
    } catch (_: Exception) {
        null
    }

    private object INVALID

    /** [JSONObject.NULL] represents a SQL NULL; [INVALID] means reject the document. */
    private fun decodeValue(value: Any?): Any = when {
        value === JSONObject.NULL -> JSONObject.NULL
        value is Long || value is Int || value is Short || value is java.math.BigInteger ->
            (value as Number).toLong()
        value is Double || value is Float || value is java.math.BigDecimal ->
            (value as Number).toDouble()
        value is String -> value
        value is JSONObject && value.length() == 1 && value.has(BLOB_KEY) ->
            runCatching { Base64.getDecoder().decode(value.getString(BLOB_KEY)) }.getOrNull() ?: INVALID
        else -> INVALID
    }

    private fun readValue(cursor: app.cash.sqldelight.db.SqlCursor, typeIndex: Int, valueIndex: Int): Any = when (cursor.getString(typeIndex)) {
        "null" -> JSONObject.NULL
        "integer" -> (cursor.getLong(valueIndex) ?: 0L)
        "real" -> (cursor.getDouble(valueIndex) ?: 0.0)
        "blob" -> JSONObject().put(BLOB_KEY, Base64.getEncoder().encodeToString(cursor.getBytes(valueIndex) ?: ByteArray(0)))
        else -> (cursor.getString(valueIndex) ?: "")
    }

    private fun bind(binder: app.cash.sqldelight.db.SqlPreparedStatement, index: Int, value: Any) {
        when (value) {
            JSONObject.NULL -> binder.bindString(index, null)
            is Long -> binder.bindLong(index, value)
            is Double -> binder.bindDouble(index, value)
            is String -> binder.bindString(index, value)
            is ByteArray -> binder.bindBytes(index, value)
            else -> error("Unsupported backup value type ${value::class}")
        }
    }

    // --- schema introspection ---------------------------------------------

    private fun userTables(): List<String> = query(
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name",
        0,
    ) { cursor ->
        val out = ArrayList<String>()
        while (cursor.next().value) cursor.getString(0)?.let { out.add(it) }
        QueryResult.Value(out)
    }

    private fun columnsOf(table: String): List<String> = query("PRAGMA table_info(${q(table)})", 0) { cursor ->
        val out = ArrayList<String>()
        while (cursor.next().value) cursor.getString(1)?.let { out.add(it) }
        QueryResult.Value(out)
    }

    /** Parent-before-child order derived from declared foreign keys. */
    private fun dependencyOrder(tables: List<String>): List<String> {
        val tableSet = tables.toSet()
        val deps = tables.associateWith { table ->
            query("PRAGMA foreign_key_list(${q(table)})", 0) { cursor ->
                val out = HashSet<String>()
                while (cursor.next().value) cursor.getString(2)?.let { if (it in tableSet) out.add(it) }
                QueryResult.Value(out)
            }.intersect(tableSet)
        }
        val ordered = ArrayList<String>()
        val pending = tables.toMutableSet()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { deps.getValue(it).all { dep -> dep in ordered } }.sorted()
            if (ready.isEmpty()) {
                // Only possible with a cyclic FK graph; keep a deterministic order.
                ordered.addAll(pending.sorted())
                break
            }
            ready.forEach { ordered.add(it); pending.remove(it) }
        }
        return ordered
    }

    private fun <T> query(sql: String, binders: Int, block: (app.cash.sqldelight.db.SqlCursor) -> QueryResult<T>): T =
        driver.executeQuery(null, sql, block, binders).value

    private fun q(name: String) = "\"${name.replace("\"", "\"\"")}\""
}
