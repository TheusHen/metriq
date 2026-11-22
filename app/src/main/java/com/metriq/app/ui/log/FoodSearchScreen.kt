package com.metriq.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.compose.runtime.getValue
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
import com.metriq.app.ui.components.FoodIcon
import com.metriq.app.ui.components.FoodRow
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.data.FoodHit
import java.util.Locale

/** Single canonical category key per food, consumed by [FoodIcon]. */
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
    onAdd: (FoodHit, Double) -> Unit,
    onVoice: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(1) }
    val results = remember(query) {
        if (query.isBlank()) AppGraph.store.food.recent(40).map { food ->
            FoodHit(
                id = food.id,
                name = food.name,
                brand = food.brand,
                category = food.category,
                iconId = food.iconId,
                calories = food.calories,
                protein = food.protein,
                fat = food.fat,
                carbs = food.carbs,
                servingLabel = food.servingLabel,
                servingGrams = food.servingGrams,
            )
        }
        else AppGraph.store.food.search(query)
    }
    val favorites = remember(results) { results.take(6) }
    val picks = remember(results) { results.take(3) }

    FramedColumn {
        // Top bar: close, meal time, calorie budget pill, quick actions.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MetriqColors.ink2)
            }
            Text("10 AM", style = MaterialTheme.typography.labelLarge, color = MetriqColors.ink2)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MetriqColors.surface2)
                    .border(1.dp, MetriqColors.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("0 / 2014", style = MaterialTheme.typography.labelLarge, color = MetriqColors.accent)
                Spacer(Modifier.width(5.dp))
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MetriqColors.ink3)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPhoto) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "AI photo", tint = MetriqColors.ink2)
            }
            Text(
                "New",
                style = MaterialTheme.typography.labelLarge,
                color = MetriqColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCreateFood() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    3 -> onCreateFood()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SearchField(query, { query = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip("Scan label", Icons.AutoMirrored.Filled.Label, onScanLabel)
                ActionChip("Barcode", Icons.Default.QrCode, onScan)
                ActionChip("Photo", Icons.Default.PhotoCamera, onPhoto)
                ActionChip("Voice", Icons.Filled.Mic, onVoice)
                ActionChip("Custom", Icons.Default.Search, onCreateFood)
            }

            Spacer(Modifier.height(22.dp))

            if (favorites.isNotEmpty()) {
                SectionHeader(title = "Favorites")
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        favorites.forEach { food ->
                            FavoriteTile(food) { onAdd(food, food.servingGrams ?: 100.0) }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            if (picks.isNotEmpty()) {
                SectionHeader(title = "10 AM Picks", action = "See all")
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.fillMaxWidth()) {
                    picks.forEachIndexed { index, food ->
                        FoodRow(
                            name = food.name,
                            meta = macroMeta(food.calories, food.protein, food.carbs, food.fat),
                            icon = foodCategoryKey(food.name, food.category),
                            onClick = { onAdd(food, food.servingGrams ?: 100.0) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (index < picks.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            SectionHeader(
                title = "Latest",
                action = if (query.isBlank()) null else "${results.size} results",
            )
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                if (results.isEmpty()) {
                    Text(
                        "No foods found. Try another search or create a custom food.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MetriqColors.ink2,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    results.forEachIndexed { index, food ->
                        FoodRow(
                            name = food.name,
                            meta = macroMeta(food.calories, food.protein, food.carbs, food.fat),
                            icon = foodCategoryKey(food.name, food.category),
                            onClick = { onAdd(food, food.servingGrams ?: 100.0) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (index < results.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bottom action bar: stays above scrolled content.
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .background(MetriqColors.surface1)
                .border(1.dp, MetriqColors.border, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            MetriqButton(
                text = "Log Foods",
                onClick = { results.firstOrNull()?.let { onAdd(it, it.servingGrams ?: 100.0) } },
                primary = true,
            )
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
        shape = RoundedCornerShape(14.dp),
        colors = darkFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MetriqColors.surface2)
            .border(1.dp, MetriqColors.border, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MetriqColors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MetriqColors.ink)
    }
}

@Composable
private fun FavoriteTile(food: FoodHit, onClick: () -> Unit) {
    Box {
        Column(
            Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MetriqColors.surface2)
                .border(1.dp, MetriqColors.border, RoundedCornerShape(18.dp))
                .clickable { onClick() }
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FoodIcon(
                foodCategoryKey(food.name, food.category),
                modifier = Modifier.size(36.dp),
            )
            Text(
                food.name.take(12),
                style = MaterialTheme.typography.labelSmall,
                color = MetriqColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(MetriqColors.accent)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.labelLarge, color = MetriqColors.accentInk)
        }
    }
}
