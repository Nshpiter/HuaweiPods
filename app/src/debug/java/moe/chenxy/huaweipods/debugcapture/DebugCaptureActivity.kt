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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Debug 构建专用的协议采集入口；Release 源集中不存在此 Activity。 */
class DebugCaptureActivity : ComponentActivity() {
    private var resumeToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            CaptureStore.stopActiveSessionForDifferentTarget(this, SMART_AUDIO_PACKAGE)
        }
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
private const val NAME_SOURCE_MANUAL = "manual"

private const val SMART_AUDIO_PACKAGE = SmartAudioCaptureTarget.PACKAGE_NAME

private enum class StepStatus {
    PENDING,
    ACTIVE,
    DONE,
    SKIPPED,
    ABORTED,
}

private enum class StepAction(
    val markerSuffix: String,
    val targetStatus: StepStatus,
) {
    START("start", StepStatus.ACTIVE),
    COMPLETE("done", StepStatus.DONE),
    SKIP("skipped", StepStatus.SKIPPED),
    RESET("reset", StepStatus.PENDING),
}

private val StepStatus.isResolved: Boolean
    get() = this == StepStatus.DONE || this == StepStatus.SKIPPED || this == StepStatus.ABORTED

private fun StepStatus.accepts(action: StepAction): Boolean = when (action) {
    StepAction.START -> this == StepStatus.PENDING
    StepAction.COMPLETE -> this == StepStatus.ACTIVE
    StepAction.SKIP -> this == StepStatus.PENDING || this == StepStatus.ACTIVE
    StepAction.RESET -> isResolved
}

private enum class CaptureStepGroup(@StringRes val titleRes: Int) {
    DEVICE(R.string.debug_capture_group_device),
    NOISE_VOLUME(R.string.debug_capture_group_noise_volume),
    GESTURE_WEAR(R.string.debug_capture_group_gesture_wear),
    SOUND_QUALITY(R.string.debug_capture_group_sound_quality),
    CONNECTION_CASE(R.string.debug_capture_group_connection_case),
    REVIEW(R.string.debug_capture_group_review),
}

private data class OfficialApp(
    val packageName: String,
    @StringRes val labelRes: Int,
)

private data class CaptureStep(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val group: CaptureStepGroup,
)

private val officialApps = listOf(
    OfficialApp(SMART_AUDIO_PACKAGE, R.string.debug_capture_app_smart_audio),
)

/**
 * 华为耳机通用能力目录。向导只记录智慧音频当前可见的项目，不据此猜测设备能力；
 * 任意设备缺少的项目都可以明确跳过。
 */
