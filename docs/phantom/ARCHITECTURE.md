# Phantom IDEA 아키텍처

목적: 큰 트리에서 길을 찾고 프로그램을 안전하게 고치기 위한 구조·흐름 지도. 패치 지점 전체 목록은 [FORK-DELTA.md](FORK-DELTA.md), 빌드·서명·릴리스·벤치 명령은 [BUILD.md](BUILD.md)가 맡는다.
기준 커밋: be60914099
작성일: 2026-11-12

표기: `경로:줄`은 기준 커밋에서 직접 연 위치다. "(추정)"은 읽은 사실에서 끌어낸 결론, "(미확인)"은 이번에 열어 보지 못한 내용이다.

## 1. 이 프로그램이 무엇인가

- JetBrains intellij-community 모노레포를 태그 `idea/262.10968.63`에서 가져온 개인 포크다(README.md:139-141). 첫 커밋 d2faebf601이 업스트림 트리이고, 그 뒤 103개 커밋이 Phantom 변경이다. d2faebf601..HEAD 차이는 227개 파일(+13898/-2982)이다.
- 저장소 루트가 곧 community 루트다. `community/` 디렉터리는 없다. 업스트림 문서의 `community/...`는 `./...`로 읽는다.
- 구성은 업스트림과 같다. IntelliJ Platform(`platform/`) 위에 IDEA Community 제품(`build/src/.../IdeaCommunityProperties.kt`)이 번들 플러그인 목록과 콘텐츠 모듈을 얹는다.
- Phantom이 구조 수준에서 바꾸는 것은 네 가지다. (1) 브랜딩과 빌드 번호 체계, (2) 번들에서 플러그인 14개를 빼고 Phantom 플러그인 2개와 Lombok을 넣음, (3) JetBrains 서버 호출을 하나의 시스템 속성 게이트 뒤로 숨김, (4) `dev.phantom.*` 패키지로 플랫폼 기능(GitHub 플러그인 저장소, 메모리 자동 조정, Endpoints 도구 창)을 추가함.
- Spring, Spring Boot, JPA, DB, HTTP Client, Tabs, Workspaces 플러그인은 이 저장소가 아니라 별도 저장소 newkayak12/Phantom-plugins에 있다(README.md:44-54). Bytecode Viewer, WebP, Modal Commit도 그쪽으로 옮겨졌다(IdeaCommunityProperties.kt:73). 이 IDE는 그 저장소를 기본 플러그인 저장소로 항상 등록한다(7절).

## 2. 디렉터리 지도

파일 수는 `git ls-files <dir> | wc -l`(기준 커밋, 전체 274,322개). "Phantom 변경"은 d2faebf601..HEAD에서 바뀐 파일 수다.

| 디렉터리 | 내용 | 파일 수 | Phantom 변경 |
|---|---|---|---|
| plugins | 번들·선택 플러그인 거의 전부(kotlin, gradle, maven, git, github, terminal, grazie 등) + `phantom-sync`, `phantom-theme` | 128,811 | 62 |
| java | Java 언어 지원(PSI, 분석, 디버거, `java/plugin` = com.intellij.java) | 55,316 | 10 (java-promo 삭제 등) |
| platform | IntelliJ Platform 본체: util, core, platform-impl, lang-impl, indexing, vfs, statistics, build-scripts, bootstrap 등 | 41,430 | 87 |
| python | PyCharm/Python 지원. IDEA CE 번들에는 기본으로 들지 않음(추정) | 30,096 | 0 |
| build | CE 제품 빌드: `IdeaCommunityProperties.kt`, Bazel 규칙(`jps_*.bzl`), 설치본 이미지, `conf/` | 5,195 | 14 |
| xml | XML/HTML PSI | 4,299 | 0 |
| json | JSON / JSON Schema | 1,553 | 0 |
| jps | JPS 빌드 시스템 | 1,160 | 0 |
| fleet | Fleet 공용 라이브러리(rpc, kernel) | 861 | 0 |
| tools | `rg.cmd`, `fd.cmd`, IDE Starter 테스트 프레임워크, `phantom-runner-setup.sh` | 752 | 1 |
| libraries | 서드파티 라이브러리 래퍼 모듈(`intellij.libraries.*`) | 735 | 0 |
| grid | DataGrid UI | 611 | 0 |
| jvm | JVM 공용 모듈 | 610 | 0 |
| uast | Unified AST | 499 | 0 |
| RegExpSupport | 정규식 언어 | 489 | 0 |
| spellchecker | 맞춤법 검사 | 227 | 0 |
| native | 네이티브 코드: XPlatLauncher(Rust), fsnotifier 등 | 209 | 0 |
| notebooks | 노트북 지원 | 168 | 0 |
| lib | 미리 빌드된 jar 메타데이터, `lib/MODULE.bazel` | 144 | 0 |
| images | 공용 아이콘 모듈 | 137 | 0 |
| jupyter | Jupyter | 119 | 0 |
| community-resources | CE 제품 커스터마이즈: `IdeaApplicationInfo.xml`, 로고, `phantom-brand-src/` | 98 | 11 |
| updater | 패치 업데이터 | 78 | 0 |
| commandInterface | 명령 인터페이스 | 55 | 0 |
| docs | 업스트림 puml·노트북 + `docs/phantom/`, `docs/plans/`, `docs/superpowers/` | 54 | 25 |
| mps | MPS 관련 | 31 | 0 |
| android-customization | Android Studio 커스터마이즈 | 30 | 0 |
| bin | `idea.properties`와 네이티브 보조 바이너리. 실행 스크립트 아님(미확인) | 24 | 0 |
| aether-dependency-resolver | Maven 의존성 해석 | 20 | 0 |
| idea | `idea/customization/base` (`IntelliJIdeaExternalResourceUrls.kt`) | 13 | 1 |
| wintools | Windows 보조 도구 | 12 | 0 |
| license | 서드파티 라이선스 | 4 | 0 |
| graalvm-debugger | GraalVM 디버거 | 2 | 0 |
| module-set-plugins | 생성된 모듈 세트 래퍼 플러그인 `generated/intellij.moduleSet.plugin.main` | 2 | 0 |
| .idea | JPS 프로젝트 모델(`modules.xml`, `libraries/`) | 297 | 1 |
| .agents / .claude / .ai | 에이전트 스킬(21개 / 23개)과 AGENTS.md 원본 | 31 / 28 / 10 | 0 |
| .github | 워크플로. 업스트림 4개 삭제, `phantom-release.yml` 추가 | 6 | 5 |
| (루트 파일) | `README.md`, `AGENTS.md`, `BUILD.bazel`, `MODULE.bazel`, `intellij.idea.community.main*.iml`, `build.txt`, `installers.cmd`, `tests.cmd`, `bazel.cmd`, `phantom-*.sh`, `phantom-bench-report.py` | - | 10 |

