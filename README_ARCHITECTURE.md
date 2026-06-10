# BookNext 阅读器 — 架构说明书

## 项目概览

BookNext 是一个 Android 电子书阅读器，基于 Kotlin + Jetpack Compose + Hilt + Room 构建。
支持 EPUB/TXT/PDF/DOC(X)/MOBI/AZW3/MD/HTML 多格式阅读，集成 TTS 朗读、云端同步、在线翻译、
元数据补全等功能。

- **包名**: `com.booknext.app`
- **targetSdk**: 35, **minSdk**: 26
- **架构**: MVVM（但实际大量业务逻辑散落在 Repository 和 UI 层）

---

## 1. 阅读格式与底层引擎

| 格式 | 引擎 | 原理 |
|------|------|------|
| **EPUB** | **Readium 3.1.2** | 使用 Readium Kotlin Toolkit 的 `PublicationOpener` 解析 EPUB 文件为 `Publication` 对象，然后通过 `EpubNavigatorFragment`（基于 WebView）渲染。Readium 管理目录（TOC）、分页、CSS 控制。BookNext 通过 JS 注入实现文字选择检测和搜索高亮。 |
| **TXT** | **自实现 + 可选转换** | 用 `EncodingDetector` 自动检测文件编码后读为行列表，在 Compose `LazyColumn` 中逐行渲染为 `TextView`。**同时会尝试用 `TxtToEpubConverter` 将 TXT 自动转为 EPUB**，成功则走 Readium 渲染，失败则回退到纯文本模式。 |
| **PDF** | **Android 原生 PdfRenderer** | 使用 `android.graphics.pdf.PdfRenderer` 将 PDF 页面渲染为 `Bitmap`，在 Compose 中显示图片。支持单页模式和滚动模式。每页渲染为 1080px 宽的位图。 |
| **DOC/DOCX** | **Apache POI** | 使用 `XWPFDocument` 读取 Word 文档，提取所有段落的纯文本，保存为临时 TXT 文件，然后用 TXT 渲染器展示。 |
| **MOBI/AZW3** | **服务端转换** | 调用服务端 API `GET /api/convert/{id}` 在服务器端转为 EPUB，下载转换后的文件后走 Readium 渲染。 |
| **MD** | **自实现** | 同 TXT 流程（读取为行列表后 LazyColumn 渲染），但 100% 走 EPUB 转换路径（必走 `TxtToEpubConverter`）。Markdown 本身不做任何语法解析，全部当纯文本处理。 |
| **HTML/HTM** | **WebView** | 直接用 `WebView.loadDataWithBaseURL()` 加载 HTML 内容，注入 CSS 控制暗色模式和字体大小。 |

### 关键依赖

```kotlin
// Readium 3.1.2 — EPUB 核心引擎
org.readium.kotlin-toolkit:readium-shared:3.1.2
org.readium.kotlin-toolkit:readium-streamer:3.1.2
org.readium.kotlin-toolkit:readium-navigator:3.1.2

// Apache POI — Word 解析
org.apache.poi:poi-ooxml:5.2.5
org.apache.xmlbeans:xmlbeans:5.2.0

// 其他
io.coil-kt:coil-compose          // 图片加载
io.noties.markwon:core            // 声明但未实际用于 Markdown 渲染
androidx.datastore:datastore-preferences  // 偏好存储
```

---

## 2. 架构分层（实际 vs 理想）

### 当前架构

```
MainActivity (手动路由)
  ├── BookshelfScreen / RecentScreen / CloudScreen / LocalScreen ...
  ├── ReaderScreen
  │     ├── EpubReaderScreen  ── Readium EpubNavigatorFragment (WebView)
  │     ├── TxtReaderScreen   ── LazyColumn + TextView per line
  │     ├── PdfReaderScreen   ── PdfRenderer → Bitmap
  │     └── HtmlReaderScreen  ── WebView
  │     └── ReaderToolbarOverlay (1179 行巨无霸组件)
  └── SettingsScreen / QuotesScreen / StatsScreen ...

ReaderViewModel (296 行, 60+ 偏好 StateFlow)
  ├── BookRepository (343 行, DAO/同步/转换/元数据/封面)
  ├── AnnotationRepository
  ├── ReadingSessionRepository
  ├── TtsController @Singleton
  ├── AutoScrollController @Singleton
  └── UserPreferences @Singleton (上帝类, 397 行)
```

**关键问题**：

- **无 NavHost**：`MainActivity.kt` 用 7 个布尔标志 + `BackHandler` when 块驱动导航
- **空 Domain 层**：`domain/model/` 和 `domain/usecase/` 完全空置
- **Repository 职责过载**：`BookRepository` 同时处理 DAO CRUD、云同步、格式转换、封面提取、元数据补全
- **无接口**：所有 Repository 和 UserPreferences 都是具体类，无 interface

### 理想架构

