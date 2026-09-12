package com.miaodi.note.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.miaodi.note.MiaodiApplication
import com.miaodi.note.R
import com.miaodi.note.databinding.ActivityMainBinding
import com.miaodi.note.service.ClipboardMonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var viewModel: com.miaodi.note.ui.viewmodel.MainViewModel

    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repository = (application as MiaodiApplication).repository
        viewModel = androidx.lifecycle.ViewModelProvider(this, com.miaodi.note.ui.viewmodel.MainViewModel.Factory(repository))[com.miaodi.note.ui.viewmodel.MainViewModel::class.java]

        setupNavigation()
        setupToolbar()
        setupFab()
        setupDrawer()
        observeViewModel()
        setupBackPressed()
        applyManualNavSetting()
        handleQuickNoteIntent(intent)

        // 恢复状态栏颜色为 primary 避免系统默认白色覆盖
        window.statusBarColor = getColor(R.color.primary_dark)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickNoteIntent(intent)
    }

    /**
     * 处理通知栏“快捷记录 / 剪贴板导入询问”带过来的 Intent：
     * 写入预填文本，并导航到编辑页新建文章。
     */
    private fun handleQuickNoteIntent(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE, false) &&
            !intent.getBooleanExtra(ClipboardMonitorService.EXTRA_ASK_IMPORT, false)
        ) return

        lifecycleScope.launch {
            viewModel.currentChapterId.first { it > 0 }
            val quickText = intent.getStringExtra(ClipboardMonitorService.EXTRA_QUICK_NOTE_TEXT)
            viewModel.setPendingQuickNoteText(quickText)
            val chapterId = viewModel.currentChapterId.value
            val editIntent = Intent(this@MainActivity, EditActivity::class.java).apply {
                putExtra("articleId", -1L)
                putExtra("chapterId", chapterId)
                putExtra("quickText", quickText)
            }
            startActivity(editIntent)
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.mainFragment),
            binding.drawerLayout
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isMain = destination.id == R.id.mainFragment
            if (isMain) {
                binding.appBarLayout.visibility = View.VISIBLE
                binding.fabMain.visibility = View.VISIBLE
                if (isFabMenuOpen) {
                    binding.fabNewFolder.visibility = View.VISIBLE
                    binding.fabNewDoc.visibility = View.VISIBLE
                }
            } else {
                binding.appBarLayout.visibility = View.GONE
                binding.fabMain.visibility = View.GONE
                binding.fabNewFolder.visibility = View.GONE
                binding.fabNewDoc.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (navController.currentDestination?.id == R.id.mainFragment) {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            } else {
                navController.navigateUp()
            }
        }

        // 点击标题栏区域打开书本/章节选择面板
        binding.toolbarTitleContainer.setOnClickListener {
            showBookChapterSheet()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    if (navController.currentDestination?.id == R.id.mainFragment) {
                        showSearchDialog()
                    }
                    true
                }
                R.id.action_more -> {
                    if (navController.currentDestination?.id == R.id.mainFragment) {
                        viewModel.onSortClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "输入关键字搜索标题或内容"
            setText(viewModel.searchQuery.value)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("内容检索")
            .setView(container)
            .setPositiveButton("搜索") { _, _ ->
                val query = input.text.toString().trim()
                viewModel.setSearchQuery(query)
                if (query.isBlank()) {
                    Toast.makeText(this, "已清除搜索", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清除") { _, _ ->
                viewModel.setSearchQuery("")
                Toast.makeText(this, "已清除搜索", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupFab() {
        binding.fabMain.setOnClickListener {
            toggleFabMenu()
        }

        binding.fabNewFolder.setOnClickListener {
            toggleFabMenu()
            // Show book/chapter selection to add folder
            showBookChapterSheet()
        }

        binding.fabNewDoc.setOnClickListener {
            toggleFabMenu()
            // Navigate to edit activity to create new article
            val chapterId = viewModel.currentChapterId.value
            if (chapterId > 0) {
                val intent = Intent(this, EditActivity::class.java).apply {
                    putExtra("articleId", -1L)
                    putExtra("chapterId", chapterId)
                }
                startActivity(intent)
            }
        }

        // 默认收起：仅显示"+"按钮
        binding.fabNewFolder.visibility = View.GONE
        binding.fabNewDoc.visibility = View.GONE
        binding.fabMain.setImageResource(R.drawable.ic_add)
        isFabMenuOpen = false
    }

    /** 应用“手动管理导航栏”设置：隐藏/显示系统导航栏 */
    private fun applyManualNavSetting() {
        val manualNav = getSharedPreferences("miaodi_settings", MODE_PRIVATE)
            .getString("manual_nav", "跟随系统")
        when (manualNav) {
            "隐藏导航栏" -> window.decorView.systemUiVisibility =
                (android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
            "显示导航栏" -> window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            else -> window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun toggleFabMenu() {
        isFabMenuOpen = !isFabMenuOpen
        if (isFabMenuOpen) {
            binding.fabNewFolder.visibility = View.VISIBLE
            binding.fabNewDoc.visibility = View.VISIBLE
            binding.fabMain.setImageResource(R.drawable.ic_close)
        } else {
            binding.fabNewFolder.visibility = View.GONE
            binding.fabNewDoc.visibility = View.GONE
            binding.fabMain.setImageResource(R.drawable.ic_add)
        }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_records -> {
                    if (navController.currentDestination?.id != R.id.mainFragment) {
                        navController.navigate(R.id.mainFragment)
                    }
                }
                R.id.nav_todo -> {
                    navController.navigate(R.id.todoFragment)
                }
                R.id.nav_settings -> {
                    navController.navigate(R.id.settingsFragment)
                }
                R.id.nav_about -> {
                    navController.navigate(R.id.aboutFragment)
                }
            }
            true
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.books.collect { books ->
                    // Update UI if needed
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentBookId.collect { bookId ->
                    val book = viewModel.books.value.find { it.id == bookId }
                    binding.tvBookTitle.text = book?.name ?: getString(R.string.app_name)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chapters.collect { chapters ->
                    val chapterId = viewModel.currentChapterId.value
                    val chapter = chapters.find { it.id == chapterId }
                    binding.tvChapterTitle.text = chapter?.name ?: "默认"
                }
            }
        }
    }

    private fun showBookChapterSheet() {
        val sheet = com.miaodi.note.ui.fragment.BookChapterBottomSheet.newInstance()
        sheet.show(supportFragmentManager, "BookChapterSheet")
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (isFabMenuOpen) {
                    toggleFabMenu()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
