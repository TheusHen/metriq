package com.metriq.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DataType {
    FOOD_LOG, WEIGHT, STEPS, WATER, HABIT, BODY_MEASUREMENT, PROFILE,
    PHOTO, PERIOD, RECIPE, HEALTH_CONNECT_SYNC, NUTRIENT_TARGET,
}

data class DataChangeEvent(val type: DataType, val timestamp: Long)

class DataChangeNotifier {
    private val _events = MutableSharedFlow<DataChangeEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DataChangeEvent> = _events.asSharedFlow()

    fun notify(type: DataType) {
        _events.tryEmit(DataChangeEvent(type, System.currentTimeMillis()))
    }
}