private val allHuaweiHeadsetSteps = listOf(
    CaptureStep("device_overview", R.string.debug_capture_step_device_overview_title, R.string.debug_capture_step_device_overview_desc, CaptureStepGroup.DEVICE),
    CaptureStep("device_info", R.string.debug_capture_step_device_info_title, R.string.debug_capture_step_device_info_desc, CaptureStepGroup.DEVICE),
    CaptureStep("battery_state", R.string.debug_capture_step_battery_state_title, R.string.debug_capture_step_battery_state_desc, CaptureStepGroup.DEVICE),
    CaptureStep("noise_modes", R.string.debug_capture_step_noise_modes_title, R.string.debug_capture_step_noise_modes_desc, CaptureStepGroup.NOISE_VOLUME),
    CaptureStep("noise_submodes", R.string.debug_capture_step_noise_submodes_title, R.string.debug_capture_step_noise_submodes_desc, CaptureStepGroup.NOISE_VOLUME),
    CaptureStep("adaptive_audio", R.string.debug_capture_step_adaptive_audio_title, R.string.debug_capture_step_adaptive_audio_desc, CaptureStepGroup.NOISE_VOLUME),
    CaptureStep("noisy_volume_boost", R.string.debug_capture_step_noisy_volume_boost_title, R.string.debug_capture_step_noisy_volume_boost_desc, CaptureStepGroup.NOISE_VOLUME),
    CaptureStep("gesture_tap", R.string.debug_capture_step_gesture_tap_title, R.string.debug_capture_step_gesture_tap_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("gesture_press", R.string.debug_capture_step_gesture_press_title, R.string.debug_capture_step_gesture_press_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("gesture_swipe_buttons", R.string.debug_capture_step_gesture_swipe_buttons_title, R.string.debug_capture_step_gesture_swipe_buttons_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("wear_detection", R.string.debug_capture_step_wear_detection_title, R.string.debug_capture_step_wear_detection_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("drop_reminder", R.string.debug_capture_step_drop_reminder_title, R.string.debug_capture_step_drop_reminder_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("head_motion", R.string.debug_capture_step_head_motion_title, R.string.debug_capture_step_head_motion_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("voice_control", R.string.debug_capture_step_voice_control_title, R.string.debug_capture_step_voice_control_desc, CaptureStepGroup.GESTURE_WEAR),
    CaptureStep("equalizer", R.string.debug_capture_step_equalizer_title, R.string.debug_capture_step_equalizer_desc, CaptureStepGroup.SOUND_QUALITY),
    CaptureStep("spatial_audio", R.string.debug_capture_step_spatial_audio_title, R.string.debug_capture_step_spatial_audio_desc, CaptureStepGroup.SOUND_QUALITY),
    CaptureStep("audio_quality", R.string.debug_capture_step_audio_quality_title, R.string.debug_capture_step_audio_quality_desc, CaptureStepGroup.SOUND_QUALITY),
    CaptureStep("low_latency", R.string.debug_capture_step_low_latency_title, R.string.debug_capture_step_low_latency_desc, CaptureStepGroup.SOUND_QUALITY),
    CaptureStep("dual_device", R.string.debug_capture_step_dual_device_title, R.string.debug_capture_step_dual_device_desc, CaptureStepGroup.CONNECTION_CASE),
    CaptureStep("connection_center", R.string.debug_capture_step_connection_center_title, R.string.debug_capture_step_connection_center_desc, CaptureStepGroup.CONNECTION_CASE),
    CaptureStep("case_sound", R.string.debug_capture_step_case_sound_title, R.string.debug_capture_step_case_sound_desc, CaptureStepGroup.CONNECTION_CASE),
    CaptureStep("smart_charging", R.string.debug_capture_step_smart_charging_title, R.string.debug_capture_step_smart_charging_desc, CaptureStepGroup.CONNECTION_CASE),
    CaptureStep("case_settings", R.string.debug_capture_step_case_settings_title, R.string.debug_capture_step_case_settings_desc, CaptureStepGroup.CONNECTION_CASE),
    CaptureStep("ear_fit", R.string.debug_capture_step_ear_fit_title, R.string.debug_capture_step_ear_fit_desc, CaptureStepGroup.REVIEW),
    CaptureStep("magnetic_power", R.string.debug_capture_step_magnetic_power_title, R.string.debug_capture_step_magnetic_power_desc, CaptureStepGroup.REVIEW),
    CaptureStep("disconnect_reconnect", R.string.debug_capture_step_disconnect_reconnect_title, R.string.debug_capture_step_disconnect_reconnect_desc, CaptureStepGroup.REVIEW),
    CaptureStep("other_feature", R.string.debug_capture_step_other_feature_title, R.string.debug_capture_step_other_feature_desc, CaptureStepGroup.REVIEW),
)

private val groupedHuaweiHeadsetSteps = allHuaweiHeadsetSteps.groupBy(CaptureStep::group)

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
    var scopeChecked by rememberSaveable { mutableStateOf(false) }
    var smartAudioAssetsAttached by rememberSaveable {
        mutableStateOf(
            CaptureStore.hasSmartAudioAssets(
                context,
                (initialActiveSession ?: initialStoreState?.latestSession)?.id,
            ),
        )
    }
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
            !captureActive &&
                latest != null &&
                !latest.isActive &&
                latest.officialAppPackage == SMART_AUDIO_PACKAGE &&
                latest.id != handledSessionId,
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
        val automaticTarget = CaptureHeadsetSelectionPolicy.automaticTarget(result)
        if (automaticTarget != null) {
            val device = automaticTarget
            headsetNameInput = device.displayName
            selectedHeadsetAddress = device.address
            headsetNameSource = CONNECTED_HEADSET_NAME_SOURCE
        } else if (
            result is DetectionResult.Success &&
            selectedHeadsetAddress != null &&
            CaptureHeadsetSelectionPolicy.selectedTarget(
                result = result,
                selectedAddress = selectedHeadsetAddress,
                selectedFromConnection = headsetNameSource == CONNECTED_HEADSET_NAME_SOURCE,
            ) == null
        ) {
            selectedHeadsetAddress = null
            headsetNameSource = NAME_SOURCE_MANUAL
        }
    }

    val steps = allHuaweiHeadsetSteps
    val groupedSteps = groupedHuaweiHeadsetSteps
    val activeStep = steps.firstOrNull { statusByKey[it.key] == StepStatus.ACTIVE }
    val completedCount = steps.count { statusByKey[it.key]?.isResolved == true }
    var currentProtocolEventCount by remember {
        mutableStateOf(
            (initialStoreState?.activeSession ?: initialStoreState?.latestSession)
                ?.protocolEventCount
                ?: 0L,
        )
    }
    var currentHookReadyCount by remember {
        mutableStateOf(
            (initialStoreState?.activeSession ?: initialStoreState?.latestSession)
                ?.hookReadyCount
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
        smartAudioAssetsAttached = CaptureStore.hasSmartAudioAssets(
            context,
            (activeSession ?: latestSession)?.id,
        )
        currentProtocolEventCount = (activeSession ?: latestSession)?.protocolEventCount ?: 0L
        currentHookReadyCount = (activeSession ?: latestSession)?.hookReadyCount ?: 0L

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
            latestSession.officialAppPackage == SMART_AUDIO_PACKAGE &&
            latestSession.id != prefs.getString(PREF_HANDLED_SESSION_ID, null)
    }
    val issueValid = isValidIssue(issueInput)
    val selectedConnectedHeadset = CaptureHeadsetSelectionPolicy.selectedTarget(
        result = detectionResult,
        selectedAddress = selectedHeadsetAddress,
        selectedFromConnection = headsetNameSource == CONNECTED_HEADSET_NAME_SOURCE,
    )
    val canStart = installedApps.isNotEmpty() &&
        selectedOfficialPackage == SMART_AUDIO_PACKAGE &&
        !detectingHeadsets &&
        selectedConnectedHeadset != null &&
        onlyTargetChecked && scopeChecked && issueValid

    fun runStepAction(
        step: CaptureStep,
        action: StepAction,
        details: String,
        onSuccess: () -> Unit = {},
    ) {
        val currentStatus = statusByKey[step.key] ?: StepStatus.PENDING
        if (storageBusy || !currentStatus.accepts(action)) return

        storageBusy = true
        coroutineScope.launch {
            try {
                val marked = addMarkerSafely(
                    context = context,
                    label = "operation.${step.key}.${action.markerSuffix}",
                    details = details,
                )
                if (marked) {
                    updateStatus(
                        contextPrefs = prefs,
                        statuses = statusByKey,
                        sessionId = activeSessionId,
                        key = step.key,
                        status = action.targetStatus,
                    )
                    onSuccess()
                } else {
                    context.toast(R.string.debug_capture_marker_failed)
                }
            } finally {
                storageBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.debug_capture_title),
                navigationIcon = {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable(onClick = onOpenMain)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.debug_capture_open_main_short),
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        },
    ) { pagePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = 16.dp,
                        top = pagePadding.calculateTopPadding() + 12.dp,
                        end = 16.dp,
                        bottom = pagePadding.calculateBottomPadding() + 24.dp,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
            captureActive -> {
                key("capture_progress") {
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
                        Spacer(Modifier.height(8.dp))
                        if (currentHookReadyCount > 0L) {
                            SummaryText(
                                text = stringResource(R.string.debug_capture_hook_ready),
                                color = Color(0xFF2E7D32),
                            )
                        } else {
                            SummaryText(
                                text = stringResource(R.string.debug_capture_hook_not_ready),
                                color = Color(0xFFC62828),
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                text = stringResource(R.string.debug_capture_check_hook),
                                onClick = {
                                    selectedOfficialPackage?.let { targetPackage ->
                                        CaptureContract.sendHookProbe(context, targetPackage)
                                    }
                                    if (!context.openOfficialApp(selectedOfficialPackage)) {
                                        context.toast(R.string.debug_capture_official_open_failed)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
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
                // 步骤数量固定且较少，进入页面时一次完成组合，避免快速滑动期间逐项组合造成掉帧。
                groupedSteps.forEach { (group, groupSteps) ->
                    key("group.${group.name}") {
                        StepGroupHeader(
                            group = group,
                            completed = groupSteps.count {
                                statusByKey[it.key]?.isResolved == true
                            },
                            total = groupSteps.size,
                        )
                    }
                    groupSteps.forEach { step ->
                        val status = statusByKey[step.key] ?: StepStatus.PENDING
                        val title = stringResource(step.titleRes)
                        val description = stringResource(step.descriptionRes)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer),
                        ) {
                            OperationRow(
                                step = step,
                                status = status,
                                blockedByAnotherStep = currentHookReadyCount == 0L ||
                                    storageBusy ||
                                    (activeStep != null && activeStep.key != step.key),
                                busy = storageBusy,
                                onStart = {
                                    runStepAction(
                                        step = step,
                                        action = StepAction.START,
                                        details = markerDetails(
                                            activeHeadsetName,
                                            title,
                                            description,
                                        ),
                                    ) {
                                        if (!context.openOfficialApp(selectedOfficialPackage)) {
                                            context.toast(
                                                R.string.debug_capture_official_open_failed,
                                            )
                                        }
                                    }
                                },
                                onOpenOfficial = {
                                    if (!context.openOfficialApp(selectedOfficialPackage)) {
                                        context.toast(
                                            R.string.debug_capture_official_open_failed,
                                        )
                                    }
                                },
                                onComplete = {
                                    runStepAction(
                                        step = step,
                                        action = StepAction.COMPLETE,
                                        details = markerDetails(
                                            activeHeadsetName,
                                            title,
                                            description,
                                        ),
                                    )
                                },
                                onSkip = {
                                    runStepAction(
                                        step = step,
                                        action = StepAction.SKIP,
                                        details = markerDetails(
                                            activeHeadsetName,
                                            title,
                                            "设备界面未提供此功能",
                                        ),
                                    )
                                },
                                onReset = resetStep@{
                                    if (activeStep != null) return@resetStep
                                    runStepAction(
                                        step = step,
                                        action = StepAction.RESET,
                                        details = markerDetails(
                                            activeHeadsetName,
                                            title,
                                            "用户点了重做",
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                key("capture_stop") {
                    SectionCard(title = stringResource(R.string.debug_capture_stop_title)) {
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
                                            val stoppedSession = checkNotNull(
                                                CaptureStore.stopSession(context, reason = "user"),
                                            ) {
                                                "当前没有活动的采集会话"
                                            }
                                            stoppedSession to CaptureStore.autoAttachSmartAudioAssets(
                                                context = context,
                                                expectedSessionId = stoppedSession.id,
                                            )
                                        }
                                    }.onSuccess { (stoppedSession, autoAssets) ->
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
                                        currentHookReadyCount = stoppedSession.hookReadyCount
                                        smartAudioAssetsAttached =
                                            autoAssets.status == SmartAudioAutoAttachmentStatus.ATTACHED ||
                                            autoAssets.status == SmartAudioAutoAttachmentStatus.ALREADY_ATTACHED ||
                                            CaptureStore.hasSmartAudioAssets(context, stoppedSession.id)
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
                key("capture_finished") {
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
                        TextButton(
                            text = stringResource(R.string.debug_capture_export),
                            onClick = exportCapture@{
                                if (
                                    storageBusy ||
                                    (currentProtocolEventCount == 0L && !smartAudioAssetsAttached)
                                ) return@exportCapture
                                storageBusy = true
                                coroutineScope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            CaptureStore.exportLatest(context)
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
                            modifier = Modifier.fillMaxWidth().alpha(
                                if (
                                    !storageBusy &&
                                    (
                                        currentProtocolEventCount > 0L ||
                                            smartAudioAssetsAttached
                                    )
                                ) 1f else 0.45f,
                            ),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = stringResource(R.string.debug_capture_new_session),
                            onClick = {
                                if (storageBusy) return@TextButton
                                captureFinished = false
                                smartAudioAssetsAttached = false
                                latestSessionId?.let { handledSessionId ->
                                    markSessionHandled(prefs, handledSessionId)
                                }
                                activeSessionId = null
                                activeHeadsetName = ""
                                loadedStatusSessionId = null
                                resetStatuses(statusByKey)
                                headsetNameInput = ""
                                headsetNameSource = NAME_SOURCE_MANUAL
                                selectedHeadsetAddress = null
                                onlyTargetChecked = false
                                scopeChecked = false
                                detectionRefreshToken++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            else -> {
                key("capture_prepare") {
                    PreparationCard(
                        installedApps = installedApps,
                        onlyTargetChecked = onlyTargetChecked,
                        onOnlyTargetChecked = { onlyTargetChecked = it },
                        scopeChecked = scopeChecked,
                        onScopeChecked = { scopeChecked = it },
                    )
                }
                key("capture_metadata") {
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
                            selectedFromConnection = headsetNameSource == CONNECTED_HEADSET_NAME_SOURCE,
                            onGrantPermission = {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            },
                            onRefresh = { detectionRefreshToken++ },
                            onSelect = { device ->
                                headsetNameInput = device.displayName
                                headsetNameSource = CONNECTED_HEADSET_NAME_SOURCE
                                selectedHeadsetAddress = device.address
                            },
                        )
                        if (selectedConnectedHeadset == null) {
                            SummaryText(
                                text = stringResource(R.string.debug_capture_model_selection_required),
                                color = Color(0xFFC62828),
                            )
                        }
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
                                val captureTarget = selectedConnectedHeadset
                                val sessionMetadata = CaptureSessionMetadata(
                                    issueId = issueInput.trim().ifBlank { null },
                                    headsetModel = captureTarget.displayName,
                                    officialAppPackage = selectedOfficialPackage,
                                    headsetAddress = captureTarget.address,
                                    headsetNameSource = CONNECTED_HEADSET_NAME_SOURCE,
                                    featureCatalogVersion = DEFAULT_FEATURE_CATALOG_VERSION,
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
                                        smartAudioAssetsAttached = false
                                        currentProtocolEventCount = 0L
                                        currentHookReadyCount = 0L
                                        captureActive = true
                                        captureFinished = false
                                        CaptureContract.sendHookProbe(context, SMART_AUDIO_PACKAGE)
                                    }.onFailure {
                                        context.toast(startFailedPrefix + it.userMessage())
                                    }
                                    storageBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().alpha(if (canStart && !storageBusy) 1f else 0.45f),
                            enabled = canStart && !storageBusy,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PreparationCard(
    installedApps: List<OfficialApp>,
    onlyTargetChecked: Boolean,
    onOnlyTargetChecked: (Boolean) -> Unit,
    scopeChecked: Boolean,
    onScopeChecked: (Boolean) -> Unit,
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
            text = stringResource(R.string.debug_capture_prepare_lsp_scope),
            checked = scopeChecked,
            onCheckedChange = onScopeChecked,
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
private fun OperationRow(
    step: CaptureStep,
    status: StepStatus,
    blockedByAnotherStep: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onOpenOfficial: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    val description = stringResource(step.descriptionRes)
    val summaryText = when (status) {
        StepStatus.PENDING -> description
        StepStatus.ACTIVE ->
            "${stringResource(R.string.debug_capture_status_active)} · $description"
        StepStatus.DONE -> stringResource(R.string.debug_capture_status_done)
        StepStatus.SKIPPED -> stringResource(R.string.debug_capture_status_skipped)
        StepStatus.ABORTED -> stringResource(R.string.debug_capture_status_aborted)
    }
    val statusColor = when (status) {
        StepStatus.PENDING -> Color(0xFF8A8A8A)
        StepStatus.ACTIVE -> MiuixTheme.colorScheme.primary
        StepStatus.DONE -> Color(0xFF2E8B57)
        StepStatus.SKIPPED -> Color(0xFF78909C)
        StepStatus.ABORTED -> Color(0xFF8D6E63)
    }
    val primaryEnabled = when (status) {
        StepStatus.PENDING -> !blockedByAnotherStep
        StepStatus.ACTIVE -> !busy
        StepStatus.DONE, StepStatus.SKIPPED, StepStatus.ABORTED ->
            !blockedByAnotherStep && !busy
    }
    val rowEnabled = when (status) {
        StepStatus.PENDING -> primaryEnabled
        StepStatus.ACTIVE -> !busy
        StepStatus.DONE, StepStatus.SKIPPED, StepStatus.ABORTED -> false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = rowEnabled) {
                when (status) {
                    StepStatus.PENDING -> onStart()
                    StepStatus.ACTIVE -> onOpenOfficial()
                    StepStatus.DONE, StepStatus.SKIPPED, StepStatus.ABORTED -> Unit
                }
            }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(step.titleRes),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = summaryText,
                color = if (status == StepStatus.ACTIVE) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                style = MiuixTheme.textStyles.body2,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            TextButton(
                text = stringResource(
                    when (status) {
                        StepStatus.PENDING -> R.string.debug_capture_skip_short
                        StepStatus.ACTIVE -> R.string.debug_capture_complete_short
                        StepStatus.DONE,
                        StepStatus.SKIPPED,
                        StepStatus.ABORTED,
                        -> R.string.debug_capture_reset_short
                    },
                ),
                onClick = {
                    if (!primaryEnabled) return@TextButton
                    when (status) {
                        StepStatus.PENDING -> onSkip()
                        StepStatus.ACTIVE -> onComplete()
                        StepStatus.DONE,
                        StepStatus.SKIPPED,
                        StepStatus.ABORTED,
                        -> onReset()
                    }
                },
                modifier = Modifier.alpha(if (primaryEnabled) 1f else 0.45f),
                colors = if (status == StepStatus.ACTIVE) {
                    ButtonDefaults.textButtonColorsPrimary()
                } else {
                    ButtonDefaults.textButtonColors()
                },
            )
            if (status == StepStatus.ACTIVE) {
                TextButton(
                    text = stringResource(R.string.debug_capture_skip_short),
                    onClick = { if (!busy) onSkip() },
                    modifier = Modifier.alpha(if (busy) 0.45f else 1f),
                )
            }
        }
    }
}

@Composable
private fun StepGroupHeader(
    group: CaptureStepGroup,
    completed: Int,
    total: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(group.titleRes),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.debug_capture_group_progress, completed, total),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                content()
            }
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

private fun markSessionHandled(
    contextPrefs: android.content.SharedPreferences,
    sessionId: String,
) {
    val editor = contextPrefs.edit()
        .putString(PREF_HANDLED_SESSION_ID, sessionId)
    allHuaweiHeadsetSteps.forEach { step ->
        editor.remove(statusPreferenceKey(sessionId, step.key))
    }
    editor.apply()
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
