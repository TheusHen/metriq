package com.metriq.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    val permissions: Set<String> = readPermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

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
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minusSeconds(days * 86400L)
        return client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end)),
        ).records.map { record ->
            WeightReading(
                date = LocalDate.ofInstant(record.time, zone),
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

    companion object {
        val RECORD_TYPES: Set<KClass<out androidx.health.connect.client.records.Record>> = setOf(
            StepsRecord::class,
            WeightRecord::class,
            TotalCaloriesBurnedRecord::class,
            ActiveCaloriesBurnedRecord::class,
            ExerciseSessionRecord::class,
        )
    }
}