```
UI (Composable) → ViewModel → UseCase (空) → Repository → DataSource (Room/API/File)
```

---

## 3. 核心数据流

### 3.1 打开书籍

```
ReaderScreen(bookId)
  └─ ReaderViewModel.loadBook(bookId)
       └─ BookRepository.loadBook(bookId)
            ├─ 查 Room → BookEntity
            ├─ 检查本地文件是否存在
            ├─ 不存在则从服务器流式下载 (api.streamBook)
            └─ prepareFile():
                 ├─ MOBI/AZW3 → 调服务端 API 转换为 EPUB
                 ├─ DOC/DOCX → Apache POI 提取为 TXT
                 ├─ TXT → TxtToEpubConverter 转为 EPUB（可选）
                 └─ 其他格式 → 原样返回
```

### 3.2 阅读器 UI 路由

```
ReaderScreen → 根据 format 分发:
  ├─ "epub" → EpubReaderScreen
  ├─ "txt" | "mobi" | "azw3" → TxtReaderScreen
  ├─ "docx" | "doc" → TxtReaderScreen（经 POI 转换）
  ├─ "md" → TxtReaderScreen
  ├─ "pdf" → PdfReaderScreen
  ├─ "html" | "htm" → HtmlReaderScreen
  └─ 其他 → "暂不支持"
```

### 3.3 进度保存

```
EPUB 用户翻页 → EpubNavigatorFragment.PaginationListener.onPageChanged()
  → 回调 onProgressChange(chapterIndex)
  → ReaderViewModel.savePageProgress(page)
  → BookRepository.updateProgressNumeric(bookId, page, timestamp)
  → BookDao 更新 Room

TXT 用户滚动 → LazyColumn.firstVisibleItemIndex 变化
  → LaunchedEffect → onProgressChange(index)
  → 同上路径

onCleared 时 → GlobalScope.launch(NonCancellable) 持久化阅读会话
```

### 3.4 偏好系统

```
UserPreferences @Singleton
  └─ DataStore<Preferences> ("booknext_prefs")
  └─ 50+ 个 Preferences Key
  └─ 50+ 个 Flow getter
  └─ 50+ 个 suspend setter

ReaderViewModel 通过 stateIn() 将其中 ~60 个转为 StateFlow
ReaderScreen collect 这些 StateFlow
ReaderToolbarOverlay 统一写入
```

---

## 4. 阅读功能清单

### 4.1 视觉定制
| 功能 | 实现 |
|------|------|
| 字体大小 | 11-28sp，ReaderToolbar +/- 按钮 |
| 字体族 | serif/sans-serif/monospace/cursive/自定义字体文件 |
| 行间距 | 默认 1.8，可配置 |
| 背景颜色 | 9 种预设（护眼绿/羊皮纸/深蓝灰等）+ 自定义 |
| 暗色模式 | 布尔开关，EPUB 走色覆盖层（非 Readium Theme），TXT 切背景色 |
| 蓝光护眼 | `Color(1f, 0.7f, 0.4f, alpha)` 覆盖层 |
| 屏幕方向 | auto/portrait/landscape/reverse |
| 亮度 | 系统跟随/手动滑动 |
| 状态栏/导航栏 | 可隐藏（DisposableEffect 控制 insets） |
| 进度条 | 底部可滑动进度条（默认隐藏） |

### 4.2 操作控制
| 功能 | 实现 |
|------|------|
| 三区点击 | 左→上一页 / 中→切换UI / 右→下一页 |
| 九区手势 | 可自定义每个区域的点击动作 |
| 音量键翻页 | 可配置 |
| 滑动手势 | 上/下/左/右 四方向可自定义 |
| 长按 | 弹出文字选择菜单 |
| 捏合缩放 | EPUB 禁用 / TXT 不支持 |

### 4.3 阅读工具
| 功能 | 实现方式 |
|------|---------|
| **目录 (TOC)** | EPUB: Readium 的 tableOfContents (Link 树型) / TXT: 正则匹配"第X章"识别 |
| **书签** | DataStore 存储 `bookmarks_{bookId}` → 逗号分隔页码列表 |
| **搜索** | EPUB: 后台遍历每个章节提取文本做全库搜索 / TXT: `lines.contains()` |
| **替换** | EPUB: 不支持 / TXT: 正则替换 |
| **文本选择** | EPUB: JS 轮询 `window.getSelection()` / TXT: `TextView.setCustomSelectionActionModeCallback()` |
| **高亮** | EPUB: 不支持 / TXT: SpannableString + BackgroundColorSpan（存到 Room AnnotationEntity） |
| **笔记** | 选中文本 → AlertDialog → 保存到 AnnotationEntity |
| **翻译** | 调用 translate.googleapis.com（Google 免费接口/百度/有道），在 TranslationDialog 展示 |
| **词典** | 发送 `ACTION_PROCESS_TEXT` Intent，调用系统已安装词典应用 |
| **截图设封面** | 截取 decorView 为 Bitmap，裁剪状态栏/导航栏后保存为封面 |

