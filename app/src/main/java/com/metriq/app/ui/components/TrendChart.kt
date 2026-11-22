package com.metriq.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24

/**
 * Compact chart caption kept for compatibility. The chart itself lives in
 * [MetriqComponents.kt] under the redesigned signature.
 */
@Composable
fun ChartHeader(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.label, color = MetriqColors.ink2)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.number24, color = MetriqColors.ink)
        Text(caption, style = MaterialTheme.typography.caption, color = MetriqColors.ink2)
    }
}
