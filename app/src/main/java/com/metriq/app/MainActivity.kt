package com.metriq.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metriq.app.health.HealthConnectSyncWorker
import com.metriq.app.log.FoodRecognitionManager
import com.metriq.app.hevy.HevyKeyStore
import com.metriq.app.integrations.HistoricalImport
import com.metriq.app.ui.aimodels.AiModelsScreen
import com.metriq.app.ui.integrations.IntegrationsScreen
import com.metriq.app.ui.goal.GoalEditorSheet
import com.metriq.app.ui.analytics.AnalyticsScreen
import com.metriq.app.ui.analytics.AnalyticsViewModel
import com.metriq.app.ui.onboarding.OnboardingScreen
import com.metriq.app.ui.onboarding.OnboardingViewModel
import com.metriq.app.ui.settings.DataSettingsScreen
import com.metriq.app.ui.voice.VoiceLogScreen
import com.metriq.app.ui.body.BodyScreen
import com.metriq.app.ui.habits.HabitsScreen
import com.metriq.app.ui.home.DashboardViewModel
import com.metriq.app.ui.home.HomeScreen
import com.metriq.app.ui.log.BarcodeScreen
import com.metriq.app.ui.log.CreateFoodScreen
import com.metriq.app.ui.log.FoodDraft
import com.metriq.app.ui.log.FoodPhotoScreen
import com.metriq.app.ui.log.FoodSearchScreen
import com.metriq.app.ui.log.LabelScannerScreen
import com.metriq.app.ui.nutrients.MicronutrientsScreen
import com.metriq.app.ui.recipes.RecipesScreen
import com.metriq.app.ui.settings.ProfileScreen
import com.metriq.app.ui.steps.StepsScreen
import com.metriq.app.ui.theme.MetriqTheme
import com.metriq.app.ui.weight.WeightScreen
import androidx.health.connect.client.PermissionController
import com.metriq.core.NutritionLabelParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HealthRequestSource { ONBOARDING, DASHBOARD }

