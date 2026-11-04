# AGENTS.md

本文件约束 AI 编码助手（大模型）在本仓库中的开发行为。开始任何任务前先阅读，冲突时以本文件为准。

## 1. 项目概要

- 平台：Android，单模块 `:app`
- 语言 / UI：Kotlin + Jetpack Compose
- 包名 / 命名空间：`com.CatMoeCircle.fuyutd`
- SDK：`minSdk 33`、`targetSdk 36`、`compileSdk 37.1`
- 源码目录：`app/src/main/java/com/CatMoeCircle/fuyutd/`

**当前构建栈（改动前先确认，不要凭记忆写版本号）**

| 项 | 版本 |
| --- | --- |
| Gradle Wrapper | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| material3 | 1.5.0-alpha28（显式 pin，见下） |
| miuix | 0.9.4（ui / icons / preference / blur / nav） |
| material-kolor | 5.0.1 |
| Java / JVM target | 21 |

## 2. 验证标准（最重要）

**本项目的正确性验证以「编译通过」为唯一标准。不要运行程序。**

- 改完代码后，只需执行编译命令确认无语法 / 类型 / 资源引用错误，即视为完成。
- 禁止运行、也无需尝试的「运行类」操作：
  - `./gradlew run`、启动模拟器 / 真机上的应用
  - `adb install` / `adb shell am start` / `adb logcat` 等设备交互
  - `./gradlew connectedAndroidTest`、`installDebug`、`installRelease`
  - 为「验证一下」而全量 `./gradlew build`（会连带打包、Lint、测试，耗时且无必要）
  - 单元测试 / 仪器测试的执行（除非用户明确要求跑测试）
- 允许且推荐的验证命令（Windows 使用 `gradlew.bat`）：

  ```bash
  # 主源码编译（日常改动后跑这个即可）
  ./gradlew :app:compileDebugKotlin

  # 涉及资源 / Manifest / 布局时追加
  ./gradlew :app:processDebugResources
  ```

- 只要求「通过编译」，因此：
  - 不要为了跑起来而在代码里加临时的调试日志、mock 数据、假入口。
  - 不要因为「没在设备上看到效果」而反复改动或重构。
  - 编译失败才继续修，编译通过即停止。
- **增量缓存会掩盖警告**：任务 `UP-TO-DATE` 时 `w:` 不会重现。确认「无警告」或改动涉及全局编译配置时，用
  `./gradlew :app:compileDebugKotlin --rerun-tasks`。
- 如果编译需要很久或卡住，不要反复重试；说明现象并交回用户判断。

## 3. 代码改动原则

- **最小改动**：只改与任务直接相关的行。不顺手重构、不改旁边代码的格式与注释、不动无关文件。
- **不做投机式设计**：不添加未被要求的功能、抽象层、配置项、"以后可能用到"的接口。
- **匹配现有风格**：Kotlin 使用 official code style（`kotlin.code.style=official`），Compose 遵循现有 `ui/theme` 的组织方式。
- **不删除既有代码**：发现未被使用的代码只提示，不擅自清理（除非该无用代码是你本次改动造成的）。
- 需求不明确、存在多种合理解释时，先提问确认，不要自行选定一种实现。
- **第三方代码 / API 的处理**：
  - 引入外部代码时，先改 `package` 与 `import` 前缀，再编译；不要顺手"优化"其算法、数值或注释。
  - 只保留实际被引用的部分；通用实现里的重载 / 未用分支不要全量搬入，否则会留下死代码。
    删之前用 `grep -rn` 确认零引用。
  - 依赖的第三方 API 在本项目版本中不存在（包被拆、类被删）时，不要伪造兼容层；
    先确认是"版本不对"还是"确实没有"，再决定升级依赖还是降级实现。

## 4. 依赖与构建配置

- 新增依赖必须写进 `gradle/libs.versions.toml`，再在 `app/build.gradle.kts` 中以 `libs.*` 别名引用；不要内联硬编码版本号。
- 不要升级 AGP / Kotlin / Compose BOM / SDK 版本，除非用户明确要求。
- 不要修改 `local.properties`、`gradle.properties` 中的 JVM 与路径配置。
- 不要改动 `settings.gradle.kts` 的仓库声明（已启用 `FAIL_ON_PROJECT_REPOS`）。

### 4.1 版本联动（务必先读）

- **AGP 上限由 IDE 决定**。用户当前 Android Studio 只支持到 AGP 9.2.1；升到 9.4.1 会报
  "incompatible version of the Android Gradle plugin"。要让新依赖编译通过，优先升 Kotlin / 依赖库，
  最后才考虑 AGP，且必须先问用户。
- **AGP 与 Gradle Wrapper 要成对**。改 AGP 时同步检查 `gradle/wrapper/gradle-wrapper.properties`，
  否则 IDE 仍报不匹配。
- **Kotlin 版本必须 ≥ 依赖库的 class 元数据版本**。miuix 0.9.4 的元数据是 Kotlin 2.4，
  用 Kotlin 2.2.10 编译会报
  `Module was compiled with an incompatible version of Kotlin ... expected version is 2.2.0`。
  报这个错时**不要**去加 `-Xskip-metadata-version-check` 强压，那只是把问题延后。
