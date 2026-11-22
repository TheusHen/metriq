package com.metriq.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metriq.app.AppSettings
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.Metrics
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.display
import com.metriq.app.ui.theme.h1
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import com.metriq.app.ui.theme.number40
import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.Sex
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Seven short steps, one question at a time. The frame is centred and capped so it
 * reads the same on a phone and on a wide window; the footer stays put while the
 * question column scrolls.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reducedMotion = AppSettings.bool(AppSettings.REDUCED_MOTION)

    // Short opacity-only step feedback. Never scales from zero, never bounces.
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(state.step, reducedMotion) {
        if (reducedMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0.6f)
            reveal.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MetriqColors.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = Metrics.contentMaxWidth)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ProgressHeader(step = state.step)

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Metrics.pageGutter),
            ) {
                Spacer(Modifier.height(28.dp))
                Column(Modifier.graphicsLayer { alpha = reveal.value }) {
                    when (state.step) {
                        0 -> WelcomeStep()
                        1 -> AboutStep(state, viewModel)
                        2 -> BodyStep(state, viewModel)
                        3 -> ActivityStep(state, viewModel)
                        4 -> GoalStep(state, viewModel)
                        5 -> DietStep(state, viewModel)
                        else -> PlanStep(state, viewModel)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Footer(state = state, viewModel = viewModel, onComplete = onComplete)
        }
    }
}

@Composable
private fun ProgressHeader(step: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.pageGutter)) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "PASSO ${step + 1} DE ${OnboardingViewModel.TOTAL_STEPS}",
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(OnboardingViewModel.TOTAL_STEPS) { index ->
                val color = when {
                    index == step -> MetriqColors.accent
                    index < step -> MetriqColors.ink2
                    else -> MetriqColors.border
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
    }
}

@Composable
private fun Footer(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.pageGutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.step > 0) {
            Text(
                text = "Voltar",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier
                    .clickable { viewModel.previousStep() }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        } else {
            Spacer(Modifier.width(1.dp))
        }

        val canContinue = state.step != OnboardingViewModel.LAST_STEP ||
            !state.isMinor || state.guardianAcknowledged
        PrimaryButton(
            text = if (state.step == OnboardingViewModel.LAST_STEP) "Concluir" else "Continuar",
            enabled = canContinue,
            onClick = {
                if (state.step == OnboardingViewModel.LAST_STEP) {
                    viewModel.complete()
                    onComplete()
                } else {
                    viewModel.nextStep()
                }
            },
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 12.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MetriqColors.accent,
            contentColor = MetriqColors.accentInk,
            disabledContainerColor = MetriqColors.surface2,
            disabledContentColor = MetriqColors.ink3,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyStrong, maxLines = 1)
    }
}

@Composable
private fun Question(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.h1,
        color = MetriqColors.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.label,
        color = MetriqColors.ink2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    description: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val titleColor = when {
        !enabled -> MetriqColors.ink3
        selected -> MetriqColors.ink
        else -> MetriqColors.ink2
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyStrong,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = if (selected && enabled) MetriqColors.ink else MetriqColors.border,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected && enabled) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MetriqColors.ink),
                    )
                }
            }
        }
    }
    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
}

@Composable
private fun NumberField(
    label: String,
    initial: String,
    onValueChange: (String) -> Unit,
    helper: String? = null,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                text = next
                onValueChange(next)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.number24,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (helper != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MinorBanner() {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text(
            text = "Ainda não tens 18 anos",
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Vamos adaptar as recomendações à tua idade. No fim, um responsável confirma o plano contigo.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
    }
}

@Composable
private fun WelcomeStep() {
    Text(
        text = "Metriq",
        style = MaterialTheme.typography.display,
        color = MetriqColors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Local-first nutrition tracker",
        style = MaterialTheme.typography.h2,
        color = MetriqColors.ink2,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = "Um registo de refeições e peso que vive no teu dispositivo. Leu-me bem: não há conta, nem servidor, nem recolha de dados de utilização.",
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
    )
    Spacer(Modifier.height(24.dp))
    WelcomeFact("Sem conta", "Não pedimos email nem palavra-passe.")
    WelcomeFact("Fica no telemóvel", "Os teus registos não saem do dispositivo.")
    WelcomeFact("Sem telemetria", "Não enviamos estatísticas de utilização.")
}

@Composable
private fun WelcomeFact(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyStrong, color = MetriqColors.ink)
        Spacer(Modifier.height(3.dp))
        Text(text = description, style = MaterialTheme.typography.caption, color = MetriqColors.ink3)
    }
    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
}

