package com.miaodi.note.ui.fragment

import android.app.Activity
import android.app.Dialog
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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
    // 新建文章插入进行中标记：避免返回键 / 工具栏 / onPause 重复触发导致插入两条相同文章
    private var isNewArticleInsertPending = false
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
        // 初始应用默认“滑动状态”模式（不自动进入编辑态）
        applyMode()

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
                binding.etContent.visibility = View.VISIBLE
                binding.etContent.isFocusable = true
                binding.etContent.setTextIsSelectable(true)
                binding.etContent.isFocusableInTouchMode = true
                clearFullPreview()
            }
            EditorMode.SLIDE -> {
                binding.etContent.isEnabled = true
                binding.etContent.visibility = View.VISIBLE
                // 未点击内容前不进入编辑态：禁用触摸聚焦并清除焦点，避免自动弹出键盘
                binding.etContent.isFocusableInTouchMode = false
                binding.etContent.isFocusable = false
                binding.etContent.setTextIsSelectable(false)
                binding.etContent.clearFocus()
                hideKeyboard()
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

    /**
     * 统一的横向滑动手势：
     * - 锁定态：不响应滑动（返回 false）。
     * - 滑动态（MD 源码）：左滑 → 进入 MD 渲染模式。
     * - 渲染态（MD 预览）：右滑 → 返回 MD 源码（滑动状态）。
     * 不再触发相邻文章切换，避免误产生“文章副本”。
     */
    private val swipeTouchListener = View.OnTouchListener { _, event ->
        when (currentMode) {
            EditorMode.LOCKED_EDIT -> false
            EditorMode.SLIDE -> {
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
                        // 左滑：dx < 0 且以横向为主 → 进入 MD 渲染预览
                        if (!isGestureConsuming && dx < -60 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                            isGestureConsuming = true
                            currentMode = EditorMode.MD_READONLY
                            applyMode()
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasConsuming = isGestureConsuming
                        isGestureConsuming = false
                        // 未发生滑动时为“点击”：滑动状态且 MD 源码模式下点击源码界面才进入编辑态（弹出键盘）
                        if (!wasConsuming) {
                            val dx = event.x - swipeDownX
                            val dy = event.y - swipeDownY
                            if (kotlin.math.abs(dx) < 20 && kotlin.math.abs(dy) < 20) {
                                enableContentEditing()
                            }
                        }
                        false
                    }
                    else -> false
                }
            }
            EditorMode.MD_READONLY -> {
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
                        // 右滑：dx > 0 且以横向为主 → 回到源码模式
                        if (!isGestureConsuming && dx > 60 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                            isGestureConsuming = true
                            currentMode = EditorMode.SLIDE
                            applyMode()
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
        // 滑动状态下点击正文后才进入编辑态（不点击不自动进入编辑）
        binding.etContent.setOnClickListener {
            if (currentMode == EditorMode.SLIDE) {
                enableContentEditing()
            }
        }
    }

    /** 滑动状态下点击正文后进入编辑态：恢复触摸聚焦并弹出软键盘 */
    private fun enableContentEditing() {
        binding.etContent.isFocusable = true
        binding.etContent.isFocusableInTouchMode = true
        binding.etContent.setTextIsSelectable(true)
        binding.etContent.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etContent, InputMethodManager.SHOW_IMPLICIT)
    }

    /** 收起软键盘 */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
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

    /** 是否检测到物理/蓝牙外接键盘输入 */
    private var isHardwareKeyboardActive = false

    /**
     * 底部快捷工具栏在键盘输入模式时显示：
     * - 软键盘（IME）弹出，或
     * - 外接键盘（蓝牙/有线）激活且编辑框获得焦点。
     * 未进入输入模式时隐藏；同时受设置页“快捷栏”总开关约束。
     */
    private fun setupInputAwareBottomToolbar() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            // 快捷栏上移量取“软键盘”与“系统导航栏”二者的较大值，
            // 保证既跟随软键盘，又不会与系统导航栏图标重叠。
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sysBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val lift = maxOf(imeBottom, sysBarBottom)
            (binding.bottomToolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.bottomMargin != lift) {
                    lp.bottomMargin = lift
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
        // 外接键盘支持：来源为真实设备（deviceId != -1）的按键即视为键盘输入激活；
        // 同时提供 Ctrl+S 保存 / Ctrl+Z 撤销 / Ctrl+Y 重做的物理键盘快捷操作。
        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (_binding == null) return@OnKeyListener false
            if (event.deviceId != -1) {
                isHardwareKeyboardActive = true
                updateBottomToolbarVisibility()
            }
            if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
                when (keyCode) {
                    KeyEvent.KEYCODE_S -> { saveArticle(); true }
                    KeyEvent.KEYCODE_Z -> { undo(); true }
                    KeyEvent.KEYCODE_Y -> { redo(); true }
                    else -> false
                }
            } else {
                false
            }
        }
        binding.etTitle.setOnKeyListener(keyListener)
        binding.etContent.setOnKeyListener(keyListener)
        // 初始状态：未进入输入模式，先隐藏
        updateBottomToolbarVisibility()
    }

    /** 根据“快捷栏”设置与键盘输入模式刷新底部快捷工具栏的显隐 */
    private fun updateBottomToolbarVisibility() {
        if (_binding == null) return
        val showQuickBar = requireContext()
            .getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
            .getBoolean("quick_bar", true)
        // 软键盘弹出，或（外接键盘激活且编辑框聚焦）时显示快捷栏
        val inputMode = isImeVisible || (isHardwareKeyboardActive && isEditorFocused)
        binding.bottomToolbar.visibility =
            if (showQuickBar && inputMode) View.VISIBLE else View.GONE
    }

    /** 根据“优先预览文章”设置，Markdown 文章打开时自动弹出渲染预览 */
    private fun maybeAutoPreview() {
        if (currentArticle?.isMarkdown != true) return
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("preview_first", false)) {
            binding.root.post { showMarkdownPreview() }
        }
    }

    /** 全屏 Markdown 预览：铺满屏幕、无标题栏、无右下角关闭按钮，可横向滑动或返回键退出 */
    private fun showMarkdownPreview() {
        val prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)
        val mdStyle = prefs.getString("md_style", "默认") ?: "默认"
        val content = binding.etContent.text.toString()
        val html = MarkdownPreviewUtils.render(content, mdStyle)

        val webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = false
            settings.textZoom = 100
            setBackgroundColor(android.graphics.Color.WHITE)
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        val container = android.widget.FrameLayout(requireContext()).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            addView(
                webView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.setContentView(container)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 手势退出：横向滑动即关闭预览（无关闭按钮）
        var downX = 0f
        var downY = 0f
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) > 120 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2) {
                        dialog.dismiss()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        dialog.setOnDismissListener { webView.destroy() }
        dialog.show()
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

            if (newArticle.id == 0L) {
                // 新建文章：若插入已在进行中则跳过，避免返回键/工具栏/onPause 重复插入产生两条相同文章
                if (isNewArticleInsertPending) return
                isNewArticleInsertPending = true
            }

            lifecycleScope.launch {
                if (newArticle.id == 0L) {
                    val newId = repository.insertArticle(newArticle)
                    currentArticle = newArticle.copy(id = newId)
                    isNewArticleInsertPending = false
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
                "editor_settings" -> showEditorSettingsSheet()
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

    /** 弹出“编辑器设置”面板：快捷栏 / 编辑器设置 / 文本格式 三个标签页 */
    private fun showEditorSettingsSheet() {
        val sheet = EditorSettingsBottomSheet.newInstance()
        sheet.onInsertText = { text -> insertAtCursor(binding.etContent, text) }
        sheet.onTextTransform = { action -> applyTextTransform(action) }
        sheet.onLineSpacingChanged = { spacing -> applyLineSpacing(spacing) }
        sheet.onSettingChanged = { key, checked ->
            when (key) {
                "focus_mode" -> {
                    // 专注模式：编辑时隐藏顶部工具栏，减少干扰
                    binding.toolbar.visibility = if (checked) View.GONE else View.VISIBLE
                }
            }
        }
        sheet.show(parentFragmentManager, "EditorSettingsSheet")
    }

    /** 应用文本行距（单位：sp 的倍数近似） */
    private fun applyLineSpacing(spacing: Int) {
        val dp = spacing.toFloat()
        binding.etContent.setLineSpacing(0f, 1f + dp / 20f)
    }

    /** 对正文执行常见的文本格式整理 */
    private fun applyTextTransform(action: String) {
        val original = binding.etContent.text.toString()
        val result = when (action) {
            "add_blank_line" -> original.replace(Regex("(?m)(^[^\\n]*\\S)$"), "$1\n")
            "remove_blank_line" -> original.replace(Regex("(?m)^\\s*$\\n?"), "").let {
                // 将连续多个换行压缩为单个换行
                it.replace(Regex("\\n{2,}"), "\n")
            }
            "indent_first_line" -> original.lines().joinToString("\n") { line ->
                if (line.isNotBlank() && !line.startsWith(" ")) "　　$line" else line
            }
            "remove_indent" -> original.lines().joinToString("\n") { it.replace(Regex("^[　\\s]+"), "") }
            "cjk_space" -> original.replace(Regex("([\\u4e00-\\u9fa5])([A-Za-z0-9])"), "$1 $2")
                .replace(Regex("([A-Za-z0-9])([\\u4e00-\\u9fa5])"), "$1 $2")
            "cjk_num_space" -> original.replace(Regex("([\\u4e00-\\u9fa5])(\\d)"), "$1 $2")
                .replace(Regex("(\\d)([\\u4e00-\\u9fa5])"), "$1 $2")
            else -> original
        }
        if (result != original) {
            binding.etContent.setText(result)
            binding.etContent.setSelection(result.length)
        }
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
