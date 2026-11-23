package com.metriq.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.theme.MetriqColors

@Composable
fun CreateFoodScreen(
    onBack: () -> Unit,
    onSave: (FoodDraft) -> Unit,
    initial: FoodDraft? = null,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var brand by remember { mutableStateOf(initial?.brand ?: "") }
    var serving by remember { mutableStateOf(initial?.servingLabel ?: "") }
    var grams by remember { mutableStateOf((initial?.servingGrams ?: 100.0).toString()) }
    var kcal by remember { mutableStateOf(initial?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(initial?.protein?.toString() ?: "") }
    var fat by remember { mutableStateOf(initial?.fat?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbs?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    FramedColumn {
        ScreenHeader(
            title = stringResource(R.string.create_title),
            subtitle = stringResource(R.string.create_nutrition),
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.details))
                Spacer(Modifier.height(10.dp))
                Field(stringResource(R.string.create_name), name) { name = it; error = null }
                Spacer(Modifier.height(10.dp))
                Field(stringResource(R.string.create_brand), brand) { brand = it }
            }

            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.serving))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.serving), serving, Modifier.weight(1f)) { serving = it }
                    Field(stringResource(R.string.grams), grams, Modifier.weight(1f)) { grams = it }
                }
            }

            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.create_section))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.create_calories), kcal, Modifier.weight(1f)) { kcal = it; error = null }
                    Field(stringResource(R.string.create_protein), protein, Modifier.weight(1f)) { protein = it }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.create_fat), fat, Modifier.weight(1f)) { fat = it }
                    Field(stringResource(R.string.create_carbs), carbs, Modifier.weight(1f)) { carbs = it }
                }
                error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MetriqColors.danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .background(MetriqColors.surface1)
                .border(1.dp, MetriqColors.border, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MetriqButton(
                text = stringResource(R.string.create_save),
                onClick = {
                    val kcalValue = kcal.replace(',', '.').toDoubleOrNull()
                    if (kcalValue == null || kcalValue <= 0.0) {
                        error = context.getString(R.string.create_error)
                    } else {
                        onSave(
                            FoodDraft(
                                name = name.ifBlank { context.getString(R.string.quick_add_name) },
                                brand = brand.ifBlank { null },
                                calories = kcalValue,
                                protein = protein.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                fat = fat.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                carbs = carbs.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                servingLabel = serving.ifBlank { context.getString(R.string.serving_one) },
                                servingGrams = grams.replace(',', '.').toDoubleOrNull() ?: 100.0,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MetriqColors.accent)
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = darkFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}
