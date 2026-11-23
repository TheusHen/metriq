package com.metriq.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metriq.app.AppSettings
import com.metriq.app.R
import com.metriq.app.health.HealthConnectManager
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.Metrics
import com.metriq.app.ui.theme.Spacing
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Eight short steps, one question at a time. The frame is centred and capped so it
 * reads the same on a phone and on a wide window; the footer stays put while the
 * question column scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    healthConnect: HealthConnectManager? = null,
    onRequestHealthConnect: () -> Unit = {},
    onConnectHevy: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reducedMotion = AppSettings.bool(AppSettings.REDUCED_MOTION)

    // Integration status is derived from the real manager, then kept in ViewModel state.
    LaunchedEffect(Unit) {
        val manager = healthConnect
        val available = manager?.isAvailable() == true
        val connected = if (available) {
            runCatching { manager?.hasAllPermissions() == true }.getOrDefault(false)
        } else {
            false
        }
        viewModel.refreshHealthStatus(available, connected)
    }

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
                Spacer(Modifier.height(Spacing.lg))
                Column(Modifier.graphicsLayer { alpha = reveal.value }) {
                    when (state.step) {
                        0 -> WelcomeStep()
                        1 -> AboutStep(state, viewModel)
                        2 -> BodyStep(state, viewModel)
                        3 -> ActivityStep(state, viewModel)
                        4 -> GoalStep(state, viewModel)
                        5 -> DietStep(state, viewModel)
                        6 -> IntegrationsStep(state, viewModel, onRequestHealthConnect, onConnectHevy)
                        else -> PlanStep(state, viewModel)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Footer(state = state, viewModel = viewModel, onComplete = onComplete)
        }
    }
}

@Composable
private fun ProgressHeader(step: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.pageGutter)) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.onboarding_step, step + 1, OnboardingViewModel.TOTAL_STEPS),
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
        Spacer(Modifier.height(Spacing.sm))
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
        Spacer(Modifier.height(Spacing.lg))
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
                text = stringResource(R.string.back),
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
            text = stringResource(
                if (state.step == OnboardingViewModel.LAST_STEP) R.string.onboarding_complete
                else R.string.onboarding_continue,
            ),
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
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
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
        modifier = modifier,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyStrong, maxLines = 1)
    }
}

@Composable
private fun SmallButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MetriqColors.surface2,
            contentColor = MetriqColors.ink,
            disabledContainerColor = MetriqColors.surface2,
            disabledContentColor = MetriqColors.ink3,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = Modifier
            .width(46.dp)
            .height(40.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = text, style = MaterialTheme.typography.number24, maxLines = 1)
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
                    Spacer(Modifier.height(Spacing.xs))
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
    password: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                text = next
                onValueChange(next)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.number24,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Decimal,
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else
                androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        if (helper != null) {
            Spacer(Modifier.height(Spacing.sm))
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
            text = stringResource(R.string.onboarding_minor_title),
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_minor_info),
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
    }
}

@Composable
private fun WelcomeStep() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.size(104.dp),
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.display,
        color = MetriqColors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = stringResource(R.string.onboarding_subtitle),
        style = MaterialTheme.typography.h2,
        color = MetriqColors.ink2,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = stringResource(R.string.onboarding_privacy_info),
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
    )
    Spacer(Modifier.height(Spacing.xl))
    WelcomeFact(
        stringResource(R.string.onboarding_fact1_title),
        stringResource(R.string.onboarding_fact1_body),
    )
    WelcomeFact(
        stringResource(R.string.onboarding_fact2_title),
        stringResource(R.string.onboarding_fact2_body),
    )
    WelcomeFact(
        stringResource(R.string.onboarding_fact3_title),
        stringResource(R.string.onboarding_fact3_body),
    )
    WelcomeFact(
        stringResource(R.string.onboarding_fact4_title),
        stringResource(R.string.onboarding_fact4_body),
    )
}

@Composable
private fun WelcomeFact(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyStrong, color = MetriqColors.ink)
        Spacer(Modifier.height(Spacing.xs))
        Text(text = description, style = MaterialTheme.typography.caption, color = MetriqColors.ink3)
    }
    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
}

@Composable
private fun AboutStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(stringResource(R.string.onboarding_about_title), stringResource(R.string.onboarding_about_body))
    Spacer(Modifier.height(28.dp))

    FieldLabel(stringResource(R.string.onboarding_sex))
    Spacer(Modifier.height(Spacing.sm))
    ChoiceRow(
        title = stringResource(R.string.onboarding_female),
        selected = state.sex == Sex.FEMALE,
        onClick = { viewModel.setSex(Sex.FEMALE) },
    )
    ChoiceRow(
        title = stringResource(R.string.onboarding_male),
        selected = state.sex == Sex.MALE,
        onClick = { viewModel.setSex(Sex.MALE) },
    )

    Spacer(Modifier.height(24.dp))
    NumberField(
        label = stringResource(R.string.onboarding_age),
        initial = state.age.toString(),
        onValueChange = { raw -> raw.toIntLoose()?.let { viewModel.setAge(it) } },
        helper = stringResource(R.string.onboarding_age_info),
    )

    if (state.isMinor) {
        Spacer(Modifier.height(20.dp))
        MinorBanner()
    }
}

