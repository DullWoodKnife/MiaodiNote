package com.miaodi.note.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.Intent
import com.miaodi.note.ui.EditActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.databinding.BottomSheetSortBinding
import com.miaodi.note.databinding.FragmentMainBinding
import com.miaodi.note.ui.adapter.ArticleAdapter
import com.miaodi.note.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private lateinit var articleAdapter: ArticleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = (requireActivity().application as MiaodiApplication).repository
        viewModel = ViewModelProvider(requireActivity(), MainViewModel.Factory(repository))[MainViewModel::class.java]

        setupRecyclerView()
        setupMultiSelectBar()
        observeViewModel()
        observeEvents()
    }

    private fun setupRecyclerView() {
        articleAdapter = ArticleAdapter(
            onItemClick = { article ->
                viewModel.onArticleClick(article.id)
            },
            onItemLongClick = { article ->
                // Long press enters multi-select mode
            },
            onSelectionChanged = {
                updateMultiSelectBar()
            }
        )
        binding.recyclerView.apply {
            // 根据设置页“文章列表显示方式”切换布局
            val listMode = requireContext()
                .getSharedPreferences("miaodi_settings", android.content.Context.MODE_PRIVATE)
                .getString("list_mode", "单列列表")
            layoutManager = if (listMode == "两列卡片") {
                GridLayoutManager(requireContext(), 2)
            } else {
                LinearLayoutManager(requireContext())
            }
            adapter = articleAdapter
        }
    }

    private fun setupMultiSelectBar() {
        binding.btnDeleteSelected.setOnClickListener {
            val selected = articleAdapter.getSelectedArticles()
            if (selected.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除确认")
                    .setMessage("警告：一旦删除将无法恢复，确认删除吗？\n本次共删除${selected.size}篇文章")
                    .setPositiveButton("确定") { _, _ ->
                        viewModel.deleteArticles(selected)
                        articleAdapter.resetSelection()
                    }
                    .setNegativeButton("算了", null)
                    .show()
            }
        }
        binding.btnCancelSelect.setOnClickListener {
            articleAdapter.resetSelection()
        }
        // 中间“更多”按钮：全选 / 取消全选
        binding.btnMoreSelected.setOnClickListener { anchor ->
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, "全选")
            popup.menu.add(0, 2, 1, "取消全选")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> articleAdapter.selectAll()
                    2 -> articleAdapter.resetSelection()
                }
                true
            }
            popup.show()
        }
    }

    private fun updateMultiSelectBar() {
        val count = articleAdapter.selectedCount
        binding.layoutMultiSelect.visibility = if (count > 0) View.VISIBLE else View.GONE
        // 多选模式下隐藏右下角“+”悬浮按钮，避免与多选操作栏重叠
        (activity as? com.miaodi.note.ui.MainActivity)?.setFabMenuVisibleForSelection(count == 0)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.articles.collect { articles ->
                    articleAdapter.submitList(articles)
                    binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (articles.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun observeEvents() {
        viewModel.navigateToEdit.observe(viewLifecycleOwner) { articleId ->
            articleId?.let {
                val intent = Intent(requireContext(), EditActivity::class.java).apply {
                    putExtra("articleId", it)
                    putExtra("chapterId", viewModel.currentChapterId.value)
                }
                startActivity(intent)
                viewModel.onEditNavigated()
            }
        }

        viewModel.showSortSheet.observe(viewLifecycleOwner) { show ->
            if (show == true) {
                showSortBottomSheet()
                viewModel.onSortSheetDismissed()
            }
        }
    }

    private fun showSortBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetSortBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(sheetBinding.root)

        when (viewModel.sortType.value) {
            MainViewModel.SortType.UPDATE_TIME_DESC -> sheetBinding.rbUpdateTimeDesc.isChecked = true
            MainViewModel.SortType.TITLE_ASC -> sheetBinding.rbTitleAsc.isChecked = true
            MainViewModel.SortType.TITLE_DESC -> sheetBinding.rbTitleDesc.isChecked = true
            MainViewModel.SortType.MANUAL -> sheetBinding.rbManual.isChecked = true
        }

        sheetBinding.radioGroupSort.setOnCheckedChangeListener { _, checkedId ->
            val sortType = when (checkedId) {
                R.id.rbUpdateTimeDesc -> MainViewModel.SortType.UPDATE_TIME_DESC
                R.id.rbTitleAsc -> MainViewModel.SortType.TITLE_ASC
                R.id.rbTitleDesc -> MainViewModel.SortType.TITLE_DESC
                R.id.rbManual -> MainViewModel.SortType.MANUAL
                else -> MainViewModel.SortType.UPDATE_TIME_DESC
            }
            viewModel.setSortType(sortType)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    override fun onResume() {
        super.onResume()
        articleAdapter.resetSelection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}