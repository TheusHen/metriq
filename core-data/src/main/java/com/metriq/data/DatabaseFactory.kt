package com.metriq.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.metriq.data.db.MetriqDatabase
import java.io.File

class MetriqStore(
    val db: MetriqDatabase,
    val driver: SqlDriver,
    val notifier: DataChangeNotifier,
    context: Context? = null,
) {
    val food = FoodRepository(db, driver, notifier)
    val weights = WeightRepository(db, notifier)
    val logs = LogRepository(db, notifier)
    val settings = SettingsRepository(db)
    val profile = ProfileRepository(db, notifier)
    val checkin = CheckinRepository(db, driver)
    val body = BodyRepository(db, notifier)
    val photo = PhotoRepository(
        db,
        File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), "progress_photos"),
        notifier,
    )
    val habit = HabitRepository(db, notifier)
    val step = StepRepository(db, notifier)
    val water = WaterRepository(db, notifier)
    val dayProperty = DayPropertyRepository(db, notifier)
    val nutrientTarget = NutrientTargetRepository(db, notifier)
    val recipe = RecipeRepository(db, notifier, driver)
    val program = ProgramRepository(db)
    val backup = BackupRepository(driver, notifier)
    // Reassigned by the app at startup to the current-month release tag (with the
    // user override applied); core-data itself cannot compute that app-owned default.
    var foodPacks = FoodPackManager(food, settings)
}

object DatabaseFactory {

    private const val MICROS_SQL =
        "CREATE TABLE IF NOT EXISTS foodMicros(foodId INTEGER PRIMARY KEY REFERENCES food(id), json TEXT NOT NULL)"

    fun create(context: Context): MetriqStore {
        val driver: SqlDriver = AndroidSqliteDriver(
            MetriqSchema,
            context,
            "metriq.db",
            callback = object : AndroidSqliteDriver.Callback(MetriqSchema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    // Enforce REFERENCES declared in Metriq.sq (e.g. logEntry.foodId).
                    // Deliberately no ON DELETE CASCADE: SQLite cannot add that to an
                    // existing table without a full rebuild, which is too risky for
                    // already-installed databases. See the final report for details.
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Data-preserving, additive steps only; never DROP an existing table.
                    MetriqMigrations.migrate(oldVersion.toLong(), newVersion.toLong()) { db.execSQL(it) }
                }
            },
        )
        driver.execute(null, MICROS_SQL, 0)

        val store = MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier(), context)
        store.food.seedIfNeeded(
            context.assets.open("foods.tsv").bufferedReader().use { it.readText() },
        )
        return store
    }
}