@Composable
private fun BodyStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(stringResource(R.string.onboarding_body_title), stringResource(R.string.onboarding_body_body))
    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NumberField(
            label = stringResource(R.string.onboarding_height),
            initial = state.heightCm.toString(),
            onValueChange = { raw -> raw.toDoubleLoose()?.let { viewModel.setHeightCm(it) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.onboarding_weight),
            initial = state.weightKg.toString(),
            onValueChange = { raw -> raw.toDoubleLoose()?.let { viewModel.setWeightKg(it) } },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(20.dp))
    NumberField(
        label = stringResource(R.string.onboarding_body_fat),
        initial = state.bodyFatPercent?.toString().orEmpty(),
        onValueChange = { raw -> viewModel.setBodyFat(raw.toDoubleLoose()) },
        helper = stringResource(R.string.onboarding_body_fat_info),
    )
}

@Composable
private fun ActivityStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(
        stringResource(R.string.onboarding_activity_title),
        stringResource(R.string.onboarding_activity_body),
    )
    Spacer(Modifier.height(20.dp))

    val choices = listOf(
        ActivityLevel.SEDENTARY to R.string.onboarding_activity_sedentary,
        ActivityLevel.LIGHT to R.string.onboarding_activity_light,
        ActivityLevel.MODERATE to R.string.onboarding_activity_moderate,
        ActivityLevel.HIGH to R.string.onboarding_activity_high,
        ActivityLevel.ATHLETE to R.string.onboarding_activity_athlete,
    )
    choices.forEach { (level, descriptionRes) ->
        ChoiceRow(
            title = activityLabel(level),
            description = stringResource(descriptionRes),
            selected = state.activityLevel == level,
            onClick = { viewModel.setActivity(level) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(stringResource(R.string.onboarding_goal_title), stringResource(R.string.onboarding_goal_body))
    Spacer(Modifier.height(20.dp))

    val choices = listOf(
        GoalType.MAINTAIN to (R.string.onboarding_goal_maintain to R.string.onboarding_goal_maintain_info),
        GoalType.CUT to (R.string.onboarding_goal_cut to R.string.onboarding_goal_cut_info),
        GoalType.BULK to (R.string.onboarding_goal_bulk to R.string.onboarding_goal_bulk_info),
    )
    choices.forEach { (goal, titleAndDescription) ->
        val (titleRes, descriptionRes) = titleAndDescription
        ChoiceRow(
            title = stringResource(titleRes),
            description = stringResource(descriptionRes),
            selected = state.goal == goal,
            onClick = { viewModel.setGoal(goal) },
        )
    }

    Spacer(Modifier.height(28.dp))
    FieldLabel(stringResource(R.string.onboarding_optional_goal))
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.onboarding_target_info),
        style = MaterialTheme.typography.caption,
        color = MetriqColors.ink3,
    )
    Spacer(Modifier.height(14.dp))

    FieldLabel(stringResource(R.string.onboarding_target))
    Spacer(Modifier.height(Spacing.xs))
    NumberField(
        label = stringResource(R.string.onboarding_optional),
        initial = state.targetWeightKg?.toString().orEmpty(),
        onValueChange = { raw -> viewModel.setTargetWeightKg(raw.toDoubleLoose()) },
        helper = null,
    )

    Spacer(Modifier.height(Spacing.md))
    FieldLabel(stringResource(R.string.onboarding_deadline))
    Spacer(Modifier.height(Spacing.xs))
    val weeks = state.weeks
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallButton(
            text = "−",
            description = stringResource(R.string.onboarding_deadline_minus),
            enabled = weeks != null && weeks > 1,
            onClick = { viewModel.setWeeks((weeks ?: 4).minus(1).takeIf { it >= 1 }) },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (weeks == null) stringResource(R.string.onboarding_optional)
            else stringResource(R.string.onboarding_deadline_weeks, weeks),
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        SmallButton(
            text = "+",
            description = stringResource(R.string.onboarding_deadline_plus),
            enabled = true,
            onClick = { viewModel.setWeeks((weeks ?: 3).plus(1)) },
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    Slider(
        value = (weeks ?: 4).toFloat().coerceIn(1f, 52f),
        onValueChange = { viewModel.setWeeks(it.roundToInt()) },
        valueRange = 1f..52f,
        steps = 50,
    )

    var showDatePicker by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { showDatePicker = true }) {
            Text(stringResource(R.string.onboarding_deadline_pick_date))
        }
        if (weeks != null) {
            TextButton(onClick = { viewModel.setWeeks(null) }) {
                Text(stringResource(R.string.onboarding_deadline_clear))
            }
        }
    }

    val feedback = state.plan?.let { DeadlinePlanner.feedback(it, viewModel.input(state), LocalDate.now()) }
    Spacer(Modifier.height(Spacing.sm))
    DeadlineFeedbackPanel(feedback)

    if (showDatePicker) {
        val today = LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setWeeks(DeadlinePlanner.weeksUntil(today, picked))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.onboarding_deadline_date_title),
                style = MaterialTheme.typography.h2,
                color = MetriqColors.ink,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DeadlineFeedbackPanel(feedback: DeadlineFeedback?) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .background(MetriqColors.surface1)
            .padding(14.dp),
    ) {
        FieldLabel(stringResource(R.string.onboarding_deadline_title))
        Spacer(Modifier.height(Spacing.sm))
        if (feedback == null) {
            Text(
                text = stringResource(R.string.onboarding_deadline_none),
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
            )
            return@Column
        }
        StatRow(
            stringResource(R.string.onboarding_deadline_kcal),
            formatCount(feedback.dailyKcal),
        )
        StatRow(
            stringResource(R.string.onboarding_deadline_rate),
            formatRate(feedback.kgPerWeek),
        )
        StatRow(
            stringResource(R.string.onboarding_deadline_reference),
            formatRate(feedback.referenceKgPerWeek),
        )
        val pattern = stringResource(R.string.onboarding_deadline_date_pattern)
        StatRow(
            stringResource(R.string.onboarding_deadline_target_date),
            DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(feedback.targetDate),
        )
        Spacer(Modifier.height(Spacing.sm))
        val warning = when (feedback.assessment) {
            DeadlineAssessment.AGGRESSIVE -> stringResource(
                R.string.onboarding_deadline_aggressive,
                formatRate(feedback.kgPerWeek),
                formatRate(feedback.referenceKgPerWeek),
            )
            DeadlineAssessment.INCOMPATIBLE -> stringResource(R.string.onboarding_deadline_incompatible)
            DeadlineAssessment.ON_TRACK -> stringResource(R.string.onboarding_deadline_ok)
            DeadlineAssessment.NONE -> stringResource(R.string.onboarding_deadline_none)
        }
        Text(
            text = warning,
            style = MaterialTheme.typography.body,
            color = if (feedback.isRealistic) MetriqColors.ink2 else MetriqColors.ink,
        )
    }
}

