package moe.chenxy.huaweipods.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.huaweipods.R
import moe.chenxy.huaweipods.pods.FreeClip2BooleanFeature
import moe.chenxy.huaweipods.pods.FreeClip2HeadMotionAction
import moe.chenxy.huaweipods.pods.FreeClip2HeadMotionState
import moe.chenxy.huaweipods.pods.HuaweiDeviceRoute
import moe.chenxy.huaweipods.ui.components.FreeClip2BooleanUiState
import moe.chenxy.huaweipods.ui.components.FreeClip2SettingsGroups
import moe.chenxy.huaweipods.ui.components.HuaweiEqualizerPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** Debug-only visual fixture. It never opens Bluetooth or writes device preferences. */
class FreeClip2SettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", true)
        val unknown = intent.getBooleanExtra("unknown", false)
        setContent {
            AppTheme(colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) {
                val state = remember {
                    FreeClip2BooleanUiState().apply {
                        if (!unknown) {
                            values = FreeClip2BooleanFeature.entries.associateWith { it == FreeClip2BooleanFeature.WEAR_DETECTION }
                            updateHead(FreeClip2HeadMotionState(false, FreeClip2HeadMotionAction.ANSWER_CALL, FreeClip2HeadMotionAction.REJECT_CALL))
                        }
                        change = { feature, enabled, done ->
                            values = values + (feature to enabled)
                            if (feature == FreeClip2BooleanFeature.HEAD_MOTION_CONTROL) {
                                updateHead(FreeClip2HeadMotionState(enabled, FreeClip2HeadMotionAction.ANSWER_CALL, FreeClip2HeadMotionAction.REJECT_CALL))
                            }
                            done(true)
                        }
                        changeHead = { gesture, action, done ->
                            head?.let { updateHead(if (gesture == moe.chenxy.huaweipods.pods.FreeClip2HeadMotionGesture.NOD) it.copy(nod = action) else it.copy(shake = action)) }
                            done(true)
                        }
                    }
                }
                Scaffold(topBar = { TopAppBar(title = "HUAWEI FreeClip 2") }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                        SmallTitle(stringResource(R.string.freeclip2_sound_effect))
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            ArrowPreference(
                                title = stringResource(R.string.freeclip2_sound_effect_preset),
                                summary = "默认",
                            )
                            HuaweiEqualizerPreference(
                                address = "",
                                route = HuaweiDeviceRoute.HUAWEI_FREECLIP2,
                                requestOnMount = false,
                            )
                        }
                        FreeClip2SettingsGroups(state)
                    }
                }
            }
        }
    }
}