## 3. 모듈·플러그인 체계

### 모듈 선언

- 모듈의 원본은 `*.iml`(JPS)이다. 전체 모듈 목록은 `.idea/modules.xml`에 있다. Phantom 모듈 4개는 `.idea/modules.xml:359-362`에 있다(phantom.theme, phantom.sync.core, phantom.sync, phantom.sync.tests).
- `BUILD.bazel`은 `.iml`에서 생성된다. 생성 구간은 `### auto-generated section ... start` 주석으로 표시된다(예: `plugins/phantom-sync/BUILD.bazel:3`). 생성 대상 목록은 `build/bazel-generated-file-list.txt`(1,722줄)이다.
- 생성기 실행: 이 저장소에는 `build/jpsModelToBazelCommunityOnly.cmd`만 있다. 이 스크립트는 `platform/build-scripts/bazel`에서 `bazel run //:jps-to-bazel`을 실행한다(`build/jpsModelToBazelCommunityOnly.cmd:11-12`, 대상 정의 `platform/build-scripts/bazel/BUILD.bazel:115`). AGENTS.md는 2026-11-12에 이 경로로 고쳤다. `.ai/guide.md:30`, `.ai/tool-permissions.json:40`, `.claude/skills`와 `.agents/skills`의 여러 스킬은 아직 존재하지 않는 `./build/jpsModelToBazel.cmd`를 가리킨다(마지막 절 참고).
- 루트 집계 모듈 `intellij.idea.community.main.iml`은 소스 실행·테스트용 런타임 의존성을 모은다. Phantom 모듈은 `intellij.idea.community.main.iml:138-140`에, 대응 Bazel 대상은 루트 `BUILD.bazel:284-286`(테스트 라이브러리 `:473-475`)에 있다.

### 플러그인 선언

- 플러그인은 `META-INF/plugin.xml`로 선언한다. 플러그인 안의 콘텐츠 모듈은 `<content><module name=.../></content>`로 나열하고, 각 모듈 기술자는 모듈 리소스 루트의 `<module>.xml`이다. 예: `plugins/phantom-sync/resources/META-INF/plugin.xml:15-17` → `plugins/phantom-sync/core/resources/phantom.sync.core.xml`.
- 플러그인이 아닌 플랫폼 확장은 `platform/platform-resources/src/META-INF/PlatformExtensions.xml`, `LangExtensions.xml`, 확장 지점은 `PlatformExtensionPoints.xml`, `LangExtensionPoints.xml`에 등록한다. 액션은 `platform/platform-resources/src/idea/PlatformActions.xml`.
- 모듈 기술자의 `<dependencies>` 중 "Generated dependencies" 구간은 Product DSL 생성기가 채운다(예: `platform/remoteDev-util/resources/intellij.remoteDev.util.xml:2`).

### Product DSL과 모듈 세트

- DSL 코드와 문서: `platform/build-scripts/product-dsl/`(README.md, `docs/` 11개 문서). 이 트리를 고치기 전에 그 README와 `docs/module-sets.md`를 읽는다.
- 모듈 세트 정의: `platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt`(`essentialMinimal` :69, `essential` :118, `rdCommon` :359, `ideCommon` :376), `CoreModuleSets.kt`, `CommunityProductFragments.kt`(`javaIdeBaseFragment` :21).
- 생성기 실행: IDE 실행 구성 `Generate Product Layouts` 또는 `bazel run //platform/buildScripts:plugin-model-tool`(product-dsl/README.md:7-8).

### IDEA Community 제품 조립

- 정의 위치: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`.
  - `applicationInfoModule = "intellij.idea.community.customization"` (:55), `productImplementationModules` (:63-66).
  - 번들 플러그인: `IDEA_BUNDLED_PLUGINS`(`platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:14`)에서 14개를 빼고 `PHANTOM_BUNDLED_PLUGINS`(:20-24: `phantom.sync`, `phantom.theme`, `intellij.lombok`)를 더한다(:76-90).
  - 제품 콘텐츠: `getProductContentDescriptor()` (:150) → `intellijCommunityBaseFragment()` (:217-255). `javaIdeBaseFragment()`, 개별 모듈, `CommunityModuleSets.ideCommon()`(:251), `rdCommon()`(:252)을 포함한다.
- 번들에서 빠진 플러그인은 14개다(IdeaCommunityProperties.kt:77-90): featuresTrainer, mcpserver, performanceTesting, platform.acp.plugin, dev, eclipse, java.byteCodeViewer, webp, vcs.git.commit.modal, vcs.gitlab, keymap.eclipse, keymap.visualStudio, keymap.netbeans, configurationScript. 이와 별도로 `IDEA_BUNDLED_PLUGINS` 자체에서 `intellij.android.gradle.declarative.lang.ide`, `intellij.compose.ide.plugin` 2개가 지워졌고(BaseIdeaProperties.kt diff), Android 플러그인 레이아웃이 빠졌다(IdeaCommunityProperties.kt:97).

```
 *.iml (JPS 모듈, 원본) ──jps-to-bazel──▶ BUILD.bazel (생성)
      │
      ▼
 콘텐츠 모듈 <module>.xml ──<content>──▶ 플러그인 META-INF/plugin.xml
      │                                        │
      ▼ (모듈 세트: CommunityModuleSets.kt)    ▼ (bundledPluginModules)
 ┌──────────────────────── 제품 번들 ────────────────────────┐
 │ IdeaCommunityProperties.kt                                │
 │  - getProductContentDescriptor() → 코어 모듈 + 모듈 세트  │
 │  - bundledPluginModules = IDEA_BUNDLED_PLUGINS − 14       │
 │                           + PHANTOM_BUNDLED_PLUGINS       │
 └───────────────────────────────────────────────────────────┘
