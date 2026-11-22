package com.metriq.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.theme.MetriqTheme

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetriqTheme {
                Text(
                    "Metriq reads steps, weight and calories from Health Connect to improve " +
                        "your expenditure estimate. Data stays on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = androidx.compose.ui.Modifier.padding(24.dp),
                )
            }
        }
    }
}
