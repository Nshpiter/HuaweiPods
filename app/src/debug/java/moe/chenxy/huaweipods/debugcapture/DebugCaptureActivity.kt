package moe.chenxy.huaweipods.debugcapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.MainActivity
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.ui.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Debug 构建专用的协议采集入口；Release 源集中不存在此 Activity。 */
class DebugCaptureActivity : ComponentActivity() {
    private var resumeToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                CaptureGuideScreen(
                    resumeToken = resumeToken,
                    onOpenMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeToken++
    }
}

private const val GUIDE_PREFS = "debug_capture_guide"
private const val PREF_MODEL = "model"
private const val PREF_NAME_SOURCE = "name_source"
private const val PREF_ISSUE = "issue"
private const val PREF_OFFICIAL_PACKAGE = "official_package"
private const val PREF_HANDLED_SESSION_ID = "handled_session_id"
private const val STATUS_PREFIX = "status."
private const val FEATURE_CATALOG_VERSION = "huawei-headset-v1"
private const val NAME_SOURCE_CONNECTED = "connected_profile"
private const val NAME_SOURCE_MANUAL = "manual"

private const val AI_LIFE_PACKAGE = "com.huawei.smarthome"
private const val SMART_AUDIO_PACKAGE = "com.huawei.smartaudio"

private enum class StepStatus {
    PENDING,
    ACTIVE,
    DONE,
    SKIPPED,
    ABORTED,
}

private data class OfficialApp(
    val packageName: String,
    @StringRes val labelRes: Int,
)

