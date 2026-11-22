package com.metriq.app.ui.log

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.log.FoodRecognitionManager
import com.metriq.app.ui.components.FoodIcon
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import com.metriq.data.FoodHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-device photo recognition with a review step. Anything found can be topped up from a
 * manual library search, so a weak photo never blocks logging. Nothing is uploaded.
 */
@Composable
fun FoodPhotoScreen(
    manager: FoodRecognitionManager,
    onBack: () -> Unit,
    onSelect: (List<Pair<FoodHit, Double>>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<FoodRecognitionManager.Suggestion>>(emptyList()) }
    var manualItems by remember { mutableStateOf<List<FoodRecognitionManager.Suggestion>>(emptyList()) }
    val items = suggestions + manualItems
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Upload a photo, or search manually below.") }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var gramsById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var manualQuery by remember { mutableStateOf("") }
    var manualResults by remember { mutableStateOf<List<FoodHit>>(emptyList()) }

    LaunchedEffect(manualQuery) {
        val query = manualQuery.trim()
        if (query.length < 2) {
            manualResults = emptyList()
            return@LaunchedEffect
        }
        manualResults = withContext(Dispatchers.IO) { AppGraph.store.food.search(query, limit = 8) }
    }

    fun gramsFor(index: Int, suggestion: FoodRecognitionManager.Suggestion): Double =
        gramsById[index]?.toDoubleOrNull()
            ?: suggestion.food?.servingGrams
            ?: 100.0

    fun addManual(hit: FoodHit) {
        val index = items.size
        manualItems = manualItems + FoodRecognitionManager.Suggestion(
            label = hit.name,
            confidence = 1f,
            food = hit,
        )
        selected = selected + index
        gramsById = gramsById + (index to fmt0(hit.servingGrams ?: 100.0))
        manualQuery = ""
        manualResults = emptyList()
        message = "Added ${hit.name}. Set grams below."
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = false
            message = "Identifying foods on-device…"
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        val bitmap = android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(context.contentResolver, uri),
                        )
                        manager.recognize(bitmap)
                    }
                    val combined = result + manualItems
                    suggestions = result
                    selected = result.indices.filter { combined[it].food != null }.toSet()
                    gramsById = combined.mapIndexed { index, item ->
                        index to fmt0(item.food?.servingGrams ?: 100.0)
                    }.toMap()
                    message = if (result.isEmpty()) {
                        "No match in your library — search manually below."
                    } else {
                        "Review the matches, then choose what to log."
                    }
                } catch (_: Exception) {
                    suggestions = emptyList()
                    val kept = manualItems
                    selected = kept.indices.toSet()
                    gramsById = kept.mapIndexed { index, item ->
                        index to fmt0(item.food?.servingGrams ?: 100.0)
                    }.toMap()
                    error = true
                    message = "Could not read that image. You can still search manually below."
                } finally {
                    busy = false
                }
            }
        }
    }

    val totalKcal = selected.toList().sumOf { index ->
        items.getOrNull(index)?.food?.let { food ->
            food.calories * gramsFor(index, items[index]) / 100.0
        } ?: 0.0
    }

    FramedColumn {
        ScreenHeader(
            title = "AI food photo",
            subtitle = "On-device recognition",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose a photo of your meal. Matching happens on your device — nothing is uploaded.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                )
                Spacer(Modifier.height(14.dp))
                MetriqButton(
                    text = if (busy) "Identifying…" else "Upload photo",
                    onClick = { if (!busy) pick.launch("image/*") },
                    primary = true,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(
                title = "Suggestions",
                action = if (items.isEmpty()) null else "${items.size} found",
            )
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                when {
                    busy -> Text(
                        text = "Identifying foods on-device…",
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    items.isEmpty() -> Text(
                        text = message,
                        style = MaterialTheme.typography.body,
                        color = if (error) MetriqColors.danger else MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    else -> {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.label,
                            color = MetriqColors.ink3,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        items.forEachIndexed { index, suggestion ->
                            val hit = suggestion.food
                            val match = String.format(
                                Locale.ENGLISH,
                                "%.0f%% match",
                                suggestion.confidence * 100,
                            )
                            if (hit == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodIcon(
                                        "food",
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.label,
                                            style = MaterialTheme.typography.bodyStrong,
                                            color = MetriqColors.ink2,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "$match · No match in your library",
                                            style = MaterialTheme.typography.caption,
                                            color = MetriqColors.ink3,
                                        )
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = index in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + index else selected - index
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MetriqColors.accent,
                                            uncheckedColor = MetriqColors.ink3,
                                            checkmarkColor = MetriqColors.accentInk,
                                        ),
                                    )
                                    FoodIcon(hit.category)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = hit.name,
                                            style = MaterialTheme.typography.bodyStrong,
                                            color = MetriqColors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = match,
                                            style = MaterialTheme.typography.caption,
                                            color = MetriqColors.ink2,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = gramsById[index] ?: fmt0(hit.servingGrams ?: 100.0),
                                        onValueChange = { value -> gramsById = gramsById + (index to value) },
                                        label = { Text("Grams") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = darkFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 46.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "${fmt0(hit.calories * gramsFor(index, suggestion) / 100.0)} kcal",
                                        style = MaterialTheme.typography.caption,
                                        color = MetriqColors.ink2,
                                    )
                                }
                            }
                            if (index < items.lastIndex) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(title = "Search manually")
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = manualQuery,
                    onValueChange = { manualQuery = it },
                    placeholder = { Text("Food name", color = MetriqColors.ink3) },
                    label = { Text("Add a food by name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                val query = manualQuery.trim()
                when {
                    query.length < 2 -> Text(
                        text = "Type at least 2 letters to search your library.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                    )

                    manualResults.isEmpty() -> Text(
                        text = "No match in your library — try another name.",
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink2,
                    )

                    else -> manualResults.forEachIndexed { index, hit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { addManual(hit) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoodIcon(hit.category)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = hit.name,
                                    style = MaterialTheme.typography.bodyStrong,
                                    color = MetriqColors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${fmt0(hit.calories)} kcal · ${fmt0(hit.servingGrams ?: 100.0)} g serving",
                                    style = MaterialTheme.typography.caption,
                                    color = MetriqColors.ink2,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.label,
                                color = MetriqColors.accent,
                            )
                        }
                        if (index < manualResults.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bottom action bar: live running total plus the commit button.
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .background(MetriqColors.surface1)
                .border(
                    1.dp,
                    MetriqColors.border,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${selected.size} selected",
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                )
                Text(
                    text = "${fmt0(totalKcal)} kcal",
                    style = MaterialTheme.typography.number24,
                    color = MetriqColors.accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            MetriqButton(
                text = "Log ${selected.size} foods",
                primary = true,
                onClick = {
                    val pairs = selected.toList().sorted().mapNotNull { index ->
                        items.getOrNull(index)?.food?.let { hit ->
                            hit to gramsFor(index, items[index])
                        }
                    }
                    if (pairs.isNotEmpty()) onSelect(pairs)
                },
            )
        }
    }
}
