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
        window.decorView.setOnApplyWindowInsetsListener { _, insets -> insets }

        val repository = (application as MiaodiApplication).repository
        viewModel = androidx.lifecycle.ViewModelProvider(this, com.miaodi.note.ui.viewmodel.MainViewModel.Factory(repository))[com.miaodi.note.ui.viewmodel.MainViewModel::class.java]

        setupNavigation()
        setupToolbar()
        setupFab()
        setupDrawer()
        observeViewModel()
        setupBackPressed()
        applyManualNavSetting()
        // 仅在首次创建时处理启动 Intent；配置变更（如旋转）重建时不再重复处理，
        // 避免重复导入外部文档。
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

    // ==================== 外部 Markdown 文档导入 ====================
    // 处理从系统「打开方式 / 分享」进入本应用的外部 Markdown 文档：
    // 流式读取文件内容（支持大文件，读取在 IO 线程执行），导入并保存到默认书本。
    // 所有外部数据解析与读取均做容错处理，任何异常都不会导致应用崩溃。

    /** 导入外部文档时允许的最大字符数，防止极端大文件导致内存溢出（默认约 25MB 文本）。 */
    private val maxImportChars = 25_000_000

    private fun handleExternalFileIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val isView = action == Intent.ACTION_VIEW
        val isSend = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        if (!isView && !isSend) return

        // 解析外部 Uri —— 必须容错，任何异常都不应导致崩溃
        val uri: android.net.Uri? = try {
            extractExternalUri(intent)
        } catch (t: Throwable) {
            null
        }
        if (uri == null) return

        val displayName: String? = try {
            queryDisplayName(uri)
        } catch (t: Throwable) {
            null
        }
        val mimeType: String? = try {
            contentResolver.getType(uri)
        } catch (t: Throwable) {
            null
        }

        if (!isMarkdownLike(displayName, mimeType, uri)) {
            Toast.makeText(this, "仅支持导入 Markdown（.md）文档", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在导入文档…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val articleId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                importExternalMarkdown(uri, displayName)
            }
            if (articleId > 0) {
                Toast.makeText(this@MainActivity, "已保存到默认书本", Toast.LENGTH_SHORT).show()
                val article = viewModel.getArticleById(articleId)
                val editIntent = Intent(this@MainActivity, EditActivity::class.java).apply {
                    putExtra("articleId", articleId)
                    putExtra("chapterId", article?.chapterId ?: viewModel.currentChapterId.value)
                }
                startActivity(editIntent)
            } else {
                Toast.makeText(this@MainActivity, "导入失败：无法读取该文档", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 从外部 Intent 中安全提取待处理文件的 Uri。
     * 兼容 EXTRA_STREAM 为单个 Uri、Uri 列表（多选分享）以及 ClipData 的情形，
     * 并在任何类型不匹配/读取异常时返回 null，避免外部数据异常导致进程崩溃。
     */
    private fun extractExternalUri(intent: Intent): android.net.Uri? {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { return it }
            intent.clipData?.let { clip -> if (clip.itemCount > 0) return clip.getItemAt(0)?.uri }
            return null
        }
        // ACTION_SEND / ACTION_SEND_MULTIPLE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            when (val extra = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)) {
                is android.net.Uri -> return extra
                is ArrayList<*> -> extra.filterIsInstance<android.net.Uri>().firstOrNull()?.let { return it }
            }
        }
        // 回退：部分应用仅在 ClipData 中携带 Uri
        intent.clipData?.let { clip -> if (clip.itemCount > 0) return clip.getItemAt(0)?.uri }
        return null
    }

    /** 查询外部 Uri 的显示名（文件名）。 */
    private fun queryDisplayName(uri: android.net.Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        var name: String? = null
        try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        } catch (t: Throwable) {
            // 忽略：查询失败时回退到路径推断
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')
        return name
    }

    /** 判断外部文档是否为 Markdown（按扩展名或 MIME 类型）。 */
    private fun isMarkdownLike(name: String?, mime: String?, uri: android.net.Uri): Boolean {
        val n = (name ?: uri.lastPathSegment ?: "").lowercase()
        val isMdExt = n.endsWith(".md") || n.endsWith(".markdown") ||
            n.endsWith(".mdown") || n.endsWith(".mkd")
        val m = (mime ?: "").lowercase()
        val isMdMime = m == "text/markdown" || m == "text/x-markdown" ||
            m == "application/x-markdown" || m == "text/plain"
        return isMdExt || isMdMime
    }

    /**
     * 流式读取外部 Uri 的文本内容并写入默认书本，返回新建文章的 id（失败返回 -1）。
     * 读取在 IO 线程执行，采用缓冲流逐块读取，避免一次性载入导致大文件 OOM。
     */
    private suspend fun importExternalMarkdown(uri: android.net.Uri, displayName: String?): Long {
        val content = readTextFromUri(uri) ?: return -1L
        if (content.isEmpty()) return -1L

        val rawName = (displayName ?: uri.lastPathSegment ?: "").substringAfterLast('/')
        var title = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
        title = title.trim()
        if (title.isBlank()) {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
            title = "外部文档-$stamp"
        }
        return viewModel.importExternalMarkdown(title, content)
    }

    /** 以缓冲流方式读取文本内容；超过上限时截断，任何异常返回 null。 */
    private fun readTextFromUri(uri: android.net.Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    val buffer = CharArray(64 * 1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        sb.append(buffer, 0, read)
                        if (sb.length >= maxImportChars) break
                    }
                    sb.toString()
                }
            }
        } catch (t: Throwable) {
            null
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
