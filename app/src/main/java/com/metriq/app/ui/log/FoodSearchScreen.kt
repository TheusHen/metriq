package com.metriq.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.ui.components.FoodRow
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.components.formatCount
import com.metriq.app.ui.components.tabularNumbers
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.data.FoodHit
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** One food aggregated from real log history: how often it was logged and when last. */
private data class RecentEntry(val food: FoodHit, val count: Int, val lastDate: String)

/**
 * Smart history: read the last 30 days of real logs via the log repository, group by food id,
 * then order by recency first and frequency second. No synthetic or sample data.
 */
private fun recentFromLogs(limit: Int = 6): List<RecentEntry> {
    val today = LocalDate.now()
    val datesByFood = LinkedHashMap<Long, MutableList<String>>()
    for (daysAgo in 0 until 30) {
        val date = today.minusDays(daysAgo.toLong()).toString()
        AppGraph.store.logs.forDate(date).forEach { item ->
            datesByFood.getOrPut(item.foodId) { ArrayList() }.add(item.date)
        }
    }
    return datesByFood.entries.mapNotNull { (foodId, dates) ->
        val row = AppGraph.store.food.byId(foodId) ?: return@mapNotNull null
        RecentEntry(
            food = FoodHit(
                id = row.id,
                name = row.name,
                brand = row.brand,
                category = row.category,
                iconId = row.iconId,
                calories = row.calories,
                protein = row.protein,
                fat = row.fat,
                carbs = row.carbs,
                servingLabel = row.servingLabel,
                servingGrams = row.servingGrams,
            ),
            count = dates.size,
            lastDate = dates.maxOrNull().orEmpty(),
        )
    }
        .sortedWith(compareByDescending<RecentEntry> { it.lastDate }.thenByDescending { it.count })
        .take(limit)
}

/** Single canonical category key per food, consumed by FoodRow's monochrome glyph. */
private fun foodCategoryKey(name: String, category: String?): String {
    val text = "$name ${category.orEmpty()}".lowercase(Locale.ROOT)
    return when {
        "egg" in text -> "egg"
        "burger" in text -> "burger"
        "pizza" in text -> "pizza"
        "sushi" in text || "fish" in text || "salmon" in text || "tuna" in text ||
            "chicken" in text || "beef" in text || "steak" in text ||
            "meat" in text || "protein" in text -> "meat"
        "cake" in text || "chocolate" in text || "dessert" in text ||
            "honey" in text || "sweet" in text -> "sweet"
        "fruit" in text || "berry" in text || "berries" in text || "banana" in text ||
            "apple" in text || "grape" in text || "melon" in text || "orange" in text ||
            "mango" in text || "kiwi" in text -> "fruit"
        "vegetable" in text || "greens" in text || "salad" in text || "leaf" in text ||
            "broccoli" in text || "carrot" in text || "spinach" in text -> "vegetable"
        "milk" in text || "cheese" in text || "yogurt" in text || "dairy" in text -> "dairy"
        "grain" in text || "bread" in text || "rice" in text || "pasta" in text ||
            "oat" in text || "cereal" in text || "quinoa" in text -> "grain"
        "drink" in text || "beverage" in text || "water" in text || "juice" in text ||
            "coffee" in text || "tea" in text -> "drink"
        else -> category?.lowercase(Locale.ROOT) ?: ""
    }
}

