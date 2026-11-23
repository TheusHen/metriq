package com.metriq.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.metriq.app.MainActivity

// Glance's supported color API is ColorProvider(Color), not a @ColorRes integer. The
// suppression is scoped to these constants only: lint's ResourceType check cannot see that
// the Int-backed Color value class is already an ARGB color, not a resource id.
@Suppress("ResourceType")
private val ink = ColorProvider(Color(0xFFF3F5F6.toInt()))

@Suppress("ResourceType")
private val accent = ColorProvider(Color(0xFFC6F26E.toInt()))

@Suppress("ResourceType")
private val surface = ColorProvider(Color(0xFF15181C.toInt()))

@Suppress("ResourceType")
private val button = ColorProvider(Color(0xFF1E2228.toInt()))

class QuickLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val logIntent = Intent(context, MainActivity::class.java).apply { action = MainActivity.ACTION_LOG }
        val scanIntent = Intent(context, MainActivity::class.java).apply { action = MainActivity.ACTION_SCAN }
        provideContent {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(10.dp),
            ) {
                Text("METRIQ", style = TextStyle(color = accent))
                Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(
                        GlanceModifier
                            .defaultWeight()
                            .background(button)
                            .padding(10.dp)
                            .clickable(actionStartActivity(logIntent)),
                    ) { Text("Registar", style = TextStyle(color = ink)) }
                    Column(
                        GlanceModifier
                            .defaultWeight()
                            .background(button)
                            .padding(10.dp)
                            .clickable(actionStartActivity(scanIntent)),
                    ) { Text("Digitalizar", style = TextStyle(color = ink)) }
                }
            }
        }
    }
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickLogWidget()
}
