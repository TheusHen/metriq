package com.metriq.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
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

private val ink = ColorProvider(Color.parseColor("#F3F5F6"))
private val accent = ColorProvider(Color.parseColor("#C6F26E"))
private val surface = ColorProvider(Color.parseColor("#15181C"))
private val button = ColorProvider(Color.parseColor("#1E2228"))

class QuickLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val logIntent = Intent(context, MainActivity::class.java).apply { action = MainActivity.ACTION_LOG }
        val scanIntent = Intent(context, MainActivity::class.java).apply { action = MainActivity.ACTION_SCAN }
        provideContent {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(10),
            ) {
                Text("METRIQ", style = TextStyle(color = accent))
                Row(GlanceModifier.fillMaxWidth().padding(top = 8)) {
                    Column(
                        GlanceModifier
                            .defaultWeight()
                            .background(button)
                            .padding(10)
                            .clickable(actionStartActivity(logIntent)),
                    ) { Text("Registar", style = TextStyle(color = ink)) }
                    Column(
                        GlanceModifier
                            .defaultWeight()
                            .background(button)
                            .padding(10)
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
