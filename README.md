# SrPass — 本地密码保险库

完全离线的 Android 密码管理器。无网络权限、无后端、无账号体系，所有数据加密后仅存于本机私有目录。支持密码、笔记、银行卡、动态口令与隐藏空间五大存储类型，指纹解锁 + 恢复密钥双保险。

- 纯 Kotlin + Jetpack Compose，Material 3 设计
- 采用 KeePass 开放格式（KDBX 4.1），不私有格式锁定

---

## 功能特性

### 密码保险库
- 管理网站 / 应用的登录凭据：账号、密码、网址、备注与自定义字段
- 内置分类（登录 / 邮箱 / 金融 / 社交 / 工作等）与全文搜索
- 密码生成器：可配置长度、字符集与排除项
- 一键复制，60 秒后自动清空剪贴板

### 安全笔记
- 富文本编辑（加粗、高亮、列表等）
- 可插入图片、手绘涂鸦与录音，全部随库加密存储

### 银行卡
- 卡号、有效期、CVV、持卡人信息
- 网银账号等扩展信息

### 验证器（OTP）
- TOTP 动态口令，兼容标准算法（SHA-1 / SHA-256 / SHA-512）
- 支持相机扫码与手动输入导入

### 隐藏空间
- 独立密码保护，与主库完全隔离
- 隐藏账号 / 笔记 / 验证器，不在公开列表出现

### 解锁与恢复
- **指纹 / 面容解锁**：基于 Android Keystore 的 Class 3 强生物识别，每次使用需重新授权；新增指纹后密钥自动失效，防止后门
- **主密码**：Argon2id 派生，解锁延迟约 1 秒，暴力破解成本极高
- **恢复密钥**：创建保险库时生成 32 位一次性密钥，忘记主密码时的唯一逃生通道

### 自动防护
- 离开应用自动锁定，回到前台需重新解锁
- 禁止截图、录屏、投屏，不进入最近任务缩略图
- 无任何联网能力（见下文），不存在数据外发途径

## 安全设计

| 层面 | 做法 |
| --- | --- |
| 主密钥 | 256 位随机生成，仅驻留内存，锁定时立即擦除，永不落盘 |
| 密钥派生 | Argon2id（64 MiB / t=3 / p=4），参数随库保存 |
| 数据加密 | AES-256-GCM 认证加密，任何篡改直接拒绝解锁 |
| 文件格式 | KeePass KDBX 4.1，与主密钥双重加密 |
| 生物识别 | 密钥存于 Android Keystore，不可导出、不离开设备 |
| 备份文件 | 自带加密（含主密码 / 恢复密钥信封），无凭证无法打开 |
| 网络权限 | Manifest 不声明 INTERNET，系统层面禁止联网 |

数据保存在本机私有目录。**卸载应用或清除应用数据将导致密码无法恢复**，请定期导出 `.srpass` 备份。

## 技术栈

| 类别 | 组件 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin | 全部代码 |
| UI | Jetpack Compose + Material 3 | 声明式界面 |
| 架构 | MVVM（AndroidViewModel + StateFlow） | 自研导航状态机，未引入导航库 |
| 文件格式 | [kotpass](https://github.com/keemobile/kotpass) | KDBX 4.1 读写 |
| 密钥派生 | BouncyCastle | 仅使用 Argon2id 算法实现 |
| 生物识别 | androidx.biometric | Class 3 强生物识别 |
| 扫码 | CameraX + ZXing | OTP 二维码导入 |
| 平台 | minSdk 30 / targetSdk 37 | Android 11 ~ 17 |

## 构建

### 前置条件
- JDK 17+
- Android SDK（API 37 平台）
- Android Studio 或命令行 Gradle（项目自带 wrapper，无需另装）

### 编译

```bash
# 调试包（默认 debug 签名）
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk

# 发布包（R8 混淆 + 资源压缩）
./gradlew assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

### 发布签名（可选）

`app/build.gradle.kts` 的 release 签名从 `local.properties` 读取，字段名以 `kykfz01` 为前缀（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`）。不配置时请自行生成密钥库：

```bash
keytool -genkeypair -keystore my-release.keystore -storetype PKCS12 \
  -alias mykey -keyalg RSA -keysize 2048 -validity 10950
```

`local.properties` 与 `*.keystore` 均已被 `.gitignore` 忽略，请勿提交到仓库。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

> 覆盖安装须使用同一签名密钥，否则报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 目录结构

```
├── app/src/main/java/app/srpass/kykfz01/
│   ├── MainActivity.kt        # 入口、SAF 选择器、离开自动锁屏
│   ├── ui/                    # Compose 界面与 ViewModel
│   ├── crypto/                # 加密层：主密钥、信封、Argon2id、AES-GCM、恢复密钥
│   └── vault/                 # KDBX 封装与条目模型（密码 / 笔记 / 银行卡 / OTP）
├── THIRD-PARTY-NOTICES.md     # 第三方开源组件清单
├── LICENSE                    # Apache 2.0
└── .gitignore                 # 排除密钥库 / local.properties / 构建产物
```

## 常见问题

**Q: 数据会上传到服务器吗？**
不会。应用 Manifest 未声明 INTERNET 权限，系统层面无法发起任何网络请求。

**Q: 忘记主密码怎么办？**
使用创建保险库时生成的恢复密钥解锁。恢复密钥请妥善保管，丢失后无法找回。

**Q: 如何迁移到新手机？**
旧手机导出 `.srpass` 备份 → 新手机导入。备份自带加密信封，导入后用原主密码或恢复密钥解锁，指纹需重新启用。

**Q: 可以用 KeePass 打开备份文件吗？**
`.srpass` 是本应用的自包含备份格式（内含 KDBX 数据库 + 密钥信封），不能直接用其它 KeePass 客户端打开。

## 许可证

本项目以 [Apache License 2.0](LICENSE) 开源。第三方组件清单见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
