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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metriq.app.health.HealthConnectSyncWorker
import com.metriq.app.log.FoodRecognitionManager
import com.metriq.app.ui.aimodels.AiModelsScreen
import com.metriq.app.ui.analytics.AnalyticsScreen
import com.metriq.app.ui.analytics.AnalyticsViewModel
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
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.metriq.core.NutritionLabelParser
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val navAction = MutableStateFlow<String?>(null)
    private var foodDraftPrefill: FoodDraft? = null
    private var pendingCsv: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.read(this)?.let { report ->
            CrashLog.clear(this)
            AlertDialog.Builder(this)
                .setTitle("Metriq — crash report")
                .setMessage(report.take(4000))
                .setPositiveButton("Copy") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Metriq crash", report))
                    Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }
        enableEdgeToEdge()
        navAction.value = intent?.action
        setContent {
            MetriqTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                var screen by rememberSaveable {
                    mutableStateOf(screenFor(intent?.action) ?: Screen.DASHBOARD)
                }
                val action by navAction.collectAsStateWithLifecycle()
                LaunchedEffect(action) {
                    screenFor(action)?.let { screen = it }
                }
                val recognition = remember { FoodRecognitionManager(AppGraph.store) }
                val health = AppGraph.healthConnect
                val healthStatus = when {
                    health == null -> "Unavailable on this device"
                    !health.isAvailable() -> "Health Connect is not installed"
                    else -> "Available"
                }
                val hcLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                ) {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                            .setInputData(Data.Builder().putBoolean("manual", true).build())
                            .build(),
                    )
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

                BackHandler(enabled = screen != Screen.DASHBOARD) { screen = Screen.DASHBOARD }

                when (screen) {
                    Screen.DASHBOARD -> HomeScreen(
                        state = state,
                        currentScreen = Screen.DASHBOARD,
                        onNavigate = { screen = it },
                        onAddFood = { screen = Screen.SEARCH },
                        onDeleteLog = { viewModel.deleteLog(it) },
                        onAcceptCheckin = { viewModel.acceptCheckin() },
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
                                Toast.makeText(context, "Barcode not in local pack.", Toast.LENGTH_SHORT).show()
                                foodDraftPrefill = null
                                screen = Screen.CREATE
                            }
                        },
                    )

                    Screen.LABEL_SCAN -> LabelScannerScreen(
                        onBack = { screen = Screen.SEARCH },
                        onEdit = { label ->
                            foodDraftPrefill = FoodDraft(
                                name = label.servingSize?.let { "Label food · $it" } ?: "Label food",
                                brand = null,
                                calories = label.calories ?: 0.0,
                                protein = label.protein ?: 0.0,
                                fat = label.totalFat ?: 0.0,
                                carbs = label.totalCarbs ?: 0.0,
                                servingLabel = label.servingSize ?: "1 serving",
                                servingGrams = 100.0,
                            )
                            screen = Screen.CREATE
                        },
                        onResult = { label: NutritionLabelParser.ParsedLabel ->
                            viewModel.createFood(
                                FoodDraft(
                                    name = "Label food",
                                    brand = null,
                                    calories = label.calories ?: 0.0,
                                    protein = label.protein ?: 0.0,
                                    fat = label.totalFat ?: 0.0,
                                    carbs = label.totalCarbs ?: 0.0,
                                    servingLabel = label.servingSize ?: "1 serving",
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
                        targetWeightKg = null,
                        onBack = { screen = Screen.DASHBOARD },
                        onSave = { profile, target ->
                            viewModel.saveProfile(profile, target)
                            screen = Screen.DASHBOARD
                        },
                        onExport = { exportLauncher.launch("metriq-export.csv") },
                        onConnectHealth = {
                            if (health != null && health.isAvailable()) {
                                hcLauncher.launch(health.permissions)
                            } else {
                                Toast.makeText(context, healthStatus, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSyncHealth = {
                            WorkManager.getInstance(context).enqueue(
                                OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                            .setInputData(Data.Builder().putBoolean("manual", true).build())
                            .build(),
                            )
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
                        settings = AppSettings.all(),
                        onSettingChange = { key, value -> viewModel.setSetting(key, value) },
                        onExport = { exportLauncher.launch("metriq-export.csv") },
                        onSyncHealth = {
                            WorkManager.getInstance(context).enqueue(
                                OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                            .setInputData(Data.Builder().putBoolean("manual", true).build())
                            .build(),
                            )
                        },
                        onConnectHealth = {
                            if (health != null && health.isAvailable()) {
                                hcLauncher.launch(health.permissions)
                            } else {
                                Toast.makeText(context, healthStatus, Toast.LENGTH_SHORT).show()
                            }
                        },
                        healthStatus = healthStatus,
                        onBack = { screen = Screen.PROFILE },
                        onOpenAiModels = { screen = Screen.AI_MODELS },
                    )

                    Screen.AI_MODELS -> AiModelsScreen(
                        settings = AppSettings.all(),
                        onSettingChange = { key, value -> viewModel.setSetting(key, value) },
                        onBack = { screen = Screen.SETTINGS },
                    )
                }
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
    }

    private fun setting(key: String, default: Boolean): Boolean =
        AppGraph.store.settings.get(key)?.toBooleanStrictOrNull() ?: default

    private fun formatEpoch(raw: String): String = try {
        Instant.ofEpochMilli(raw.toLong())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH))
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
