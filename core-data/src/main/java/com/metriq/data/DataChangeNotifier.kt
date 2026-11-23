package com.metriq.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

enum class DataType {
    FOOD_LOG, WEIGHT, STEPS, WATER, HABIT, BODY_MEASUREMENT, PROFILE,
    PHOTO, PERIOD, RECIPE, HEALTH_CONNECT_SYNC, NUTRIENT_TARGET,
}

data class DataChangeEvent(val type: DataType, val timestamp: Long)

/**
 * Coalescing change bus. Each data type has a single-slot, replaying flow, so a burst
 * of notifications for the same type collapses to its latest event. Late subscribers
 * to [events] receive that latest event for every type that has changed.
 */
class DataChangeNotifier {

    private val byType: Map<DataType, MutableSharedFlow<DataChangeEvent>> =
        DataType.entries.associateWith {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 0,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

    /**
     * Cold merge of the per-type replay flows. A new collector immediately receives
     * the latest event of each type that has changed, so no custom SharedFlow is needed.
     */
    val events: Flow<DataChangeEvent> = merge(*byType.values.toTypedArray())

    fun notify(type: DataType) {
        byType.getValue(type).tryEmit(DataChangeEvent(type, System.currentTimeMillis()))
    }
}