```

## 4. 기동 흐름

### 네이티브 런처

- `native/XPlatLauncher`(Rust). `src/lib.rs:125`에서 JRE와 메인 클래스를 정하고(`prepare_for_launch`), `lib.rs:155`에서 JVM을 띄운다. `src/java.rs:358` `call_main_method`가 JNI로 메인 클래스의 `main`을 부른다.
- 메인 클래스 이름 기본값은 `platform/build-scripts/src/org/jetbrains/intellij/build/ProductProperties.kt:79` `com.intellij.idea.Main`이다.
- VM 옵션은 빌드 시 `platform/build-scripts/src/org/jetbrains/intellij/build/impl/VmOptionsGenerator.kt`가 만든다. **Phantom 패치**: :26(G1 주기적 GC, 힙 반환), :33(`-ea` 제거).

### main에서 환영 화면·프로젝트 열기까지

1. `platform/bootstrap/src/com/intellij/idea/Main.kt:60` `main` → `:69` `mainImpl`. 인자 전처리, `AppMode.setFlags`, `PathManager.loadProperties`(:79), `customizePaths`(:81), `runBlocking`(:85). 실패하면 `StartupErrorReporter.showError`(:100).
2. `Main.kt:105` `startApp`. Marketplace 액션 스크립트 실행(:144), `initMarketplace`(:153), `com.intellij.idea.MainImpl`을 리플렉션으로 로드(:162/:175), `startApplication` 호출(:180).
3. `platform/platform-impl/bootstrap/src/com/intellij/platform/ide/bootstrap/startup.kt:115` `startApplication`. 시스템 디렉터리 잠금(:147-151), 설정 가져오기(:246), `runActionScript`(호출 :276, 정의 :752), `PluginAutoUpdater`(:278-280), **플러그인 기술자 로딩 예약**(:285), 커널 시작(:300), `ApplicationImpl` 생성(:327), `loadApp`(:331), `appStarter.prepareStart`(:346), `appStarter.start`(:355). `interface AppStarter`는 :738.
4. `platform/starter/src/com/intellij/idea/MainImpl.kt:9` `MainImpl : AppStarter` → `initApplication`(ApplicationLoader.kt:110). 앱 로딩 완료를 기다린다.
5. `platform/platform-impl/bootstrap/src/com/intellij/platform/ide/bootstrap/ApplicationLoader.kt:118` `loadApp`. 플러그인 집합 대기(:133), **서비스·확장 등록** `app.registerComponents`(:137), `ApplicationManager.setApplication`(:140), OpenTelemetry(:161), `ideFingerprint()`(:176), 설정 저장소 초기화(:188, 정의 :437), 앱 스타터 생성(:191, 정의 :511, 기본 `IdeStarter` :515, LightEdit :544), 핵심 서비스 미리 로드(:197), `COMPONENTS_LOADED`(:221), `ApplicationInitializedListener` 호출(:240), `checkThirdPartyPluginsAllowed`(:252), `runPostAppInitTasks`(:259).
6. `platform/platform-impl/bootstrap/src/com/intellij/idea/IdeStarter.kt:55` `IdeStarter`, `start`(:73) → `openProjectIfNeeded`(:124). 명령줄 파일이 있으면 `ProjectUtil.openOrImportFilesAsync`(:181), 아니면 최근 프로젝트 재오픈(:196), 열린 프로젝트가 없으면 환영 화면(:208, `showWelcomeFrame` :215). 환영 화면 클래스는 `platform/platform-impl/src/com/intellij/openapi/wm/impl/welcomeScreen/WelcomeFrame.kt:68`(`showIfNoProjectOpened` :208), `FlatWelcomeFrame.kt:111`. 첫 실행 마법사 확장 지점은 `IdeStartupWizard.kt:33`(`com.intellij.ideStartupWizard`).
7. 프로젝트 열기: `platform/platform-impl/src/com/intellij/ide/impl/ProjectUtil.kt:114` `object ProjectUtil`(`openOrImportFilesAsync` :562) → `platform/platform-impl/src/com/intellij/openapi/project/impl/ProjectManagerImpl.kt:170`(`openProjectAsync` :617, `ProjectImpl` 생성 :976, `initProject` :1410) → `StartupManagerImpl.initProject`(ProjectManagerImpl.kt:1602), `runPostStartupActivities`(ProjectManagerImpl.kt:846) → `platform/platform-impl/src/com/intellij/ide/startup/impl/StartupManagerImpl.kt:98`(`runPostStartupActivities` :208)이 `ProjectActivity`(`postStartupActivity` 확장)를 실행한다.

Phantom이 고친 지점: 1~7의 기동 클래스 자체(Main.kt, startup.kt, ApplicationLoader.kt, IdeStarter.kt, ProjectManagerImpl.kt, StartupManagerImpl.kt)는 바뀌지 않았다. 바뀐 것은 경로에 걸린 부품이다.
- 1단계 실패 경로: `platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java:196-197`(JetBrains 보고 버튼은 게이트가 켜졌을 때만).
- 5단계: `platform/platform-impl/src/com/intellij/platform/ide/IdeFingerprint.kt:76`(인덱스 지문에 들어가는 플러그인 축소). OpenTelemetry 지표 파일 기본값은 `platform/diagnostic/telemetry/src/com/intellij/platform/diagnostic/telemetry/OpenTelemetryUtils.kt:62`.
- 7단계: Phantom의 `ProjectActivity` 2개가 여기서 돈다(`PlatformExtensions.xml:841` PhantomPluginAdvisor, `:843` PhantomMemorySizer). 업데이트 검사 `UpdateCheckerProjectActivity`(`PlatformExtensions.xml:1623`)도 같은 자리에서 돌며, 그 안의 저장소 선택이 패치되어 있다(6절).

### 플러그인 기술자 로딩

- `platform/core-impl/src/com/intellij/ide/plugins/PluginManagerCore.kt:395` `scheduleDescriptorLoading` → `platform/core-impl/src/com/intellij/ide/plugins/PluginDescriptorLoader.kt:476` `scheduleLoading` → `loadDescriptors`(:451, :566). 결과 `PluginSet`을 ApplicationLoader.kt:133이 기다린다.

### 서비스와 확장 지점의 생성

- 등록: `platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:379` `registerComponents`. 모듈마다 서비스 등록(:397), 확장 지점 생성(:415), 그 뒤 확장 등록(:429). 이 단계에서는 등록만 한다.
- 생성: 서비스는 처음 요청될 때 만든다(`getService` :747 → `doGetService` :775). 확장은 `platform/extensions/src/com/intellij/openapi/extensions/impl/ExtensionPointImpl.kt:230` `getExtensionList`가 처음 불릴 때 만든다. 확장 영역은 `ExtensionsAreaImpl.kt:67`.
- 미리 만드는 서비스는 ApplicationLoader.kt:197 `preloadCriticalServices`와 `ComponentManagerImpl.kt:1111` `preloadServices`가 처리한다.

## 5. 핵심 런타임 개념과 위치

| 개념 | 핵심 클래스(경로) | 한 줄 설명 | Phantom 변경 |
|---|---|---|---|
| 확장 지점·서비스 | `platform/extensions/src/com/intellij/openapi/extensions/ExtensionPointName.kt:40`, `ExtensionPointImpl.kt:51`, `ComponentManagerImpl.kt:170`, `ApplicationImpl.java:137`, `ProjectImpl.kt:77` | 앱·프로젝트 컨테이너가 XML 등록을 보고 지연 생성한다. 설명: `platform/service-container/overview.md`, `docs/getting-service.puml`, `docs/plugin-model.puml` | 없음 |
| 액션 | `platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java:58`, `platform/platform-impl/src/com/intellij/openapi/actionSystem/impl/ActionManagerImpl.kt:116`, `PlatformActions.xml` | 메뉴·단축키 명령. 규칙: `.agents/skills/actions/SKILL.md` | 액션 1개 추가(PlatformActions.xml:1807) |
| VFS | `platform/core-api/src/com/intellij/openapi/vfs/VirtualFileSystem.java:26`, `platform/core-impl/.../vfs/impl/VirtualFileManagerImpl.java`, `platform/platform-impl/.../vfs/newvfs/persistent/PersistentFSImpl.java:166`, `.../vfs/impl/local/LocalFileSystemImpl.java:70` | 디스크 파일의 영속 스냅샷 | 없음 |
| PSI | `platform/core-api/src/com/intellij/psi/PsiFile.java:26`, `platform/core-impl/src/com/intellij/psi/impl/PsiManagerImpl.java:57` | 파일의 구문 트리 모델 | 없음 |
| 인덱싱 | `platform/indexing-api/.../FileBasedIndex.java:47`, `platform/lang-impl/src/com/intellij/util/indexing/FileBasedIndexImpl.java:162`, `platform/indexing-impl/src/com/intellij/find/ngrams/` | 파일 기반 인덱스와 트라이그램 인덱스 | 있음: TrigramIndex.java:93, TrigramIndexFilter.kt:57/:66(라이브러리 텍스트 파일 제외), IdeFingerprint.kt:76, `FileBasedIndexFileTypeListener.java`, 진단 덤프 기본 끔 |
| 프로젝트 모델 | `platform/backend/workspace/src/WorkspaceModel.kt:38`, `platform/projectModel-impl/.../workspaceModel/ide/impl/WorkspaceModelImpl.kt:82`, `.../legacyBridge/module/ModuleManagerBridgeImpl.kt:130`, `.../roots/impl/ProjectRootManagerImpl.kt:44` | Workspace Model 엔티티 저장소와 기존 Module/Roots API 브리지 | 없음 |
| 스레딩 모델 | `platform/core-api/src/com/intellij/openapi/application/ReadAction.java:29`, `WriteAction.java:18`, `ModalityState.java:39`, `coroutines.kt`(`readAction` :32, `smartReadAction` :45, `writeAction` :384, `Dispatchers.EDT` :448), `platform/util/src/com/intellij/util/ui/EDT.java:30`, `NonBlockingReadActionImpl.java:101` | EDT, 읽기/쓰기 잠금, 코루틴. 설명: `docs/notebooks/1-ContextPropagation.ipynb`, `2-CancellationModel.ipynb`, `3-ReadWriteLock.ipynb`, ThreadingAssertions.java:34의 jb.gg/ij-platform-threading | 있음: NonBlockingReadActionImpl.java:126(OpenTelemetry 계측 기본 끔) |
| 설정 저장 | `platform/projectModel-api/.../components/PersistentStateComponent.java:22`, `platform/configuration-store-impl/src/ComponentStoreImpl.kt:108`, `StateStorageManagerImpl.kt:38`, `ApplicationStoreImpl.kt:37` | `@State` 컴포넌트를 XML로 저장. 설명: `docs/configurable.md` | 없음. 설정 폴더 이름만 `PhantomIDEA`로 고정(IdeaCommunityProperties.kt:177) |
| 레지스트리 키 | `platform/util/src/com/intellij/openapi/util/registry/Registry.kt:43`, `RegistryValue.kt:28`, `platform/platform-impl/.../registry/RegistryManagerImpl.kt:28`, 기본값 `platform/util/resources/misc/registry.properties`와 XML `<registryKey>` | 런타임 플래그. 규칙: `.agents/skills/registry/SKILL.md` | 기본값 변경: `ide.show.plugin.suggestions.on.open=false`(registry.properties:2241), `jdk.updater=false`(PlatformExtensions.xml:1383), `csat.survey.enabled=false`(`platform/feedback/resources/intellij.platform.feedback.xml:83`) |
| 업데이트 검사 | `platform/platform-impl/update-checker/src/com/intellij/openapi/updateSettings/impl/UpdateChecker.kt:176`, `UpdateCheckerService.java:52`, `UpdateCheckerProjectActivity.kt:21`, 피드 URL `platform/platform-impl/src/com/intellij/platform/ide/impl/customization/BaseJetBrainsExternalProductResourceUrls.kt:113` | IDE·플러그인 업데이트 확인 | 있음: IDE 피드는 `-Didea.updates.url` 없으면 null(:112-114), 플러그인 업데이트에서 Marketplace는 게이트 뒤(UpdateChecker.kt:342-343) |
| 통계·텔레메트리 | `platform/statistics/src/com/intellij/internal/statistic/utils/StatisticsUploadAssistant.java`, `.../service/fus/collectors/FUCounterUsageLogger.java`, `platform/statistics/uploader/`, 동의 `platform/platform-impl/src/com/intellij/ide/gdpr/ConsentOptions.java` | FUS 이벤트 로그 수집·전송. 전송은 사용자 동의로 판단(StatisticsUploadAssistant.java:42-63, :87-99) | 부분: MLEventLoggerProvider.kt:43(항상 켜진 로거 끔), PluginInfoDetector.kt:213. 동의·업로더 자체는 그대로(6절) |
| Marketplace·플러그인 저장소 | `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt`, `.../marketplace/utils/MarketplaceUrls.kt:19`, `RepositoryHelper.java`, `PluginManagerConfigurable.kt:38`, 호스트 기본값 `platform/core-impl/.../application/impl/ApplicationInfoImpl.java:47` | 플러그인 목록·다운로드·업데이트 | 있음: 게이트 정의(MarketplaceRequests.kt:104-109), GitHub 저장소 지원(RepositoryHelper.java:76-78, :150-161), 설치 불가 항목 숨김(MarketplaceRequests.kt:679, :699) |

## 6. 외부 통신 구조

### 구조

- JetBrains 서버 주소는 한곳에 모여 있지 않다. 제품 단위 URL은 `ExternalProductResourceUrls`(`platform/platform-api/src/com/intellij/platform/ide/customization/ExternalProductResourceUrls.kt`) 구현인 `BaseJetBrainsExternalProductResourceUrls.kt`와 `idea/customization/base/src/IntelliJIdeaExternalResourceUrls.kt`가 낸다. 플러그인 호스트는 `ApplicationInfoImpl.java:47`, 그 밖의 주소는 각 서브시스템 소스에 상수로 흩어져 있다.
- Phantom은 서버별로 막지 않고 시스템 속성 하나를 게이트로 쓴다.

### 단일 게이트: `phantom.jetbrains.services`

- 정의: `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:104-109` `MarketplaceRequests.jetBrainsServicesEnabled = Boolean.getBoolean("phantom.jetbrains.services")`. 기본값은 꺼짐이다.
- 읽는 곳(`./tools/rg.cmd -n -e jetBrainsServicesEnabled -e phantom.jetbrains.services -g '*.kt' -g '*.java'`로 본 기준 커밋의 제품 코드. 테스트에서만 읽는 곳은 `platform/lang-impl/testSources/com/intellij/openapi/projectRoots/impl/jdkDownloader/JdkDownloaderIntegrationTest.kt:39`, `plugins/grazie/src/tests.pro/testSrc/com/intellij/grazie/pro/BaseTestCase.kt:34`, `:55`):

| 서브시스템 | 위치 | 꺼졌을 때 |
|---|---|---|
| 깨진 플러그인 목록 | MarketplaceRequests.kt:403 (`getBrokenPlugins`) | null |
| 기능 추천 | MarketplaceRequests.kt:572 (`getFeatures`) | 빈 목록 |
| Marketplace 플러그인 ID 목록 | MarketplaceRequests.kt:613, :935 | 빈 집합, 갱신 안 함 |
| 플러그인 업데이트 저장소 | UpdateChecker.kt:343 | Marketplace 제외 |
| 기동 오류 보고 | StartupErrorReporter.java:196-197 (속성 직접 읽음) | 보고 버튼 없음 |
| 로그 업로드 REST | `platform/built-in-server/src/org/jetbrains/ide/UploadLogsService.kt:38`, :87 | 거부 |
| 피드백·설문 | `platform/feedback/src/com/intellij/platform/feedback/ActionBasedFeedbackConfig.kt:65`, `.../impl/FeedbackSurveyUtils.kt:74` | 설문 안 띄움 |
| JDK 목록 | `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/JdkList.kt:535-536` | foojay 피드(`FoojayJdkList.FEED_URL`) |
| 완성 로그 전송 | `plugins/stats-collector/src/com/intellij/stats/completion/sender/SenderPreloadingActivity.kt:33-34` | 실행 안 함 |
| Grazie Cloud | `plugins/grazie/.../cloud/DefaultGrazieCloudConnector.kt:22`, :44, :46, `.../cloud/license/GrazieLoginManager.kt:80`, :159, :260, `.../utils/Utils.kt:79` | 로컬 검사만 |

- 게이트를 쓰지 않고 따로 끈 곳: Package Search(`plugins/repository-search/.../PackageSearchApiClientService.kt:87-92`, 항상 예외), Help 메뉴 JetBrains URL(`IntelliJIdeaExternalResourceUrls.kt:22-42`, 모두 null), Marketplace ID 목록 다운로드(`platform/statistics/.../PluginInfoDetector.kt:213`), ML 로거(`platform/ml-impl/.../MLEventLoggerProvider.kt:43`), IDE 업데이트 피드(BaseJetBrainsExternalProductResourceUrls.kt:112-114).

### 설계상 켜 둔 것

- Marketplace 탐색: 플러그인 호스트 기본값 `https://plugins.jetbrains.com`(ApplicationInfoImpl.java:47). `-Didea.plugins.host`로 바꾼다(:48, :559). 게이트 주석도 "Browsing the Marketplace tab still works"라고 적는다(MarketplaceRequests.kt:105).
- Phantom 플러그인 저장소: `GitHubPluginRepository.OFFICIAL = "newkayak12/Phantom-plugins"`(`platform/platform-impl/src/dev/phantom/ide/plugins/GitHubPluginRepository.kt:40`)를 항상 호스트에 넣는다(RepositoryHelper.java:76-78, `-Dphantom.plugins.official=false`로 끔). 메타데이터는 `raw.githubusercontent.com/.../.phantom/marketplace.json`(:54), API는 `api.github.com`(:45), 다운로드는 GitHub 릴리스(:58)에서 받는다.
- JDK 다운로드는 foojay로 바뀌었고 JetBrains 피드는 게이트 뒤다(JdkList.kt:535-536).
- 게이트도 없고 끄지도 않은 JetBrains 다운로드(호출 시점은 확인하지 못함): Grazie 언어 파일(LanguageTool 모델 `resources.jetbrains.com/grazie/model/language-tool`, Hunspell `packages.jetbrains.team/maven/...`, `plugins/grazie/src/main/kotlin/com/intellij/grazie/GraziePlugin.kt:20`, `:25`. 98673d9e9a 본문은 JetBrains 자체 LanguageTool 빌드라 Maven Central 대체가 없다고 설명), JBR 런타임 선택 피드(`download.jetbrains.com/jdk/feed/v1/jbr-choose-runtime-<major>.json.xz`, `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/RuntimeChooserJbr.kt:43`. 레지스트리 `runtime.chooser.url`로 바꿀 수 있음), Git 설치 목록 피드(`download.jetbrains.com/jdk/feed/v1/gits.json.xz`, `plugins/git4idea/backend/src/config/GitDownloadAndInstall.kt:20`, `:105`).

