package moe.chenxy.huaweipods.debugcapture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** 接收 Debug 注入器上报的协议事件；Release 构建中不存在该类。 */
class CaptureEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CaptureContract.ACTION_CAPTURE_EVENT) return
        val senderPackage = sentFromPackage
        if (senderPackage !in ALLOWED_SENDERS) {
            Log.w(TAG, "忽略非官方应用发送的 Debug 抓包事件：$senderPackage")
            return
        }
        if (!CaptureStore.isCaptureActive(context)) return

        val event = CapturedProtocolEvent(
            eventType = intent.getStringExtra(CaptureContract.EXTRA_EVENT_TYPE),
            direction = intent.getStringExtra(CaptureContract.EXTRA_DIRECTION),
            channel = intent.getStringExtra(CaptureContract.EXTRA_CHANNEL),
            operation = intent.getStringExtra(CaptureContract.EXTRA_OPERATION),
            payloadHex = intent.getStringExtra(CaptureContract.EXTRA_PAYLOAD_HEX),
            summary = intent.getStringExtra(CaptureContract.EXTRA_SUMMARY),
            sourceProcess = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_PROCESS),
            deviceName = intent.getStringExtra(CaptureContract.EXTRA_DEVICE_NAME),
            deviceAddress = intent.getStringExtra(CaptureContract.EXTRA_DEVICE_ADDRESS),
            timestampEpochMs = intent
                .getLongExtra(CaptureContract.EXTRA_TIMESTAMP_EPOCH_MS, 0L)
                .takeIf { it > 0L },
        )
        val pendingResult = goAsync()
        try {
            executor.execute {
                try {
                    CaptureStore.appendProtocolEvent(context.applicationContext, event)
                } catch (throwable: Throwable) {
                    Log.e(TAG, "保存 Debug 抓包事件失败", throwable)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: RejectedExecutionException) {
            droppedReceiverEvents.incrementAndGet()
            Log.w(TAG, "Debug 抓包事件队列已满，本条事件被丢弃")
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "HuaweiPods-Capture"
        private const val MAX_PENDING_EVENTS = 512
        private val ALLOWED_SENDERS = setOf(
            "com.huawei.smarthome",
            "com.huawei.smartaudio",
        )

        private val droppedReceiverEvents = AtomicLong(0L)
        private val executor = ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(MAX_PENDING_EVENTS),
        ).apply {
            allowCoreThreadTimeOut(true)
        }

        internal fun awaitPendingEvents(
            context: Context,
            timeoutMillis: Long = 8_000L,
        ): Boolean {
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                val barrier = CountDownLatch(1)
                try {
                    executor.execute { barrier.countDown() }
                } catch (_: RejectedExecutionException) {
                    if (System.nanoTime() >= deadlineNanos) return false
                    try {
                        Thread.sleep(25L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                    continue
                }

                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) return false
                val drained = try {
                    barrier.await(remainingNanos, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
                if (drained) {
                    val dropped = droppedReceiverEvents.getAndSet(0L)
                    if (dropped > 0L) {
                        runCatching {
                            CaptureStore.addMarker(
                                context,
                                "capture.receiver_queue_dropped",
                                "count=$dropped",
                            )
                        }
                    }
                }
                return drained
            }
        }
    }
}
