package com.miaodi.note.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
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

        // 确保状态栏背景色与 Toolbar 一致，防止系统默认白色覆盖
        window.statusBarColor = getColor(R.color.primary_dark)

        val repository = (application as MiaodiApplication).repository
        viewModel = androidx.lifecycle.ViewModelProvider(this, com.miaodi.note.ui.viewmodel.MainViewModel.Factory(repository))[com.miaodi.note.ui.viewmodel.MainViewModel::class.java]

        setupNavigation()
        setupToolbar()
        setupFab()
        setupDrawer()
        observeViewModel()
        setupBackPressed()
        applyManualNavSetting()
        // 仅在首次创建 Activity 时处理启动 Intent；
        // 因配置变更（如屏幕旋转）重建时不再重复导入，避免重复插入文章。
        if (savedInstanceState == null) {
            handleQuickNoteIntent(intent)
            handleExternalFileIntent(intent)
        }

        // 恢复状态栏颜色为 primary 避免系统默认白色覆盖
        window.statusBarColor = getColor(R.color.primary_dark)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickNoteIntent(intent)
        handleExternalFileIntent(intent)
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

    /**
     * 处理外部应用分享/打开 Markdown/文本文件到本应用：
     * 读取文件内容，保存到默认书本的首个章节，并打开编辑页。
     */
    private fun handleExternalFileIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        // 仅处理外部“打开/分享”进入本应用的情形；正常从桌面启动
        // （SplashActivity 发来的显式 Intent，action 为 null/MAIN）时直接返回。
        if (action != Intent.ACTION_VIEW &&
            action != Intent.ACTION_SEND &&
            action != Intent.ACTION_SEND_MULTIPLE
        ) return

        var fileUri: android.net.Uri? = null
        var fileName: String? = null
        var fileContent: String? = null

        // 解析外部 Intent 必须容错：EXTRA_STREAM 可能不是 Uri、可能根本不存在，
        // 旧式 getParcelableExtra 会抛 ClassCastException；而此处位于协程之外，
        // 一旦抛出且未捕获就会直接崩溃整个进程 —— 这正是 eaa282d 引入的
        // “通过外部打开/分享文件进入应用后崩溃”的根因。
        try {
            when (action) {
                Intent.ACTION_VIEW -> {
                    fileUri = intent.data
                }
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                    fileUri = extractStreamUri(intent)
                    fileName = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    fileContent = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
        } catch (t: Throwable) {
            // 外部数据异常时静默返回，绝不让应用因此崩溃
            return
        }

        if (fileUri == null && fileContent == null) return

        lifecycleScope.launch {
            try {
                val repository = (application as MiaodiApplication).repository

                // 读取文件内容
                val content = if (fileContent != null) {
                    fileContent
                } else if (fileUri != null) {
                    contentResolver.openInputStream(fileUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: ""
                } else {
                    ""
                }

                // 提取文件名（不含扩展名）作为文章标题
                val title = fileName ?: fileUri?.let { getFileNameFromUri(it) } ?: "外部导入文章"
                val cleanTitle = title.removeSuffix(".md").removeSuffix(".txt").removeSuffix(".markdown")

                // 等待默认书本就绪
                val defaultBook = viewModel.books.first { it.isNotEmpty() }.firstOrNull()
                if (defaultBook == null) {
                    Toast.makeText(this@MainActivity, "没有可用的书本", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 从数据库直接查询该书本的第一个章节（不依赖 UI 状态流）
                val chapters = repository.getChaptersByBookOnce(defaultBook.id)
                val defaultChapter = chapters.firstOrNull()
                if (defaultChapter == null) {
                    Toast.makeText(this@MainActivity, "该书本没有章节，请先创建章节", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val article = com.miaodi.note.data.model.Article(
                    chapterId = defaultChapter.id,
                    title = cleanTitle,
                    content = content,
                    isMarkdown = true,
                    isNew = true
                )
                val articleId = repository.insertArticle(article)

                Toast.makeText(this@MainActivity, "已导入 \"$cleanTitle\"", Toast.LENGTH_SHORT).show()

                // 打开编辑页
                val editIntent = Intent(this@MainActivity, EditActivity::class.java).apply {
                    putExtra("articleId", articleId)
                    putExtra("chapterId", defaultChapter.id)
                }
                startActivity(editIntent)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 安全地从外部 Intent 中提取待处理文件的 Uri。
     * 兼容 EXTRA_STREAM 为单个 Uri、Uri 列表（多选分享）以及 ClipData 的情形，
     * 并在任何类型不匹配/读取异常时返回 null，避免外部数据异常导致进程崩溃。
     */
    private fun extractStreamUri(intent: Intent): android.net.Uri? {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 推荐的类型安全重载，永不抛 ClassCastException
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
            } else {
                @Suppress("DEPRECATION")
                when (val extra = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)) {
                    is android.net.Uri -> return extra
                    is ArrayList<*> -> return extra.filterIsInstance<android.net.Uri>().firstOrNull()
                }
            }
            // 回退：部分应用（如“分享”多选）仅在 ClipData 中携带 Uri
            intent.clipData?.let { clip ->
                if (clip.itemCount > 0) return clip.getItemAt(0)?.uri
            }
        } catch (t: Throwable) {
            // 忽略：外部数据异常不应导致应用崩溃
        }
        return null
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String {
        var name = "外部导入文章"
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: name
                }
            }
        } else {
            name = uri.lastPathSegment ?: name
        }
        return name
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.mainFragment),
            binding.drawerLayout
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 同步侧边栏选中高亮：确保从 Todo/设置/关于返回“记录”时高亮也会刷新
            when (destination.id) {
                R.id.mainFragment -> binding.navigationView.setCheckedItem(R.id.nav_records)
                R.id.todoFragment -> binding.navigationView.setCheckedItem(R.id.nav_todo)
                R.id.settingsFragment -> binding.navigationView.setCheckedItem(R.id.nav_settings)
                R.id.aboutFragment -> binding.navigationView.setCheckedItem(R.id.nav_about)
            }
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
            // 仅在主界面保留 NavHost 与 AppBar 的滚动联动（内容位于工具栏下方）；
            // 其他页面隐藏全局 AppBar 后需移除该联动，避免 NavHost 仍按 AppBar 高度
            // 向下偏移而在页面顶部留下空白带（如“任务安排”页）。
            (binding.navHostFragment.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
                lp.behavior = if (isMain)
                    com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
                else
                    null
                binding.navHostFragment.layoutParams = lp
            }
        }
    }

    /**
     * 多选模式下隐藏右下角悬浮按钮（+ 及其子按钮），
     * 避免与多选操作栏（删除/取消）发生布局重叠。
     */
    fun setFabMenuVisibleForSelection(visible: Boolean) {
        if (visible) {
            binding.fabMain.visibility = View.VISIBLE
        } else {
            binding.fabMain.visibility = View.GONE
            binding.fabNewFolder.visibility = View.GONE
            binding.fabNewDoc.visibility = View.GONE
            isFabMenuOpen = false
            binding.fabMain.setImageResource(R.drawable.ic_add)
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
                    binding.navigationView.setCheckedItem(R.id.nav_records)
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
                kotlinx.coroutines.flow.combine(
                    viewModel.chapters,
                    viewModel.currentChapterId
                ) { chapters, chapterId -> chapters to chapterId }
                    .collect { (chapters, chapterId) ->
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
