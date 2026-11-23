package com.metriq.app.ai

import android.content.Context
import com.metriq.app.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

/** One measured result. [score] is a real value; [unit] and [note] explain how to read it. */
data class BenchmarkResult(
    val label: String,
    val score: Double,
    val unit: String,
    val note: String,
)

/**
 * Real, local measurements only. No model accuracy or speed numbers are invented: there is no
 * inference runtime bundled, so the only throughput reported is for the deterministic
 * phrase/portion parser that actually runs on this device.
 */
object ModelBenchmark {

    private data class Portion(val amount: Double, val unit: String)

    private val AMOUNT = Regex("""^\s*(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)""")

    private val SAMPLES = listOf(
        "2 cups oatmeal",
        "150 g grilled chicken",
        "1 slice whole wheat bread",
        "3 bananas",
        "250 ml almond milk",
        "1/2 cup brown rice",
        "200 g salmon fillet",
        "2 tbsp peanut butter",
        "1 large egg",
        "100 g greek yogurt",
        "1 cup spinach",
        "50 g cheddar cheese",
        "300 g cooked pasta",
        "1 medium apple",
        "2 oranges",
        "75 g almonds",
        "1 tsp olive oil",
        "400 ml water",
        "180 g firm tofu",
        "1 cup cooked lentils",
    )

    // Published so the JIT cannot elide the parser work during the timing loop.
    @Volatile
    private var sink = 0.0

    suspend fun run(context: Context): List<BenchmarkResult> = withContext(Dispatchers.Default) {
        val ram = DeviceProfiler.ramMb(context)
        val free = DeviceProfiler.freeMb(context)
        val score = DeviceProfiler.score(context)

        val modelId = AppSettings.get(AppSettings.AI_MODEL_ID)
        val selectedInstalled = modelId.isNotBlank() && ModelManager(context).pathFor(modelId).exists()
        val engineNote = if (selectedInstalled) {
            "O modelo '$modelId' está instalado e selecionado. Não é incluído nenhum motor de " +
                "inferência, pelo que a análise continua a correr no motor determinístico — " +
                "não são reivindicadas velocidade ou precisão do modelo."
        } else {
            "determinístico (sem modelo carregado)"
        }

        val opsPerSec = measureThroughput()
        val headline = String.format(Locale.US, "%.0f", opsPerSec)

        listOf(
            BenchmarkResult("Motor ativo", 0.0, "", engineNote),
            BenchmarkResult(
                label = "Taxa do analisador",
                score = opsPerSec,
                unit = "frases/seg",
                note = "Medido localmente em ${SAMPLES.size} frases incluídas (~$headline/seg).",
            ),
            BenchmarkResult(
                label = "Armazenamento livre",
                score = free.toDouble(),
                unit = "MB",
                note = "Espaço disponível na partição de ficheiros da app (StatFs).",
            ),
            BenchmarkResult(
                label = "Classe de memória",
                score = ram.toDouble(),
                unit = "MB",
                note = "Limite de memória por app obtido de ActivityManager.getMemoryClass().",
            ),
            BenchmarkResult(
                label = "Perfil do dispositivo",
                score = 0.0,
                unit = score,
                note = "Categoria BAIXO / MÉDIO / ALTO derivada da classe de memória.",
            ),
        )
    }

    /** Times repeated deterministic parsing of the built-in sample phrases. */
    private fun measureThroughput(): Double {
        // Warm up the code path so compilation/JIT cost is not counted.
        repeat(200) { for (phrase in SAMPLES) parsePortion(phrase) }

        val start = System.nanoTime()
        val deadline = start + 300_000_000L // 300 ms sample window
        var ops = 0L
        var localSink = 0.0
        while (System.nanoTime() < deadline) {
            for (phrase in SAMPLES) {
                localSink += parsePortion(phrase)?.amount ?: 0.0
            }
            ops += SAMPLES.size.toLong()
        }
        val elapsed = max(1L, System.nanoTime() - start)
        sink = localSink
        return ops * 1_000_000_000.0 / elapsed.toDouble()
    }

    private fun parsePortion(phrase: String): Portion? {
        val match = AMOUNT.find(phrase) ?: return null
        val raw = match.groupValues[1].replace(',', '.').trim()
        val amount = when {
            raw.contains(' ') -> {
                val parts = raw.split(Regex("\\s+"))
                val whole = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
                val fraction = parts.getOrNull(1)?.let(::fractionValue) ?: 0.0
                whole + fraction
            }
            raw.contains('/') -> fractionValue(raw) ?: return null
            else -> raw.toDoubleOrNull() ?: return null
        }
        return Portion(amount, match.groupValues[2].lowercase(Locale.ROOT))
    }

    private fun fractionValue(text: String): Double? {
        val parts = text.split('/')
        if (parts.size != 2) return null
        val numerator = parts[0].toDoubleOrNull() ?: return null
        val denominator = parts[1].toDoubleOrNull() ?: return null
        return if (denominator != 0.0) numerator / denominator else null
    }
}
