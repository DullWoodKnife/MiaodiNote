# 喵滴笔记（MiaodiNote）· 自定义安卓版

参照「喵滴 app」交互设计实现的本地笔记应用，**已去除注册登录、会员与云服务模块**，所有数据仅存本地（Room 数据库 + 文件导出）。

## 功能总览

| 模块 | 说明 |
| --- | --- |
| 启动页 | 猫咪 Logo 居中，2 秒后进入主界面；支持 PIN 解锁（设置中开启） |
| 主界面 | 书/章节/文章三级结构，卡片式选择器，文章多选批量删除，搜索实时过滤 |
| 编辑器 | 标题 + 正文 + 字数统计；底部快捷工具栏（猫咪/爪子/方向键/复制/撤销/重做）；MD5 加密/解密；真实 PDF / PNG 图片 / Markdown / HTML / TXT 导出 |
| Markdown 预览 | 内置轻量渲染器（标题/列表/代码块/链接/引用），支持默认/简约/夜间三种样式 |
| Todo 页 | 时间选择、每日提醒、完成切换 |
| 剪贴板记录 | 前台服务监听剪贴板，复制内容自动入库；支持「剪贴板导入询问」与「通知栏快捷记录」 |
| 设置页 | 通用/显示/编辑器/安全/备份/应用更新 六组设置（见下） |
| 关于页 | 版本、开发者、隐私政策等展示 |

## 设置项（全部真实生效，无需注册/登录）

- **通用**：剪贴板记录、剪贴板导入询问、通知栏快捷记录（三开关任一开启即启动前台服务，需 Android 13+ 通知权限）
- **显示**：编辑页顶部栏高斯模糊、手动管理导航栏（隐藏/显示系统导航栏）、语言（跟随系统/简体中文）、文章列表显示方式（单列列表/两列卡片）
- **编辑器**：悬浮按钮模式（展开/收起）、新建文章编辑器模式（普通/Markdown）、光标设置（5 种颜色）、自定义字体（默认/无衬线/衬线/等宽）、自定义 Markdown 样式（默认/简约/夜间）、优先预览文章、快捷栏显示设置
- **安全**：PIN 解锁（4-6 位数字，启动时校验；关闭或先关再开可修改）、文字小板提示
- **备份**：恢复数据（JSON 备份还原，自动重建外键关系）、导入文件（md/txt → 自动建默认书/章节）、导出到本地（JSON 全量备份）
- **应用更新**：自动更新开关、检查更新（显示当前版本号）

> 视频中出现的「高级会员」「云同步」「图床」「服务器地区」等依赖账号/云端的功能按需求不做实现。

## 构建

环境要求：JDK 17+、Android SDK（`compileSdk 35`）、网络（首次构建下载 Gradle 8.7 与依赖）。

```bash
# 1. 在 local.properties 或环境变量中指定 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties   # 或 export ANDROID_HOME=/path/to/android-sdk

# 2. 构建 Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 3. 打包正式版（需配置签名，见 SIGNING_SETUP.md）
./gradlew assembleRelease
```

## 技术栈

- Kotlin 2.0、AndroidX、Jetpack Navigation（SafeArgs）、ViewBinding
- Room（KSP）+ Flow：书/章节/文章/Todo/剪贴板记录五张表，含迁移脚本
- Android `PdfDocument` 渲染 A4 PDF、`Canvas + StaticLayout` 渲染长图 PNG
- 前台服务 `ClipboardMonitorService`：剪贴板监听 + 通知栏快捷入口（`specialUse` 类型）

## 隐私说明

应用不包含账号体系、不上传任何数据；导出/导入文件由用户通过系统文件选择器自行管理。