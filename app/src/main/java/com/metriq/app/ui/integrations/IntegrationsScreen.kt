package com.metriq.app.ui.integrations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import com.metriq.app.integrations.IntegrationSpec
import com.metriq.app.integrations.IntegrationsHub
import com.metriq.app.integrations.IntegrationsHub.settingsKey
import com.metriq.app.ui.common.SubScreen
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label

/**
 * Honest integration directory: only Health Connect and Hevy are wired up in this
 * build. Every other row is a plain, non-interactive placeholder — no connect
 * buttons that lead nowhere.
 */
@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    onImportHistory: () -> Unit,
    onOpenHevy: () -> Unit,
    onOpenFoodPacks: () -> Unit = {},
) {
    val context = LocalContext.current
    // Literal `integration_<id>` keys, read straight from the local settings table.
    val statuses = remember {
        AppGraph.ensure(context)
        val settings = IntegrationsHub.all.associate { spec ->
            settingsKey(spec.id) to AppGraph.store.settings.get(settingsKey(spec.id)).orEmpty()
        }
        IntegrationsHub.status(settings)
    }

    val reducedMotion = AppSettings.bool(AppSettings.REDUCED_MOTION)
    val reveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) reveal.snapTo(1f)
        else reveal.animateTo(1f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }
    val alpha by reveal.asState()

    SubScreen(title = stringResource(R.string.integrations_title), onBack = onBack) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.integrations_intro),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = stringResource(R.string.integrations_available))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .clip(RoundedCornerShape(12.dp))
                .background(MetriqColors.surface1)
                .padding(horizontal = 16.dp),
        ) {
            IntegrationsHub.all.forEachIndexed { index, spec ->
                if (index > 0) {
                    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                }
                IntegrationRow(
                    spec = spec,
                    state = statuses[spec.id].orEmpty(),
                    onOpenHevy = onOpenHevy,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = stringResource(R.string.integrations_import))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MetriqColors.surface1)
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.integrations_import_info) + " " +
                    stringResource(R.string.integrations_import_note),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            MetriqButton(
                text = stringResource(R.string.integrations_import),
                onClick = onImportHistory,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.integrations_privacy) + " " +
                    stringResource(R.string.settings_hevy_weight),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenFoodPacks),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_food_packs),
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.settings_food_packs_info),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("›", style = MaterialTheme.typography.bodyStrong, color = MetriqColors.accent)
            }
        }
    }
}

@Composable
private fun IntegrationRow(
    spec: IntegrationSpec,
    state: String,
    onOpenHevy: () -> Unit,
) {
    val available = state != IntegrationsHub.STATUS_COMING_SOON
    val statusText = stringResource(if (available) R.string.health_available else R.string.integrations_unavailable)
    val statusColor = if (available) MetriqColors.accent else MetriqColors.ink3
    val opensSettings = spec.id == "hevy"
    val clickable = if (opensSettings) Modifier.clickable { onOpenHevy() } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(clickable)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = spec.name,
                style = MaterialTheme.typography.bodyStrong,
                color = if (available) MetriqColors.ink else MetriqColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = spec.description,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.widthIn(max = 132.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.label,
                color = statusColor,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (opensSettings) {
                Text(
                    text = stringResource(R.string.integrations_open),
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}
