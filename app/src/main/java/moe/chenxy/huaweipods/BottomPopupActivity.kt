package moe.chenxy.huaweipods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.config.DeviceRoutePrefs
import moe.chenxy.huaweipods.pods.hasChargingCase
import moe.chenxy.huaweipods.ui.AppTheme
import moe.chenxy.huaweipods.ui.components.PodStatus
import moe.chenxy.huaweipods.utils.PodImageLoader
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.sendIdentitySharingBroadcast
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.HuaweiPodsAction
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.addHuaweiPodsAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class BottomPopupActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        moe.chenxy.huaweipods.ui.AppLocale.rememberDeviceLocale(newBase)
        moe.chenxy.huaweipods.ui.AppLocale.apply(
            newBase,
            newBase.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("app_language", moe.chenxy.huaweipods.ui.AppLocale.SYSTEM),
        )
        super.attachBaseContext(newBase)
    }

    private var popupTarget by mutableStateOf<PopupDeviceTarget?>(null)

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val initial = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
            ?.let { resolveTarget(it, prefs) }
        if (initial == null) {
            finish()
            return
        }
        popupTarget = initial

        setContent {
            val colorSchemeMode = when (prefs.getInt("theme_mode", 0)) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            AppTheme(colorSchemeMode = colorSchemeMode, accentMode = prefs.getInt("accent_mode", 0)) {
                val target = popupTarget
                if (target != null) {
                    // 切换设备时重建卡片状态，同时取消旧设备尚未结束的关闭任务。
                    key(target.address) {
                        BottomPopupContent(
                            target = target,
                            onFinish = { finish() },
                        )
                    }
                }
            }
        }
    }

    // singleTop：旧弹窗未关闭时连接另一设备，复用本 Activity，更新设备信息，
    // 卡片内部会据此重置电量与自动关闭计时。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val prefs = getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
        val newTarget = intent.parcelableDevice("android.bluetooth.device.extra.DEVICE")
            ?.let { resolveTarget(it, prefs) } ?: return
        popupTarget = newTarget
        setIntent(intent)
    }

    private fun Intent.parcelableDevice(key: String): BluetoothDevice? =
        getParcelableExtra(key, BluetoothDevice::class.java)

    @SuppressLint("MissingPermission")
    private fun resolveTarget(device: BluetoothDevice, prefs: android.content.SharedPreferences): PopupDeviceTarget? {
        val address = runCatching { device.address }.getOrNull() ?: return null
        val deviceName = runCatching {
            device.name?.takeIf(String::isNotBlank)
                ?: device.alias?.takeIf(String::isNotBlank)
                ?: ""
        }.getOrDefault("")
        val route = DeviceRoutePrefs.resolve(prefs = prefs, address = address, deviceName = deviceName)
        return popupDeviceTargetOrNull(address, deviceName, route)
    }
}

private const val AUTO_DISMISS_MS = 6000L
private const val ANIM_MS = 280

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@androidx.compose.runtime.Composable
private fun BottomPopupContent(
    target: PopupDeviceTarget,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val isDark = isSystemInDarkTheme()
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    var visible by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf(BatteryParams()) }

    fun close() {
        if (finished) return
        finished = true
        visible = false
    }

    val receiver = remember(target) {
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val identity = PopupBroadcastIdentity(
                    address = intent.getStringExtra("address"),
                    deviceName = intent.getStringExtra("device_name"),
                    route = moe.chenxy.huaweipods.pods.decodeHuaweiDeviceRouteFromBroadcast(
                        intent.getStringExtra(HuaweiPodsAction.EXTRA_DEVICE_ROUTE),
                    ),
                )
                if (!popupBroadcastMatchesTarget(target, identity)) return
                when (HuaweiPodsAction.canonical(intent.action)) {
                    HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        intent.getParcelableExtra("status", BatteryParams::class.java)?.let {
                            battery = it
                        }
                    }
                    HuaweiPodsAction.ACTION_PODS_DISCONNECTED -> close()
                }
            }
        }
    }

    // 绑定 target：设备切换时反注册旧广播、为新设备重新注册并请求一次状态。
    DisposableEffect(target) {
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_BATTERY_CHANGED)
                addHuaweiPodsAction(HuaweiPodsAction.ACTION_PODS_DISCONNECTED)
            },
            Context.RECEIVER_EXPORTED,
        )
        // 让蓝牙进程立即回推一次当前状态，避免卡片先显示空电量。
        // ColorOS 蓝牙进程校验发送方身份，必须共享身份发送，否则请求会被拒绝。
        context.sendIdentitySharingBroadcast(Intent(HuaweiPodsAction.ACTION_REFRESH_STATUS).apply {
            putPopupTarget(target)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // 进场：首帧布局完成后再滑入。
    LaunchedEffect(Unit) {
        visible = true
    }
    // 切换到新设备：先清空旧电量，等新设备的广播回填。
    LaunchedEffect(target.address) {
        battery = BatteryParams()
    }
    // 自动关闭；key 为设备地址，切换设备时重新计时。
    LaunchedEffect(target.address) {
        kotlinx.coroutines.delay(AUTO_DISMISS_MS)
        close()
    }
    // 退出动画播完后真正结束 Activity。
    LaunchedEffect(finished) {
        if (finished) {
            kotlinx.coroutines.delay(ANIM_MS.toLong() + 30L)
            onFinish()
        }
    }

    val boxBitmap = remember(target.address, target.route) {
        runCatching {
            PodImageLoader.loadBoxBitmap(
                context = context,
                prefs = prefs,
                address = target.address,
                verifiedRoute = target.route,
            )
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(ANIM_MS),
                initialOffsetY = { it },
            ) + fadeIn(tween(ANIM_MS)),
            exit = slideOutVertically(
                animationSpec = tween(ANIM_MS),
                targetOffsetY = { it },
            ) + fadeOut(tween(ANIM_MS)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp)
                    .background(cardColor, RoundedCornerShape(28.dp))
                    .clickable(
                        // 拦截卡片内部点击，避免点卡片也关闭。
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = target.deviceName.ifEmpty { stringResource(R.string.app_name) },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.bottom_popup_connected),
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(8.dp))
                if (boxBitmap != null) {
                    Image(
                        bitmap = boxBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                PodStatus(
                    batteryParams = battery,
                    showCase = target.route.hasChargingCase,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.done),
                        onClick = { close() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