private data class CaptureStep(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

private val officialApps = listOf(
    OfficialApp(AI_LIFE_PACKAGE, R.string.debug_capture_app_ai_life),
    OfficialApp(SMART_AUDIO_PACKAGE, R.string.debug_capture_app_smart_audio),
)

/**
 * 华为耳机通用能力目录。向导只记录官方 App 当前可见的项目，不据此猜测设备能力；
 * 任意设备缺少的项目都可以明确跳过。
 */
private val allHuaweiHeadsetSteps = listOf(
    CaptureStep("device_overview", R.string.debug_capture_step_device_overview_title, R.string.debug_capture_step_device_overview_desc),
    CaptureStep("device_info", R.string.debug_capture_step_device_info_title, R.string.debug_capture_step_device_info_desc),
    CaptureStep("battery_state", R.string.debug_capture_step_battery_state_title, R.string.debug_capture_step_battery_state_desc),
    CaptureStep("noise_modes", R.string.debug_capture_step_noise_modes_title, R.string.debug_capture_step_noise_modes_desc),
    CaptureStep("noise_submodes", R.string.debug_capture_step_noise_submodes_title, R.string.debug_capture_step_noise_submodes_desc),
    CaptureStep("gesture_tap", R.string.debug_capture_step_gesture_tap_title, R.string.debug_capture_step_gesture_tap_desc),
    CaptureStep("gesture_press", R.string.debug_capture_step_gesture_press_title, R.string.debug_capture_step_gesture_press_desc),
    CaptureStep("gesture_swipe_buttons", R.string.debug_capture_step_gesture_swipe_buttons_title, R.string.debug_capture_step_gesture_swipe_buttons_desc),
    CaptureStep("wear_detection", R.string.debug_capture_step_wear_detection_title, R.string.debug_capture_step_wear_detection_desc),
    CaptureStep("drop_reminder", R.string.debug_capture_step_drop_reminder_title, R.string.debug_capture_step_drop_reminder_desc),
    CaptureStep("adaptive_audio", R.string.debug_capture_step_adaptive_audio_title, R.string.debug_capture_step_adaptive_audio_desc),
    CaptureStep("head_motion", R.string.debug_capture_step_head_motion_title, R.string.debug_capture_step_head_motion_desc),
    CaptureStep("voice_control", R.string.debug_capture_step_voice_control_title, R.string.debug_capture_step_voice_control_desc),
    CaptureStep("equalizer", R.string.debug_capture_step_equalizer_title, R.string.debug_capture_step_equalizer_desc),
    CaptureStep("spatial_audio", R.string.debug_capture_step_spatial_audio_title, R.string.debug_capture_step_spatial_audio_desc),
    CaptureStep("audio_quality", R.string.debug_capture_step_audio_quality_title, R.string.debug_capture_step_audio_quality_desc),
    CaptureStep("low_latency", R.string.debug_capture_step_low_latency_title, R.string.debug_capture_step_low_latency_desc),
    CaptureStep("dual_device", R.string.debug_capture_step_dual_device_title, R.string.debug_capture_step_dual_device_desc),
    CaptureStep("case_settings", R.string.debug_capture_step_case_settings_title, R.string.debug_capture_step_case_settings_desc),
    CaptureStep("ear_fit", R.string.debug_capture_step_ear_fit_title, R.string.debug_capture_step_ear_fit_desc),
    CaptureStep("magnetic_power", R.string.debug_capture_step_magnetic_power_title, R.string.debug_capture_step_magnetic_power_desc),
    CaptureStep("disconnect_reconnect", R.string.debug_capture_step_disconnect_reconnect_title, R.string.debug_capture_step_disconnect_reconnect_desc),
    CaptureStep("other_feature", R.string.debug_capture_step_other_feature_title, R.string.debug_capture_step_other_feature_desc),
)

@Composable
private fun CaptureGuideScreen(
    resumeToken: Int,
    onOpenMain: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val startFailedPrefix = stringResource(R.string.debug_capture_start_failed, "")
    val stopFailedPrefix = stringResource(R.string.debug_capture_stop_failed, "")
    val exportFailedPrefix = stringResource(R.string.debug_capture_export_failed, "")
    val prefs = remember { context.getSharedPreferences(GUIDE_PREFS, Context.MODE_PRIVATE) }
    val installedApps = remember(resumeToken) {
        officialApps.filter { context.isPackageInstalled(it.packageName) }
    }

    val initialStoreState = remember {
        runCatching { CaptureStore.getState(context) }.getOrNull()
    }
    val initialActiveSession = initialStoreState?.activeSession
    val storedHeadsetName = remember {
        normalizeStoredHeadsetName(prefs.getString(PREF_MODEL, null))
    }
    val storedHeadsetNameSource = remember {
        prefs.getString(PREF_NAME_SOURCE, null)
    }
    var headsetNameInput by rememberSaveable {
        mutableStateOf(
            initialActiveSession?.headsetModel
                ?: storedHeadsetName,
        )
    }
    var headsetNameSource by rememberSaveable {
        mutableStateOf(
            initialActiveSession?.headsetNameSource
                ?: storedHeadsetNameSource
                ?: NAME_SOURCE_MANUAL,
        )
    }
    var selectedHeadsetAddress by rememberSaveable {
        mutableStateOf(initialActiveSession?.headsetAddress)
    }
    var headsetNameManuallyEdited by rememberSaveable {
        mutableStateOf(
            initialActiveSession == null &&
                storedHeadsetName.isNotBlank() &&
                storedHeadsetNameSource == NAME_SOURCE_MANUAL,
        )
    }
    var activeHeadsetName by rememberSaveable {
        mutableStateOf(initialActiveSession?.headsetModel.orEmpty())
    }
    var issueInput by rememberSaveable {
        mutableStateOf(initialActiveSession?.issueId ?: prefs.getString(PREF_ISSUE, "").orEmpty())
    }
    var selectedOfficialPackage by rememberSaveable {
        mutableStateOf(
            initialActiveSession?.officialAppPackage
                ?: prefs.getString(PREF_OFFICIAL_PACKAGE, null),
        )
    }
    var onlyTargetChecked by rememberSaveable { mutableStateOf(false) }
    var officialAppChecked by rememberSaveable { mutableStateOf(false) }
    var scopeChecked by rememberSaveable { mutableStateOf(false) }
    var privacyChecked by rememberSaveable { mutableStateOf(false) }
    var includeHciSnoop by rememberSaveable { mutableStateOf(false) }
    var storageBusy by remember { mutableStateOf(false) }
    var captureActive by rememberSaveable {
        mutableStateOf(initialActiveSession != null)
    }
    var activeSessionId by rememberSaveable {
        mutableStateOf(initialActiveSession?.id)
    }
    var latestSessionId by rememberSaveable {
        mutableStateOf(initialStoreState?.latestSession?.id)
    }
    var captureFinished by rememberSaveable {
        val latest = initialStoreState?.latestSession
        val handledSessionId = prefs.getString(PREF_HANDLED_SESSION_ID, null)
        mutableStateOf(
            !captureActive && latest != null && !latest.isActive && latest.id != handledSessionId,
        )
    }
    val statusByKey = remember {
        mutableStateMapOf<String, StepStatus>().apply {
            restoreStatuses(prefs, this, initialActiveSession?.id)
        }
    }
    var loadedStatusSessionId by rememberSaveable {
        mutableStateOf(initialActiveSession?.id)
    }

    var bluetoothPermissionGranted by remember {
        mutableStateOf(context.hasBluetoothConnectPermission())
    }
    var permissionAskedAutomatically by rememberSaveable { mutableStateOf(false) }
    var detectionRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var detectingHeadsets by remember { mutableStateOf(false) }
    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothPermissionGranted = granted
        if (granted) detectionRefreshToken++
    }

    LaunchedEffect(installedApps, selectedOfficialPackage, captureActive) {
        if (!captureActive && selectedOfficialPackage !in installedApps.map { it.packageName }) {
            selectedOfficialPackage = installedApps.firstOrNull()?.packageName
        }
        prefs.edit().putString(PREF_OFFICIAL_PACKAGE, selectedOfficialPackage).apply()
    }
    LaunchedEffect(headsetNameInput, headsetNameSource, issueInput) {
        prefs.edit()
            .putString(PREF_MODEL, headsetNameInput)
            .putString(PREF_NAME_SOURCE, headsetNameSource)
            .putString(PREF_ISSUE, issueInput)
            .apply()
    }
    LaunchedEffect(resumeToken) {
        bluetoothPermissionGranted = context.hasBluetoothConnectPermission()
    }
    LaunchedEffect(captureActive, captureFinished, bluetoothPermissionGranted) {
        if (
            !captureActive &&
            !captureFinished &&
            !bluetoothPermissionGranted &&
            !permissionAskedAutomatically
        ) {
            permissionAskedAutomatically = true
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(
        resumeToken,
        detectionRefreshToken,
        bluetoothPermissionGranted,
        captureActive,
        captureFinished,
    ) {
        if (captureActive || captureFinished) return@LaunchedEffect
        if (!bluetoothPermissionGranted) {
            detectionResult = DetectionResult.PermissionRequired
            detectingHeadsets = false
            return@LaunchedEffect
        }

        detectingHeadsets = true
        val result = ConnectedHeadsetDetector.detect(context)
        detectionResult = result
        detectingHeadsets = false
        if (result is DetectionResult.PermissionRequired) {
            bluetoothPermissionGranted = false
        }
        if (
            result is DetectionResult.Success &&
            result.devices.size == 1 &&
            !headsetNameManuallyEdited
        ) {
            val device = result.devices.single()
            headsetNameInput = device.displayName
            selectedHeadsetAddress = device.address
            headsetNameSource = NAME_SOURCE_CONNECTED
        }
    }

    val steps = allHuaweiHeadsetSteps
    val activeStep = steps.firstOrNull { statusByKey[it.key] == StepStatus.ACTIVE }
    val completedCount = steps.count {
        statusByKey[it.key] in setOf(StepStatus.DONE, StepStatus.SKIPPED, StepStatus.ABORTED)
    }
    var currentProtocolEventCount by remember {
        mutableStateOf(
            (initialStoreState?.activeSession ?: initialStoreState?.latestSession)
                ?.protocolEventCount
                ?: 0L,
        )
    }
    LaunchedEffect(resumeToken, storageBusy) {
        if (storageBusy) return@LaunchedEffect
        val storeState = withContext(Dispatchers.IO) {
            runCatching { CaptureStore.getState(context) }.getOrNull()
        } ?: return@LaunchedEffect
        val activeSession = storeState.activeSession
        val latestSession = storeState.latestSession
        captureActive = activeSession != null
        activeSessionId = activeSession?.id
        latestSessionId = latestSession?.id
        currentProtocolEventCount = (activeSession ?: latestSession)?.protocolEventCount ?: 0L

        activeSession?.let { session ->
            activeHeadsetName = session.headsetModel
            headsetNameInput = session.headsetModel
            headsetNameSource = session.headsetNameSource
            selectedHeadsetAddress = session.headsetAddress
            issueInput = session.issueId.orEmpty()
            session.officialAppPackage?.let { selectedOfficialPackage = it }
            if (loadedStatusSessionId != session.id) {
                restoreStatuses(prefs, statusByKey, session.id)
                loadedStatusSessionId = session.id
            }
        }
        captureFinished = activeSession == null &&
            latestSession != null &&
            !latestSession.isActive &&
            latestSession.id != prefs.getString(PREF_HANDLED_SESSION_ID, null)
    }
    val issueValid = isValidIssue(issueInput)
    val canStart = installedApps.isNotEmpty() &&
        selectedOfficialPackage != null &&
        headsetNameInput.isNotBlank() &&
        onlyTargetChecked && officialAppChecked && scopeChecked && privacyChecked && issueValid

    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = safeDrawing.calculateStartPadding(LayoutDirection.Ltr) + 12.dp,
            top = safeDrawing.calculateTopPadding() + 16.dp,
            end = safeDrawing.calculateEndPadding(LayoutDirection.Ltr) + 12.dp,
            bottom = safeDrawing.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CaptureHeader(onOpenMain = onOpenMain)
        }
        item {
            PrivacyCard()
        }

        when {
            captureActive -> {
                item {
                    SectionCard(title = stringResource(R.string.debug_capture_active_title)) {
                        StatusBadge(
                            text = stringResource(R.string.debug_capture_active_badge),
                            color = Color(0xFFD84315),
                        )
                        Spacer(Modifier.height(8.dp))
                        SummaryText(stringResource(R.string.debug_capture_active_hint))
                        Spacer(Modifier.height(8.dp))
                        SummaryText(stringResource(R.string.debug_capture_progress, completedCount, steps.size))
                        SummaryText(
                            stringResource(
                                R.string.debug_capture_event_count,
                                currentProtocolEventCount,
                            ),
                        )
                        SummaryText(
                            buildString {
                                append(activeHeadsetName)
                                issueInput.trim().takeIf { it.isNotEmpty() }?.let {
                                    append(" · ")
                                    append(it)
                                }
                            },
                        )
                        if (storageBusy) {
                            SummaryText(stringResource(R.string.debug_capture_working))
                        }
                    }
                }
                items(steps, key = { it.key }) { step ->
                    val status = statusByKey[step.key] ?: StepStatus.PENDING
                    val title = stringResource(step.titleRes)
                    val description = stringResource(step.descriptionRes)
                    OperationCard(
                        step = step,
                        status = status,
                        blockedByAnotherStep = storageBusy || (activeStep != null && activeStep.key != step.key),
                        busy = storageBusy,
                        onStart = startStep@{
                            if (storageBusy) return@startStep
                            val details = markerDetails(activeHeadsetName, title, description)
                            storageBusy = true
                            coroutineScope.launch {
                                try {
                                    val marked = addMarkerSafely(
                                        context,
                                        "operation.${step.key}.start",
                                        details,
                                    )
                                    if (marked) {
                                        updateStatus(
                                            prefs,
                                            statusByKey,
                                            activeSessionId,
                                            step.key,
                                            StepStatus.ACTIVE,
                                        )
                                        if (!context.openOfficialApp(selectedOfficialPackage)) {
                                            context.toast(R.string.debug_capture_official_open_failed)
                                        }
                                    } else {
                                        context.toast(R.string.debug_capture_marker_failed)
                                    }
                                } finally {
                                    storageBusy = false
                                }
                            }
                        },
                        onOpenOfficial = {
                            if (!context.openOfficialApp(selectedOfficialPackage)) {
                                context.toast(R.string.debug_capture_official_open_failed)
                            }
                        },
                        onComplete = completeStep@{
                            if (storageBusy) return@completeStep
                            val details = markerDetails(activeHeadsetName, title, description)
                            storageBusy = true
                            coroutineScope.launch {
                                try {
                                    val marked = addMarkerSafely(
                                        context,
                                        "operation.${step.key}.done",
                                        details,
                                    )
                                    if (marked) {
                                        updateStatus(
                                            prefs,
                                            statusByKey,
                                            activeSessionId,
                                            step.key,
                                            StepStatus.DONE,
                                        )
                                    } else {
                                        context.toast(R.string.debug_capture_marker_failed)
                                    }
                                } finally {
                                    storageBusy = false
                                }
                            }
                        },
                        onSkip = skipStep@{
                            if (storageBusy) return@skipStep
                            val details = markerDetails(activeHeadsetName, title, "设备界面未提供此功能")
                            storageBusy = true
                            coroutineScope.launch {
                                try {
                                    val marked = addMarkerSafely(
                                        context,
                                        "operation.${step.key}.skipped",
                                        details,
                                    )
                                    if (marked) {
                                        updateStatus(
                                            prefs,
                                            statusByKey,
                                            activeSessionId,
                                            step.key,
                                            StepStatus.SKIPPED,
                                        )
                                    } else {
                                        context.toast(R.string.debug_capture_marker_failed)
                                    }
                                } finally {
                                    storageBusy = false
                                }
                            }
                        },
                    )
                }
                item {
                    SectionCard(title = stringResource(R.string.debug_capture_stop_title)) {
                        SummaryText(stringResource(R.string.debug_capture_stop_hint))
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = stringResource(R.string.debug_capture_stop),
                            onClick = stopCapture@{
                                if (storageBusy) return@stopCapture
                                storageBusy = true
                                coroutineScope.launch {
                                    delay(300L)
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            if (!CaptureEventReceiver.awaitPendingEvents(context)) {
                                                runCatching {
                                                    CaptureStore.addMarker(
                                                        context,
                                                        "capture.receiver_queue_drain_timeout",
                                                        "停止前等待接收队列超时，尾部事件可能不完整",
                                                    )
                                                }
                                            }
                                            activeStep?.let { step ->
                                                runCatching {
                                                    CaptureStore.addMarker(
                                                        context,
                                                        "operation.${step.key}.aborted",
                                                        "用户停止采集时此项目尚未完成",
                                                    )
                                                }
                                            }
                                            checkNotNull(CaptureStore.stopSession(context, reason = "user")) {
                                                "当前没有活动的采集会话"
                                            }
                                        }
                                    }.onSuccess { stoppedSession ->
                                        activeStep?.let { step ->
                                            updateStatus(
                                                prefs,
                                                statusByKey,
                                                activeSessionId,
                                                step.key,
                                                StepStatus.ABORTED,
                                            )
                                        }
                                        captureActive = false
                                        captureFinished = true
                                        latestSessionId = stoppedSession.id
                                        currentProtocolEventCount = stoppedSession.protocolEventCount
                                    }
                                    .onFailure {
                                        context.toast(stopFailedPrefix + it.userMessage())
                                    }
                                    storageBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().alpha(if (storageBusy) 0.45f else 1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }

            captureFinished -> {
                item {
                    SectionCard(title = stringResource(R.string.debug_capture_finished_title)) {
                        SummaryText(stringResource(R.string.debug_capture_finished_body))
                        SummaryText(
                            stringResource(
                                R.string.debug_capture_event_count,
                                currentProtocolEventCount,
                            ),
                        )
                        if (currentProtocolEventCount == 0L) {
                            SummaryText(
                                text = stringResource(R.string.debug_capture_no_protocol_events),
                                color = Color(0xFFC62828),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        ChecklistRow(
                            text = stringResource(R.string.debug_capture_include_hci),
                            checked = includeHciSnoop,
                            onCheckedChange = { if (!storageBusy) includeHciSnoop = it },
                        )
                        if (includeHciSnoop) {
                            SummaryText(
                                text = stringResource(R.string.debug_capture_include_hci_warning),
                                color = Color(0xFFC62828),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(
                            text = stringResource(R.string.debug_capture_export),
                            onClick = exportCapture@{
                                if (storageBusy) return@exportCapture
                                val includeHciForExport = includeHciSnoop
                                storageBusy = true
                                coroutineScope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            CaptureStore.exportLatest(
                                                context,
                                                includeHciSnoop = includeHciForExport,
                                            )
                                        }
                                    }
                                    .onSuccess { export ->
                                        val shareIntent = CaptureContract.createShareIntent(export)
                                        context.startActivity(shareIntent)
                                    }
                                    .onFailure {
                                        context.toast(exportFailedPrefix + it.userMessage())
                                    }
                                    storageBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().alpha(if (storageBusy) 0.45f else 1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = stringResource(R.string.debug_capture_new_session),
                            onClick = {
                                if (storageBusy) return@TextButton
                                captureFinished = false
                                includeHciSnoop = false
                                latestSessionId?.let { handledSessionId ->
                                    prefs.edit()
                                        .putString(PREF_HANDLED_SESSION_ID, handledSessionId)
                                        .apply()
                                }
                                activeSessionId = null
                                activeHeadsetName = ""
                                loadedStatusSessionId = null
                                resetStatuses(statusByKey)
                                headsetNameInput = ""
                                headsetNameSource = NAME_SOURCE_MANUAL
                                selectedHeadsetAddress = null
                                headsetNameManuallyEdited = false
                                onlyTargetChecked = false
                                officialAppChecked = false
                                scopeChecked = false
                                privacyChecked = false
                                detectionRefreshToken++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            else -> {
                item {
                    PreparationCard(
                        installedApps = installedApps,
                        selectedOfficialPackage = selectedOfficialPackage,
                        onOfficialPackageSelected = {
                            selectedOfficialPackage = it
                            prefs.edit().putString(PREF_OFFICIAL_PACKAGE, it).apply()
                        },
                        onlyTargetChecked = onlyTargetChecked,
                        onOnlyTargetChecked = { onlyTargetChecked = it },
                        officialAppChecked = officialAppChecked,
                        onOfficialAppChecked = { officialAppChecked = it },
                        scopeChecked = scopeChecked,
                        onScopeChecked = { scopeChecked = it },
                        privacyChecked = privacyChecked,
                        onPrivacyChecked = { privacyChecked = it },
                    )
                }
                item {
                    SectionCard(title = stringResource(R.string.debug_capture_metadata_title)) {
                        Text(
                            text = stringResource(R.string.debug_capture_model_title),
                            style = MiuixTheme.textStyles.headline1,
                        )
                        Spacer(Modifier.height(8.dp))
                        HeadsetDetectionContent(
                            result = detectionResult,
                            detecting = detectingHeadsets,
                            selectedAddress = selectedHeadsetAddress,
                            selectedFromConnection = headsetNameSource == NAME_SOURCE_CONNECTED,
                            onGrantPermission = {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            },
                            onRefresh = { detectionRefreshToken++ },
                            onSelect = { device ->
                                headsetNameInput = device.displayName
                                headsetNameSource = NAME_SOURCE_CONNECTED
                                selectedHeadsetAddress = device.address
                                headsetNameManuallyEdited = false
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = headsetNameInput,
                            onValueChange = { value ->
                                headsetNameInput = value.take(120)
                                headsetNameSource = NAME_SOURCE_MANUAL
                                selectedHeadsetAddress = null
                                headsetNameManuallyEdited = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SummaryText(stringResource(R.string.debug_capture_model_hint))
                        SummaryText(stringResource(R.string.debug_capture_model_manual_hint))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.debug_capture_issue_title),
                            style = MiuixTheme.textStyles.headline1,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = issueInput,
                            onValueChange = { issueInput = it.take(160) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SummaryText(
                            text = stringResource(
                                if (issueInput.isBlank() || issueValid) {
                                    R.string.debug_capture_issue_hint
                                } else {
                                    R.string.debug_capture_issue_invalid
                                }
                            ),
                            color = if (issueInput.isNotBlank() && !issueValid) Color(0xFFC62828) else null,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = stringResource(R.string.debug_capture_start),
                            onClick = startCapture@{
                                if (!canStart || storageBusy) return@startCapture
                                val sessionMetadata = CaptureSessionMetadata(
                                    issueId = issueInput.trim().ifBlank { null },
                                    headsetModel = headsetNameInput.trim(),
                                    officialAppPackage = selectedOfficialPackage,
                                    headsetAddress = selectedHeadsetAddress,
                                    headsetNameSource = headsetNameSource,
                                    featureCatalogVersion = FEATURE_CATALOG_VERSION,
                                    notes = null,
                                )
                                storageBusy = true
                                coroutineScope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            CaptureStore.startSession(
                                                context,
                                                sessionMetadata,
                                            )
                                        }
                                    }.onSuccess { startedSession ->
                                        resetStatuses(statusByKey)
                                        activeSessionId = startedSession.id
                                        loadedStatusSessionId = startedSession.id
                                        activeHeadsetName = startedSession.headsetModel
                                        headsetNameInput = startedSession.headsetModel
                                        headsetNameSource = startedSession.headsetNameSource
                                        selectedHeadsetAddress = startedSession.headsetAddress
                                        issueInput = startedSession.issueId.orEmpty()
                                        startedSession.officialAppPackage?.let {
                                            selectedOfficialPackage = it
                                        }
                                        latestSessionId = startedSession.id
                                        currentProtocolEventCount = 0L
                                        captureActive = true
                                        captureFinished = false
                                    }.onFailure {
                                        context.toast(startFailedPrefix + it.userMessage())
                                    }
                                    storageBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().alpha(if (canStart && !storageBusy) 1f else 0.45f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.debug_capture_hci_title)) {
                SummaryText(stringResource(R.string.debug_capture_hci_body))
            }
        }
    }
}

@Composable
private fun CaptureHeader(onOpenMain: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        StatusBadge(
            text = stringResource(R.string.debug_capture_badge),
            color = Color(0xFFD84315),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.debug_capture_title),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.debug_capture_subtitle),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            text = stringResource(R.string.debug_capture_open_main),
            onClick = onOpenMain,
        )
    }
}

@Composable
private fun PrivacyCard() {
    SectionCard(title = stringResource(R.string.debug_capture_privacy_title)) {
        SummaryText(
            text = stringResource(R.string.debug_capture_privacy_body),
            color = Color(0xFFC62828),
        )
    }
}

@Composable
private fun PreparationCard(
    installedApps: List<OfficialApp>,
    selectedOfficialPackage: String?,
    onOfficialPackageSelected: (String) -> Unit,
    onlyTargetChecked: Boolean,
    onOnlyTargetChecked: (Boolean) -> Unit,
    officialAppChecked: Boolean,
    onOfficialAppChecked: (Boolean) -> Unit,
    scopeChecked: Boolean,
    onScopeChecked: (Boolean) -> Unit,
    privacyChecked: Boolean,
    onPrivacyChecked: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.debug_capture_prepare_title)) {
        SummaryText(stringResource(R.string.debug_capture_prepare_hint))
        Spacer(Modifier.height(12.dp))
        ChecklistRow(
            text = stringResource(R.string.debug_capture_prepare_single_device),
            checked = onlyTargetChecked,
            onCheckedChange = onOnlyTargetChecked,
        )
        ChecklistRow(
            text = stringResource(R.string.debug_capture_prepare_official_app),
            checked = officialAppChecked,
            onCheckedChange = onOfficialAppChecked,
        )
        ChecklistRow(
            text = stringResource(R.string.debug_capture_prepare_lsp_scope),
            checked = scopeChecked,
            onCheckedChange = onScopeChecked,
        )
        ChecklistRow(
            text = stringResource(R.string.debug_capture_prepare_privacy),
            checked = privacyChecked,
            onCheckedChange = onPrivacyChecked,
        )
        Spacer(Modifier.height(8.dp))
        if (installedApps.isEmpty()) {
            SummaryText(
                text = stringResource(R.string.debug_capture_official_missing),
                color = Color(0xFFC62828),
            )
        } else {
            val installedLabels = installedApps.map { stringResource(it.labelRes) }.joinToString("、")
            SummaryText(stringResource(R.string.debug_capture_official_detected, installedLabels))
            if (installedApps.size > 1) {
                Spacer(Modifier.height(6.dp))
                SummaryText(stringResource(R.string.debug_capture_official_choose))
                installedApps.forEach { app ->
                    Spacer(Modifier.height(6.dp))
                    SelectionRow(
                        title = stringResource(app.labelRes),
                        selected = selectedOfficialPackage == app.packageName,
                        onClick = { onOfficialPackageSelected(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadsetDetectionContent(
    result: DetectionResult?,
    detecting: Boolean,
    selectedAddress: String?,
    selectedFromConnection: Boolean,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (ConnectedHeadset) -> Unit,
) {
    when {
        detecting -> {
            SummaryText(stringResource(R.string.debug_capture_model_detecting))
        }

        result == null -> {
            SummaryText(stringResource(R.string.debug_capture_model_detecting))
        }

        result is DetectionResult.PermissionRequired -> {
            SummaryText(
                text = stringResource(R.string.debug_capture_model_permission_required),
                color = Color(0xFFC62828),
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = stringResource(R.string.debug_capture_model_grant_permission),
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }

        result is DetectionResult.Success -> {
            when (result.devices.size) {
                0 -> SummaryText(stringResource(R.string.debug_capture_model_none))
                1 -> SummaryText(
                    stringResource(
                        R.string.debug_capture_model_detected,
                        result.devices.single().displayName,
                    ),
                )
                else -> SummaryText(stringResource(R.string.debug_capture_model_multiple))
            }
            result.devices.forEach { device ->
                Spacer(Modifier.height(6.dp))
                SelectionRow(
                    title = device.selectionLabel(),
                    selected = selectedFromConnection && selectedAddress == device.address,
                    onClick = { onSelect(device) },
                )
            }
            if (result.timedOut) {
                Spacer(Modifier.height(6.dp))
                SummaryText(stringResource(R.string.debug_capture_model_timeout))
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = stringResource(R.string.debug_capture_model_refresh),
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        result is DetectionResult.BluetoothDisabled -> {
            SummaryText(stringResource(R.string.debug_capture_model_bluetooth_disabled))
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = stringResource(R.string.debug_capture_model_refresh),
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        result is DetectionResult.BluetoothUnavailable -> {
            SummaryText(stringResource(R.string.debug_capture_model_bluetooth_unavailable))
        }

        result is DetectionResult.Failed -> {
            SummaryText(
                text = stringResource(R.string.debug_capture_model_failed, result.reason),
                color = Color(0xFFC62828),
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = stringResource(R.string.debug_capture_model_refresh),
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OperationCard(
    step: CaptureStep,
    status: StepStatus,
    blockedByAnotherStep: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onOpenOfficial: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    SectionCard(title = stringResource(step.titleRes)) {
        StatusBadge(
            text = stringResource(
                when (status) {
                    StepStatus.PENDING -> R.string.debug_capture_status_pending
                    StepStatus.ACTIVE -> R.string.debug_capture_status_active
                    StepStatus.DONE -> R.string.debug_capture_status_done
                    StepStatus.SKIPPED -> R.string.debug_capture_status_skipped
                    StepStatus.ABORTED -> R.string.debug_capture_status_aborted
                }
            ),
            color = when (status) {
                StepStatus.PENDING -> Color(0xFF616161)
                StepStatus.ACTIVE -> Color(0xFFD84315)
                StepStatus.DONE -> Color(0xFF2E7D32)
                StepStatus.SKIPPED -> Color(0xFF546E7A)
                StepStatus.ABORTED -> Color(0xFF6D4C41)
            },
        )
        Spacer(Modifier.height(8.dp))
        SummaryText(stringResource(step.descriptionRes))
        Spacer(Modifier.height(12.dp))
        when (status) {
            StepStatus.PENDING -> {
                val enabled = !blockedByAnotherStep
                TextButton(
                    text = stringResource(R.string.debug_capture_start_step),
                    onClick = { if (enabled) onStart() },
                    modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = stringResource(R.string.debug_capture_skip_step),
                    onClick = { if (enabled) onSkip() },
                    modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
                )
            }

            StepStatus.ACTIVE -> {
                TextButton(
                    text = stringResource(R.string.debug_capture_open_official),
                    onClick = onOpenOfficial,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.debug_capture_complete_step),
                    onClick = { if (!busy) onComplete() },
                    modifier = Modifier.fillMaxWidth().alpha(if (busy) 0.45f else 1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = stringResource(R.string.debug_capture_skip_step),
                    onClick = { if (!busy) onSkip() },
                    modifier = Modifier.fillMaxWidth().alpha(if (busy) 0.45f else 1f),
                )
            }

            StepStatus.DONE, StepStatus.SKIPPED, StepStatus.ABORTED -> Unit
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ChecklistRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckIndicator(checked)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckIndicator(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.18f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SummaryText(text: String, color: Color? = null) {
    Text(
        text = text,
        color = color ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
    )
}

private fun Context.isPackageInstalled(packageName: String): Boolean = runCatching {
    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
}.isSuccess

private fun Context.hasBluetoothConnectPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

private fun Context.openOfficialApp(packageName: String?): Boolean {
    if (packageName == null) return false
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return runCatching {
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

private fun Context.toast(@StringRes messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

private fun isValidIssue(value: String): Boolean {
    val normalized = value.trim()
    return normalized.isEmpty() ||
        normalized.matches(Regex("#?\\d+")) ||
        normalized.matches(Regex("https?://[^\\s]+/issues/\\d+(?:[/?#][^\\s]*)?"))
}

private fun normalizeStoredHeadsetName(value: String?): String = when (value.orEmpty().trim()) {
    "FREEBUDS_5", "FreeBuds 5" -> "HUAWEI FreeBuds 5"
    "FREECLIP_2", "FreeClip 2" -> "HUAWEI FreeClip 2"
    else -> value.orEmpty().trim()
}

private fun ConnectedHeadset.selectionLabel(): String {
    val profileLabel = profiles
        .sortedBy { it.ordinal }
        .joinToString("/") { profile ->
            when (profile) {
                HeadsetProfile.A2DP -> "媒体音频"
                HeadsetProfile.HEADSET -> "通话音频"
                HeadsetProfile.LE_AUDIO -> "LE Audio"
            }
        }
    val addressSuffix = address.takeLast(5)
    return buildString {
        append(displayName)
        if (profileLabel.isNotEmpty()) {
            append(" · ")
            append(profileLabel)
        }
        if (addressSuffix.isNotEmpty()) {
            append(" · …")
            append(addressSuffix)
        }
    }
}

private fun markerDetails(headsetName: String, title: String, description: String): String =
    "耳机：$headsetName；操作：$title；说明：$description"

private fun restoreStatuses(
    contextPrefs: android.content.SharedPreferences,
    statuses: MutableMap<String, StepStatus>,
    sessionId: String?,
) {
    statuses.clear()
    allHuaweiHeadsetSteps.forEach { step ->
        val saved = sessionId?.let {
            contextPrefs.getString(statusPreferenceKey(it, step.key), null)
        }
        statuses[step.key] = StepStatus.entries.firstOrNull { it.name == saved }
            ?: StepStatus.PENDING
    }
}

private fun resetStatuses(statuses: MutableMap<String, StepStatus>) {
    statuses.clear()
    allHuaweiHeadsetSteps.forEach { step ->
        statuses[step.key] = StepStatus.PENDING
    }
}

private fun statusPreferenceKey(sessionId: String, stepKey: String): String =
    "$STATUS_PREFIX$sessionId.$stepKey"

private fun updateStatus(
    contextPrefs: android.content.SharedPreferences,
    statuses: MutableMap<String, StepStatus>,
    sessionId: String?,
    key: String,
    status: StepStatus,
) {
    statuses[key] = status
    if (sessionId != null) {
        contextPrefs.edit()
            .putString(statusPreferenceKey(sessionId, key), status.name)
            .apply()
    }
}

private fun Throwable.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

private suspend fun addMarkerSafely(
    context: Context,
    label: String,
    details: String,
): Boolean = try {
    withContext(Dispatchers.IO) {
        CaptureStore.addMarker(context, label, details)
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Throwable) {
    false
}
