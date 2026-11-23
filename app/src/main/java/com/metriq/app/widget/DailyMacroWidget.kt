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

@Suppress("ResourceType")
private val proteinTint = ColorProvider(Color(0xFFFF9B82.toInt()))

@Suppress("ResourceType")
private val carbTint = ColorProvider(Color(0xFFF5C96B.toInt()))

@Suppress("ResourceType")
private val fatTint = ColorProvider(Color(0xFF7AB8FF.toInt()))

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
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("HOJE", style = labelStyle)
                Text("$kcal kcal", style = valueStyle)
                Text("do orçamento diário", style = labelStyle)
                Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("P $p g  ", style = TextStyle(color = proteinTint))
                    Text("C $c g  ", style = TextStyle(color = carbTint))
                    Text("F $f g", style = TextStyle(color = fatTint))
                }
                Text("METRIQ", style = accentStyle)
            }
        }
    }
}

class DailyMacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DailyMacroWidget()
}
