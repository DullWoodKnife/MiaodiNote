package com.miaodi.note.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.data.model.ClipboardRecord
import com.miaodi.note.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务：负责两类真实系统能力
 *  1. 剪贴板监听：ClipboardManager.OnPrimaryClipChangedListener，复制内容写入本地库
 *  2. 通知栏快捷记录：常驻通知 + "快捷记录" Action，点击直达新建笔记页
 * 任一相关设置开关开启时启动，全部关闭时停止。
 */
class ClipboardMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val ASK_NOTIFICATION_ID = 1002

        const val EXTRA_QUICK_NOTE = "com.miaodi.note.extra.QUICK_NOTE"
        const val EXTRA_QUICK_NOTE_TEXT = "com.miaodi.note.extra.QUICK_NOTE_TEXT"
        const val EXTRA_ASK_IMPORT = "com.miaodi.note.extra.ASK_IMPORT"

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var prefs: SharedPreferences
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastRecordedText: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        prefs = getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildMonitorNotification())
        registerClipboardListener()
        return START_STICKY
    }

    private fun registerClipboardListener() {
        if (listener != null) return
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return@OnPrimaryClipChangedListener
            if (text.isEmpty() || text == lastRecordedText) return@OnPrimaryClipChangedListener
            lastRecordedText = text

            // 1) 剪贴板记录：写入本地库
            serviceScope.launch {
                (application as MiaodiApplication).repository
                    .insertClipboardRecord(ClipboardRecord(content = text))
            }

            // 2) 剪贴板导入询问：弹出通知询问是否导入为新笔记
            if (prefs.getBoolean("clipboard_ask", false)) {
                showAskImportNotification(text)
            }
        }
        clipboardManager.addPrimaryClipChangedListener(listener!!)
    }

    private fun buildMonitorNotification(): Notification {
        // 点击通知本身 → 打开应用
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // "快捷记录" Action → 打开新建笔记页并预填剪贴板最新内容
        val latestText = clipboardManager.primaryClip
            ?.getItemAt(0)?.coerceToText(this)?.toString()?.take(500) ?: ""
        val quickNoteIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_QUICK_NOTE, true)
                putExtra(EXTRA_QUICK_NOTE_TEXT, latestText)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cat)
            .setContentTitle("喵滴笔记")
            .setContentText("剪贴板监听中 · 点击可快捷记录")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                "快捷记录",
                quickNoteIntent
            )
            .build()
    }

    private fun showAskImportNotification(text: String) {
        val importIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ASK_IMPORT, true)
                putExtra(EXTRA_QUICK_NOTE_TEXT, text.take(500))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cat)
            .setContentTitle("剪贴板检测到新内容")
            .setContentText(text.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(200)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(importIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ASK_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // 通知权限被拒绝时忽略
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听与快捷记录",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "剪贴板监听与通知栏快捷记录"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        listener = null
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}