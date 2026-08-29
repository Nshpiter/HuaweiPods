package moe.chenxy.huaweipods.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import com.xzakota.hyper.notification.focus.FocusNotification
import moe.chenxy.huaweipods.hook.Log
import moe.chenxy.huaweipods.config.ConfigManager
import moe.chenxy.huaweipods.utils.miuiStrongToast.data.BatteryParams

@SuppressLint("NotificationPermission", "WrongConstant")
object FocusIslandUtil {
    private const val TAG = "HuaweiPods-FocusIsland"
    private const val CHANNEL_ID = "huaweipods_focus_island"
    private const val CHANNEL_NAME = "HuaweiPods Battery"
    private const val NOTIFICATION_ID = 10086
    private const val ISLAND_TIMEOUT_SECONDS = 8
    private const val DISMISS_DELAY_MS = ISLAND_TIMEOUT_SECONDS * 1_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    @Synchronized
    fun closeForHotReload() {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = null
    }

    fun showBatteryIsland(
        context: Context,
        prefs: SharedPreferences,
        batteryParams: BatteryParams,
        address: String,
    ): Boolean {
        try {
            if (!ConfigManager.superIslandEnabled()) return false
            val leftConnected = batteryParams.left?.isConnected == true
            val rightConnected = batteryParams.right?.isConnected == true

            // Need at least one ear connected
            if (!leftConnected && !rightConnected) return false

            val leftText = if (leftConnected) "${batteryParams.left!!.battery}" else "-"
            val rightText = if (rightConnected) "${batteryParams.right!!.battery}" else "-"

            val leftBitmap = PodImageLoader.loadIslandLeftBitmap(context, prefs, address)
            val rightBitmap = PodImageLoader.loadIslandRightBitmap(context, prefs, address)

            if (leftBitmap == null || rightBitmap == null) {
                Log.e(TAG, "Failed to decode earphone icon bitmaps")
                return false
            }

            // 使用 createWithBitmap 直接嵌入图片数据，SystemUI 无需再访问模块资源
            val leftIcon = Icon.createWithBitmap(leftBitmap)
            val rightIcon = Icon.createWithBitmap(rightBitmap)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setAllowBubbles(true)
                    setLockscreenVisibility(
                        if (ConfigManager.lockscreenNotificationEnabled()) {
                            Notification.VISIBILITY_PUBLIC
                        } else {
                            Notification.VISIBILITY_SECRET
                        },
                    )
                }
            )

            val contentParts = mutableListOf<String>()
            if (leftConnected) contentParts.add("L: ${batteryParams.left!!.battery}%")
            if (rightConnected) contentParts.add("R: ${batteryParams.right!!.battery}%")
            val contentText = contentParts.joinToString("  ")

            val extras = FocusNotification.buildV3 {
                val picLeft = createPicture("key_pic_left", leftIcon)
                val picRight = createPicture("key_pic_right", rightIcon)

                enableFloat = true
                ticker = "HuaweiPods"
                tickerPic = picLeft

                isShowNotification = false
                island {
                    islandProperty = 1
                    islandTimeout = ISLAND_TIMEOUT_SECONDS
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = picLeft
                            }
                            textInfo {
                                title = leftText
                                content = "%"
                            }
                        }
                        imageTextInfoRight {
                            type = 2
                            picInfo {
                                type = 1
                                pic = picRight
                            }
                            textInfo {
                                title = rightText
                                content = "%"
                            }
                        }
                    }
                    shareData {
                        title = "HuaweiPods"
                        content = contentText
                        shareContent = contentText
                    }
                }
            }

            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("HuaweiPods")
                .setContentText(contentText)
                .setTicker("HuaweiPods")
                .setVisibility(
                    if (ConfigManager.lockscreenNotificationEnabled()) {
                        Notification.VISIBILITY_PUBLIC
                    } else {
                        Notification.VISIBILITY_SECRET
                    },
                )
                .addExtras(extras)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
            scheduleDismiss(nm)

            Log.d(TAG, "Focus Island shown: L=$leftText% R=$rightText%")
            return true
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Failed to show Focus Island: insufficient bitmap memory", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Focus Island", e)
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show Focus Island safely", t)
            return false
        }
    }

    fun cancelBatteryIsland(context: Context) {
        runCatching {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    @Synchronized
    private fun scheduleDismiss(notificationManager: NotificationManager) {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = Runnable {
            try {
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {
                // SystemUI 或通知服务重启时无需继续处理。
            }
        }.also { mainHandler.postDelayed(it, DISMISS_DELAY_MS) }
    }
}
