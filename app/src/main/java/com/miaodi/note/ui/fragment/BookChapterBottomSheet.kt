package com.miaodi.note.ui.fragment

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
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
import com.miaodi.note.databinding.DialogRenameChapterBinding
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

        // 选中书本变化时立即刷新书本列表的选中状态（列表内容不变时 ListAdapter 不会重新绑定）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentBookId.collect {
                    bookAdapter.notifyDataSetChanged()
                }
            }
        }

        // 选中章节变化时立即刷新章节列表的选中状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentChapterId.collect {
                    chapterAdapter.notifyDataSetChanged()
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
            dialog.dismiss()
            showExportMethodDialog(book)
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
        val dialogBinding = DialogRenameChapterBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvHint.text = "${chapter.name} 更改为"
        dialogBinding.etChapterName.setText(chapter.name)
        dialogBinding.etChapterName.setSelection(chapter.name.length)

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDeleteChapter.setOnClickListener {
            // 删除章节（将同时删除其下所有文章，Room 外键 CASCADE）
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除确认")
                .setMessage("确定要删除章节\"${chapter.name}\"吗？\n注意：删除章节将同时删除其中的所有文章，且无法恢复！")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteChapter(chapter)
                    Toast.makeText(requireContext(), "已删除章节", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etChapterName.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "章节名不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newName != chapter.name) {
                viewModel.updateChapter(
                    chapter.copy(name = newName, updatedAt = System.currentTimeMillis())
                )
                Toast.makeText(requireContext(), "已修改章节名", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    // ========== Export book methods ==========

    private var pendingExportBook: Book? = null

    private val customExportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val book = pendingExportBook ?: return@registerForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                exportToCustomFolder(book, uri)
            } else {
                Toast.makeText(requireContext(), "未选择目录", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportBook = null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val book = pendingExportBook ?: return@registerForActivityResult
        if (isGranted) {
            exportToDefaultFolder(book)
        } else {
            Toast.makeText(requireContext(), "需要存储权限以保存文件", Toast.LENGTH_SHORT).show()
        }
        pendingExportBook = null
    }

    private fun showExportMethodDialog(book: Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("提示")
            .setMessage("请选择保存方式\nPs:Android10及以上版本请使用自定义保存")
            .setPositiveButton("应用文件夹保存") { _, _ ->
                exportBookToDefault(book)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("自定义保存") { _, _ ->
                exportBookToCustom(book)
            }
            .show()
    }

    private fun exportBookToDefault(book: Book) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 and below need WRITE_EXTERNAL_STORAGE permission
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    exportToDefaultFolder(book)
                }
                else -> {
                    pendingExportBook = book
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            exportToDefaultFolder(book)
        }
    }

    private fun exportToDefaultFolder(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            val articles = viewModel.getArticlesByBookOnce(book.id)
            if (articles.isEmpty()) {
                Toast.makeText(requireContext(), "该书本下没有文章可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val targetDir = java.io.File(downloadDir, "zxm-note")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                var successCount = 0
                for (article in articles) {
                    val fileName = getExportFileName(article)
                    val file = java.io.File(targetDir, fileName)
                    file.writeText(resolveArticleContent(article))
                    successCount++
                }
                Toast.makeText(
                    requireContext(),
                    "已导出 $successCount 篇文章到 ${targetDir.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportBookToCustom(book: Book) {
        pendingExportBook = book
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Optional: pre-select a starting directory (not always honored by all file managers)
        }
        customExportLauncher.launch(intent)
    }

    private fun exportToCustomFolder(book: Book, treeUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val articles = viewModel.getArticlesByBookOnce(book.id)
            if (articles.isEmpty()) {
                Toast.makeText(requireContext(), "该书本下没有文章可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val pickedDir = DocumentFile.fromTreeUri(requireContext(), treeUri)
                    ?: throw IllegalStateException("无法访问选中的目录")
                // Create a subfolder named after the book
                val safeBookName = sanitizeFileName(book.name)
                val bookDir = pickedDir.findFile(safeBookName)
                    ?: pickedDir.createDirectory(safeBookName)
                    ?: throw IllegalStateException("无法创建目录: $safeBookName")

                var successCount = 0
                for (article in articles) {
                    val fileName = getExportFileName(article)
                    // Remove existing file if any
                    bookDir.findFile(fileName)?.delete()
                    val newFile = bookDir.createFile("text/markdown", fileName)
                        ?: continue
                    requireContext().contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        out.write(resolveArticleContent(article).toByteArray(Charsets.UTF_8))
                        successCount++
                    }
                }
                Toast.makeText(
                    requireContext(),
                    "已导出 $successCount 篇文章到 ${bookDir.name}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 解析文章完整正文：大文档正文落盘，从文件读取；否则单列读取 content。 */
    private suspend fun resolveArticleContent(article: com.miaodi.note.data.model.Article): String {
        val path = article.contentPath
        if (!path.isNullOrBlank()) {
            val fromFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.miaodi.note.data.ArticleContentStore.read(requireContext(), path)
            }
            if (fromFile != null) return fromFile
        }
        return viewModel.getArticleContent(article.id)
    }

    private fun getExportFileName(article: com.miaodi.note.data.model.Article): String {
        val baseName = if (article.title.isBlank()) "article_${article.id}" else sanitizeFileName(article.title)
        return "$baseName.md"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
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