- **miuix / miuix-nav 的内联字节码需要 JVM 21**。`compileOptions` 与 `kotlin.compilerOptions.jvmTarget`
  必须同时是 21，只改一个会报
  `Cannot inline bytecode built with JVM target 21 into bytecode that is being built with JVM target 11`。
- **material3 必须显式 pin**。`ShortNavigationBar`、`SegmentedListItem`、`MaterialExpressiveTheme`
  等 Expressive API 只存在于 1.5.0-alphaXX 线；Compose BOM 2026.09.00 会解析到 1.4.0，
  导致 `Unresolved reference`。所以 `androidx-compose-material3` 用 `version.ref = "material3"` 覆盖 BOM。
- **miuix 0.7.x 是单包**，0.9.x 拆成 `miuix-ui` / `miuix-icons` / `miuix-preference` / `miuix-blur` / `miuix-nav`。
  0.7.x 没有 `preference` / `blur` / `highlight` / `sensor` 包，需要这些包时必须用 0.9.x。

## 5. 资源与清单

- 字符串、颜色、尺寸等放在 `app/src/main/res/values/` 下，不要在 Compose 代码中散落硬编码的用户可见文本。
- 图标放入 `res/drawable` 或 `res/mipmap-*`，文档类 XML 放入 `res/xml`。
- `AndroidManifest.xml` 中的权限、组件声明按需最小化添加。
- **预测性返回**需要 `android:enableOnBackInvokedCallback="true"`，只写代码不加这一行，系统不会派发手势。

## 6. UI 架构约定（本项目特有，改动前必读）

界面同时支持 **Miuix（米UI）** 与 **Material 3** 两套风格，由 `UiMode` 切换。新增页面必须成对提供实现。

- 分发点：`LocalUiMode`（`ui/UiMode.kt`）→ `FuyuTDTheme` 分派到 `MiuixFuyuTDTheme` / `MaterialFuyuTDTheme`。
- 页面命名：`XxxScreen.kt`（状态与动作装配）+ `XxxMiuix.kt` + `XxxMaterial.kt`。
- 主题开关放 `ui/theme/Theme.kt` 的 `AppSettings` + `Local*` CompositionLocal，不要各页面自己读 prefs。

### 6.1 miuix 的关键行为

- **`OverlayDialog` / `OverlayDropdownPreference` 不会自行渲染**，它们把内容提交给 **根 `Scaffold` 的 `popupHost`**。
  因此：
  - 页面若自己写了 `popupHost = { }`，**必须**确保外层有 miuix `Scaffold` 兜底，否则点击后弹窗"无反应"。
  - 全屏页面不要绕过根 Scaffold 直接顶替根内容，否则弹窗、下拉、BottomSheet 全部失效。
- **`NavigationBar` / `NavigationRail` 的 API 在 0.9.x 变了**：不再接收 `items` 列表，改为
  `RowScope` / `ColumnScope` 内逐个放 `NavigationBarItem` / `NavigationRailItem`。
- `Modifier.pagerGestureOverride(mode = CrossAxisInterceptor)` 必须配合
  `userScrollEnabled = false` + `pageNestedScrollConnection = PagerGestureNestedScrollConnection`，
  三者缺一会产生手势竞争。

### 6.2 导航

- 使用 miuix-nav 的 `NavDisplay`，不要用 `AnimatedContent` 手搓页面切换——`NavDisplay` 自带
  预测性返回、滑动手势返回与转场圆角，手搓会把这些全部弄丢。
- 用 `navBackStackOf()` 建栈而不是 `rememberNavBackStack()`：前者只建内存栈，
  **不要求路由类 `@Serializable`**，可避免为此引入 kotlinx-serialization 插件。
  代价是导航栈不跨进程恢复。
- 返回键遵循 `last-composed-enabled-first`：后注册的处理器先拿到返回事件。
  页面内的返回逻辑（如主页内回第一页）要在 Sub 页面之上注册。

## 7. 交付说明

- 汇报时说明：改了哪些文件、为什么改、编译是否通过。
- 不要声称「已在设备上验证」「已运行确认」——本项目不做运行验证。
- 若因环境原因无法编译（如 SDK 缺失），如实说明并给出下一步建议，不要绕过验证步骤假装成功。
- **不能运行的代码不要写"已完成"**。涉及视觉效果的改动，如实说明"仅通过编译验证，未在设备上确认"。

## 8. 禁止清单（速查）

- ❌ 启动应用 / 模拟器 / 真机调试
- ❌ `adb` 系列命令
- ❌ 全量 `build`、`connectedAndroidTest`、`lint`
- ❌ 为验证而写一次性调试代码
- ❌ 未经确认的重构、格式化、依赖升级
- ❌ 用 `-Xskip-metadata-version-check` 掩盖版本不匹配
- ❌ 绕过根 Scaffold 直接顶替全屏内容（会让 miuix 弹窗失效）
- ✅ 只做：改代码 → `./gradlew :app:compileDebugKotlin` → 编译通过即完成
