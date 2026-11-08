package com.CatMoeCircle.fuyutd.data.kernel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.CatMoeCircle.fuyutd.MainActivity
import com.CatMoeCircle.fuyutd.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内核保活前台服务。
 *
 * 内核是 JNI 运行时，宿主进程一旦被回收就等于内核被停，因此按内核 README 的「必做」
 * 在运行期间挂一个前台服务：常驻通知显示运行状态，并持 [PowerManager.PARTIAL_WAKE_LOCK]。
 *
 * 生命周期：
 * - 内核启动成功后由 [FuyuBotRuntime.start] 调 [start] 拉起；
 * - 通知上的「停止」走 [ACTION_STOP]；
 * - `nativeStop` 超时或失败时继续持有前台服务和唤醒锁，等内核真正结束；
 * - 运行时报告 [FuyuBotState.STOPPED] / [FuyuBotState.ERROR] 时撤销通知并 [stopSelf]，
 *   不会出现「内核已停、通知还在」。
 *
 * 运行时本身是进程级单例（[FuyuBotRuntime.instance]），本服务只负责保活与展示，
 * 不持有内核状态；Activity 重建不影响这里。
 */
class KernelService : Service() {

    private val runtime by lazy { FuyuBotRuntime.instance() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stateJob: Job? = null
    private var stopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopTimedOut = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        stopTimedOut = false
        startForeground(NOTIFICATION_ID, buildNotification(runtime.state.value))
        observeRuntimeState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 跟随运行时状态刷新通知；内核停下就撤通知退出。 */
    private fun observeRuntimeState() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            // 刚拉起时运行时可能还没推 STARTING，初值仍是 STOPPED，不能当成「已停」。
            var activeSeen = false
            runtime.state.collect { state ->
                when (state.state) {
                    FuyuBotState.STARTING, FuyuBotState.READY, FuyuBotState.STOPPING -> {
                        activeSeen = true
                        notificationManager().notify(
                            NOTIFICATION_ID,
                            buildNotification(state),
                        )
                    }

                    FuyuBotState.STOPPED -> if (activeSeen) stopSelfAndForeground()

                    FuyuBotState.ERROR -> stopSelfAndForeground()
                }
            }
        }
    }

    /**
     * 请求停止内核。
     *
     * `nativeStop` 最多阻塞 8 秒，因此放到 IO 线程执行。返回失败时不能立刻结束前台服务：
     * 内核可能仍在关闭 TDLib 或刷盘，需要继续持有唤醒锁，直到运行状态真正停止。
     */
    private fun requestStop() {
        if (stopJob?.isActive == true) return
        stopTimedOut = false
        stopJob = scope.launch(Dispatchers.IO) {
            val result = runtime.stop()
            withContext(Dispatchers.Main.immediate) {
                if (!runtime.isRunning) {
                    stopSelfAndForeground()
                    return@withContext
                }

                // stop 返回成功但仍显示运行中时继续等 onState；真正失败则保留服务并提示收尾超时。
                stopTimedOut = result.isFailure
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification(runtime.state.value),
                )
            }
        }
    }

    private fun stopSelfAndForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(state: FuyuBotRuntimeState): Notification {
        val status = when (state.state) {
            FuyuBotState.STARTING -> getString(R.string.kernel_service_status_starting)
            FuyuBotState.READY -> getString(R.string.kernel_service_status_running)
            FuyuBotState.STOPPING -> if (stopTimedOut) {
                getString(R.string.kernel_service_status_stop_timeout)
            } else {
                getString(R.string.kernel_service_status_stopping)
            }
            FuyuBotState.STOPPED, FuyuBotState.ERROR ->
                getString(R.string.kernel_service_status_stopped)
        }
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, KernelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.kernel_service_title))
            .setContentText(status)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(0, getString(R.string.kernel_service_action_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.kernel_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.kernel_service_channel_description)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    /** 运行期间持部分唤醒锁，避免熄屏后 CPU 休眠把内核的工作线程挂住。 */
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:kernel",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "fuyubot_kernel"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_STOP = 0
        private const val REQUEST_OPEN = 1
        private const val ACTION_STOP = "com.CatMoeCircle.fuyutd.action.STOP_KERNEL"

        /**
         * 拉起前台保活服务；内核启动成功后由 [FuyuBotRuntime.start] 调用。
         *
         * 重复调用是安全的：已在前台时只会再走一遍 onStartCommand 并刷新通知。
         */
        fun start(context: Context) {
            val appContext = context.applicationContext
            appContext.startForegroundService(Intent(appContext, KernelService::class.java))
        }
    }
}