@Composable
private fun AboutStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("Sobre ti", "Dois dados básicos para estimar o teu gasto em repouso.")
    Spacer(Modifier.height(28.dp))

    FieldLabel("Sexo")
    Spacer(Modifier.height(6.dp))
    ChoiceRow(
        title = "Feminino",
        selected = state.sex == Sex.FEMALE,
        onClick = { viewModel.setSex(Sex.FEMALE) },
    )
    ChoiceRow(
        title = "Masculino",
        selected = state.sex == Sex.MALE,
        onClick = { viewModel.setSex(Sex.MALE) },
    )

    Spacer(Modifier.height(24.dp))
    NumberField(
        label = "Idade",
        initial = state.age.toString(),
        onValueChange = { raw -> raw.toIntLoose()?.let { viewModel.setAge(it) } },
        helper = "Anos completos",
    )

    if (state.isMinor) {
        Spacer(Modifier.height(20.dp))
        MinorBanner()
    }
}

@Composable
private fun BodyStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("O teu corpo", "Altura e peso chegam. A gordura corporal é opcional.")
    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NumberField(
            label = "Altura (cm)",
            initial = state.heightCm.toString(),
            onValueChange = { raw -> raw.toDoubleLoose()?.let { viewModel.setHeightCm(it) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Peso (kg)",
            initial = state.weightKg.toString(),
            onValueChange = { raw -> raw.toDoubleLoose()?.let { viewModel.setWeightKg(it) } },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(20.dp))
    NumberField(
        label = "Gordura corporal (%)",
        initial = state.bodyFatPercent?.toString().orEmpty(),
        onValueChange = { raw -> viewModel.setBodyFat(raw.toDoubleLoose()) },
        helper = "Opcional. Se não sabes, deixa em branco.",
    )
}

@Composable
private fun ActivityStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("O teu dia a dia", "Escolhe o que melhor descreve uma semana normal.")
    Spacer(Modifier.height(20.dp))

    val choices = listOf(
        ActivityLevel.SEDENTARY to "Trabalho sentado, pouco exercício.",
        ActivityLevel.LIGHT to "Algumas caminhadas durante a semana.",
        ActivityLevel.MODERATE to "Treino três a cinco vezes por semana.",
        ActivityLevel.HIGH to "Treino quase todos os dias.",
        ActivityLevel.ATHLETE to "Treino intenso, por vezes duas sessões.",
    )
    choices.forEach { (level, description) ->
        ChoiceRow(
            title = activityLabel(level),
            description = description,
            selected = state.activityLevel == level,
            onClick = { viewModel.setActivity(level) },
        )
    }
}

