package com.metriq.app.widget

import android.content.Context
import android.graphics.Color
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

private val ink = ColorProvider(Color.parseColor("#F3F5F6"))
private val muted = ColorProvider(Color.parseColor("#9AA1AB"))
private val accent = ColorProvider(Color.parseColor("#C6F26E"))
private val surface = ColorProvider(Color.parseColor("#15181C"))

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
                    .padding(12)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("WEIGHT TREND", style = TextStyle(color = muted))
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
