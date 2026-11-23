package com.metriq.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.metriq.app.AppGraph
import com.metriq.app.MainActivity

// Glance's supported color API is ColorProvider(Color), not a @ColorRes integer. The
// suppression is scoped to these constants only: lint's ResourceType check cannot see that
// the Int-backed Color value class is already an ARGB color, not a resource id.
@Suppress("ResourceType")
private val ink = ColorProvider(Color(0xFFF3F5F6.toInt()))

@Suppress("ResourceType")
private val muted = ColorProvider(Color(0xFF9AA1AB.toInt()))

@Suppress("ResourceType")
private val accent = ColorProvider(Color(0xFFC6F26E.toInt()))

@Suppress("ResourceType")
private val surface = ColorProvider(Color(0xFF15181C.toInt()))

class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppGraph.ensure(context)
        val entries = AppGraph.store.weights.all()
        val latest = entries.lastOrNull()
        provideContent {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("TENDÊNCIA DE PESO", style = TextStyle(color = muted))
                Text(
                    latest?.let { String.format(java.util.Locale.ENGLISH, "%.1f kg", it.kg) } ?: "—",
                    style = TextStyle(color = ink, fontWeight = FontWeight.Bold),
                )
                Text(
                    entries.takeLast(7).joinToString(" · ") {
                        String.format(java.util.Locale.ENGLISH, "%.1f", it.kg)
                    }.ifBlank { "No weigh-ins yet" },
                    style = TextStyle(color = accent),
                )
                Text("METRIQ", style = TextStyle(color = muted))
            }
        }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WeightWidget()
}
