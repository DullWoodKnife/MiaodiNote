package com.miaodi.note.ui.fragment

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.miaodi.note.databinding.BottomSheetEditorSettingsBinding

/**
 * “编辑器设置”面板：包含 快捷栏 / 编辑器设置 / 文本格式 三个标签页，支持左右滑动切换。
 * - 快捷栏：向正文光标处插入常用 Markdown 片段
 * - 编辑器设置：标题字体大小 / 文章字体大小 / 文本间距 / 文本行距 四个滑块 +
 *               同步滑动 / 所见即所得模式 / 写作模式 / 专注模式 复选框
 * - 文本格式：对正文执行常见的段落/空格整理操作
 */
class EditorSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditorSettingsBinding? = null
    private val binding get() = _binding!!

    /** 在正文光标处插入文本片段 */
    var onInsertText: ((String) -> Unit)? = null
    /** 对正文执行文本格式整理，参数为操作标识 */
    var onTextTransform: ((String) -> Unit)? = null
    /** 标题字体大小变化 */
    var onTitleFontSizeChanged: ((Int) -> Unit)? = null
    /** 文章字体大小变化 */
    var onBodyFontSizeChanged: ((Int) -> Unit)? = null
    /** 文本间距变化 */
    var onTextSpacingChanged: ((Int) -> Unit)? = null
    /** 文本行距变化 */
    var onLineSpacingChanged: ((Int) -> Unit)? = null
    /** 复选框设置变化（key, checked） */
    var onSettingChanged: ((String, Boolean) -> Unit)? = null

    private lateinit var prefs: SharedPreferences
    private var currentTab = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditorSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("miaodi_settings", Context.MODE_PRIVATE)

        binding.tabQuick.setOnClickListener { selectTab(0) }
        binding.tabEditor.setOnClickListener { selectTab(1) }
        binding.tabFormat.setOnClickListener { selectTab(2) }

        // 左右滑动切换标签页
        binding.panelContainer.onSwipeLeft = { selectTab(currentTab + 1) }
        binding.panelContainer.onSwipeRight = { selectTab(currentTab - 1) }

        // 标题字体大小（默认 18）
        bindSeek(binding.seekTitleFontSize, binding.tvTitleFontSize, "标题字体内容", "title_font_size", 18, 40) { v ->
            onTitleFontSizeChanged?.invoke(v)
        }
        // 文章字体大小（默认 14）
        bindSeek(binding.seekBodyFontSize, binding.tvBodyFontSize, "文章字体内容", "body_font_size", 14, 30) { v ->
            onBodyFontSizeChanged?.invoke(v)
        }
        // 文本间距（默认 5）
        bindSeek(binding.seekTextSpacing, binding.tvTextSpacing, "文本间距", "text_spacing", 5, 20) { v ->
            onTextSpacingChanged?.invoke(v)
        }
        // 文本行距（默认 12）
        bindSeek(binding.seekLineSpacing, binding.tvLineSpacing, "文本行距", "text_line_spacing", 12, 30) { v ->
            onLineSpacingChanged?.invoke(v)
        }

        // 复选框
        bindCheck(binding.cbSyncScroll, "sync_scroll")
        bindCheck(binding.cbWysiwyg, "wysiwyg_mode")
        bindCheck(binding.cbWritingMode, "writing_mode")
        bindCheck(binding.cbFocusMode, "focus_mode")

        // 快捷栏插入
        binding.qbTab.setOnClickListener { onInsertText?.invoke("\t") }
        binding.qbHeading.setOnClickListener { onInsertText?.invoke("# ") }
        binding.qbBold.setOnClickListener { onInsertText?.invoke("**粗体**") }
        binding.qbItalic.setOnClickListener { onInsertText?.invoke("*斜体*") }
        binding.qbQuote.setOnClickListener { onInsertText?.invoke("> ") }
        binding.qbImage.setOnClickListener { onInsertText?.invoke("![图片](https://)") }
        binding.qbOl.setOnClickListener { onInsertText?.invoke("1. ") }
        binding.qbUl.setOnClickListener { onInsertText?.invoke("- ") }
        binding.qbLink.setOnClickListener { onInsertText?.invoke("[链接](http://)") }
        binding.qbCode.setOnClickListener { onInsertText?.invoke("```\n\n```") }
        binding.qbTask.setOnClickListener { onInsertText?.invoke("- [ ] ") }
        binding.qbInlineCode.setOnClickListener { onInsertText?.invoke("`代码`") }
        binding.qbStrike.setOnClickListener { onInsertText?.invoke("~~删除线~~") }
        binding.qbMath.setOnClickListener { onInsertText?.invoke("\$公式\$") }
        binding.qbTable.setOnClickListener {
            onInsertText?.invoke("| 表头 | 表头 |\n| --- | --- |\n| 单元格 | 单元格 |")
        }

        // 文本格式整理
        binding.fmtAddBlankLine.setOnClickListener { onTextTransform?.invoke("add_blank_line") }
        binding.fmtRemoveBlankLine.setOnClickListener { onTextTransform?.invoke("remove_blank_line") }
        binding.fmtIndentFirstLine.setOnClickListener { onTextTransform?.invoke("indent_first_line") }
        binding.fmtRemoveIndent.setOnClickListener { onTextTransform?.invoke("remove_indent") }
        binding.fmtCjkSpace.setOnClickListener { onTextTransform?.invoke("cjk_space") }
        binding.fmtCjkNumSpace.setOnClickListener { onTextTransform?.invoke("cjk_num_space") }

        selectTab(1)
    }

    private fun bindSeek(
        seekBar: SeekBar,
        label: TextView,
        name: String,
        key: String,
        default: Int,
        max: Int,
        onChange: (Int) -> Unit
    ) {
        val value = prefs.getInt(key, default)
        label.text = "$name:$value"
        seekBar.max = max
        seekBar.progress = value
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "$name:$progress"
                if (fromUser) {
                    prefs.edit().putInt(key, progress).apply()
                    onChange(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun bindCheck(cb: CheckBox, key: String) {
        cb.isChecked = prefs.getBoolean(key, false)
        cb.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
            onSettingChanged?.invoke(key, checked)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index.coerceIn(0, 2)
        binding.panelQuick.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
        binding.panelEditor.visibility = if (currentTab == 1) View.VISIBLE else View.GONE
        binding.panelFormat.visibility = if (currentTab == 2) View.VISIBLE else View.GONE
        val active = 0xFFFFC107.toInt()
        val normal = 0xFFFFFFFF.toInt()
        binding.tabQuick.setTextColor(if (currentTab == 0) active else normal)
        binding.tabEditor.setTextColor(if (currentTab == 1) active else normal)
        binding.tabFormat.setTextColor(if (currentTab == 2) active else normal)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
        behavior.skipCollapsed = true
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EditorSettingsBottomSheet = EditorSettingsBottomSheet()
    }
}