### 4.4 TTS 朗读
| 方面 | 详情 |
|------|------|
| **双引擎** | 云端（微软 Azure TTS）/ 本地（系统 TextToSpeech） |
| **云端引擎** | 调用自部署的 `POST /api/tts` → 返回 MP3 字节 → MediaPlayer 播放 |
| **云端音色** | 6 种：晓晓/云扬/晓伊/云希/晓涵/Jenny |
| **云端参数** | 语速 (-50%~+50%) / 音调 (-20Hz~+20Hz) |
| **本地引擎** | 系统 TextToSpeech，自动尝试系统首选/讯飞/百度引擎 |
| **实现问题** | `TtsController @Singleton` 持有 Activity 引用，有内存泄漏风险；`openTtsSettings()` 空 catch 静默吞异常 |

---

## 5. 服务端 API

BookNext 有自部署的后端服务，`ApiClient` 根据 DataStore 中存储的 `serverUrl` + `apiKey` 动态创建 Retrofit 实例（Bearer Token 认证）。

| API | 用途 |
|-----|------|
| `POST /api/auth` | 登录认证 |
| `GET /api/health` | 健康检查 |
| `GET /api/books` | 分页列出书籍 |
| `GET /api/books/{id}` | 单本书详情 |
| `PATCH /api/books/{id}` | 更新进度/分类 |
| `GET /api/stream/{id}` | 流式下载书籍文件 |
| `POST /api/upload` | 上传书籍 |
| `DELETE /api/books/{id}` | 删除书籍 |
| `GET /api/convert/{id}` | 服务端格式转换 |
| `POST /api/tts` | 云 TTS 合成 |

---

## 6. 数据库设计

**Room 数据库** — 4 个 DAO / 3 个 Entity：

### BookEntity
| 字段 | 说明 |
|------|------|
| bookId (PK) | 书籍 ID |
| title/author | 书籍信息 |
| format | epub/txt/pdf/docx/md/html/mobi/azw3 |
| filePath | 本地文件路径 |
| fileSize | 文件大小 |
| readingPercent | 阅读进度百分比 |
| progress | 进度 JSON（EPUB 用 locator）或页码 |
| lastReadAt | 最后阅读时间 |
| totalReadingSeconds | 总阅读时长 |
| isFavorite | 收藏 |
| isFinished | 读完 |
| category/coverPath | 分类/封面路径 |
| hasCover | 是否有封面 |

### AnnotationEntity
- 支持类型: highlight / note
- 存储选中文本、颜色、笔记内容、定位信息

### ReadingSessionEntity
- 每次打开书籍到关闭的逐次记录
- startTime / durationSeconds / progressPercent

---

## 7. 已知架构问题（对应之前的评审）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | 手动路由（7 布尔标志） | `MainActivity.kt` | 无深层链接、无状态保存 |
| 2 | ReaderScreen 参数爆炸 ~55 回调 | `ReaderScreen.kt` | 维护困难 |
| 3 | ReaderToolbar 1179 行巨组件 | `ReaderToolbar.kt` | 14 面板混入，DI 绕过 |
| 4 | ViewModel 60+ StateFlow | `ReaderViewModel.kt` | 职责过载 |
| 5 | UserPreferences 上帝类 | `UserPreferences.kt` | 50+ 偏好混在一起 |
| 6 | BookRepository 职责混杂 | `BookRepository.kt` | DAO/同步/转换/封面混一起 |
| 7 | Domain 层空置 | `domain/` | 业务逻辑散落 |
| 8 | TtsController 持有 Activity 引用 | `TtsController.kt` | 内存泄漏 |
| 9 | AutoScrollController 持有 lambda | `AutoScrollController.kt` | 过时回调 |
| 10 | 全局空 catch | 多处 | 静默吞异常 |
| 11 | 绕过 DI | `ReaderScreen.kt:581` | 内联 `TranslatorService()` |
| 12 | 硬编码 Dispatchers.IO | `ReaderViewModel.kt` | 不可测试 |
| 13 | 仓库层无接口 | Repository 层 | 违背 ISP |
| 14 | 未使用依赖 | hilt.work / navigation-compose | 废弃代码 |

---

## 8. 关键数字

- **71 个 Kotlin 源文件**
- **核心阅读器**: ~3500 行（ReaderScreen + ReaderViewModel + ReaderToolbar + TtsController + AutoScrollController）
- **单文件最大**: ReaderToolbar.kt 1179 行
- **偏好数量**: 50+ 个 key
- **支持的格式**: 8 种（EPUB/TXT/PDF/DOCX/DOC/MOBI/AZW3/MD/HTML）
- **TTS 音色**: 6 种云端 + 本地引擎
- **翻译引擎**: 3 种（Google/百度/有道）
- **导航方案**: 7 个布尔变量手写 BackHandler
