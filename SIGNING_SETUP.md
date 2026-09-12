# 签名配置与自动构建说明

## 一、签名配置（重要：避免覆盖安装失败）

每次 APK 发布必须使用**相同的签名密钥**，否则新版本无法覆盖安装旧版本。请按以下步骤配置：

### 1. 生成签名密钥（如已有可跳过）

在项目根目录执行：

```bash
keytool -genkey -v -keystore release.keystore -alias miaodi -keyalg RSA -keysize 2048 -validity 10000
```

按提示填写密码和组织信息，完成后会生成 `release.keystore` 文件。

### 2. 将密钥上传到 GitHub Secrets

进入 GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**，依次添加以下 Secrets：

| Secret 名称 | 值 |
|---|---|
| `KEYSTORE_BASE64` | 将 release.keystore 转为 base64：`base64 -w 0 release.keystore` 的输出 |
| `KEYSTORE_PASSWORD` | 你设置的 keystore 密码 |
| `KEY_ALIAS` | 密钥别名，如 `miaodi` |
| `KEY_PASSWORD` | 密钥密码（通常与 keystore 密码相同） |

> **注意**：`release.keystore` 文件本身不要提交到 Git 仓库！已加入 `.gitignore` 保护。

### 3. 本地开发构建签名

本地开发不需要签名，构建 debug APK 即可。如需本地构建 release 版，在 `local.properties` 中配置：

```
signing.keystore.path=../release.keystore
signing.keystore.password=your_password
signing.key.alias=miaodi
signing.key.password=your_password
```

---

## 二、自动构建说明

### 触发方式

每次执行以下操作时，GitHub Actions 会自动构建：
- push 代码到 `main` 或 `master` 分支
- 向 `main` 或 `master` 分支发起 Pull Request
- 手动触发：仓库页面 → Actions → Android Build → Run workflow

### 版本号自动递增

- **versionCode**：自动使用 GitHub Actions 的 `run_number`（从 1 开始，每次构建 +1）
- **versionName**：格式为 `1.0.{versionCode}`，如 `1.0.42`

### 构建产物

构建完成后，APK 会以 Artifact 形式上传，可在 Actions 运行页面 → Artifacts 中下载。

### 签名一致性

由于使用固定的 `release.keystore` 密钥，所有 CI 构建的 APK 签名完全一致，确保用户可以正常覆盖安装升级，不会遇到"签名不一致"的安装失败。
