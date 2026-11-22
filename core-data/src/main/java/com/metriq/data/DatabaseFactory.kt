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
    val checkin = CheckinRepository(db)
    val body = BodyRepository(db, notifier)
    val photo = PhotoRepository(
        db,
        File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir")), "progress_photos"),
        notifier,
    )
    val habit = HabitRepository(db, notifier)
    val step = StepRepository(db, notifier)
    val water = WaterRepository(db, notifier)
    val dayProperty = DayPropertyRepository(db, notifier)
    val nutrientTarget = NutrientTargetRepository(db, notifier)
    val recipe = RecipeRepository(db, notifier)
    val program = ProgramRepository(db)
}

object DatabaseFactory {

    private const val FTS_SQL =
        "CREATE VIRTUAL TABLE IF NOT EXISTS foodFts USING fts5(name, brand, category, foodId UNINDEXED)"
    private const val MICROS_SQL =
        "CREATE TABLE IF NOT EXISTS foodMicros(foodId INTEGER PRIMARY KEY REFERENCES food(id), json TEXT NOT NULL)"

    fun create(context: Context): MetriqStore {
        val driver: SqlDriver = AndroidSqliteDriver(
            MetriqSchema,
            context,
            "metriq.db",
            callback = object : AndroidSqliteDriver.Callback(MetriqSchema) {
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Data-preserving, additive steps only; never DROP an existing table.
                    MetriqMigrations.migrate(oldVersion.toLong(), newVersion.toLong()) { db.execSQL(it) }
                }
            },
        )
        driver.execute(null, FTS_SQL, 0)
        driver.execute(null, MICROS_SQL, 0)

        val store = MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier(), context)
        store.food.seedIfNeeded(
            context.assets.open("foods.tsv").bufferedReader().use { it.readText() },
        )
        return store
    }
}