### 열린 질문

(a) FUS 업로드 엔드포인트의 출처
- 확인한 것: 전송 URL은 `platform/statistics/uploader/src/com/intellij/internal/statistic/eventLog/connection/EventLogSettingsClient.kt:96-98` `provideServiceUrl()` → `configurationClient.provideSendEndpoint()`에서 나온다. `configurationClient`는 `EventLogUploadSettingsClient.kt:39` `ConfigurationClientFactory.create(...)`가 만든다. 이 팩토리는 외부 Maven 라이브러리 `com.jetbrains.fus.reporting`(`.idea/libraries/com_jetbrains_fus_reporting_fus_api.xml:3`, `com_jetbrains_fus_reporting_ap_validation_all.xml:3`, 버전 1.0.229) 안에 있고 소스는 이 트리에 없다. 따라서 설정 URL과 전송 URL은 그 jar 안에 있다(추정).
- 확인한 것: 전송 허용은 `phantom.jetbrains.services`가 아니라 사용자 동의로 결정된다(StatisticsUploadAssistant.java:42-63, :87-99). Phantom은 이 파일을 바꾸지 않았다.
- 확인하지 못한 것: jar 내용(로컬 `~/.m2`에 없음, Bazel 캐시는 보지 않음), 동의가 기본으로 꺼져 있는지, Phantom 벤더(`company name="Phantom"`)에서 동의 파일이 `consents-<company>.json`으로 바뀌는 분기(ConsentOptions.java:169)가 실제 동의 기본값에 주는 영향.