private enum class HealthPermissionUiState { UNKNOWN, GRANTED, DENIED }

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val navAction = MutableStateFlow<String?>(null)
    private val healthPermissionState = MutableStateFlow(HealthPermissionUiState.UNKNOWN)
    private var healthRequestSource = HealthRequestSource.DASHBOARD
    private var foodDraftPrefill: FoodDraft? = null
    private var pendingCsv: String? = null

    // One launcher for the whole Activity: the wizard and the dashboard share it.
    private val hcLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> onHealthPermissionsResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.read(this)?.let { report ->
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.crash_title))
                .setMessage(report.take(4000))
                .setPositiveButton(getString(R.string.crash_copy)) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.app_name), report),
                    )
                    Toast.makeText(this, getString(R.string.crash_copied), Toast.LENGTH_SHORT).show()
                    // Only an explicit copy discards the report; plain dismiss keeps it.
                    CrashLog.clear(this)
                }
                .setNegativeButton(getString(R.string.crash_close), null)
                .show()
        }
        enableEdgeToEdge()
        // A fresh launch may route from the intent; a rotation must not re-apply it.
        if (savedInstanceState == null) navAction.value = intent?.action
        setContent {
            MetriqTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val healthPermission by healthPermissionState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var showGoalEditor by remember { mutableStateOf(false) }
                var settingsRevision by remember { mutableIntStateOf(0) }
                val settingsSnapshot = AppSettings.all().also { settingsRevision }
                // Read the keystore once, not on every recomposition.
                var hevyKeyInput by remember { mutableStateOf(HevyKeyStore.read(context).orEmpty()) }
                var hevyKeyPersisted by remember { mutableStateOf(hevyKeyInput) }
                var screen by rememberSaveable {
                    mutableStateOf(
                        when (intent?.getStringExtra("start_screen")) {
                            "settings" -> Screen.SETTINGS
                            "analytics" -> Screen.ANALYTICS
                            "integrations" -> Screen.INTEGRATIONS
                            else -> screenFor(intent?.action) ?: Screen.DASHBOARD
                        },
                    )
                }
                var onboardingDone by remember {
                    mutableStateOf(
                        intent?.getBooleanExtra("skip_onboarding", false) == true ||
                            AppSettings.bool(AppSettings.ONBOARDING_COMPLETE),
                    )
                }

                if (!onboardingDone) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        healthConnect = AppGraph.healthConnect,
                        onRequestHealthConnect = {
                            healthRequestSource = HealthRequestSource.ONBOARDING
                            val health = AppGraph.healthConnect
                            if (health != null && health.isAvailable()) {
                                hcLauncher.launch(health.permissions)
                            } else {
                                onboardingViewModel.refreshHealthStatus(false, false)
                            }
                        },
                        onConnectHevy = { key -> connectHevyFromOnboarding(key) },
                        onComplete = {
                            onboardingDone = true
                            screen = Screen.DASHBOARD
                            viewModel.refresh()
                        },
                    )
                } else {
                val action by navAction.collectAsStateWithLifecycle()
                LaunchedEffect(action) {
                    val target = screenFor(action) ?: return@LaunchedEffect
                    screen = target
                    navAction.value = null // consume once: rotation/deep-link replay must not re-route
                }
                val recognition = remember { FoodRecognitionManager(AppGraph.store) }
                val health = AppGraph.healthConnect
                val healthStatus = when {
                    health == null -> stringResource(R.string.health_unavailable)
                    !health.isAvailable() -> stringResource(R.string.health_not_installed)
                    healthPermission == HealthPermissionUiState.GRANTED -> stringResource(R.string.health_connected)
                    healthPermission == HealthPermissionUiState.DENIED -> stringResource(R.string.health_denied)
                    else -> stringResource(R.string.health_available)
                }
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv"),
                ) { uri ->
                    if (uri != null) {
                        val csv = pendingCsv ?: viewModel.exportCsv()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray())
                        }
                        pendingCsv = null
                    }
                }
                val photoPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    if (uri != null) viewModel.addPhoto(context, uri)
                }

                // System back unwinds the same chain as the header back buttons.
                val backTarget = screen.parent
                BackHandler(enabled = backTarget != null) { backTarget?.let { screen = it } }

                // Persist off the UI thread and only after typing pauses.
                LaunchedEffect(hevyKeyInput) {
                    val key = hevyKeyInput
                    if (key == hevyKeyPersisted) return@LaunchedEffect
                    delay(400)
                    val ok = withContext(Dispatchers.IO) { HevyKeyStore.save(context, key) }
                    if (hevyKeyInput != key) {
                        // Disconnect or a newer edit landed during the write: restore it.
                        withContext(Dispatchers.IO) { HevyKeyStore.save(context, hevyKeyInput) }
                        return@LaunchedEffect
                    }
                    if (ok) {
                        hevyKeyPersisted = key
                    } else {
                        showKeySaveError(context)
                    }
                }

                // Flush a pending edit on rotation/navigation (remember is not saved to a Bundle).
                DisposableEffect(Unit) {
                    onDispose {
                        val pending = hevyKeyInput
                        if (pending != hevyKeyPersisted) {
                            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                                HevyKeyStore.save(context.applicationContext, pending)
                            }
                        }
                    }
                }

                when (screen) {
                    Screen.DASHBOARD -> HomeScreen(
                        state = state,
                        currentScreen = Screen.DASHBOARD,
                        onNavigate = { screen = it },
                        onAddFood = { screen = Screen.SEARCH },
                        onDeleteLog = { viewModel.deleteLog(it) },
                        onAcceptCheckin = { viewModel.acceptCheckin() },
                        onEditGoal = { showGoalEditor = true },
                    )

                    Screen.SEARCH -> FoodSearchScreen(
                        onClose = { screen = Screen.DASHBOARD },
                        onCreateFood = { foodDraftPrefill = null; screen = Screen.CREATE },
                        onScan = { screen = Screen.SCAN },
                        onScanLabel = { screen = Screen.LABEL_SCAN },
                        onPhoto = { screen = Screen.FOOD_PHOTO },
                        onVoice = { screen = Screen.VOICE_LOG },
                        onAdd = { food, grams ->
                            viewModel.addFood(food, grams)
                            screen = Screen.DASHBOARD
                        },
                        budgetConsumed = state.kcalConsumed,
                        budgetTarget = state.kcalTarget,
                        onCopyFromDay = { date -> viewModel.copyFromDay(date) },
                        onQuickAdd = { kcal, protein, fat, carbs ->
                            viewModel.quickAdd(kcal, protein, fat, carbs)
                            screen = Screen.DASHBOARD
                        },
                        onLogDays = { food, grams, days ->
                            viewModel.logDays(food, grams, days)
                            screen = Screen.DASHBOARD
                        },
                    )

                    Screen.SCAN -> BarcodeScreen(
                        onClose = { screen = Screen.SEARCH },
                        onDetected = { code ->
                            val food = AppGraph.store.food.byBarcode(code)
                            if (food != null) {
                                viewModel.addFood(
                                    com.metriq.data.FoodHit(
                                        food.id, food.name, food.brand, food.category, food.iconId,
                                        food.calories, food.protein, food.fat, food.carbs,
                                        food.servingLabel, food.servingGrams,
                                    ),
                                    food.servingGrams ?: 100.0,
                                )
                                screen = Screen.DASHBOARD
                            } else {
                                Toast.makeText(context, context.getString(R.string.barcode_not_local), Toast.LENGTH_SHORT).show()
                                foodDraftPrefill = null
                                screen = Screen.CREATE
                            }
                        },
                    )

                    Screen.LABEL_SCAN -> LabelScannerScreen(
                        onBack = { screen = Screen.SEARCH },
                        onEdit = { label ->
                            foodDraftPrefill = FoodDraft(
                                name = label.servingSize?.let {
                                    context.getString(R.string.label_food_with, it)
                                } ?: context.getString(R.string.label_food),
                                brand = null,
                                calories = label.calories ?: 0.0,
                                protein = label.protein ?: 0.0,
                                fat = label.totalFat ?: 0.0,
                                carbs = label.totalCarbs ?: 0.0,
                                servingLabel = label.servingSize ?: context.getString(R.string.serving_one),
                                servingGrams = 100.0,
                            )
                            screen = Screen.CREATE
                        },
                        onResult = { label: NutritionLabelParser.ParsedLabel ->
                            viewModel.createFood(
                                FoodDraft(
                                    name = context.getString(R.string.label_food),
                                    brand = null,
                                    calories = label.calories ?: 0.0,
                                    protein = label.protein ?: 0.0,
                                    fat = label.totalFat ?: 0.0,
                                    carbs = label.totalCarbs ?: 0.0,
                                    servingLabel = label.servingSize ?: context.getString(R.string.serving_one),
                                    servingGrams = 100.0,
                                ),
                            )
                            screen = Screen.DASHBOARD
                        },
                    )

                    Screen.FOOD_PHOTO -> FoodPhotoScreen(
                        manager = recognition,
                        onBack = { screen = Screen.SEARCH },
                        onSelect = { items ->
                            items.forEach { (food, grams) -> viewModel.addFood(food, grams) }
                            screen = Screen.DASHBOARD
                        },
                    )

                    Screen.VOICE_LOG -> VoiceLogScreen(
                        onBack = { screen = Screen.SEARCH },
                        onSave = { draft ->
                            viewModel.createFood(draft)
                            screen = Screen.DASHBOARD
                        },
                    )

                    Screen.CREATE -> CreateFoodScreen(
                        initial = foodDraftPrefill,
                        onBack = { foodDraftPrefill = null; screen = Screen.SEARCH },
                        onSave = { draft ->
                            viewModel.createFood(draft)
                            foodDraftPrefill = null
                            screen = Screen.DASHBOARD
                        },
                    )

                    Screen.WEIGHT -> WeightScreen(
                        entries = state.weights,
                        todayIso = LocalDate.now().toString(),
                        onBack = { screen = Screen.DASHBOARD },
                        onAdd = { date, kg -> viewModel.addWeight(date, kg) },
                        onDelete = { date -> viewModel.deleteWeight(date) },
                    )

                    Screen.PROFILE -> ProfileScreen(
                        initial = AppGraph.store.profile.get() ?: DefaultProfile.PROFILE,
                        targetWeightKg = GoalStore.read()?.targetWeightKg,
                        onBack = { screen = Screen.DASHBOARD },
                        onSave = { profile, target ->
                            viewModel.saveProfile(profile, target)
                            screen = Screen.DASHBOARD
                        },
                        onExport = { exportLauncher.launch("metriq-export.csv") },
                        onConnectHealth = {
                            healthRequestSource = HealthRequestSource.DASHBOARD
                            if (health != null && health.isAvailable()) {
                                hcLauncher.launch(health.permissions)
                            } else {
                                Toast.makeText(context, healthStatus, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSyncHealth = {
                            HealthConnectSyncWorker.enqueue(context, manual = true)
                        },
                        healthStatus = healthStatus,
                        lastHealthSync = AppGraph.store.settings.get("health_last_sync")?.let(::formatEpoch),
                        importSteps = setting("health_import_steps", true),
                        importWeight = setting("health_import_weight", true),
                        onImportStepsChange = { viewModel.setSetting("health_import_steps", it.toString()) },
                        onImportWeightChange = { viewModel.setSetting("health_import_weight", it.toString()) },
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )

                    Screen.STEPS -> StepsScreen(
                        steps = state.stepsToday,
                        waterMl = state.waterToday,
                        onSaveSteps = { viewModel.saveSteps(it) },
                        onSaveWater = { viewModel.saveWater(it) },
                        onBack = { screen = Screen.DASHBOARD },
                    )

                    Screen.HABITS -> HabitsScreen(
                        habits = state.habits,
                        onAdd = { viewModel.addHabit(it) },
                        onToggle = { viewModel.toggleHabit(it) },
                        onBack = { screen = Screen.DASHBOARD },
                    )

                    Screen.BODY -> BodyScreen(
                        measurements = state.measurements,
                        photos = state.photos,
                        periodToday = state.periodToday,
                        onAddMeasurement = { site, value -> viewModel.addMeasurement(site, value) },
                        onTogglePeriod = { viewModel.setPeriod(it) },
                        onAddPhoto = { photoPick.launch("image/*") },
                        onDeletePhoto = { viewModel.deletePhoto(it) },
                        onBack = { screen = Screen.DASHBOARD },
                    )

                    Screen.RECIPES -> RecipesScreen(
                        recipes = state.recipes,
                        foods = remember { AppGraph.store.food.recent(500) },
                        onAdd = { viewModel.addRecipe(it) },
                        onLog = { viewModel.logRecipe(it) },
                        onBack = { screen = Screen.DASHBOARD },
                    )

                    Screen.MICRONUTRIENTS -> {
                        val profile = AppGraph.store.profile.get() ?: DefaultProfile.PROFILE
                        MicronutrientsScreen(
                            intake = state.micros,
                            sex = profile.sex,
                            age = profile.age,
                            onBack = { screen = Screen.DASHBOARD },
                        )
                    }

                    Screen.ANALYTICS -> {
                        val analyticsViewModel: AnalyticsViewModel = viewModel()
                        AnalyticsScreen(
                            viewModel = analyticsViewModel,
                            onBack = { screen = Screen.DASHBOARD },
                            onExport = {
                                pendingCsv = analyticsViewModel.exportCsv()
                                exportLauncher.launch("metriq-analytics.csv")
                            },
                        )
                    }

                    Screen.SETTINGS -> DataSettingsScreen(
                        settings = settingsSnapshot,
                        onSettingChange = { key, value -> viewModel.setSetting(key, value); settingsRevision++ },
                        onExport = { exportLauncher.launch("metriq-export.csv") },
                        onSyncHealth = {
                            HealthConnectSyncWorker.enqueue(context, manual = true)
                        },
                        onConnectHealth = {
                            healthRequestSource = HealthRequestSource.DASHBOARD
                            if (health != null && health.isAvailable()) {
                                hcLauncher.launch(health.permissions)
                            } else {
                                Toast.makeText(context, healthStatus, Toast.LENGTH_SHORT).show()
                            }
                        },
                        healthStatus = healthStatus,
                        onBack = { screen = Screen.PROFILE },
                        onOpenAiModels = { screen = Screen.AI_MODELS },
                        onOpenIntegrations = { screen = Screen.INTEGRATIONS },
                        hevyConnected = AppSettings.bool(AppSettings.HEVY_CONNECTED) &&
                            AppGraph.hevyRepository.hasKey(),
                        hevyAutoSync = AppSettings.bool(AppSettings.HEVY_AUTO_SYNC),
                        hevyLastSync = AppSettings.get(AppSettings.HEVY_LAST_SYNC).ifBlank { null },
                        hevyWorkoutCount = state.hevyWorkoutCount,
                        hevyApiKey = hevyKeyInput,
                        onHevyApiKeyChange = { key -> hevyKeyInput = key },
                        hevyLastError = AppSettings.get(AppSettings.HEVY_LAST_ERROR).ifBlank { null },
                        onHevyConnect = {
                            scope.launch {
                                // A sync right after typing must not race the debounced save.
                                val ok = withContext(Dispatchers.IO) {
                                    HevyKeyStore.save(context, hevyKeyInput)
                                }
                                if (ok) hevyKeyPersisted = hevyKeyInput
                                else showKeySaveError(context)
                                AppGraph.hevyRepository.sync()
                                viewModel.refresh()
                            }
                        },
                        onHevySync = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    HevyKeyStore.save(context, hevyKeyInput)
                                }
                                if (ok) hevyKeyPersisted = hevyKeyInput
                                else showKeySaveError(context)
                                AppGraph.hevyRepository.sync()
                                viewModel.refresh()
                            }
                        },
                        onHevyDisconnect = {
                            val clearError = context.getString(R.string.settings_hevy_clear_error)
                            hevyKeyInput = ""
                            hevyKeyPersisted = ""
                            scope.launch {
                                val cleared = AppGraph.hevyRepository.clear()
                                if (!cleared) {
                                    Toast.makeText(
                                        context,
                                        clearError,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                viewModel.refresh()
                            }
                        },
                        onHevyAutoSyncChange = { enabled ->
                            viewModel.setSetting(AppSettings.HEVY_AUTO_SYNC, enabled.toString())
                        },
                    )

                    Screen.AI_MODELS -> AiModelsScreen(
                        settings = settingsSnapshot,
                        onSettingChange = { key, value -> viewModel.setSetting(key, value); settingsRevision++ },
                        onBack = { screen = Screen.SETTINGS },
                    )

                    Screen.INTEGRATIONS -> IntegrationsScreen(
                        onBack = { screen = Screen.SETTINGS },
                        onImportHistory = {
                            val nothing = context.getString(R.string.import_empty)
                            scope.launch {
                                val count = HistoricalImport.importFromHealthConnect(context)
                                Toast.makeText(
                                    context,
                                    if (count > 0) context.getString(R.string.import_success, count)
                                    else nothing,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                viewModel.refresh()
                            }
                        },
                        onOpenHevy = { screen = Screen.SETTINGS },
                    )
                }
                if (showGoalEditor) {
                    val profile = AppGraph.store.profile.get() ?: DefaultProfile.PROFILE
                    GoalEditorSheet(
                        suggestedKcal = state.goalRecalculation?.suggestedKcal ?: state.kcalTarget,
                        current = GoalStore.read(),
                        isMinor = profile.age < 18,
                        previewWarnings = viewModel::previewGoalWarnings,
                        onDismiss = { showGoalEditor = false },
                        onApply = { goal ->
                            GoalStore.write(goal)
                            showGoalEditor = false
                            viewModel.refresh()
                        },
                        onReset = {
                            GoalStore.clear()
                            showGoalEditor = false
                            viewModel.refresh()
                        },
                    )
                }
                } // end onboarding else
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navAction.value = intent.action
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        // A permission may have been changed in system settings while we were away.
        lifecycleScope.launch {
            val health = AppGraph.healthConnect
            if (health?.isAvailable() == true) {
                val granted = runCatching { health.hasAllPermissions() }.getOrDefault(false)
                healthPermissionState.value = when {
                    granted -> HealthPermissionUiState.GRANTED
                    healthPermissionState.value == HealthPermissionUiState.DENIED ->
                        HealthPermissionUiState.DENIED
                    else -> HealthPermissionUiState.UNKNOWN
                }
            }
        }
        if (AppSettings.bool(AppSettings.HEVY_CONNECTED) &&
            AppSettings.bool(AppSettings.HEVY_AUTO_SYNC) &&
            HevyKeyStore.read(this) != null
        ) {
            val last = runCatching {
                java.time.Instant.parse(AppSettings.get(AppSettings.HEVY_LAST_SYNC))
            }.getOrNull()
            val stale = last == null ||
                java.time.Duration.between(last, java.time.Instant.now()).toMinutes() >= 30
            if (stale) {
                lifecycleScope.launch {
                    AppGraph.hevyRepository.sync()
                    viewModel.refresh()
                }
            }
        }
    }

    /** The launcher inspects the actual grant set, not just whether the dialog closed. */
    private fun onHealthPermissionsResult(granted: Set<String>) {
        val health = AppGraph.healthConnect
        val hasAll = health != null && granted.containsAll(health.permissions)
        healthPermissionState.value = if (hasAll) {
            HealthPermissionUiState.GRANTED
        } else {
            HealthPermissionUiState.DENIED
        }
        when (healthRequestSource) {
            HealthRequestSource.ONBOARDING -> {
                onboardingViewModel.onHealthPermissionResult(hasAll)
                if (hasAll) {
                    HealthConnectSyncWorker.enqueue(this, manual = true)
                    lifecycleScope.launch {
                        val count = HistoricalImport.backfillAfterConnect(applicationContext)
                        onboardingViewModel.onHealthImport(count)
                    }
                }
            }
            // Preserve the existing Profile/Settings behaviour: a completed request syncs.
            HealthRequestSource.DASHBOARD -> {
                HealthConnectSyncWorker.enqueue(this, manual = true)
                viewModel.refresh()
            }
        }
    }

    /** Connect writes the key immediately (no debounce) and only reports success after sync. */
    private fun connectHevyFromOnboarding(key: String) {
        if (key.isBlank()) {
            onboardingViewModel.onHevyFailed(getString(R.string.onboarding_hevy_empty_key))
            return
        }
        onboardingViewModel.onHevyConnecting()
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { HevyKeyStore.save(applicationContext, key) }
            if (!saved) {
                onboardingViewModel.onHevyKeySaveFailed()
                return@launch
            }
            val result = AppGraph.hevyRepository.sync()
            val connected = AppSettings.bool(AppSettings.HEVY_CONNECTED) &&
                AppGraph.hevyRepository.hasKey() &&
                AppSettings.get(AppSettings.HEVY_LAST_SYNC).isNotBlank() &&
                AppSettings.get(AppSettings.HEVY_LAST_ERROR).isBlank()
            val message = result.message.ifBlank { getString(R.string.onboarding_hevy_error_unknown) }
            if (connected) {
                onboardingViewModel.onHevyConnected(message)
            } else {
                onboardingViewModel.onHevyFailed(message)
            }
            viewModel.refresh()
        }
    }

    private fun setting(key: String, default: Boolean): Boolean =
        AppGraph.store.settings.get(key)?.toBooleanStrictOrNull() ?: default

    private fun formatEpoch(raw: String): String = try {
        Instant.ofEpochMilli(raw.toLong())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
    } catch (_: Exception) {
        raw
    }

    companion object {
        const val ACTION_LOG = "com.metriq.LOG"
        const val ACTION_SCAN = "com.metriq.SCAN"

        fun screenFor(action: String?): Screen? = when (action) {
            ACTION_LOG -> Screen.SEARCH
            ACTION_SCAN -> Screen.SCAN
            else -> null
        }
    }
}

private fun showKeySaveError(context: Context) {
    Toast.makeText(context, context.getString(R.string.key_save_error), Toast.LENGTH_SHORT).show()
}
