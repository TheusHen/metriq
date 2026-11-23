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
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.metriq.app.AppGraph
import com.metriq.app.MainActivity
import java.time.LocalDate

private val ink = ColorProvider(Color.parseColor("#F3F5F6"))
private val muted = ColorProvider(Color.parseColor("#9AA1AB"))
private val accent = ColorProvider(Color.parseColor("#C6F26E"))
private val surface = ColorProvider(Color.parseColor("#15181C"))

private val labelStyle = TextStyle(color = muted)
private val valueStyle = TextStyle(color = ink, fontWeight = FontWeight.Bold)
private val accentStyle = TextStyle(color = accent, fontWeight = FontWeight.Bold)

class DailyMacroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppGraph.ensure(context)
        val logs = AppGraph.store.logs.forDate(LocalDate.now().toString())
        val kcal = logs.sumOf { it.calories }.toInt()
        val p = logs.sumOf { it.protein }.toInt()
        val f = logs.sumOf { it.fat }.toInt()
        val c = logs.sumOf { it.carbs }.toInt()

        provideContent {
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(12)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("HOJE", style = labelStyle)
                Text("$kcal kcal", style = valueStyle)
                Text("do orçamento diário", style = labelStyle)
                Row(GlanceModifier.fillMaxWidth().padding(top = 8)) {
                    Text("P $p g  ", style = TextStyle(color = ColorProvider(Color.parseColor("#FF9B82"))))
                    Text("C $c g  ", style = TextStyle(color = ColorProvider(Color.parseColor("#F5C96B"))))
                    Text("F $f g", style = TextStyle(color = ColorProvider(Color.parseColor("#7AB8FF"))))
                }
                Text("METRIQ", style = accentStyle)
            }
        }
    }
}

class DailyMacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DailyMacroWidget()
}