@Composable
private fun GoalStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("O teu objetivo", "Podes mudar isto quando quiseres, sem recomeçar.")
    Spacer(Modifier.height(20.dp))

    val choices = listOf(
        GoalType.MAINTAIN to ("Manter" to "Ficar mais ou menos onde estás."),
        GoalType.CUT to ("Perder" to "Criar um défice gradual, sem pressa."),
        GoalType.BULK to ("Ganhar" to "Um pequeno excedente para ganhar massa."),
    )
    choices.forEach { (goal, titleAndDescription) ->
        val (title, description) = titleAndDescription
        ChoiceRow(
            title = title,
            description = description,
            selected = state.goal == goal,
            onClick = { viewModel.setGoal(goal) },
        )
    }

    Spacer(Modifier.height(28.dp))
    FieldLabel("Meta opcional")
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Se tens um número em mente, a estimativa ganha contexto.",
        style = MaterialTheme.typography.caption,
        color = MetriqColors.ink3,
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NumberField(
            label = "Peso-alvo (kg)",
            initial = state.targetWeightKg?.toString().orEmpty(),
            onValueChange = { raw -> viewModel.setTargetWeightKg(raw.toDoubleLoose()) },
            helper = "Opcional",
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Prazo (semanas)",
            initial = state.weeks?.toString().orEmpty(),
            onValueChange = { raw -> viewModel.setWeeks(raw.toIntLoose()) },
            helper = "Opcional",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DietStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("Como gostas de comer?", "Não é uma dieta rígida: é só a divisão dos macronutrientes.")
    Spacer(Modifier.height(20.dp))

    val ketoDescription = if (state.isMinor) {
        "Não recomendado a menores de 18 anos sem acompanhamento."
    } else {
        "Muito poucos hidratos, mais gordura nas refeições."
    }
    val choices = listOf(
        DietStyle.BALANCED to ("Equilibrado" to "Uma divisão simples entre hidratos, proteína e gordura."),
        DietStyle.LOW_CARB_HIGH_FAT to ("Baixo em hidratos" to "Menos hidratos, um pouco mais de gordura."),
        DietStyle.HIGH_CARB_LOW_FAT to ("Alto em hidratos" to "Mais hidratos, útil se treinas muito."),
        DietStyle.KETO to ("Cetogénico" to ketoDescription),
    )
    choices.forEach { (diet, titleAndDescription) ->
        val (title, description) = titleAndDescription
        val available = diet != DietStyle.KETO || !state.isMinor
        ChoiceRow(
            title = title,
            description = description,
            selected = available && state.diet == diet,
            enabled = available,
            onClick = { viewModel.setDiet(diet) },
        )
    }
}

@Composable
private fun PlanStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question("O teu plano", "Uma estimativa inicial. Vai-se ajustando à medida que registas refeições e peso.")
    Spacer(Modifier.height(28.dp))

    val plan = state.plan
    if (plan == null) {
        Text(
            text = "A calcular o teu plano…",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
    } else {
        Text(
            text = formatCount(plan.dailyKcal.roundToInt()),
            style = MaterialTheme.typography.number40,
            color = MetriqColors.ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "kcal por dia",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )

        Spacer(Modifier.height(24.dp))
        StatRow("Gasto em repouso (BMR)", "${formatCount(plan.bmr.roundToInt())} kcal")
        StatRow("Gasto diário (TDEE)", "${formatCount(plan.tdee.roundToInt())} kcal")
        Spacer(Modifier.height(16.dp))
        StatRow("Proteína", "${plan.proteinG} g")
        StatRow("Hidratos", "${plan.carbG} g")
        StatRow("Gordura", "${plan.fatG} g")

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Porquê estes números",
            style = MaterialTheme.typography.h2,
            color = MetriqColors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = plan.explanation,
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )

        if (plan.warnings.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Pontos a ter em conta",
                style = MaterialTheme.typography.h2,
                color = MetriqColors.ink,
            )
            Spacer(Modifier.height(8.dp))
            plan.warnings.forEach { warning ->
                Text(
                    text = "— $warning",
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }

    if (state.isMinor) {
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MetriqColors.surface1)
                .clickable { viewModel.setGuardianAcknowledged(!state.guardianAcknowledged) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.guardianAcknowledged,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MetriqColors.ink,
                    uncheckedColor = MetriqColors.ink3,
                    checkmarkColor = MetriqColors.bg,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Confirmação de um responsável",
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.ink,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Um adulto responsável viu este plano e concorda com ele.",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink,
            maxLines = 1,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
}

private fun activityLabel(level: ActivityLevel): String = when (level) {
    ActivityLevel.SEDENTARY -> "Sedentário"
    ActivityLevel.LIGHT -> "Leve"
    ActivityLevel.MODERATE -> "Moderado"
    ActivityLevel.HIGH -> "Alto"
    ActivityLevel.ATHLETE -> "Atleta"
}

private fun formatCount(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

private fun String.toDoubleLoose(): Double? = replace(',', '.').trim().toDoubleOrNull()

private fun String.toIntLoose(): Int? = toDoubleLoose()?.roundToInt()
