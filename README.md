# fuyuTD

> 基于 [Fuyu-Bot](https://github.com/CatMoeCircle/Fuyu-Bot) 内核的 Android 图形客户端。

fuyuTD 把 Fuyu-Bot 的 **JNI 运行时**（`libfuyubotjni.so` + TDLib）随 APK 一起分发，
在应用进程内跑起一个真正的 Telegram bot / user 会话；界面层提供 **Miuix（米 UI）**
与 **Material 3 Expressive** 两套完整风格，可随时切换。

本仓库只包含 Android 壳（Kotlin + Jetpack Compose），**不含内核二进制**。
内核来自上游 Fuyu-Bot，构建产物必须按下文放入指定目录，才算完成本项目的构建。

---

## 快速开始

```bash
# 1. 先把 Fuyu-Bot 内核产物放入指定目录（详见下文「构建前提」）
unzip -o fuyubot-v0.1.2-android-jni-arm64-v8a.zip \
  -d app/src/main/assets/fuyubot-kernel

# 2. 构建 APK
./gradlew :app:assembleRelease

# 3. 确认内核已被打进 APK
unzip -l app/build/outputs/apk/release/*.apk | grep assets/fuyubot-kernel
```

## 目录

- [快速开始](#快速开始)
- [特性](#特性)
- [架构](#架构)
- [内核来源与版本](#内核来源与版本)
- [构建前提：内核产物必须放入指定目录](#构建前提内核产物必须放入指定目录)
- [获取内核产物](#获取内核产物)
- [构建 Android 应用](#构建-android-应用)
- [首次使用](#首次使用)
- [升级内置内核](#升级内置内核)
- [常见问题](#常见问题)
- [项目结构](#项目结构)
- [开发约定](#开发约定)
- [许可与致谢](#许可与致谢)

---

## 特性

- **一个 App，两种账号**：Bot Token 与手机号 / 验证码 / 两步验证登录走同一套流程。
- **双风格 UI**：`UiMode` 一键切换 Miuix 与 Material 3；主题色、动态取色、模糊、浮动底栏、
  预测性返回、页面缩放均可单独配置。
- **首页控制台**：启动 / 停止 / 重启内核，展示运行时长、TDLib 授权状态与已加载插件数。
- **实时日志**：读取内核写在 `<root>/logs/fuyubot.log` 的日志，支持搜索、级别过滤、清空。
- **插件管理**：从 [插件商城索引](https://catmoecircle.github.io/FuyuBot-plugins/index.json)
  安装 / 启停 / 卸载插件，内核运行中改动会在重启后生效。
- **内置内核自动安装**：内核随 APK 打包，首次启动自动释放到 `filesDir/fuyubot-kernel/`；
  App 升级内核版本后会在下次启动自动重装，无需手动清数据。
- **应用内更新检查**：读取 GitHub Release 判断新版本，支持「今天不再提醒」。

---

## 架构

```text
┌───────────────────────────── App（本仓库） ─────────────────────────────┐
│  Compose UI（Miuix / Material 3）                                        │
│      │                                                                   │
│   ViewModel / Repository（Settings、Plugin、Log、Update）                │
│      │                                                                   │
│  FuyuBotRuntime ── Native（JNI 绑定，package com.catmoecircle.fuyubot…） │
│      │                    │                                              │
│  KernelManager             │  System.load(libtdjson.so → libfuyubotjni.so)│
│  assets/fuyubot-kernel ──► filesDir/fuyubot-kernel/                      │
└───────────────────────────┼──────────────────────────────────────────────┘
                            ▼
                Fuyu-Bot 内核（Rust）+ TDLib（C++）
                libfuyubotjni.so / libtdjson.so / plugins/*.so
```

- 内核**不是**可执行文件，也不走 `jniLibs`：Android 10+ 禁止执行应用私有目录下的二进制，
  但允许 `System.load()` 加载同目录的 `.so`。因此内核放在 `assets/`，启动时整体复制到
  `filesDir/fuyubot-kernel/` 再加载，不需要 root，也不需要额外权限（仅 `INTERNET`）。
- JNI 绑定是**运行期**的：内核在 `JNI_OnLoad` 中用 `RegisterNatives` 按类名 / 方法名绑定
  `com.catmoecircle.fuyubot.runtime.Native`。因此该绑定类**必须**被 R8 保留
  （见 `app/proguard-rules.pro`），否则第一次调用就会抛 `UnsatisfiedLinkError`。
- 保活、通知、唤醒锁全部由应用侧负责；内核只提供幂等的 `start` / 必须被调用的 `stop`
  与如实的 `onState` 回调。

---

## 内核来源与版本

| 项 | 值 |
| --- | --- |
| 上游仓库 | <https://github.com/CatMoeCircle/Fuyu-Bot> |
| 内置内核版本 | `0.1.2`（见 `data/update/UpdateChannel.kt` 的 `CURRENT_KERNEL_VERSION`） |
| JNI ABI | `3`（运行时校验，见 `Native.ABI_VERSION`） |
| TDLib | `1.8.69`（commit `0e88653dcbf4e44c73cb655a05b58cd749a91169`） |
| 目标 ABI | `arm64-v8a`（真机）/ `x86_64`（模拟器） |

上游 Android 交付物是一个**只有 `.so` 的 JNI 包**（不是 APK、也不是可执行文件）：

```text
fuyubot-<版本>-android-jni-<abi>.zip
├─ libfuyubotjni.so        ← 内核主库
├─ libtdjson.so            ← TDLib，必须与主库同 ABI
├─ libc++_shared.so        ← tdjson 的运行时依赖
├─ libcrypto.so / libssl.so
├─ plugins/*.so            ← 可选内置插件（/start、/help、/plugins）
├─ include/fuyubot_jni.h   ← C 接口头文件（可选）
└─ README.txt
```

---

## 构建前提：内核产物必须放入指定目录

> **本项目的构建以「内核产物已放入 `app/src/main/assets/fuyubot-kernel/`」为完成条件。**
> 该目录被 `.gitignore` 排除（内核另行分发、不入库），克隆仓库后它默认不存在。

Gradle 编译本身不会校验这个目录：缺了它仍然能编出 APK，但 APK 里没有内核，
**装到设备上首次启动会直接报「资产中缺少内核目录」**，项目等于没有构建完成。
所以每次完整构建前请先按下文准备好内核并放入指定目录。

### 指定目录

```text
app/src/main/assets/fuyubot-kernel/
```

### 必须包含的文件

| 文件 | 必需 | 说明 |
| --- | --- | --- |
| `libfuyubotjni.so` | ✅ | 内核主库，缺失时 `KernelManager` 安装失败 |
| `libtdjson.so` | ✅ | TDLib，必须与主库同 ABI |
| `libc++_shared.so` | ✅ | tdjson 的运行时依赖，缺了设备上会报 `library not found` |
| `libcrypto.so` | ✅ | tdjson 的运行时依赖（文件名以产物为准） |
| `libssl.so` | ✅ | tdjson 的运行时依赖（文件名以产物为准） |
| `plugins/fuyubot_plugin_start.so`<br>`plugins/fuyubot_plugin_help.so`<br>`plugins/fuyubot_plugin_manage.so` | 建议 | 内置命令插件；缺失时内核仍可启动，但没有 `/start`、`/help`、`/plugins` |
| `include/fuyubot_jni.h` | 可选 | C 接口契约，仅供查阅，运行时不读 |
| `README.txt` | 可选 | 上游包说明 |

> 上表按当前上游 Android 产物（`libc++` / OpenSSL 作为独立 `.so`）列出；
> 若拿到的是早期静态合并的 `tdjson`，则可能没有 `libc++_shared` / `libcrypto` / `libssl`，
> 此时以 `libtdjson.so` 的实际依赖为准。

### 目录树

```text
app/src/main/assets/fuyubot-kernel/
├── libfuyubotjni.so
├── libtdjson.so
├── libc++_shared.so
├── libcrypto.so
├── libssl.so
├── plugins/
│   ├── fuyubot_plugin_start.so
│   ├── fuyubot_plugin_help.so
│   └── fuyubot_plugin_manage.so
├── include/
│   └── fuyubot_jni.h
└── README.txt
```

### 放入指定目录

内核 JNI 包解压后**没有外层目录**，直接解压到目标目录即可。

```bash
# Git Bash / WSL / Linux / macOS
mkdir -p app/src/main/assets/fuyubot-kernel
unzip -o fuyubot-v0.1.2-android-jni-arm64-v8a.zip \
  -d app/src/main/assets/fuyubot-kernel
```

```powershell
# PowerShell
New-Item -ItemType Directory -Force app\src\main\assets\fuyubot-kernel | Out-Null
Expand-Archive -Force fuyubot-v0.1.2-android-jni-arm64-v8a.zip `
  app\src\main\assets\fuyubot-kernel
```

放入后先自检，再开始编译：

```bash
test -f app/src/main/assets/fuyubot-kernel/libfuyubotjni.so \
  && test -f app/src/main/assets/fuyubot-kernel/libtdjson.so \
  && echo "kernel assets OK"
```

> **一次只能放一个 ABI。** 两个 ABI 的 `.so` 文件名完全相同，不能同时放在同一目录；
> 真机用 `arm64-v8a`，模拟器用 `x86_64`。

---

## 获取内核产物

### 方式一：直接用上游构建好的 JNI 包（推荐）

上游的 JNI 包由 `release-bot` 工作流产出，命名固定为
`fuyubot-<版本>-android-jni-<abi>.zip`（`<abi>` = `arm64-v8a` / `x86_64`），
可在对应 Release 的 Assets 里找到；如果该版本 Release 尚未附上，可以手动跑一次工作流：

1. 打开 <https://github.com/CatMoeCircle/Fuyu-Bot/actions/workflows/release-bot.yml>；
2. 点 **Run workflow**，选 `main` 分支执行（分支上运行只会构建、不会发布）；
3. 跑完后在该次运行页面底部下载 **Artifacts** 中的
   `fuyubot-android-aarch64`（模拟器选 `fuyubot-android-x86_64`）；
4. 解压得到的 `dist/fuyubot-<版本>-android-jni-arm64-v8a.zip` 就是内核包，
   按上一节放入 `app/src/main/assets/fuyubot-kernel/`。

> 该工作流需要 TDLib 1.8.69 的 Android 产物；优先取 `TDLib-v1.8.69` Release 上的
> `TDLib-android-aarch64`，取不到会回退到最近一次成功的 `build-tdlib` 运行。

### 方式二：本地从源码构建

适用于要改内核代码、或需要自定义 ABI / 包名的场景。

#### 环境要求

| 项 | 要求 |
| --- | --- |
| 宿主系统 | Linux / macOS（Windows 请用 WSL2 或 GitHub Actions；上游脚本拒绝在 MINGW 下交叉编译） |
| Rust | 1.99+（Edition 2024），`rustup target add aarch64-linux-android` |
| Android NDK | `28.2.13676358`（r28c）；**WSL 里要装 Linux 版 NDK**，Windows 版 NDK 不含 `linux-x86_64` 工具链 |
| 其他 | Python 3、`zip`、`cmake`、`ninja`、`gperf`、`g++`、`perl`（编 TDLib 用） |
| TDLib | 1.8.69 / `0e88653dcbf4e44c73cb655a05b58cd749a91169` |

#### 步骤

```bash
# 1) 克隆上游内核
git clone https://github.com/CatMoeCircle/Fuyu-Bot.git
cd Fuyu-Bot
chmod +x .github/scripts/*.sh
rustup target add aarch64-linux-android

# 2) 准备 TDLib 的 Android 产物（目录需含 libtdjson.so 及其依赖 .so）
#    方式 A：从 TDLib-v1.8.69 Release 下载 Android 资产，解压到
#            .tdlib-cache/1.8.69/android-aarch64/
#    方式 B：本地交叉编译（首次需先做一次 host 构建，耗时较长）
git clone https://github.com/tdlib/td.git td-src
git -C td-src fetch --depth 1 origin 0e88653dcbf4e44c73cb655a05b58cd749a91169
git -C td-src checkout --detach 0e88653dcbf4e44c73cb655a05b58cd749a91169
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
export ANDROID_API_LEVEL=21
.github/scripts/build-tdlib-android.sh "$PWD/td-src" "$PWD/out" arm64-v8a
mkdir -p .tdlib-cache/1.8.69/android-aarch64
cp -a out/tdjson/. .tdlib-cache/1.8.69/android-aarch64/

# 3) 交叉编译 JNI 运行时与三个内置插件
toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$toolchain/bin/aarch64-linux-android21-clang"
export CC_aarch64_linux_android="$toolchain/bin/aarch64-linux-android21-clang"
export AR_aarch64_linux_android="$toolchain/bin/llvm-ar"
# Android 15 的 16KB 页设备要求 PT_LOAD 对齐 >= 0x4000
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
export FUYUBOT_TDLIB_VENDORED_CACHE="$PWD/.tdlib-cache"
cargo build --release --target aarch64-linux-android \
  -p fuyubot-android \
  -p fuyubot-plugin-start -p fuyubot-plugin-help -p fuyubot-plugin-manage

# 4) 打包（与 CI 同一脚本，产物结构即上文的 JNI 包）
.github/scripts/package-fuyubot-jni.sh \
  aarch64-linux-android arm64-v8a \
  1.8.69 0e88653dcbf4e44c73cb655a05b58cd749a91169 \
  dist "$PWD/.tdlib-cache/1.8.69/android-aarch64" \
  fuyubot-v0.1.2-android-jni-arm64-v8a
# → dist/fuyubot-v0.1.2-android-jni-arm64-v8a.zip
```

打包脚本会自动校验：ELF 类型必须是共享库、机器码与架构一致、16KB 页对齐、
导出符号齐全、JNI 绑定类名存在，以及 `DT_NEEDED` 只依赖 bionic 的系统库。

> 默认绑定类名就是本项目的 `com.catmoecircle.fuyubot.runtime.Native`。
> 若用 `FUYUBOT_JNI_CLASS` 改过绑定类名，App 侧的包名也必须同步改，
> 否则运行期绑定失败。

最后把产物放进指定目录：

```bash
unzip -o dist/fuyubot-v0.1.2-android-jni-arm64-v8a.zip \
  -d /path/to/fuyuTD/app/src/main/assets/fuyubot-kernel
```

---

## 构建 Android 应用

### 环境

| 项 | 版本 |
| --- | --- |
| JDK | 21 |
| Gradle Wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1（需 Android Studio 支持的上限版本，不要随手升） |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| material3 | 1.5.0-alpha28（显式 pin，见 `gradle/libs.versions.toml`） |
| miuix | 0.9.4 |
| compileSdk / targetSdk / minSdk | 37.1 / 36 / 33 |

`local.properties` 里的 `sdk.dir` 需指向本机 Android SDK，该文件不入库。

### 命令

```bash
# Windows
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleRelease

# Linux / macOS
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleRelease
```

- 日常改动的验证标准是 `:app:compileDebugKotlin` 通过（详见 `AGENTS.md`）。
- `assembleRelease` 会开启 R8 代码裁剪与资源裁剪；产物默认未签名，
  需要自行配置签名后再分发。
- 产物位置：`app/build/outputs/apk/release/`。

### 构建后自检

确认内核确实进了 APK：

```bash
unzip -l app/build/outputs/apk/release/*.apk \
  | grep 'assets/fuyubot-kernel'
```

应能看到 `assets/fuyubot-kernel/libfuyubotjni.so`、`libtdjson.so`、`plugins/*.so` 等条目。
看不到就说明内核没有放到指定目录，需要回到上一节重新放入后重新构建。

---

## 首次使用

1. 安装并打开 App。首次启动会把 `assets/fuyubot-kernel/` 复制到
   `filesDir/fuyubot-kernel/`（约 48 MB），首页启动按钮上显示安装进度。
2. 在配置弹窗中填写 Telegram 的 `api_id` / `api_hash`
   （在 <https://my.telegram.org> 申请）；想跑 Bot 就再填 `bot_token`，
   留空则走手机号 / 验证码 / 两步验证登录。
3. 点击首页的启动按钮。登录问题会以弹窗形式出现，按提示回答即可；
   扫码登录（其它设备确认）当前版本只展示链接、不支持在本机确认。
4. 登录状态由 TDLib 自己落盘，下次启动直接恢复，无需重复登录。

内核运行日志位于 App 私有目录 `filesDir/fuyubot-kernel/logs/fuyubot.log`，
应用内的「日志」页读的就是它。

---

## 升级内置内核

1. 用新版本的内核包整体替换 `app/src/main/assets/fuyubot-kernel/` 下的文件；
2. 同步修改 `data/update/UpdateChannel.kt` 中的 `CURRENT_KERNEL_VERSION`
   （以及需要时的 `Native.ABI_VERSION` 与 `proguard-rules.pro`）；
3. 重新构建 APK。

App 启动时会比较已安装版本与 `CURRENT_KERNEL_VERSION`：版本落后则自动重装内核，
版本一致则跳过。开发调试时若改了内核但版本号没变，需要清除 App 数据或重装应用才会重装内核。

---

## 常见问题

**Q：编译通过了，但安装后启动提示「资产中缺少内核目录」？**
内核没有放进 `app/src/main/assets/fuyubot-kernel/`。按[构建前提](#构建前提内核产物必须放入指定目录)
放入 `.so` 文件后重新构建。

**Q：`java.lang.UnsatisfiedLinkError` / `dlopen failed: library ... not found`？**
依次检查：内核文件是否齐全（尤其 `libc++_shared.so`、`libcrypto.so`、`libssl.so`）；
ABI 是否与设备一致；Release 包是否保留了 `Native` 绑定类（见 `app/proguard-rules.pro`）。

**Q：Android 15 / 16KB 页设备上加载内核崩溃？**
内核 `.so` 必须按 16KB 页对齐构建。用上游 CI 产物，或本地构建时加
`RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"`（TDLib 侧由 NDK 28 + 编译选项保证）。

**Q：插件装上后没有生效？**
插件在内核启动时加载。运行中安装 / 启停插件后需要重启内核；停用插件是把文件名改成
`<id>.so.disable`，内核按 `.so` 后缀扫描时自然跳过。

**Q：能同时支持 `arm64-v8a` 和 `x86_64` 吗？**
可以出两个 APK，或在构建前替换 `assets/fuyubot-kernel/` 的内容；
但同一份 assets 里不能同时放两套（文件名冲突）。

**Q：为什么不把内核放进 `jniLibs/`？**
本项目把内核当作**可替换的数据**：内置在 assets 里、启动时复制到 `filesDir`，
这样升级内核不需要重新发包，也避免 `jniLibs` 与 `extractNativeLibs` 的平台差异。
详见 `KernelManager` 的注释。

---

## 项目结构

```text
fuyuTD/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro          # 保留 JNI 绑定类
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/fuyubot-kernel/  # ← 内核产物放这里（不入库）
│       ├── java/com/CatMoeCircle/
│       │   ├── fuyubot/runtime/    # JNI 绑定（类名/签名不可改）
│       │   └── fuyutd/
│       │       ├── data/           # 配置、内核、插件、日志、更新
│       │       └── ui/             # 主题、导航、各页面（Miuix + Material 成对）
│       └── res/
├── docs/                           # 预留文档目录
├── gradle/
│   ├── libs.versions.toml          # 依赖版本统一入口
│   └── wrapper/
├── AGENTS.md                       # 仓库开发约束（AI / 协作者先读）
├── LICENSE
└── README.md
```

---

## 开发约定

- 本项目以**编译通过**作为正确性标准：改完代码执行
  `./gradlew :app:compileDebugKotlin`（Windows 用 `.\gradlew.bat`）即可，
  不需要也不要在设备上运行、调试或跑测试。
- 新增依赖先写进 `gradle/libs.versions.toml`，再用 `libs.*` 别名引用。
- 不要升级 AGP / Kotlin / Compose BOM / SDK，版本联动关系见 `AGENTS.md` 第 4 节。
- 新增页面必须成对提供 `XxxMiuix.kt` 与 `XxxMaterial.kt` 两套实现。
- 更完整的架构约定、miuix 行为、导航规则与禁止清单见 [`AGENTS.md`](AGENTS.md)。

---

## 许可与致谢

- 本项目代码采用 **GNU AGPL-3.0**，见 [`LICENSE`](LICENSE)。
- 内核 [Fuyu-Bot](https://github.com/CatMoeCircle/Fuyu-Bot) 由 CatMoeCircle 开发，
  采用 MIT OR Apache-2.0。
- 界面参考：[KernelSU](https://github.com/tiann/KernelSU)——底部导航的分页布局、
  主页状态卡片与日志页的交互方式参考了它的设计。
- 感谢 [TDLib](https://github.com/tdlib/td)、[miuix](https://github.com/compose-miuix-ui/miuix)、
  [material-kolor](https://github.com/jordond/materialkolor) 等开源项目。
