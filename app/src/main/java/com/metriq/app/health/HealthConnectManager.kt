package com.metriq.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

class HealthConnectManager(private val context: Context) {

    val client by lazy { HealthConnectClient.getOrCreate(context) }

    val readPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    private val writePermissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
    )

    /** Full set the connect wizard must request: reads plus nutrition writes. */
    val permissions: Set<String> = readPermissions + writePermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Reads must keep working when the user has not granted the optional nutrition write. */
    suspend fun hasReadPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(readPermissions)

    /**
     * Exports food logs as Health Connect nutrition records. `clientRecordId` is derived from
     * the Metriq log id, so re-exporting updates the same record instead of duplicating it.
     * Returns true only when every record was accepted (or the list was empty).
     */
    suspend fun writeNutrition(logs: List<MetriqNutritionRecord>): Boolean {
        if (logs.isEmpty()) return true
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(writePermissions)) {
                Log.w(TAG, "writeNutrition skipped: WRITE_NUTRITION permission not granted")
                return false
            }
            val zone = ZoneId.systemDefault()
            val records = logs.map { log ->
                val date = runCatching { LocalDate.parse(log.date) }.getOrDefault(LocalDate.now())
                val hour = log.hour.coerceIn(0, 23)
                val start = date.atTime(hour, 0).atZone(zone).toInstant()
                val end = start.plusSeconds(1_800L)
                val offset = zone.rules.getOffset(start)
                NutritionRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    energy = Energy.kilocalories(log.calories),
                    protein = Mass.grams(log.protein),
                    totalFat = Mass.grams(log.fat),
                    totalCarbohydrate = Mass.grams(log.carbs),
                    name = log.name,
                    mealType = mealSlotForHour(log.hour).toHealthConnectMealType(),
                    metadata = Metadata.manualEntry(MetriqNutritionRecord.clientRecordId(log.id)),
                )
            }
            client.insertRecords(records)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "writeNutrition failed: SecurityException — ${e.message ?: "no message"}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "writeNutrition failed: ${e::class.simpleName}: ${e.message ?: "no message"}")
            false
        }
    }

    suspend fun readSteps(date: LocalDate): Long {
        val filter = dayFilter(date)
        return client.readRecords(ReadRecordsRequest(StepsRecord::class, filter))
            .records.sumOf { it.count }
    }

    suspend fun readActiveCalories(date: LocalDate): Double {
        val filter = dayFilter(date)
        return client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, filter))
            .records.sumOf { it.energy.inKilocalories }
    }

    suspend fun readTotalCalories(date: LocalDate): Double {
        val filter = dayFilter(date)
        return client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, filter))
            .records.sumOf { it.energy.inKilocalories }
    }

    suspend fun readWeight(days: Int = 7): List<WeightReading> {
        val end = Instant.now()
        val start = end.minusSeconds(days * 86400L)
        return client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end)),
        ).records.map { record ->
            WeightReading(
                // LocalDate.ofInstant is API 34; atOffset(UTC) is desugaring-safe on minSdk 28.
                date = record.time.atOffset(ZoneOffset.UTC).toLocalDate(),
                kilograms = record.weight.inKilograms,
                time = record.time,
            )
        }
    }

    suspend fun readExerciseSessions(date: LocalDate): Int =
        client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, dayFilter(date)),
        ).records.size

    suspend fun readExerciseMinutes(date: LocalDate): Int {
        val records = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, dayFilter(date)),
        ).records
        return records.sumOf { record ->
            java.time.Duration.between(record.startTime, record.endTime)
                .toMinutes()
                .coerceAtLeast(0L)
        }.toInt()
    }

    private fun dayFilter(date: LocalDate): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        return TimeRangeFilter.between(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }

    private fun MealSlot.toHealthConnectMealType(): Int = when (this) {
        MealSlot.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
        MealSlot.LUNCH -> MealType.MEAL_TYPE_LUNCH
        MealSlot.DINNER -> MealType.MEAL_TYPE_DINNER
        MealSlot.SNACK -> MealType.MEAL_TYPE_SNACK
    }

    companion object {
        private const val TAG = "MetriqHealthConnect"

        /** Settings key for the opt-in nutrition export. Default is OFF. */
        const val EXPORT_NUTRITION_KEY = "health_export_nutrition"

        val RECORD_TYPES: Set<KClass<out androidx.health.connect.client.records.Record>> = setOf(
            StepsRecord::class,
            WeightRecord::class,
            TotalCaloriesBurnedRecord::class,
            ActiveCaloriesBurnedRecord::class,
            ExerciseSessionRecord::class,
            NutritionRecord::class,
        )
    }
}
