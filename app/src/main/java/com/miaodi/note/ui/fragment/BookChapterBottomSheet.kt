package com.miaodi.note.ui.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.data.model.Book
import com.miaodi.note.databinding.BottomSheetBookChapterBinding
import com.miaodi.note.databinding.DialogAddItemBinding
import com.miaodi.note.databinding.DialogNewBookBinding
import com.miaodi.note.databinding.DialogRenameBookBinding
import com.miaodi.note.databinding.BottomSheetBookOpsBinding
import com.miaodi.note.ui.adapter.BookAdapter
import com.miaodi.note.ui.adapter.ChapterAdapter
import com.miaodi.note.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class BookChapterBottomSheet : DialogFragment() {

    private var _binding: BottomSheetBookChapterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private lateinit var bookAdapter: BookAdapter
    private lateinit var chapterAdapter: ChapterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            behavior.peekHeight = resources.displayMetrics.heightPixels
            behavior.isDraggable = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetBookChapterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = (requireActivity().application as MiaodiApplication).repository
        viewModel = ViewModelProvider(requireActivity(), MainViewModel.Factory(repository))[MainViewModel::class.java]

        setupToolbar()
        setupRecyclerViews()
        observeViewModel()

        binding.btnAddBookChapter.setOnClickListener {
            showAddItemDialog()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerViews() {
        bookAdapter = BookAdapter(
            onItemClick = { book ->
                viewModel.selectBook(book.id)
            },
            onMenuClick = { book, anchorView ->
                showBookOpsDialog(book)
            },
            getSelectedId = { viewModel.currentBookId.value }
        )

        chapterAdapter = ChapterAdapter(
            onItemClick = { chapter ->
                viewModel.selectChapter(chapter.id)
                dismiss()
            },
            onMenuClick = { chapter, anchorView ->
                showChapterOpsDialog(chapter)
            },
            getSelectedId = { viewModel.currentChapterId.value }
        )

        binding.rvBooks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookAdapter
        }

        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chapterAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.books.collect { books ->
                    bookAdapter.submitList(books)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chapters.collect { chapters ->
                    chapterAdapter.submitList(chapters)
                }
            }
        }
    }

    private fun showAddItemDialog() {
        val dialogBinding = DialogAddItemBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cardBook.setOnClickListener {
            showNewBookDialog()
            dialog.dismiss()
        }

        dialogBinding.cardChapter.setOnClickListener {
            val bookId = viewModel.currentBookId.value
            if (bookId > 0) {
                showInputDialog("新建章节") { name ->
                    viewModel.insertChapter(bookId, name)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showNewBookDialog() {
        val dialogBinding = DialogNewBookBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnRestoreDefault.setOnClickListener {
            // Restore default book - select the first book (which is typically the default)
            val defaultBook = viewModel.books.value.firstOrNull()
            defaultBook?.let {
                viewModel.selectBook(it.id)
                Toast.makeText(requireContext(), "已恢复默认书本", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.etBookName.text.toString().trim()
            if (name.isNotBlank()) {
                viewModel.insertBook(name)
                Toast.makeText(requireContext(), "已创建书本: $name", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showInputDialog(title: String, onConfirm: (String) -> Unit) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.hint = "请输入名称"

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    onConfirm(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBookOpsDialog(book: Book) {
        val dialogBinding = BottomSheetBookOpsBinding.inflate(layoutInflater)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnExportBook.setOnClickListener {
            // TODO: Implement book export
            Toast.makeText(requireContext(), "导出功能开发中", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBinding.btnRenameOrDelete.setOnClickListener {
            dialog.dismiss()
            showRenameBookDialog(book)
        }

        dialogBinding.btnSetCover.setOnClickListener {
            // TODO: Implement cover setting
            Toast.makeText(requireContext(), "设置封面功能开发中", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRenameBookDialog(book: Book) {
        val dialogBinding = DialogRenameBookBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.etBookName.setText(book.name)

        // Protect default book (first book) from deletion
        val isDefaultBook = viewModel.books.value.indexOfFirst { it.id == book.id } == 0

        dialogBinding.btnDeleteBook.setOnClickListener {
            if (isDefaultBook) {
                Toast.makeText(requireContext(), "默认书本不可删除", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除确认")
                    .setMessage("确定要删除书本\"${book.name}\"吗？\n注意：删除书本将同时删除其所有章节和文章，且无法恢复！")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteBook(book)
                        Toast.makeText(requireContext(), "已删除书本", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val newName = dialogBinding.etBookName.text.toString().trim()
            if (newName.isNotBlank() && newName != book.name) {
                viewModel.updateBook(book.copy(name = newName, updatedAt = System.currentTimeMillis()))
                Toast.makeText(requireContext(), "已修改书名", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showChapterOpsDialog(chapter: com.miaodi.note.data.model.Chapter) {
        // TODO: Implement chapter operations (rename, delete)
        Toast.makeText(requireContext(), "章节操作功能开发中", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BookChapterBottomSheet {
            return BookChapterBottomSheet()
        }
    }
}
