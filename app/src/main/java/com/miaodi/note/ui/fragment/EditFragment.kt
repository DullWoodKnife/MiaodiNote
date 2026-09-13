package com.miaodi.note.ui.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.data.model.Article
import com.miaodi.note.databinding.FragmentEditBinding
import com.miaodi.note.data.repository.NoteRepository
import com.miaodi.note.utils.ImageExportUtils
import com.miaodi.note.utils.MarkdownPreviewUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.miaodi.note.utils.Md5Utils
import com.miaodi.note.utils.PdfExportUtils
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditFragment : Fragment() {

    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NoteRepository
    private var currentArticle: Article? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val titleDateFormat = SimpleDateFormat("yyyy-MM-dd(HHmmss)", Locale.getDefault())

    /** 编辑器三态：锁定编辑 → 滑动浏览 → MD 只读预览 */
    private enum class EditorMode { LOCKED_EDIT, SLIDE, MD_READONLY }
    // 默认进入“滑动状态”：可编辑 MD 源码，左右滑动进入渲染预览
    private var currentMode = EditorMode.SLIDE

    private var pendingExportType = ""
    private var pendingExportTitle = ""
    private var pendingExportContent = ""
    private var pendingExportExtension = ""
    private var pendingExportMime = ""

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    when (pendingExportExtension) {
                        "pdf" -> PdfExportUtils.exportToPdf(
                            requireContext(), uri, pendingExportTitle, pendingExportContent
                        )
                        "png" -> ImageExportUtils.exportToImage(
                            requireContext(), uri, pendingExportTitle, pendingExportContent
                        )
                        else -> writeToUri(uri, pendingExportContent)
                    }
                    Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as MiaodiApplication).repository

        setupToolbar()
        setupWordCount()
        setupBottomToolbar()
        setupSwipeGesture()
        applyEditorPreferences()
        setupInputAwareBottomToolbar()
        loadOrCreateArticle()

        // 确保 Toolbar 不被系统状态栏覆盖，留出顶部安全区域
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveArticle()
                requireActivity().finish()
            }
        })
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            saveArticle()
            requireActivity().finish()
        }

        // 确保状态栏区域留出安全内边距，避免按钮被系统状态栏覆盖
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // 右上角“更多”按钮：打开工具 / 文章设置面板
        binding.btnMore.setOnClickListener {
            showEditOptionsSheet()
        }

    }

    private fun setupWordCount() {
        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.toString()?.length ?: 0
                binding.tvWordCount.text = getString(R.string.word_count, count)
            }
        })
    }

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()


    /** 三态模式 UI 应用：锁定(源码可编辑) / 滑动(手势进入渲染) / MD 只读预览(整页渲染) */
    private fun applyMode() {
        when (currentMode) {
            EditorMode.LOCKED_EDIT -> {
                binding.etContent.isEnabled = true
                binding.etContent.setTextIsSelectable(true)
                binding.etContent.visibility = View.VISIBLE
                binding.etContent.isFocusableInTouchMode = true
                clearFullPreview()
            }
            EditorMode.SLIDE -> {
                binding.etContent.isEnabled = true
                binding.etContent.setTextIsSelectable(true)
                binding.etContent.visibility = View.VISIBLE
                binding.etContent.isFocusableInTouchMode = true
                clearFullPreview()
            }
            EditorMode.MD_READONLY -> {
                binding.etContent.isEnabled = false
                binding.etContent.isFocusableInTouchMode = false
                binding.etContent.visibility = View.GONE
                refreshFullPreview()
            }
        }
    }

    /** 手势检测：锁定状态不可滑动；滑动/渲染状态左右滑动切换相邻文章，滑动状态下滑动同时进入渲染模式 */
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var isGestureConsuming = false
    private var previewWebView: WebView? = null

    /** 统一的横向滑动手势：锁定态返回 false（不可滑动），滑动/渲染态消费横向拖动 */
    private val swipeTouchListener = View.OnTouchListener { _, event ->
        when (currentMode) {
            EditorMode.LOCKED_EDIT -> false
            EditorMode.SLIDE, EditorMode.MD_READONLY -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeDownX = event.x
                        swipeDownY = event.y
                        isGestureConsuming = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - swipeDownX
                        val dy = event.y - swipeDownY
                        if (!isGestureConsuming && kotlin.math.abs(dx) > 60 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                            isGestureConsuming = true
                            loadAdjacentArticle(if (dx < 0) +1 else -1)
                            if (currentMode == EditorMode.SLIDE) {
                                currentMode = EditorMode.MD_READONLY
                                applyMode()
                            } else {
                                refreshFullPreview()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        isGestureConsuming = false
                        false
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupSwipeGesture() {
        binding.etContent.setOnTouchListener(swipeTouchListener)
        binding.contentContainer.setOnTouchListener(swipeTouchListener)
    }

    /** 整页 Markdown 渲染预览（占据内容区） */
    private fun refreshFullPreview() {
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        val mdStyle = prefs.getString("md_style", "默认") ?: "默认"
        val content = binding.etContent.text.toString()
        val html = MarkdownPreviewUtils.render(content, mdStyle)

        previewWebView?.destroy()
        previewWebView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = false
            settings.textZoom = 100
            setOnTouchListener(swipeTouchListener)
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
        binding.previewContainer.removeAllViews()
        binding.previewContainer.addView(
            previewWebView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        binding.previewContainer.visibility = View.VISIBLE
    }

    private fun clearFullPreview() {
        previewWebView?.destroy()
        previewWebView = null
        binding.previewContainer.removeAllViews()
        binding.previewContainer.visibility = View.GONE
    }

    /** 加载相邻文章：direction = +1 下一篇 / -1 上一篇；若处于渲染模式则切换后刷新整页预览 */
    private var adjacentArticles: List<Long>? = null

    private fun loadAdjacentArticle(direction: Int) {
        lifecycleScope.launch {
            val currentId = currentArticle?.id ?: return@launch
            if (currentId == 0L) return@launch
            val chapterId = currentArticle?.chapterId ?: return@launch
            if (chapterId <= 0) return@launch

            val articles = repository.getArticlesByChapterOnce(chapterId)
            if (articles.isEmpty()) return@launch

            adjacentArticles = articles.map { it.id }
            val currentIndex = adjacentArticles!!.indexOf(currentId)
            if (currentIndex < 0) return@launch

            val newIndex = (currentIndex + direction).coerceIn(0, adjacentArticles!!.size - 1)
            if (newIndex == currentIndex) return@launch

            // 先保存当前文章
            saveArticle()

            val nextArticle = articles[newIndex]
            currentArticle = nextArticle
            binding.etTitle.setText(nextArticle.title)
            binding.etContent.setText(nextArticle.content)
            binding.tvWordCount.text = getString(R.string.word_count, nextArticle.content.length)
            if (currentMode == EditorMode.MD_READONLY) {
                refreshFullPreview()
            }
            Toast.makeText(requireContext(), "已切换到：${nextArticle.title}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupBottomToolbar() {
        binding.btnToolCat.setOnClickListener {
            insertAtCursor(binding.etContent, "🐱")
        }
        binding.btnToolPaw.setOnClickListener {
            insertAtCursor(binding.etContent, "🐾")
        }
        binding.btnToolPrev.setOnClickListener {
            moveCursorBy(-1)
        }
        binding.btnToolNext.setOnClickListener {
            moveCursorBy(1)
        }
        binding.btnToolCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("note_content", binding.etContent.text.toString()))
            Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        binding.btnToolUndo.setOnClickListener {
            undo()
        }
        binding.btnToolRedo.setOnClickListener {
            redo()
        }

        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isOperationInProgress) {
                    undoStack.addLast(s?.toString() ?: "")
                    if (undoStack.size > 50) undoStack.removeFirst()
                }
                redoStack.clear()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private var isOperationInProgress = false

    /**
     * 应用设置页的各项编辑偏好：
     * 高斯模糊顶部栏 / 自定义字体 / 光标颜色 / 快捷栏显示 / 底部状态栏叠加。
     */
    private fun applyEditorPreferences() {
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)

        // 1) 高斯模糊顶部栏 → 顶部栏变为半透明深色（悬浮毛玻璃观感）
        if (prefs.getBoolean("blur_top_bar", false)) {
            binding.toolbar.background = android.graphics.drawable.ColorDrawable(0x66000000)
            binding.toolbar.elevation = 0f
        } else {
            binding.toolbar.background = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), R.drawable.bg_toolbar_solid
            )
            (binding.toolbar.background as? android.graphics.drawable.GradientDrawable)?.setColor(0xFF3F51B5.toInt())
            binding.toolbar.elevation = 4f
        }

        // 2) 自定义字体
        val fontFamily = prefs.getString("font_family", "默认")
        val typeface = when (fontFamily) {
            "无衬线" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "衬线" -> Typeface.create("serif", Typeface.NORMAL)
            "等宽" -> Typeface.create("monospace", Typeface.NORMAL)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        binding.etTitle.typeface = typeface
        binding.etContent.typeface = typeface

        // 3) 光标颜色
        val cursorColor = when (prefs.getString("cursor_color", "默认")) {
            "跟随主题色" -> 0xFF3F51B5.toInt()
            "蓝色" -> 0xFF2196F3.toInt()
            "绿色" -> 0xFF4CAF50.toInt()
            "橙色" -> 0xFFFF9800.toInt()
            else -> 0xFF3F51B5.toInt()
        }
        // 光标色（API 29+ 支持 textCursorDrawable 着色；低版本使用 highlightColor 近似）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            binding.etContent.textCursorDrawable?.mutate()?.setTint(cursorColor)
            binding.etTitle.textCursorDrawable?.mutate()?.setTint(cursorColor)
        }
        binding.etContent.highlightColor = cursorColor or 0x33000000

        // 4) 快捷栏显示设置：仅在键盘输入模式下显示
        updateBottomToolbarVisibility()
    }

    /** IME（软键盘）是否可见 */
    private var isImeVisible = false

    /** 标题/正文编辑框是否获得焦点 */
    private var isEditorFocused = false

    /**
     * 底部快捷工具栏仅在键盘输入模式（软键盘弹出或编辑框获得焦点）时显示，
     * 未进入输入模式时隐藏；同时受设置页“快捷栏”总开关约束。
     */
    private fun setupInputAwareBottomToolbar() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            // 快捷栏跟随软键盘上移，避免被键盘 / 系统导航栏遮挡
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            (binding.bottomToolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.bottomMargin != imeBottom) {
                    lp.bottomMargin = imeBottom
                    binding.bottomToolbar.layoutParams = lp
                }
            }
            updateBottomToolbarVisibility()
            insets
        }
        val focusListener = View.OnFocusChangeListener { _, _ ->
            if (_binding == null) return@OnFocusChangeListener
            isEditorFocused = binding.etTitle.hasFocus() || binding.etContent.hasFocus()
            updateBottomToolbarVisibility()
        }
        binding.etTitle.setOnFocusChangeListener(focusListener)
        binding.etContent.setOnFocusChangeListener(focusListener)
        // 初始状态：未进入输入模式，先隐藏
        updateBottomToolbarVisibility()
    }

    /** 根据“快捷栏”设置与键盘输入模式刷新底部快捷工具栏的显隐 */
    private fun updateBottomToolbarVisibility() {
        if (_binding == null) return
        val showQuickBar = requireContext()
            .getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
            .getBoolean("quick_bar", true)
        // 仅当软键盘弹出（进入输入模式）时显示快捷栏，未唤起键盘时隐藏
        binding.bottomToolbar.visibility =
            if (showQuickBar && isImeVisible) View.VISIBLE else View.GONE
    }

    /** 根据“优先预览文章”设置，Markdown 文章打开时自动弹出渲染预览 */
    private fun maybeAutoPreview() {
        if (currentArticle?.isMarkdown != true) return
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("preview_first", false)) {
            binding.root.post { showMarkdownPreview() }
        }
    }

    /** 用 WebView 渲染 Markdown 预览（样式跟随设置页“自定义Markdown样式”） */
    private fun showMarkdownPreview() {
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        val mdStyle = prefs.getString("md_style", "默认") ?: "默认"
        val content = binding.etContent.text.toString()
        val html = MarkdownPreviewUtils.render(content, mdStyle)

        val webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = false
            settings.textZoom = 100
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        AlertDialog.Builder(requireContext())
            .setTitle("Markdown 预览")
            .setView(webView, padding, padding, padding, padding)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun insertAtCursor(editText: EditText, text: String) {
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(start)
        isOperationInProgress = true
        editText.text.replace(start, end, text)
        editText.setSelection(start + text.length)
        isOperationInProgress = false
    }

    private fun moveCursorBy(offset: Int) {
        val pos = binding.etContent.selectionStart
        if (pos >= 0) {
            val newPos = (pos + offset).coerceIn(0, binding.etContent.text.length)
            binding.etContent.setSelection(newPos)
        }
    }

    private fun undo() {
        val snapshot = binding.etContent.text.toString()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot)
        isOperationInProgress = true
        binding.etContent.setText(previous)
        binding.etContent.setSelection(previous.length)
        isOperationInProgress = false
    }

    private fun redo() {
        val snapshot = binding.etContent.text.toString()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot)
        isOperationInProgress = true
        binding.etContent.setText(next)
        binding.etContent.setSelection(next.length)
        isOperationInProgress = false
    }

    private fun loadOrCreateArticle() {
        val articleId = arguments?.getLong("articleId", -1L) ?: -1L
        val chapterId = arguments?.getLong("chapterId", -1L) ?: -1L
        val quickText = arguments?.getString("quickText")

        if (articleId > 0) {
            lifecycleScope.launch {
                currentArticle = repository.getArticleById(articleId)
                currentArticle?.let { article ->
                    binding.etTitle.setText(article.title)
                    binding.etContent.setText(article.content)
                    binding.tvWordCount.text = getString(R.string.word_count, article.content.length)
                }
                maybeAutoPreview()
            }
        } else if (chapterId > 0) {
            // 从通知栏“快捷记录 / 剪贴板导入询问”进入时预填内容
            if (!quickText.isNullOrBlank()) {
                val title = quickText.lineSequence().firstOrNull { it.isNotBlank() }
                    ?.take(20) ?: "快捷记录"
                currentArticle = Article(
                    chapterId = chapterId,
                    title = title,
                    content = quickText,
                    isNew = true
                )
                binding.etTitle.setText(title)
                binding.etContent.setText(quickText)
                binding.tvWordCount.text = getString(R.string.word_count, quickText.length)
            } else {
                // 根据设置页“新建文章编辑器模式”决定新文章的 Markdown 标记
                val editorMode = requireContext()
                    .getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
                    .getString("editor_mode", "普通")
                currentArticle = Article(
                    chapterId = chapterId,
                    title = titleDateFormat.format(Date()),
                    isMarkdown = editorMode == "Markdown",
                    isNew = true
                )
                binding.tvWordCount.text = getString(R.string.word_count, 0)
            }
        }
    }

    private fun saveArticle() {
        var title = binding.etTitle.text.toString()
        val content = binding.etContent.text.toString()

        currentArticle?.let { article ->
            if (title.isBlank()) {
                title = titleDateFormat.format(Date())
            }
            val newArticle = article.copy(
                title = title,
                content = content,
                wordCount = content.length,
                updatedAt = System.currentTimeMillis(),
                isNew = false
            )

            lifecycleScope.launch {
                if (newArticle.id == 0L) {
                    val newId = repository.insertArticle(newArticle)
                    currentArticle = newArticle.copy(id = newId)
                } else {
                    repository.updateArticle(newArticle)
                    currentArticle = newArticle
                }
            }
        }
    }

    private fun showEditOptionsSheet() {
        val sheet = EditOptionsBottomSheet.newInstance()
        val article = currentArticle
        if (article != null && article.id > 0) {
            lifecycleScope.launch {
                val chapter = repository.getChapterById(article.chapterId)
                sheet.setInfo(
                    chapter?.name ?: "默认",
                    dateFormat.format(Date(article.createdAt)),
                    dateFormat.format(Date(article.updatedAt))
                )
            }
        }

        sheet.onOptionSelected = { option ->
            when (option) {
                "delete" -> deleteArticle()
                "clear_all" -> {
                    binding.etContent.setText("")
                    binding.etTitle.setText("")
                }
                "select_all_copy" -> {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = binding.etContent.text.toString()
                    val clip = ClipData.newPlainText("note_content", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                "preview" -> showMarkdownPreview()
                "switch_status" -> {
                    // 在“滑动状态”与“锁定状态”之间切换，并弹出当前状态提示
                    currentMode = if (currentMode == EditorMode.LOCKED_EDIT) {
                        EditorMode.SLIDE
                    } else {
                        EditorMode.LOCKED_EDIT
                    }
                    applyMode()
                    val label = if (currentMode == EditorMode.SLIDE) "滑动状态" else "锁定状态"
                    Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
                }
                "encrypt" -> showEncryptDialog()
                "decrypt" -> showDecryptDialog()
                "save_as" -> saveAsNewArticle()
                "export_md" -> exportDocument("md", "text/markdown")
                "export_txt" -> exportDocument("txt", "text/plain")
                "export_html" -> exportDocument("html", "text/html")
                "export_pdf" -> exportDocument("pdf", "application/pdf")
                "export_image" -> exportDocument("png", "image/png")
                "export_preview" -> exportDocument("png", "image/png")
            }
        }
        sheet.show(parentFragmentManager, "EditOptionsSheet")
    }

    private fun exportDocument(extension: String, mimeType: String) {
        val title = binding.etTitle.text.toString()
        val content = binding.etContent.text.toString()

        var exportContent = content
        // HTML 需要生成完整文档；PDF/PNG 只保存原始内容交给渲染工具
        if (extension == "html") {
            val escaped = content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            exportContent = """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>$title</title></head>
<body><pre>$escaped</pre></body></html>"""
        }

        pendingExportType = extension
        pendingExportTitle = title
        pendingExportContent = exportContent
        pendingExportExtension = extension
        pendingExportMime = mimeType

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, "$title.$extension")
        }
        createDocumentLauncher.launch(intent)
    }

    private fun writeToUri(uri: Uri, content: String) {
        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(content)
            }
        }
    }

    private fun showEncryptDialog() {
        val input = EditText(requireContext())
        input.hint = "请输入加密密码"
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("MD5加密")
            .setView(container)
            .setPositiveButton("加密") { _, _ ->
                val password = input.text.toString()
                if (password.isNotBlank()) {
                    val text = binding.etContent.text.toString()
                    if (text.isNotBlank()) {
                        try {
                            val encrypted = Md5Utils.encrypt(text, password)
                            binding.etContent.setText(encrypted)
                            Toast.makeText(requireContext(), "加密成功", Toast.LENGTH_SHORT).show()
                        } catch (e: Md5Utils.EncryptionException) {
                            Toast.makeText(requireContext(), "加密失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDecryptDialog() {
        val input = EditText(requireContext())
        input.hint = "请输入解密密码"
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("MD5解密")
            .setView(container)
            .setPositiveButton("解密") { _, _ ->
                val password = input.text.toString()
                if (password.isNotBlank()) {
                    val text = binding.etContent.text.toString()
                    if (text.isNotBlank()) {
                        try {
                            val decrypted = Md5Utils.decrypt(text, password)
                            binding.etContent.setText(decrypted)
                            Toast.makeText(requireContext(), "解密成功", Toast.LENGTH_SHORT).show()
                        } catch (e: Md5Utils.DecryptionException) {
                            Toast.makeText(requireContext(), "解密失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteArticle() {
        currentArticle?.let { article ->
            if (article.id > 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除这篇文章吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteArticle(article)
                            requireActivity().finish()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                requireActivity().finish()
            }
        }
    }

    /** 另存为：复制当前文章为新文章，保留原文不变 */
    private fun saveAsNewArticle() {
        val current = currentArticle ?: return
        val originalTitle = binding.etTitle.text.toString()
        val content = binding.etContent.text.toString()

        val input = EditText(requireContext()).apply {
            setText("$originalTitle-副本")
            selectAll()
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("另存为新文章")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val newTitle = input.text.toString().ifBlank { "$originalTitle-副本" }
                lifecycleScope.launch {
                    val newArticle = Article(
                        chapterId = current.chapterId,
                        title = newTitle,
                        content = content,
                        isMarkdown = current.isMarkdown,
                        wordCount = content.length,
                        isNew = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.insertArticle(newArticle)
                    Toast.makeText(requireContext(), "已另存为: $newTitle", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        saveArticle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearFullPreview()
        _binding = null
    }
}