(b) 게이트 밖에 남은 네 곳 — 모두 d2faebf601 이후 수정 없음
- `platform/feedback/src/com/intellij/platform/feedback/impl/GeneralFeedbackSubmit.kt`: 실제로 외부 전송한다. `submitFeedback`(:115-130)이 `forms-service.jetbrains.com/feedback`(:25) 또는 스테이징 `forms-stgn.w3jbcom-nonprod.aws.intellij.net/feedback`(:24)으로 보내고, 그 전에 `RegionUrlMapper.tryMapUrlBlocking`이 `www.jetbrains.com/config/JetBrainsResourceMapping.json`(`platform/platform-impl/src/com/intellij/ide/RegionUrlMapper.java:42`, 요청 :152-158)을 받는다. 게이트는 설문 자동 표시 경로(ActionBasedFeedbackConfig, FeedbackSurveyUtils)에만 걸려 있다. 게이트 없는 호출자: Tips of the Day(마지막 절 2번), `BlockBasedFeedbackDialog.kt:162`, `BlockBasedFeedbackDialogWithEmail.kt:77`, `LocalizationFeedbackDialog.kt:125`. 대화상자 쪽이 사용자 조작으로만 열리는지는 확인하지 않았다.
- `platform/ide-core/src/com/intellij/facet/frameworks/LibrariesDownloadConnectionService.java:6-7`: 실제로 외부 접속한다. 경로는 `java/idea-ui/.../DownloadableLibraryType.java:48` → `LibraryVersionsFetcher` → `FileSetVersionsFetcherBase.java:43` → `LibrariesDownloadAssistant.java:35`, `:54`이고, 설정 `www.jetbrains.com/idea/download-assistant.xml`과 서비스 `frameworks.jetbrains.com`을 쓴다. 프레임워크 라이브러리 다운로드 UI를 열 때만 도는 것으로 보인다(추정).
- `platform/remoteDev-util`(`RemoteDevSystemSettings.kt:17-19`, `JetBrainsClientDownloaderConfigurationProvider.kt:148-149`, `util/ProtocolUtils.kt:16`): 주소는 남아 있다. 이 트리 안에서 `CodeWithMeClientDownloader`, `CodeWithMeGuestLauncher`, `createHttpUrl`을 부르는 곳은 remoteDev-util 자신뿐이다(rg 검색). 모듈은 `essential.minimal` 세트에 내장된다(CommunityModuleSets.kt:74). 번들에 없는 Code With Me·Gateway 플러그인을 설치해야 쓰이는 것으로 보인다(추정).
- `platform/built-in-server/src/org/jetbrains/ide/StartUpMeasurementService.kt`: 바깥으로 보내지 않는다. 내장 HTTP 서버의 수신 REST 서비스이고, `ij-perf.jetbrains.com`/`ij-perf.labs.jb.gg` origin의 브라우저 페이지가 로컬 기동 보고서를 읽도록 허용한다(:16-21, 응답 :23-28). Phantom이 같은 종류의 `UploadLogsService`는 막았으나 이것은 열어 두었다.