@Composable
fun FoodSearchScreen(
    onClose: () -> Unit,
    onCreateFood: () -> Unit,
    onScan: () -> Unit,
    onScanLabel: () -> Unit,
    onPhoto: () -> Unit,
    onAdd: (com.metriq.data.FoodHit, Double) -> Unit,
    onVoice: () -> Unit = {},
    budgetConsumed: Int = 0,
    budgetTarget: Int = 0,
    mealLabel: String? = null,
    onCopyFromDay: (String) -> Unit = {},
    onQuickAdd: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    onLogDays: (com.metriq.data.FoodHit, Double, Int) -> Unit = { _, _, _ -> },
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(1) }
    val selected = remember { mutableStateListOf<FoodHit>() }
    var onlineResults by remember { mutableStateOf<List<FoodHit>>(emptyList()) }

    LaunchedEffect(query) {
        onlineResults = if (query.trim().length >= 3) {
            com.metriq.app.food.FoodSearchEngine.search(query.trim())
        } else emptyList()
    }

    val results = remember(query, onlineResults) {
        if (query.isBlank()) {
            AppGraph.store.food.commonFoods(40)
        } else {
            val local = AppGraph.store.food.search(query)
            val seen = local.map { it.id }.toHashSet()
            local + onlineResults.filter { it.id !in seen }
        }
    }
    val favorites = remember { AppGraph.store.food.favorites(6) }
    val picks = remember(results) { results.take(3) }
    val recent = remember { recentFromLogs() }
    val selectedKcal = selected.sumOf { it.calories.toInt() }

    // Meal scope is either supplied by the caller or the device's current time.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalTime.now()
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val mealScope = mealLabel?.takeIf { it.isNotBlank() } ?: now.format(timeFormatter)

    val consumedText = formatCount(budgetConsumed)
    val budgetText = if (budgetTarget <= 0) {
        "$consumedText / – kcal"
    } else {
        "$consumedText / ${formatCount(budgetTarget)} kcal"
    }

    // Multi-day add flow. Day 1 keeps the existing onAdd path; 3/7 route through onLogDays.
    var logDays by remember { mutableStateOf(1) }

    // Quick-add macro fields (kcal / protein / carbs / fat), used only on the Quick Add tab.
    var quickKcal by remember { mutableStateOf("") }
    var quickProtein by remember { mutableStateOf("") }
    var quickCarbs by remember { mutableStateOf("") }
    var quickFat by remember { mutableStateOf("") }

    fun toggle(food: FoodHit) {
        val existing = selected.firstOrNull { it.id == food.id }
        if (existing != null) selected.remove(existing) else selected.add(food)
    }

    FramedColumn {
        // Top bar: close, meal scope, and the utilities. No calorie pill here.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MetriqColors.ink2)
            }
            Text(mealScope, style = MaterialTheme.typography.body, color = MetriqColors.ink2)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onCopyFromDay(LocalDate.now().minusDays(1).toString()) }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy yesterday",
                    tint = MetriqColors.ink2,
                )
            }
            IconButton(onClick = onPhoto) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "AI photo", tint = MetriqColors.ink2)
            }
            Text(
                "New",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCreateFood() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Segmented(
            options = listOf("Scan", "Search", "AI", "Quick Add", "Library"),
            selectedIndex = tab,
            onSelect = { index ->
                tab = index
                when (index) {
                    0 -> onScan()
                    2 -> onPhoto()
                    // "Quick Add" stays on this screen and reveals the inline macro row.
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (tab == 3) {
                QuickAddRow(
                    kcal = quickKcal,
                    protein = quickProtein,
                    carbs = quickCarbs,
                    fat = quickFat,
                    onKcal = { quickKcal = it },
                    onProtein = { quickProtein = it },
                    onCarbs = { quickCarbs = it },
                    onFat = { quickFat = it },
                    onAdd = {
                        fun parse(value: String) = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                        onQuickAdd(
                            parse(quickKcal),
                            parse(quickProtein),
                            parse(quickCarbs),
                            parse(quickFat),
                        )
                        quickKcal = ""
                        quickProtein = ""
                        quickCarbs = ""
                        quickFat = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            // One search field only.
            SearchField(query, { query = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            val chips = listOf(
                Triple("Scan label", Icons.AutoMirrored.Filled.Label, onScanLabel),
                Triple("Barcode", Icons.Default.QrCode, onScan),
                Triple("Photo", Icons.Default.PhotoCamera, onPhoto),
                Triple("Voice", Icons.Filled.Mic, onVoice),
                Triple("Custom", Icons.Default.Search, onCreateFood),
            )
            chips.chunked(3).forEach { rowChips ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowChips.forEach { (label, icon, action) ->
                        ActionChip(
                            label = label,
                            icon = icon,
                            onClick = action,
                            modifier = if (rowChips.size == 3) Modifier.weight(1f) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            if (favorites.isNotEmpty()) {
                SectionHeader(title = "Favorites")
                Spacer(Modifier.height(8.dp))
                favorites.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { food ->
                            FavoriteCell(
                                food = food,
                                isSelected = selected.any { it.id == food.id },
                                onClick = { toggle(food) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            // Smart history from real logs, shown alongside favorites and picks on an empty query.
            if (query.isBlank() && recent.isNotEmpty()) {
                SectionHeader(title = "Recent")
                Spacer(Modifier.height(8.dp))
                RecentRows(recent, selected, ::toggle)
                Spacer(Modifier.height(12.dp))
            }

            if (picks.isNotEmpty()) {
                SectionHeader(title = "Picks for $mealScope")
                Spacer(Modifier.height(8.dp))
                ResultRows(picks, selected, ::toggle)
                Spacer(Modifier.height(12.dp))
            }

            SectionHeader(
                title = "Latest",
                action = if (query.isBlank()) null else "${results.size} results",
            )
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MetriqColors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Text(
                        "No foods found. Try another search or create a custom food.",
                        style = MaterialTheme.typography.body,
                        color = MetriqColors.ink3,
                    )
                }
            } else {
                ResultRows(results, selected, ::toggle)
            }
            Spacer(Modifier.height(12.dp))
        }

        // Bottom bar: multi-day selector, selection total, live budget readout, primary action.
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MetriqColors.border))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Days", style = MaterialTheme.typography.label, color = MetriqColors.ink3)
                Spacer(Modifier.width(12.dp))
                listOf(1, 3, 7).forEach { days ->
                    DayChip(
                        days = days,
                        selected = logDays == days,
                        onClick = { logDays = days },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MetriqColors.surface1)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${selected.size} selected",
                        style = tabularNumbers(),
                        color = MetriqColors.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = "${formatCount(selectedKcal)} kcal",
                        style = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                        color = MetriqColors.ink3,
                        maxLines = 1,
                    )
                    Text(
                        text = budgetText,
                        style = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                        color = MetriqColors.ink3,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MetriqButton(
                    text = "Log Foods",
                    onClick = {
                        selected.forEach { food ->
                            val grams = food.servingGrams ?: 100.0
                            if (logDays <= 1) onAdd(food, grams) else onLogDays(food, grams, logDays)
                        }
                        selected.clear()
                    },
                    primary = selected.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search foods", color = MetriqColors.ink3) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MetriqColors.ink3) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = darkFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = darkFieldColors(),
        modifier = modifier,
    )
}

/** Compact, inline macro entry for the "Quick Add" tab — no custom food is created. */
@Composable
private fun QuickAddRow(
    kcal: String,
    protein: String,
    carbs: String,
    fat: String,
    onKcal: (String) -> Unit,
    onProtein: (String) -> Unit,
    onCarbs: (String) -> Unit,
    onFat: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MetriqColors.surface1)
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text("QUICK ADD MACROS", style = MaterialTheme.typography.labelSmall, color = MetriqColors.accent)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField("kcal", kcal, onKcal, Modifier.weight(1f))
            NumberField("P g", protein, onProtein, Modifier.weight(1f))
            NumberField("C g", carbs, onCarbs, Modifier.weight(1f))
            NumberField("F g", fat, onFat, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MetriqButton(text = "Add", onClick = onAdd, primary = true)
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MetriqColors.surface1)
            .border(1.dp, MetriqColors.border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MetriqColors.ink2, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayChip(
    days: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MetriqColors.surface2 else MetriqColors.surface1)
            .border(
                1.dp,
                if (selected) MetriqColors.accent else MetriqColors.border,
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$days",
            style = tabularNumbers(),
            color = if (selected) MetriqColors.ink else MetriqColors.ink2,
        )
    }
}

@Composable
private fun FavoriteCell(
    food: FoodHit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MetriqColors.surface2 else MetriqColors.surface1)
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp),
    ) {
        Text(
            text = food.name,
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatCount(food.calories.toInt())} kcal",
                style = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                color = MetriqColors.ink3,
                maxLines = 1,
            )
            Text(
                text = if (isSelected) "Added" else "+",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentRows(
    entries: List<RecentEntry>,
    selected: List<FoodHit>,
    onToggle: (FoodHit) -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        val isSelected = selected.any { it.id == entry.food.id }
        FoodRow(
            name = entry.food.name,
            meta = "${entry.count}× · ${formatCount(entry.food.calories.toInt())} kcal",
            icon = foodCategoryKey(entry.food.name, entry.food.category),
            onClick = { onToggle(entry.food) },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.background(
                        MetriqColors.surface2,
                        RoundedCornerShape(8.dp),
                    ) else Modifier,
                ),
        )
        if (index < entries.lastIndex) {
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
        }
    }
}

@Composable
private fun ResultRows(
    foods: List<FoodHit>,
    selected: List<FoodHit>,
    onToggle: (FoodHit) -> Unit,
) {
    foods.forEachIndexed { index, food ->
        val isSelected = selected.any { it.id == food.id }
        FoodRow(
            name = food.name,
            meta = macroMeta(food.calories, food.protein, food.carbs, food.fat),
            icon = foodCategoryKey(food.name, food.category),
            onClick = { onToggle(food) },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.background(
                        MetriqColors.surface2,
                        RoundedCornerShape(8.dp),
                    ) else Modifier,
                ),
        )
        if (index < foods.lastIndex) {
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
        }
    }
}
