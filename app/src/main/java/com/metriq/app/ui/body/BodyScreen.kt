package com.metriq.app.ui.body

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.*
import com.metriq.data.db.BodyMeasurement
import com.metriq.data.db.ProgressPhoto
import java.util.Locale

@Composable
fun BodyScreen(
    measurements: List<BodyMeasurement>,
    photos: List<ProgressPhoto>,
    periodToday: Boolean,
    onAddMeasurement: (String, Double) -> Unit,
    onTogglePeriod: (Boolean) -> Unit,
    onAddPhoto: () -> Unit,
    onDeletePhoto: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var site by remember { mutableStateOf("Waist") }
    var value by remember { mutableStateOf("") }

    SubScreen("Body", onBack) {
        Text(
            "Track your measurements, cycle and progress photos.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Measurements")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            if (measurements.isEmpty()) {
                Text(
                    "No measurements logged yet.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink3,
                )
            } else {
                measurements.forEachIndexed { index, measurement ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            measurement.site,
                            style = MaterialTheme.typography.bodyStrong,
                            color = MetriqColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format(Locale.ENGLISH, "%.1f", measurement.valueCm),
                            style = MaterialTheme.typography.number24,
                            color = MetriqColors.ink,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "cm",
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.ink3,
                        )
                    }
                    if (index < measurements.lastIndex) Hairline()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "Add a measurement",
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text("Site") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("cm") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            MetriqButton(
                text = "Save measurement",
                onClick = {
                    value.replace(',', '.').toDoubleOrNull()?.let {
                        onAddMeasurement(site, it)
                        value = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Cycle")
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onTogglePeriod(!periodToday) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Period today",
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Mark today as part of your cycle.",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .width(52.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (periodToday) MetriqColors.accent else MetriqColors.surface2)
                        .padding(3.dp),
                    contentAlignment = if (periodToday) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (periodToday) MetriqColors.accentInk else MetriqColors.ink3),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Progress photos", action = "Add", onAction = onAddPhoto)
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            if (photos.isEmpty()) {
                Text(
                    "No photos yet. Add one from your gallery.",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink3,
                )
            } else {
                val rows = photos.chunked(2)
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { photo ->
                            PhotoTile(
                                photo = photo,
                                modifier = Modifier.weight(1f),
                                onDelete = { onDeletePhoto(photo.id) },
                            )
                        }
                    }
                    if (rowIndex < rows.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            MetriqButton(
                text = "Add photo from gallery",
                onClick = onAddPhoto,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PhotoTile(
    photo: ProgressPhoto,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
) {
    Column(modifier) {
        val bitmap = remember(photo.path) { BitmapFactory.decodeFile(photo.path)?.asImageBitmap() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MetriqColors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = "Progress photo from ${photo.date}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "Image unavailable",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                photo.date,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Delete",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.danger,
                modifier = Modifier.clickable { onDelete() },
            )
        }
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