## 7. Phantom 고유 코드가 붙는 자리

| 위치 | 내용 | 등록 |
|---|---|---|
| `platform/platform-impl/src/dev/phantom/ide/plugins/GitHubPluginRepository.kt` (186줄) | GitHub `owner/repo`를 플러그인 저장소로 읽음 | RepositoryHelper.java:78, :151-160; PluginHostsConfigurable.java:301; PluginManagerMain.java:305 (코드 호출) |
| `.../plugins/GitHubTokens.kt` (96줄) | 비공개 저장소 토큰, `GitHubPluginRepositoryAuthProvider`(:64), `SetGitHubPluginTokenAction`(:84) | PlatformExtensions.xml:2082 `pluginRepositoryAuthProvider`; PlatformActions.xml:1807 |
| `.../plugins/PhantomMarketplace.kt` (32줄) | Phantom에서 설치할 수 없는 Marketplace 항목 숨김, `-Dphantom.marketplace.showAll`(:20) | MarketplaceRequests.kt:679, :699 (코드 호출) |
| `.../plugins/PhantomPluginAdvisor.kt` (158줄) | 프로젝트 라이브러리를 보고 저장소의 플러그인 추천 | PlatformExtensions.xml:841 `postStartupActivity`; 알림 그룹 :845 |
| `.../plugins/PhantomPluginNewProjectWizard.kt` (70줄) | 새 프로젝트 마법사의 Spring Boot 항목(`SpringBootPluginNewProjectWizard` :64) | PlatformExtensions.xml:844 `newProjectWizard.generator` |
| `platform/platform-impl/src/dev/phantom/ide/memory/PhantomMemorySizer.kt` (95줄) | 힙·파일 페이지 캐시 크기를 사용자 `.vmoptions`에 씀(`MemoryPlan` :74). 테스트 `platform/platform-tests/testSrc/dev/phantom/ide/memory/MemoryPlanTest.kt` | PlatformExtensions.xml:843 `postStartupActivity` |
| `platform/lang-impl/src/dev/phantom/ide/endpoints/` (`EndpointsToolWindow.kt` 259줄, `EndpointRequests.kt` 176줄) | Endpoints 도구 창과 `.http` 요청 생성. 내용은 업스트림 EP `com.intellij.microservices.endpointsProvider`(LangExtensionPoints.xml:736) 구현이 채우며, 구현은 Phantom-plugins 쪽에 있다 | LangExtensions.xml:1556-1557 `toolWindow id="Endpoints"` |
| `plugins/phantom-sync/` | 플러그인 `dev.phantom.sync`. 콘텐츠 모듈 `phantom.sync.core`가 Settings Sync 통신자를 자기 git 저장소로 바꿈. 테스트 `plugins/phantom-sync/tests/` | plugin.xml:2, :15-17; `core/resources/phantom.sync.core.xml:7` `settingsSync.communicatorProvider` |
| `plugins/phantom-theme/` | 플러그인 `dev.phantom.theme`. Phantom Dark/Light 테마와 색 구성표. 생성 스크립트 `tools/generate-themes.py` | plugin.xml:16-18 (`bundledColorScheme`, `themeProvider` ×2) |
| `plugins/github/github-core/.../authentication/GHDeviceFlow.kt` | JetBrains 중계 없이 GitHub OAuth 장치 흐름으로 로그인 | `ui/GHOAuthCredentialsUi.kt:61-68` (코드 호출) |