@Composable
private fun DietStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(
        stringResource(R.string.onboarding_diet_title),
        stringResource(R.string.onboarding_diet_body),
    )
    Spacer(Modifier.height(20.dp))

    val ketoDescription = if (state.isMinor) {
        stringResource(R.string.onboarding_diet_keto_minor)
    } else {
        stringResource(R.string.onboarding_diet_keto_info)
    }
    val choices = listOf(
        DietStyle.BALANCED to (
            stringResource(R.string.onboarding_diet_balanced) to
                stringResource(R.string.onboarding_diet_balanced_info)
            ),
        DietStyle.LOW_CARB_HIGH_FAT to (
            stringResource(R.string.onboarding_diet_low_carb) to
                stringResource(R.string.onboarding_diet_low_carb_info)
            ),
        DietStyle.HIGH_CARB_LOW_FAT to (
            stringResource(R.string.onboarding_diet_high_carb) to
                stringResource(R.string.onboarding_diet_high_carb_info)
            ),
        DietStyle.KETO to (stringResource(R.string.onboarding_diet_keto) to ketoDescription),
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
private fun IntegrationsStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onRequestHealthConnect: () -> Unit,
    onConnectHevy: (String) -> Unit,
) {
    Question(
        stringResource(R.string.onboarding_integrations_title),
        stringResource(R.string.onboarding_integrations_body),
    )
    Spacer(Modifier.height(Spacing.lg))

    IntegrationCard(title = stringResource(R.string.health_connect)) {
        Text(
            text = when (state.healthStatus) {
                OnboardingHealthStatus.CHECKING -> stringResource(R.string.onboarding_hc_checking)
                OnboardingHealthStatus.NOT_INSTALLED -> stringResource(R.string.onboarding_hc_not_installed)
                OnboardingHealthStatus.AVAILABLE -> stringResource(R.string.onboarding_hc_available)
                OnboardingHealthStatus.CONNECTED -> stringResource(R.string.onboarding_hc_connected)
                OnboardingHealthStatus.DENIED -> stringResource(R.string.onboarding_hc_denied)
            },
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        state.healthImported?.let { count ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.onboarding_hc_imported, count),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = stringResource(R.string.profile_connect),
            enabled = state.healthStatus == OnboardingHealthStatus.AVAILABLE ||
                state.healthStatus == OnboardingHealthStatus.DENIED ||
                state.healthStatus == OnboardingHealthStatus.CONNECTED,
            onClick = onRequestHealthConnect,
        )
    }

    Spacer(Modifier.height(Spacing.lg))
    IntegrationCard(title = stringResource(R.string.onboarding_hevy_title)) {
        Text(
            text = stringResource(R.string.onboarding_hevy_available),
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
        )
        Spacer(Modifier.height(Spacing.md))
        NumberField(
            label = stringResource(R.string.onboarding_hevy_key),
            initial = state.hevyApiKey,
            onValueChange = { viewModel.setHevyKey(it) },
            password = true,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = stringResource(R.string.onboarding_hevy_connect),
            enabled = state.hevyApiKey.isNotBlank() && state.hevyStatus != OnboardingHevyStatus.CONNECTING,
            onClick = { onConnectHevy(state.hevyApiKey) },
        )
        Spacer(Modifier.height(Spacing.sm))
        when (state.hevyStatus) {
            OnboardingHevyStatus.IDLE -> Unit
            OnboardingHevyStatus.CONNECTING -> StatusLine(stringResource(R.string.onboarding_hevy_connecting))
            OnboardingHevyStatus.CONNECTED -> StatusLine(
                stringResource(R.string.onboarding_hevy_connected, state.hevyMessage.orEmpty()),
            )
            OnboardingHevyStatus.FAILED -> StatusLine(
                stringResource(R.string.onboarding_hevy_failed, state.hevyMessage.orEmpty()),
            )
            OnboardingHevyStatus.KEY_SAVE_FAILED -> StatusLine(stringResource(R.string.key_save_error))
        }
    }

    Spacer(Modifier.height(Spacing.xl))
    Text(
        text = stringResource(R.string.onboarding_integrations_skip),
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
        modifier = Modifier
            .clickable { viewModel.skipIntegrations() }
            .padding(vertical = 8.dp),
    )
    if (state.integrationsSkipped) {
        Text(
            text = stringResource(R.string.onboarding_integrations_skipped),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
    }
}

@Composable
private fun IntegrationCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MetriqColors.border, RoundedCornerShape(10.dp))
            .background(MetriqColors.surface1)
            .padding(16.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.h2, color = MetriqColors.ink)
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.caption, color = MetriqColors.ink2)
}

