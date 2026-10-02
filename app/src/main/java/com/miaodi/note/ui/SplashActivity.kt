package com.miaodi.note.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.miaodi.note.databinding.ActivitySplashBinding
import com.miaodi.note.service.ClipboardMonitorService
import com.miaodi.note.utils.Md5Utils

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var pinDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            // 透传通知栏“快捷记录/剪贴板导入询问”的 extra，保证冷启动时也能接收
            val mainIntent = Intent(this, MainActivity::class.java)
            intent?.let { original ->
                if (original.getBooleanExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE, false) ||
                    original.getBooleanExtra(ClipboardMonitorService.EXTRA_ASK_IMPORT, false)
                ) {
                    mainIntent.putExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE,
                        original.getBooleanExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE, false))
                    mainIntent.putExtra(ClipboardMonitorService.EXTRA_ASK_IMPORT,
                        original.getBooleanExtra(ClipboardMonitorService.EXTRA_ASK_IMPORT, false))
                    original.getStringExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE_TEXT)?.let {
                        mainIntent.putExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE_TEXT, it)
                    }
                }

                // 透传外部「打开方式 / 分享」意图，使 MainActivity 能导入外部 Markdown 文档。
                // 外部文档统一经由 SplashActivity（应用唯一对外入口，含 PIN 锁）转发，
                // 避免从外部直接唤起 MainActivity 而绕过 PIN 锁。
                forwardExternalIntent(original, mainIntent)
            }

            // PIN 解锁验证
            val prefs = getSharedPreferences("miaodi_settings", MODE_PRIVATE)
            val pinHash = prefs.getString("pin_hash", null)
            if (pinHash != null) {
                showPinDialog(mainIntent)
            } else {
                startActivity(mainIntent)
                finish()
            }
        }, 2000)
    }

    /**
     * 将外部「打开方式 / 分享」意图透传给 MainActivity。
     *
     * SplashActivity 是应用唯一的对外入口（承载 PIN 锁），因此外部文档打开统一在此转发，
     * 以免直接唤起 MainActivity 而绕过 PIN 校验。所有外部数据解析均做容错处理，
     * 任何异常都不会导致应用崩溃。
     */
    private fun forwardExternalIntent(original: Intent, target: Intent) {
        try {
            when (original.action) {
                Intent.ACTION_VIEW -> {
                    val data = original.data
                    if (data == null) return
                    target.action = Intent.ACTION_VIEW
                    target.setDataAndType(data, original.type)
                }
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                    target.action = original.action
                    target.type = original.type
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        original.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let {
                            target.putExtra(Intent.EXTRA_STREAM, it)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        original.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)?.let {
                            target.putExtra(Intent.EXTRA_STREAM, it)
                        }
                    }
                    original.getStringExtra(Intent.EXTRA_SUBJECT)?.let {
                        target.putExtra(Intent.EXTRA_SUBJECT, it)
                    }
                }
                else -> return
            }
            // 部分应用（如“分享”多选）仅在 ClipData 中携带 Uri，一并透传
            original.clipData?.let { target.clipData = it }
            // 保证 MainActivity 能读取 content:// 数据
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (t: Throwable) {
            // 忽略：外部意图异常不应导致应用崩溃
        }
    }

    private fun showPinDialog(mainIntent: Intent) {
        if (pinDialog?.isShowing == true) return
        val input = EditText(this).apply {
            hint = "请输入PIN码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }
        pinDialog = AlertDialog.Builder(this)
            .setTitle("喵滴笔记已锁定")
            .setMessage("请输入PIN码解锁")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("解锁") { _, _ ->
                val pin = input.text.toString()
                val pinHash = getSharedPreferences("miaodi_settings", MODE_PRIVATE).getString("pin_hash", null)
                if (pinHash != null && pinHash == Md5Utils.md5Hex(pin)) {
                    startActivity(mainIntent)
                    finish()
                } else {
                    Toast.makeText(this, "PIN码错误", Toast.LENGTH_SHORT).show()
                    pinDialog?.dismiss()
                    pinDialog = null
                    showPinDialog(mainIntent)
                }
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .create()
        pinDialog?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pinDialog?.dismiss()
        pinDialog = null
    }
}