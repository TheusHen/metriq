package com.metriq.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.Segmented
import com.metriq.app.ui.theme.*
import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.Profile
import com.metriq.core.ProteinTier
import com.metriq.core.Sex

@Composable
fun ProfileScreen(
    initial: Profile,
    targetWeightKg: Double?,
    onBack: () -> Unit,
    onSave: (Profile, Double?) -> Unit,
    onExport: () -> Unit,
    onConnectHealth: () -> Unit,
    onSyncHealth: () -> Unit,
    healthStatus: String,
    lastHealthSync: String?,
    importSteps: Boolean,
    importWeight: Boolean,
    onImportStepsChange: (Boolean) -> Unit,
    onImportWeightChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var sex by remember { mutableStateOf(initial.sex) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var height by remember { mutableStateOf(initial.heightCm.toString()) }
    var weight by remember { mutableStateOf(initial.weightKg.toString()) }
    var bodyFat by remember { mutableStateOf(initial.bodyFatPercent?.toString() ?: "") }
    var target by remember { mutableStateOf(targetWeightKg?.toString() ?: "") }
    var activity by remember { mutableStateOf(initial.activityLevel) }
    var goal by remember { mutableStateOf(initial.goal) }
    var diet by remember { mutableStateOf(initial.diet) }
    var tier by remember { mutableStateOf(initial.proteinTier) }

    // Optimistic local toggles: a settings-only write does not always re-emit from the
    // parent StateFlow, so the switch must flip before the write is forwarded.
    var localImportSteps by remember { mutableStateOf(importSteps) }
    var localImportWeight by remember { mutableStateOf(importWeight) }
    LaunchedEffect(importSteps) { localImportSteps = importSteps }
    LaunchedEffect(importWeight) { localImportWeight = importWeight }

    SubScreen("Your profile", onBack) {
        Text(
            "Your stats and goals drive every daily target in Metriq.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Basics")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            ChoiceSegmented(
                label = "Sex",
                options = listOf("Female" to Sex.FEMALE, "Male" to Sex.MALE),
                selected = sex,
                onSelect = { sex = it },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Age", age, Modifier.weight(1f)) { age = it }
                Field("Height cm", height, Modifier.weight(1f)) { height = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Weight kg", weight, Modifier.weight(1f)) { weight = it }
                Field("Body fat %", bodyFat, Modifier.weight(1f)) { bodyFat = it }
            }
            Spacer(Modifier.height(10.dp))
            Field("Target weight kg (optional)", target) { target = it }
        }
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Plan")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            ChoiceSegmented(
                label = "Goal",
                options = listOf("Cut" to GoalType.CUT, "Maintain" to GoalType.MAINTAIN, "Bulk" to GoalType.BULK),
                selected = goal,
                onSelect = { goal = it },
            )
            Spacer(Modifier.height(14.dp))
            ChoiceSegmented(
                label = "Activity",
                options = ActivityLevel.entries.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it
                },
                selected = activity,
                onSelect = { activity = it },
            )
            Spacer(Modifier.height(14.dp))
            ChoiceSegmented(
                label = "Diet",
                options = listOf(
                    "Balanced" to DietStyle.BALANCED,
                    "Low carb" to DietStyle.LOW_CARB_HIGH_FAT,
                    "High carb" to DietStyle.HIGH_CARB_LOW_FAT,
                    "Keto" to DietStyle.KETO,
                ),
                selected = diet,
                onSelect = { diet = it },
            )
            Spacer(Modifier.height(14.dp))
            ChoiceSegmented(
                label = "Protein",
                options = ProteinTier.entries.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it
                },
                selected = tier,
                onSelect = { tier = it },
            )
        }
        Spacer(Modifier.height(14.dp))
        MetriqButton(
            text = "Save profile",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    Profile(
                        sex = sex,
                        age = age.toIntOrNull() ?: 30,
                        heightCm = height.toDoubleOrNull() ?: 178.0,
                        weightKg = weight.toDoubleOrNull() ?: 80.0,
                        bodyFatPercent = bodyFat.toDoubleOrNull(),
                        activityLevel = activity,
                        goal = goal,
                        diet = diet,
                        proteinTier = tier,
                    ),
                    target.toDoubleOrNull(),
                )
            },
        )

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Data")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "Export your food, weight and body logs as a CSV file.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            Spacer(Modifier.height(12.dp))
            MetriqButton(
                text = "Export CSV",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onOpenSettings() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "All settings",
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Units, sync, analytics & privacy",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "›",
                    style = MaterialTheme.typography.h2,
                    color = MetriqColors.accent,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Health Connect")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                healthStatus,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
            )
            lastHealthSync?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Last sync: $it",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                )
            }
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(6.dp))
            SettingToggle(
                label = "Import steps",
                subtitle = "Pull step counts from Health Connect",
                checked = localImportSteps,
                onChange = { enabled ->
                    localImportSteps = enabled
                    onImportStepsChange(enabled)
                },
            )
            SettingToggle(
                label = "Import weight",
                subtitle = "Pull weight samples from Health Connect",
                checked = localImportWeight,
                onChange = { enabled ->
                    localImportWeight = enabled
                    onImportWeightChange(enabled)
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetriqButton(
                    text = "Connect",
                    onClick = onConnectHealth,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
                MetriqButton(
                    text = "Sync now",
                    onClick = onSyncHealth,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun <T> ChoiceSegmented(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.label,
        color = MetriqColors.ink3,
    )
    Spacer(Modifier.height(8.dp))
    val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    Segmented(
        options = options.map { it.first },
        selectedIndex = selectedIndex,
        onSelect = { onSelect(options[it].second) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingToggle(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MetriqColors.accentInk,
                checkedTrackColor = MetriqColors.accent,
                checkedBorderColor = MetriqColors.accent,
                uncheckedThumbColor = MetriqColors.ink3,
                uncheckedTrackColor = MetriqColors.surface2,
                uncheckedBorderColor = MetriqColors.border,
                disabledCheckedThumbColor = MetriqColors.accentInk.copy(alpha = 0.5f),
                disabledCheckedTrackColor = MetriqColors.accent.copy(alpha = 0.5f),
                disabledUncheckedThumbColor = MetriqColors.ink3.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = MetriqColors.surface2.copy(alpha = 0.5f),
            ),
        )
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
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    )
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
