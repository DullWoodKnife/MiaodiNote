package com.miaodi.note.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.miaodi.note.databinding.BottomSheetEditOptionsBinding

class EditOptionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditOptionsBinding? = null
    private val binding get() = _binding!!
    var onOptionSelected: ((String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetEditOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tools section
        binding.btnEditorSettings.setOnClickListener { onOptionSelected?.invoke("editor_settings"); dismiss() }
        binding.btnFindReplace.setOnClickListener { onOptionSelected?.invoke("find_replace"); dismiss() }
        binding.btnDelete.setOnClickListener { onOptionSelected?.invoke("delete"); dismiss() }
        binding.btnEncrypt.setOnClickListener { onOptionSelected?.invoke("encrypt"); dismiss() }
        binding.btnDecrypt.setOnClickListener { onOptionSelected?.invoke("decrypt"); dismiss() }
        binding.btnSelectAllCopy.setOnClickListener { onOptionSelected?.invoke("select_all_copy"); dismiss() }
        binding.btnClearAll.setOnClickListener { onOptionSelected?.invoke("clear_all"); dismiss() }
        binding.btnPreview.setOnClickListener { onOptionSelected?.invoke("preview"); dismiss() }

        binding.btnSaveAs.setOnClickListener { onOptionSelected?.invoke("save_as"); dismiss() }

        // Article settings
        binding.btnSwitchStatus.setOnClickListener { onOptionSelected?.invoke("switch_status"); dismiss() }
        binding.btnMoveToBook.setOnClickListener { onOptionSelected?.invoke("move_to_book"); dismiss() }
        binding.btnMoveToChapter.setOnClickListener { onOptionSelected?.invoke("move_to_chapter"); dismiss() }

        // Share
        binding.btnShareQuote.setOnClickListener { onOptionSelected?.invoke("share_quote"); dismiss() }
        binding.btnShareLink.setOnClickListener { onOptionSelected?.invoke("share_link"); dismiss() }
        binding.btnCollaborate.setOnClickListener { onOptionSelected?.invoke("collaborate"); dismiss() }

        // Export
        binding.btnExportMd.setOnClickListener { onOptionSelected?.invoke("export_md"); dismiss() }
        binding.btnExportTxt.setOnClickListener { onOptionSelected?.invoke("export_txt"); dismiss() }
        binding.btnExportPdf.setOnClickListener { onOptionSelected?.invoke("export_pdf"); dismiss() }
        binding.btnExportHtml.setOnClickListener { onOptionSelected?.invoke("export_html"); dismiss() }
        binding.btnExportImage.setOnClickListener { onOptionSelected?.invoke("export_image"); dismiss() }
        binding.btnExportPreview.setOnClickListener { onOptionSelected?.invoke("export_preview"); dismiss() }
    }

    fun setInfo(chapterName: String, createdAt: String, updatedAt: String) {
        view?.post {
            _binding?.tvChapterName?.text = "当前章节: $chapterName"
            _binding?.tvCreateTime?.text = "创建时间: $createdAt"
            _binding?.tvUpdateTime?.text = "更新时间: $updatedAt"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EditOptionsBottomSheet = EditOptionsBottomSheet()
    }
}
