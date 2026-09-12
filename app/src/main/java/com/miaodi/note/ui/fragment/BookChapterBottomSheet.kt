package com.miaodi.note.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.databinding.BottomSheetBookChapterBinding
import com.miaodi.note.databinding.DialogAddItemBinding
import com.miaodi.note.ui.adapter.BookAdapter
import com.miaodi.note.ui.adapter.ChapterAdapter
import com.miaodi.note.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class BookChapterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBookChapterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private lateinit var bookAdapter: BookAdapter
    private lateinit var chapterAdapter: ChapterAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetBookChapterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = (requireActivity().application as MiaodiApplication).repository
        viewModel = ViewModelProvider(requireActivity(), MainViewModel.Factory(repository))[MainViewModel::class.java]

        setupRecyclerViews()
        observeViewModel()

        binding.btnAddBookChapter.setOnClickListener {
            showAddItemDialog()
        }
    }

    private fun setupRecyclerViews() {
        bookAdapter = BookAdapter(
            onItemClick = { book ->
                viewModel.selectBook(book.id)
            },
            getSelectedId = { viewModel.currentBookId.value }
        )

        chapterAdapter = ChapterAdapter(
            onItemClick = { chapter ->
                viewModel.selectChapter(chapter.id)
                dismiss()
            },
            getSelectedId = { viewModel.currentChapterId.value }
        )

        binding.rvBooks.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
            adapter = bookAdapter
        }

        binding.rvChapters.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
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
            showInputDialog("新建书本") { name ->
                viewModel.insertBook(name)
            }
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

    private fun showInputDialog(title: String, onConfirm: (String) -> Unit) {
        val input = TextInputEditText(requireContext())
        input.hint = "请输入名称"

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
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
