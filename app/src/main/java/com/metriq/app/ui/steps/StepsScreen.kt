package com.metriq.app.ui.steps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metriq.app.R
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.*
import java.util.Locale

@Composable
fun StepsScreen(
    steps: Long?,
    waterMl: Long?,
    onSaveSteps: (Long) -> Unit,
    onSaveWater: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var stepInput by remember { mutableStateOf(steps?.toString() ?: "") }
    var waterInput by remember { mutableStateOf(waterMl?.toString() ?: "") }

    SubScreen("Passos e água", onBack) {
        Text(
            "Regista o movimento e a hidratação de hoje.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Passos de hoje")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = steps?.let { String.format(Locale.ENGLISH, "%,d", it) } ?: "—",
                    style = MaterialTheme.typography.number40,
                    color = if (steps == null) MetriqColors.ink3 else MetriqColors.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "steps",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Meta diária: 10 000 passos",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = stepInput,
                onValueChange = { stepInput = it },
                label = { Text(stringResource(R.string.steps)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            MetriqButton(
                text = stringResource(R.string.save_steps),
                onClick = { stepInput.toLongOrNull()?.let(onSaveSteps) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Água de hoje")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = waterMl?.let { String.format(Locale.ENGLISH, "%,d", it) } ?: "—",
                    style = MaterialTheme.typography.number40,
                    color = if (waterMl == null) MetriqColors.ink3 else MetriqColors.fat,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ml",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Meta diária: 2 500 ml",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = waterInput,
                onValueChange = { waterInput = it },
                label = { Text(stringResource(R.string.water)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            MetriqButton(
                text = stringResource(R.string.save_water),
                onClick = { waterInput.toLongOrNull()?.let(onSaveWater) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
