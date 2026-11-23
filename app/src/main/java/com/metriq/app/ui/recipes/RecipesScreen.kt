package com.metriq.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.FoodRow
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.*
import com.metriq.data.RecipeDraft
import com.metriq.data.db.Food
import com.metriq.data.db.Recipe

@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    foods: List<Food>,
    onAdd: (RecipeDraft) -> Unit,
    onLog: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var grams by remember { mutableStateOf("100") }
    var selectedFood by remember { mutableStateOf<Food?>(null) }
    var items by remember { mutableStateOf<List<Pair<Food, Double>>>(emptyList()) }

    SubScreen("Receitas", onBack) {
        Text(
            "Cria uma receita com os teus alimentos e regista uma dose com um toque.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "As tuas receitas")
        Spacer(Modifier.height(6.dp))
        Text(
            "Receitas guardadas · toca em Registar para adicionar uma dose a hoje",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            if (recipes.isEmpty()) {
                Text(
                    "Ainda não há receitas. Cria uma abaixo.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink3,
                )
            } else {
                recipes.forEachIndexed { index, recipe ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                recipe.name,
                                style = MaterialTheme.typography.bodyStrong,
                                color = MetriqColors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${recipe.servings} servings · ${recipe.totalGrams.toInt()} g",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink3,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MetriqColors.surface2)
                                .clickable { onLog(recipe.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Registar",
                                style = MaterialTheme.typography.label,
                                color = MetriqColors.accent,
                            )
                        }
                    }
                    if (index < recipes.lastIndex) Hairline()
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "New recipe")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Recipe name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Servings") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Ingredient g") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Ingredients")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "Escolhe um alimento, define os gramas e adiciona.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (foods.isEmpty()) {
                    Text(
                        "Ainda não há alimentos disponíveis.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                    )
                } else {
                    foods.take(200).forEach { food ->
                        FoodRow(
                            name = food.name,
                            meta = "${food.calories.toInt()} kcal / 100 g",
                            icon = food.iconId,
                            onClick = { selectedFood = food },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (selectedFood != null) {
                Text(
                    "Selected: ${selectedFood?.name}",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }
            MetriqButton(
                text = "Adicionar ingrediente",
                onClick = {
                    val food = selectedFood
                    val amount = grams.replace(',', '.').toDoubleOrNull()
                    if (food != null && amount != null && amount > 0) {
                        items = items + (food to amount)
                        selectedFood = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (selectedFood != null) 1f else 0.5f),
                primary = false,
            )
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "Included", action = "${items.size} items")
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth()) {
                items.forEach { (food, amount) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${food.name} · ${amount.toInt()} g",
                            style = MaterialTheme.typography.body,
                            color = MetriqColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.danger,
                            modifier = Modifier.clickable {
                                items = items.filterNot { pair -> pair.first.id == food.id }
                            },
                        )
                    }
                }
            }
        }

        val kcalPerServing = if (items.isEmpty()) {
            0.0
        } else {
            val total = items.sumOf { (food, amount) -> food.calories * amount / 100.0 }
            total / (servings.toIntOrNull()?.coerceAtLeast(1) ?: 1)
        }
        Spacer(Modifier.height(18.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "ENERGIA POR DOSE",
                style = MaterialTheme.typography.label,
                color = MetriqColors.ink3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "≈ ${kcalPerServing.toInt()} kcal",
                style = MaterialTheme.typography.number40,
                color = MetriqColors.energy,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Across ${items.size} ingredient(s) and ${servings.toIntOrNull()?.coerceAtLeast(1) ?: 1} serving(s)",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
        }
        Spacer(Modifier.height(14.dp))
        MetriqButton(
            text = "Create recipe",
            onClick = {
                if (name.isNotBlank() && items.isNotEmpty()) {
                    onAdd(
                        RecipeDraft(
                            name = name.trim(),
                            servings = servings.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            items = items.map { it.first.id to it.second },
                        ),
                    )
                    name = ""
                    items = emptyList()
                    selectedFood = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (name.isNotBlank() && items.isNotEmpty()) 1f else 0.5f),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MetriqColors.border),
    )
}
