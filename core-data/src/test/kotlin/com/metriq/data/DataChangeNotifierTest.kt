package com.metriq.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class DataChangeNotifierTest {

    @Test
    fun `a burst of one type produces a single event`() = runBlocking {
        val notifier = DataChangeNotifier()
        val seen = mutableListOf<DataChangeEvent>()
        val job = launch { notifier.events.collect { seen.add(it) } }
        yield()

        repeat(100) { notifier.notify(DataType.FOOD_LOG) }
        delay(100)
        job.cancel()

        assertEquals(1, seen.size)
        assertEquals(DataType.FOOD_LOG, seen.single().type)
    }

    @Test
    fun `late subscriber sees the latest event for each notified type`() = runBlocking {
        val notifier = DataChangeNotifier()
        repeat(20) { notifier.notify(DataType.FOOD_LOG) }
        repeat(20) { notifier.notify(DataType.WEIGHT) }

        val seen = withTimeout(1000) { notifier.events.take(2).toList() }
        assertEquals(setOf(DataType.FOOD_LOG, DataType.WEIGHT), seen.map { it.type }.toSet())
    }
}
