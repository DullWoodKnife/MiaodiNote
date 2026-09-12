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