@Composable
private fun PlanStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Question(
        stringResource(R.string.onboarding_plan_title),
        stringResource(R.string.onboarding_plan_intro),
    )
    Spacer(Modifier.height(28.dp))

    val plan = state.plan
    if (plan == null) {
        Text(
            text = stringResource(R.string.onboarding_calculating),
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
            text = stringResource(R.string.onboarding_kcal_day),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )

        Spacer(Modifier.height(24.dp))
        StatRow(stringResource(R.string.onboarding_bmr), "${formatCount(plan.bmr.roundToInt())} kcal")
        StatRow(stringResource(R.string.onboarding_tdee), "${formatCount(plan.tdee.roundToInt())} kcal")
        Spacer(Modifier.height(16.dp))
        StatRow(stringResource(R.string.onboarding_protein), "${plan.proteinG} g")
        StatRow(stringResource(R.string.onboarding_carbs), "${plan.carbG} g")
        StatRow(stringResource(R.string.onboarding_fat), "${plan.fatG} g")

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.onboarding_why_numbers),
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
                text = stringResource(R.string.onboarding_considerations),
                style = MaterialTheme.typography.h2,
                color = MetriqColors.ink,
            )
            Spacer(Modifier.height(8.dp))
            plan.warnings.forEach { warning ->
                Text(
                    text = stringResource(R.string.onboarding_warning_prefix, warning),
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
                    text = stringResource(R.string.onboarding_guardian_title),
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.ink,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_guardian_info),
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

@Composable
private fun activityLabel(level: ActivityLevel): String = stringResource(
    when (level) {
        ActivityLevel.SEDENTARY -> R.string.onboarding_activity_label_sedentary
        ActivityLevel.LIGHT -> R.string.onboarding_activity_label_light
        ActivityLevel.MODERATE -> R.string.onboarding_activity_label_moderate
        ActivityLevel.HIGH -> R.string.onboarding_activity_label_high
        ActivityLevel.ATHLETE -> R.string.onboarding_activity_label_athlete
    },
)

private fun formatCount(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

private fun formatRate(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

private fun String.toDoubleLoose(): Double? = replace(',', '.').trim().toDoubleOrNull()

private fun String.toIntLoose(): Int? = toDoubleLoose()?.roundToInt()
