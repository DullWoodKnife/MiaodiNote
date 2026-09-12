package com.miaodi.note.ui.fragment

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.miaodi.note.BuildConfig
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.data.model.Article
import com.miaodi.note.data.model.Book
import com.miaodi.note.data.model.Chapter
import com.miaodi.note.data.model.Todo
import com.miaodi.note.databinding.FragmentSettingsBinding
import com.miaodi.note.service.ClipboardMonitorService
import com.miaodi.note.utils.Md5Utils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppBackup(
    val books: List<Book>,
    val chapters: List<Chapter>,
    val articles: List<Article>,
    val todos: List<Todo>
)

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    /** 防止 showPinSetupDialog 内设置开关回写时重复触发监听器 */
    private var suppressPinListener = false

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportAllData(it) }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromJson(it) }
    }

    private val fileImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importTextFile(it) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(requireContext(), "已开启通知权限", Toast.LENGTH_SHORT).show()
            syncServiceState()
        } else {
            Toast.makeText(requireContext(), "未授予通知权限，快捷记录通知可能无法显示", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("miaodi_settings", android.content.Context.MODE_PRIVATE)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupRows()
    }

    private fun setupRows() {
        // ===== 通用 =====
        restoreSwitchFromPrefs(R.id.switchClipboard, "clipboard_record", false) { checked -> saveBool("clipboard_record", checked) }
        restoreSwitchFromPrefs(R.id.switchClipboardAsk, "clipboard_ask", false) { checked -> saveBool("clipboard_ask", checked) }
        restoreSwitchFromPrefs(R.id.switchNotifyQuick, "notify_quick", false) { checked -> saveBool("notify_quick", checked) }

        // ===== 显示 =====
        restoreSwitchFromPrefs(R.id.switchBlurTopBar, "blur_top_bar", false) { checked -> saveBool("blur_top_bar", checked) }
        binding.rowManualNav.setOnClickListener {
            showSingleChoiceDialog(
                "手动管理导航栏",
                arrayOf("跟随系统", "显示导航栏", "隐藏导航栏"),
                "manual_nav",
                "跟随系统"
            )
        }
        binding.rowLanguage.setOnClickListener {
            showSingleChoiceDialog(
                "语言",
                arrayOf("跟随系统", "简体中文"),
                "language",
                "跟随系统"
            )
        }
        binding.rowListMode.setOnClickListener {
            showSingleChoiceDialog(
                "文章列表显示方式",
                arrayOf("单列列表", "两列卡片"),
                "list_mode",
                "单列列表"
            )
        }

        // ===== 编辑器 =====
        binding.rowFabMode.setOnClickListener {
            showSingleChoiceDialog(
                "悬浮按钮模式",
                arrayOf("展开模式", "收起模式"),
                "fab_mode",
                "展开模式"
            )
        }
        binding.rowEditorMode.setOnClickListener {
            showSingleChoiceDialog(
                "新建文章编辑器模式",
                arrayOf("普通", "Markdown"),
                "editor_mode",
                "普通"
            )
        }
        binding.rowCursor.setOnClickListener {
            showSingleChoiceDialog(
                "光标设置",
                arrayOf("默认", "跟随主题色", "蓝色", "绿色", "橙色"),
                "cursor_color",
                "默认"
            )
        }
        binding.rowFont.setOnClickListener {
            showSingleChoiceDialog(
                "自定义字体",
                arrayOf("默认", "无衬线", "衬线", "等宽"),
                "font_family",
                "默认"
            )
        }
        binding.rowMdStyle.setOnClickListener {
            showSingleChoiceDialog(
                "自定义Markdown样式",
                arrayOf("默认", "简约", "夜间"),
                "md_style",
                "默认"
            )
        }
        restoreSwitchFromPrefs(R.id.switchPreviewFirst, "preview_first", false) { checked -> saveBool("preview_first", checked) }
        restoreSwitchFromPrefs(R.id.switchQuickBar, "quick_bar", true) { checked -> saveBool("quick_bar", checked) }

        // ===== 安全 =====
        binding.switchPin.isChecked = prefs.contains("pin_hash")
        binding.switchPin.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!suppressPinListener) {
                    showPinSetupDialog()
                }
            } else {
                prefs.edit().remove("pin_hash").apply()
                Toast.makeText(requireContext(), "PIN 已关闭", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowMiniBoard.setOnClickListener {
            Toast.makeText(requireContext(), "文字小板为桌面组件功能，请长按桌面添加", Toast.LENGTH_SHORT).show()
        }

        // ===== 备份 =====
        binding.rowRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("application/json", "*/*"))
        }
        binding.rowImportData.setOnClickListener {
            fileImportLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
        }
        binding.rowExportData.setOnClickListener {
            val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("miaodi_backup_$now.json")
        }

        // ===== 应用更新 =====
        restoreSwitchFromPrefs(R.id.switchAutoUpdate, "auto_update", false) { checked -> saveBool("auto_update", checked) }
        binding.rowCheckUpdate.setOnClickListener {
            val version = BuildConfig.VERSION_NAME
            AlertDialog.Builder(requireContext())
                .setTitle("检查更新")
                .setMessage("当前版本：v$version\n已是最新版本。")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ===== 工具方法 =====

    private fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /** 从偏好恢复开关状态，同时避免重复注册监听器 */
    private fun restoreSwitchFromPrefs(id: Int, key: String, defaultVal: Boolean, save: (Boolean) -> Unit) {
        val switch = when (id) {
            R.id.switchClipboard -> binding.switchClipboard
            R.id.switchClipboardAsk -> binding.switchClipboardAsk
            R.id.switchNotifyQuick -> binding.switchNotifyQuick
            R.id.switchBlurTopBar -> binding.switchBlurTopBar
            R.id.switchPreviewFirst -> binding.switchPreviewFirst
            R.id.switchQuickBar -> binding.switchQuickBar
            R.id.switchAutoUpdate -> binding.switchAutoUpdate
            else -> return
        }
        val checked = prefs.getBoolean(key, defaultVal)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, v ->
            save(v)
            Toast.makeText(requireContext(), if (v) "已开启" else "已关闭", Toast.LENGTH_SHORT).show()
            if (key == "clipboard_record" || key == "clipboard_ask" || key == "notify_quick") {
                ensureNotificationPermission()
                syncServiceState()
            }
        }
    }

    private fun showSingleChoiceDialog(title: String, options: Array<String>, key: String, default: String) {
        var current = prefs.getString(key, default)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options, options.indexOfFirst { it == current }.coerceAtLeast(0)) { dialog, which ->
                current = options[which]
            }
            .setPositiveButton("确定") { _, _ ->
                prefs.edit().putString(key, current).apply()
                Toast.makeText(requireContext(), "$title：$current", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPinSetupDialog() {
        val input = EditText(requireContext()).apply {
            hint = "请输入4-6位数字PIN码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("设置PIN解锁")
            .setMessage("开启后启动应用需输入PIN码")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6 && pin.all { it.isDigit() }) {
                    prefs.edit().putString("pin_hash", Md5Utils.md5Hex(pin)).apply()
                    suppressPinListener = true
                    binding.switchPin.isChecked = true
                    suppressPinListener = false
                    Toast.makeText(requireContext(), "PIN 已设置", Toast.LENGTH_SHORT).show()
                } else {
                    suppressPinListener = true
                    binding.switchPin.isChecked = false
                    suppressPinListener = false
                    Toast.makeText(requireContext(), "请输入4-6位数字PIN码", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消") { _, _ ->
                suppressPinListener = true
                binding.switchPin.isChecked = false
                suppressPinListener = false
            }
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun syncServiceState() {
        val shouldRun = prefs.getBoolean("clipboard_record", false) ||
            prefs.getBoolean("clipboard_ask", false) ||
            prefs.getBoolean("notify_quick", false)
        if (shouldRun) {
            ClipboardMonitorService.start(requireContext())
        } else {
            ClipboardMonitorService.stop(requireContext())
        }
    }

    // ===== 备份 / 恢复 =====

    private fun exportAllData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val repository = (requireActivity().application as MiaodiApplication).repository
                val books = repository.getAllBooksOnce()
                val chapters = books.flatMap { repository.getChaptersByBookOnce(it.id) }
                val articles = mutableListOf<Article>()
                chapters.forEach { chapter ->
                    articles += repository.getArticlesByChapterOrderTitleAsc(chapter.id).first()
                }
                val todos = repository.getAllTodosOnce()

                val backup = AppBackup(books, chapters, articles, todos)
                val json = gson.toJson(backup)

                requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(requireContext(), "数据已导出到本地", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importFromJson(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                } ?: ""
                val type = object : TypeToken<AppBackup>() {}.type
                val backup = gson.fromJson<AppBackup>(content, type)
                val repository = (requireActivity().application as MiaodiApplication).repository

                AlertDialog.Builder(requireContext())
                    .setTitle("恢复确认")
                    .setMessage("恢复将覆盖现有数据，共 ${backup.books.size} 本书、${backup.chapters.size} 章节、${backup.articles.size} 篇文章、${backup.todos.size} 个任务。确认恢复吗？")
                    .setPositiveButton("确认恢复") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                // 旧 id -> 新 id 映射，保证外键关系不丢失
                                val bookIdMap = mutableMapOf<Long, Long>()
                                backup.books.forEach { book ->
                                    bookIdMap[book.id] = repository.insertBook(book.copy(id = 0))
                                }
                                val chapterIdMap = mutableMapOf<Long, Long>()
                                backup.chapters.forEach { chapter ->
                                    val newBookId = bookIdMap[chapter.bookId] ?: chapter.bookId
                                    chapterIdMap[chapter.id] = repository.insertChapter(
                                        chapter.copy(id = 0, bookId = newBookId)
                                    )
                                }
                                backup.articles.forEach { article ->
                                    val newChapterId = chapterIdMap[article.chapterId] ?: article.chapterId
                                    repository.insertArticle(article.copy(id = 0, chapterId = newChapterId))
                                }
                                backup.todos.forEach { repository.insertTodo(it.copy(id = 0)) }
                                Toast.makeText(requireContext(), "恢复成功", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 导入 md/txt 文本文件为文章（插入默认章节或新建章节） */
    private fun importTextFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                } ?: return@launch
                if (content.isBlank()) {
                    Toast.makeText(requireContext(), "文件内容为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val repository = (requireActivity().application as MiaodiApplication).repository
                val books = repository.getAllBooksOnce()
                val bookId = if (books.isNotEmpty()) books.first().id else {
                    repository.insertBook(Book(name = "导入"))
                }
                val chapters = repository.getChaptersByBookOnce(bookId)
                val chapterId = if (chapters.isNotEmpty()) chapters.first().id else {
                    repository.insertChapter(Chapter(bookId = bookId, name = "导入"))
                }
                var title = uri.lastPathSegment?.substringAfterLast('/') ?: "导入文件"
                if (title.contains('.')) title = title.substringBeforeLast('.')
                if (title.isBlank()) title = "导入文件"
                repository.insertArticle(
                    Article(chapterId = chapterId, title = title, content = content, isMarkdown = true, isNew = true)
                )
                Toast.makeText(requireContext(), "已导入文章：$title", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}