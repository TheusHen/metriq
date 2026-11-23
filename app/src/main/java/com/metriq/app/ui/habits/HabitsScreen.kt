package com.metriq.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.*
import com.metriq.app.ui.home.HabitUi

@Composable
fun HabitsScreen(
    habits: List<HabitUi>,
    onAdd: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val doneCount = habits.count { it.doneToday }

    SubScreen("Hábitos", onBack) {
        Text(
            "Small actions, repeated.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Hoje")
        Spacer(Modifier.height(6.dp))
        Text(
            "$doneCount de ${habits.size} concluídos hoje",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            if (habits.isEmpty()) {
                Text(
                    "Ainda não há hábitos. Adiciona um abaixo para iniciar uma sequência.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink3,
                )
            } else {
                habits.forEach { habit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (habit.doneToday) MetriqColors.surface2 else Color.Transparent)
                            .clickable { onToggle(habit.id) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (habit.doneToday) MetriqColors.accent else MetriqColors.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (habit.doneToday) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.caption,
                                    color = MetriqColors.accentInk,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.bodyStrong,
                            color = MetriqColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${habit.streak}d",
                                style = MaterialTheme.typography.number24,
                                color = if (habit.streak > 0) MetriqColors.accent else MetriqColors.ink3,
                            )
                            Text(
                                "STREAK",
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.ink3,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Toca num hábito para marcar a conclusão de hoje.",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Adicionar hábito")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Habit name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            MetriqButton(
                text = "Adicionar hábito",
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name.trim())
                        name = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
