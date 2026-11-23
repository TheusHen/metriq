package com.metriq.app.ui.weight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.log.FramedColumn
import com.metriq.app.ui.log.darkFieldColors
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.core.WeightEntry
import java.util.Locale

@Composable
fun WeightScreen(
    entries: List<WeightEntry>,
    todayIso: String,
    onBack: () -> Unit,
    onAdd: (String, Double) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    var kg by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayIso) }
    var error by remember { mutableStateOf<String?>(null) }

    // Display unit follows the user setting; storage stays in kilograms.
    val displayInPounds = remember {
        AppSettings.get(AppSettings.UNIT_SYSTEM).equals("lb", ignoreCase = true)
    }
    val unitFactor = if (displayInPounds) 2.20462 else 1.0
    val unitLabel = if (displayInPounds) "lb" else "kg"

    val reversed = remember(entries) { entries.asReversed() }
    val latest = reversed.firstOrNull()

    FramedColumn {
        ScreenHeader(
            title = stringResource(R.string.weight_title),
            subtitle = stringResource(R.string.weight_follow),
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
                Text(stringResource(R.string.weight_current), style = MaterialTheme.typography.labelSmall, color = MetriqColors.ink3)
                Spacer(Modifier.height(4.dp))
                Text(
                    latest?.let { String.format(Locale.ENGLISH, "%.1f", it.kg * unitFactor) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = MetriqColors.accent,
                )
                Text(
                    if (latest != null) "$unitLabel · ${latest.date}" else stringResource(R.string.weight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MetriqColors.ink2,
                )
            }

            Spacer(Modifier.height(18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.weight_add), style = MaterialTheme.typography.labelSmall, color = MetriqColors.accent)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Field(stringResource(R.string.weight_field), kg, Modifier.weight(1f)) { kg = it; error = null }
                    Field(stringResource(R.string.date), date, Modifier.weight(1.2f)) { date = it; error = null }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.weight_date_info), style = MaterialTheme.typography.labelSmall, color = MetriqColors.ink3)

                error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MetriqColors.danger)
                }

                Spacer(Modifier.height(14.dp))
                MetriqButton(
                    text = stringResource(R.string.weight_save),
                    onClick = {
                        val value = kg.replace(',', '.').toDoubleOrNull()
                        val validDate = com.metriq.app.util.DateInput.parse(date)
                        when {
                            value == null || value <= 0 -> {
                                error = context.getString(R.string.weight_value_error)
                            }
                            validDate == null -> {
                                error = context.getString(R.string.weight_date_error)
                            }
                            else -> {
                                onAdd(validDate.toString(), value)
                                kg = ""
                                date = validDate.toString()
                                error = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = true,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(title = stringResource(R.string.weight_title), action = stringResource(R.string.weight_total, entries.size))
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                if (reversed.isEmpty()) {
                    Text(
                        stringResource(R.string.weight_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MetriqColors.ink2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    reversed.forEachIndexed { index, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.date.toString(), style = MaterialTheme.typography.titleMedium, color = MetriqColors.ink)
                                Text(
                                    String.format(Locale.ENGLISH, "%.1f $unitLabel", entry.kg * unitFactor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MetriqColors.ink2,
                                )
                            }
                            IconButton(onClick = { onDelete(entry.date.toString()) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.weight_delete), tint = MetriqColors.danger)
                            }
                        }
                        if (index < reversed.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = darkFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}