번들 등록: `.idea/modules.xml:359-362`, `intellij.idea.community.main.iml:138-140`, `BUILD.bazel:284-286`, `PHANTOM_BUNDLED_PLUGINS`(IdeaCommunityProperties.kt:20-24).

### 브랜딩·버전 배관

- 제품 정보: `community-resources/resources/idea/IdeaApplicationInfo.xml:2-10`. 버전(`major`/`minor`, 빌드 날짜 YY.MM.DD), 회사 Phantom, 로고·아이콘, 이름 `Phantom IDEA`, 기본 테마 `Phantom Light`/`Phantom Dark`. `<version>` 줄은 `phantom-build.sh:16`이 빌드 때 제자리에서 고친다.
- 빌드 번호는 두 개다. 플랫폼 빌드 `262.<YYMMDD>`(`-Dbuild.number`)와 표시용 `PH-<YYMMDD>.<N>`(`-Dphantom.build.number`, phantom-build.sh:30, :47). 저장소의 `build.txt`는 `262.SNAPSHOT`이다.
  - `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt:11-14`: 속성 이름과 `phantomBuildNumber(context)`.
  - `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:139-141`: 버전·브랜치 일치 검사 완화. `:164-169`: `number`를 Phantom 번호로 바꾸고 플랫폼 번호는 `apiVersion`으로 남김. 플러그인 호환성은 `apiVersion`으로 판정된다.
  - `platform/build-scripts/src/org/jetbrains/intellij/build/impl/MacDistributionBuilder.kt:571`, `:597`: Info.plist에 Phantom 번호.
  - IdeaCommunityProperties.kt: 설정 폴더 `PhantomIDEA`(:177), 산출물 이름 `phantom-idea-<N>`(:181-182), 출력 폴더(:184), Windows 이름(:263-264), mac URL 스킴 `phantom`·번들 ID `dev.phantom.idea`·`Phantom IDEA.app`(:273-278), Linux 루트(:293).

## 8. 더 읽을 곳

| 경로 | 내용 |
|---|---|
| `AGENTS.md`, `.ai/guide.md` | 저장소 규칙(iml이 원본, 테스트는 `./tests.cmd`). 생성기 경로는 마지막 절 참고 |
| `.agents/skills/*/SKILL.md` (21개) | actions, registry, module-dependencies, module-set-pluginization, extract-module, testing, testing-internals, writing-tests, eel, remote-dev 등 작업별 규칙 |
| `platform/build-scripts/product-dsl/README.md`, `docs/*.md` | Product DSL: quick-start, dsl-api-reference, module-sets, architecture-overview, plugin-graph, validation-rules, errors |
| `platform/service-container/overview.md` | 서비스 컨테이너 구조 |
| `docs/plugin-model.puml`, `docs/getting-service.puml`, `docs/projectClose-dispose-flow.puml`, `docs/jar-format.puml` (렌더본 `docs/out/*.svg`) | 플러그인 모델, 서비스 조회, 프로젝트 닫기, jar 형식 다이어그램 |
| `docs/notebooks/1-ContextPropagation.ipynb`, `2-CancellationModel.ipynb`, `3-ReadWriteLock.ipynb` | 코루틴 문맥 전파, 취소 모델, 읽기/쓰기 잠금 |
| `docs/configurable.md` | 설정 화면(Configurable) |
| `README.md` | Phantom 소개, 플러그인 분리 표, 버전 체계 |
| `docs/phantom/2026-10-16-feature-matrix.md`, `docs/phantom/design/` | 기능 매트릭스와 플러그인 설계 문서 |
| `docs/phantom/bench/README.md` | 벤치마크 설명(명령은 BUILD.md) |
| `platform/main/README.md`, `native/XPlatLauncher/README.md`, `platform/workspace/codegen/README.md` | 집계 모듈, 런처, 엔티티 코드 생성(파일 존재만 확인, 내용은 읽지 않음) |

## 읽다가 확인한 문제

설명만 한다. 고치지 않았다.

1. GitHub 브라우저 로그인 기본값이 비어 있다. `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/GHDeviceFlow.kt:29` `PHANTOM_CLIENT_ID = ""`. 그래서 `clientId`(:31)가 null이 되고, `ui/GHOAuthCredentialsUi.kt:61-62`가 gh CLI 로그인이나 `-Dphantom.github.oauth.client.id`가 없으면 "Browser login is not set up in this build" 예외를 던진다. JetBrains OAuth 클래스는 삭제되어 대체 경로가 없다.
2. Tips of the Day 좋아요/싫어요가 게이트 없이 JetBrains로 간다. 레지스트리 기본값 `tips.of.the.day.feedback=staging`(`platform/tips-of-the-day/resources/intellij.platform.tips.xml:40-42`) → 대화상자 닫기 `TipDialog.java:107-111` → `TipsFeedback.kt:50-71`(빌드 후 120일 이내, :29) → `GeneralFeedbackSubmit.kt:123`의 JetBrains 스테이징 URL(:24). 전송 전에 `RegionUrlMapper.java:42`의 www.jetbrains.com 매핑도 받는다. Tips 모듈은 제품에 포함된다(IdeaCommunityProperties.kt:245).
3. 생성기 안내가 맞지 않는다. AGENTS.md는 2026-11-12에 고쳤다(`build/jpsModelToBazelCommunityOnly.cmd`, Product DSL 규칙은 `platform/build-scripts/product-dsl/README.md`와 `docs/module-sets.md`). 아직 옛 경로 `./build/jpsModelToBazel.cmd`가 남은 파일: `.ai/guide.md:30`, `.ai/tool-permissions.json:40`, `.claude/skills`와 `.agents/skills`의 module-dependencies(`:24`/`:25`, `:31`/`:32`), pseudo-kmp, testing-internals, module-set-pluginization, extract-module. 옛 Product DSL 규칙 경로(`.claude/rules/product-dsl.md`)가 남은 파일: `.ai/partials/module-specific.md:6,9`, 두 `module-set-pluginization/SKILL.md`. 실제로 있는 생성기는 `build/jpsModelToBazelCommunityOnly.cmd`뿐이고 `.claude/rules/product-dsl.md`는 이 트리에 없다. `.ai/guide.md`와 partials를 고치지 않고 `render-guides.mjs`를 돌리면 AGENTS.md의 수정이 덮인다.
4. 사용자에게 보이는 문자열이 `*.properties`가 아니라 코드에 박혀 있다(AGENTS.md 규칙 위반). 예: `platform/platform-impl/src/dev/phantom/ide/plugins/GitHubTokens.kt:84` 액션 이름, `GHOAuthCredentialsUi.kt:56`, `:58`, `:62`, `:66` 상태·오류 문구, `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/ui/GHTokenCredentialsUi.kt:53` 버튼 문구 "From gh CLI".
5. `phantom-build.sh:16`은 추적 중인 `IdeaApplicationInfo.xml`을 `sed -i`로 고친다. 빌드할 때마다 작업 트리가 바뀌므로 커밋 전에 확인해야 한다.
