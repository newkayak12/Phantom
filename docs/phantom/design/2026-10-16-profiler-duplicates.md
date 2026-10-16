# Phantom 프로파일러와 중복 코드 탐지 설계 (profiler-duplicates)

- 날짜: 2026-10-16
- 대상 트리: `/Users/sanghyeonkim/Downloads/port/phantom-idea` (intellij-community `idea/262.10968.63`, Bazel)
- 플러그인 두 개: `plugins/phantom-profiler` (JVM 프로파일러), `plugins/phantom-duplicates` (중복 코드 탐지)
- 사용자: Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자. 시험 프로젝트는 `~/orca/workspaces/prototype-reservation-system` (Kotlin 파일 약 4k개, worktree 여러 개, Java 0개 [V: `find`로 셈])
- 표기: **[V]**는 이 트리나 설치본 설명자에서 경로, 클래스, 시그니처를 직접 확인한 것이다. **[A]**는 가정이며 첫 구현이나 첫 실행에서 확인해야 한다.
- 클린룸: 근거는 community 소스, 공개 문서(async-profiler README, JDK JFR API, HPROF 형식), 분석 노트(`Intellij-rea/reversed/README.md`, `03-decompiled/2026-10-16-ult-*.md`, `02-runtime/2026-10-16-startup-jfr.md`)의 **동작 설명**, Ultimate 설명자(`plugin.xml`/콘텐츠 모듈 xml)의 **기능 이름**뿐이다. `reversed/source/`는 열지 않았다. Ultimate 디컴파일 코드를 옮기거나 풀어 쓰지 않는다.
- 공통 배선 규칙(디렉터리, 모듈 이름, 번들 등록, `namespace="jetbrains"`, P1–P7)은 `2026-10-16-build-wiring.md`를 따른다. 이 문서는 그 규칙 위에서 기능만 설계한다.

---

## 1. 목표와 범위

### 1.1 Ultimate가 제공하는 기능 체크리스트 (설명자에서 읽은 이름만)

| 영역 | Ultimate 설명자에 보이는 항목 | 출처 |
|---|---|---|
| 프로파일러 | `com.intellij.profiler.executor` EP, `executor`, `runToolbarProcess`, 도구 창 `Profiler`, 액션 `AttachProfilerToLocalProcess`, `ToggleFlameGraphViewAction`, `Profiler.FocusOnSubtreeAction`/`FocusOnCallAction`/`ExcludeSubTreeAction`/`ExcludeCallAction`, `Profiler.OpenMergedCalleesAction`, `Profiler.OpenBackTracesAction`, `ImportProfilerResultsFromHistory`, `Profiler.RevealSnapshotAction`, 레지스트리 `idea.profiler.allocations.in.bytes`, `idea.profiler.flame.chart.filter.value`, `profiler.diff.profilerCallStackElementsCombiner`(스냅샷 비교) | `lib/intellij.profiler.common.jar!/intellij.profiler.common.xml` [V] |
| async-profiler 네이티브 | `lib/async-profiler/libasyncProfiler.dylib`, `aarch64/`, `amd64/` | 설치본 디렉터리 목록 [V] |
| 줄 단위 힌트 | `com.intellij.LineProfiler` (프로파일 결과를 에디터 줄에 표시) | `plugins/profiler-lineProfiler` [V], `ult-other-plugins.md` 2.13 |
| 중복 탐지 | inspection `DuplicatedCode`(`enabledByDefault=true`, `runForWholeFile=true`), `fileBasedIndex` `HashFragmentIndex`, EP `duplicateScope`, 설정 `duplicates.index`(Editor 그룹), EP `duplicates.profile`/`duplicates.presentation`, 액션 `DupLocate`, `DuplicatesForm.SendToLeft/Right`, `appStarter id="duplocate"` | `lib/intellij.platform.duplicatesDetector.jar`, `lib/intellij.platform.duplicates.jar` 설명자 [V] |

이 표는 **무엇을 만들지 고르는 체크리스트**로만 쓴다. 구현 방식은 아래에서 community API와 공개 사양으로 새로 정한다.

### 1.2 MVP (v0.1)

백엔드 개발자가 실제로 매일 또는 매주 하는 일에 맞춘다. "느린 API 하나를 재현해서 어디서 시간을 쓰는지 본다", "메모리가 계속 늘어서 힙을 본다", "비슷한 서비스 코드를 복사해서 붙였는지 리뷰 전에 확인한다".

**프로파일러 (`phantom-profiler`)**

| # | 기능 | 사용자가 보는 결과 |
|---|---|---|
| P-M1 | **Profile 실행기 2종**: "Profile with JFR"(기본, 네이티브 의존 없음), "Profile with async-profiler" | 실행 위젯의 Run/Debug 옆 드롭다운에 Profile 그룹이 생긴다. Kotlin `main`/Spring Boot `Application` 실행 구성에서 선택하면 앱이 녹화 중으로 뜬다 |
| P-M2 | **Gradle 위임 실행 지원**: "Build and run using: Gradle"(기본값)인 Application 실행 구성도 같은 JVM 옵션으로 뜬다 | 사용자 프로젝트처럼 Gradle 위임을 켠 상태에서도 P-M1이 동작한다 |
| P-M3 | **실행 중인 로컬 JVM에 붙기**: 프로세스 목록에서 고르고 Start/Stop | `./gradlew bootRun`으로 터미널에서 띄운 앱에 붙어서 30초 녹화 후 결과가 열린다 |
| P-M4 | **스냅샷 뷰어**: Flame Graph, Call Tree, Method List(hot methods, self/total), 스레드 필터, "내 코드만" 토글, 검색 | `.jfr` 파일을 열거나 녹화가 끝나면 에디터 탭으로 열린다 |
| P-M5 | **이벤트 종류 전환**: CPU 샘플, 할당(Allocation, 바이트/개수), (async-profiler일 때) Wall-clock | 뷰어 상단 콤보에서 CPU ↔ Allocations를 바꾸면 같은 3개 뷰가 다시 그려진다 |
| P-M6 | **소스 이동**: 프레임 더블클릭이나 F4로 Java/Kotlin 소스의 해당 줄로 간다 | Kotlin 람다, `$$SpringCGLIB$$` 프록시 프레임도 원래 클래스로 간다 |
| P-M7 | **힙 덤프 기본 분석**: 붙은 프로세스에서 "Capture Heap Dump", `.hprof` 열기 → 클래스 히스토그램(인스턴스 수, shallow size), 클래스 이름 검색, 소스 이동 | 1 GB 이하 덤프가 백그라운드에서 분석되고 상위 클래스 표가 보인다 |
| P-M8 | **스냅샷 목록**: 도구 창 `Phantom Profiler`에 이번 세션과 `system/phantom-profiler/snapshots/`의 결과 | 지난 녹화를 다시 연다, 탐색기에서 보기 |

**중복 탐지 (`phantom-duplicates`)**

| # | 기능 | 사용자가 보는 결과 |
|---|---|---|
| D-M1 | **Analyze Duplicates 액션** (Code 메뉴, 프로젝트 뷰 컨텍스트 메뉴): 범위 선택(`AnalysisScope`), 최소 크기 | 백그라운드 진행 표시 뒤 도구 창 `Duplicates`에 그룹 목록이 뜬다 |
| D-M2 | **Java/Kotlin 정규화 PSI 해시**: 지역 변수, 파라미터, 리터럴 값 익명화 옵션 | 이름만 다른 두 메서드 본문이 같은 그룹으로 묶인다 |
| D-M3 | **결과 도구 창**: 그룹(크기·발생 수 순 정렬) → 발생 위치, 두 위치 diff 보기, 소스 이동 | 그룹을 고르면 두 조각이 diff 창으로 나란히 보인다 |
| D-M4 | **에디터 inspection `PhantomDuplicatedCode`** (기본 **꺼짐**): 켜면 **열린 파일 안**과 **마지막 분석 결과**에 대해서만 하이라이트 | Settings > Inspections에서 켜면 현재 파일 내부 중복에 weak warning이 보인다 |

### 1.3 v0.2 이후

- 프로파일러: 스냅샷 비교(diff flame graph), 에디터 줄 거터에 self/total 시간 표시(LineProfiler 상당, `InlayHintsProvider` 또는 `LineMarkerProvider`), Lock/Monitor 이벤트 뷰, JUnit/Gradle `test` 실행 구성 프로파일링, CPU·힙 실시간 차트(JMX `MemoryMXBean`), 힙 덤프의 retained size·dominator·GC root 경로(community `GCRootPathsTree` [V: `platform/platform-impl/src/com/intellij/diagnostic/hprof/analysis/GCRootPathsTree.kt`] 참고), Docker 안 JVM 프로파일링(`phantom-docker`와 연동), 원격 JVM(SSH) 프로파일링.
- 중복 탐지: 증분 캐시(파일별 해시 결과를 `PsiModificationTracker` 기준으로 메모리 보관, 2.3 참고), 다중 모듈 프로젝트에서 "테스트 코드 제외" 기본 필터, Extract Method 제안 연결(community `ExtractMethodProcessor`), CLI `appStarter`(CI용 리포트, community `DuplocatorHashCallback.writeDuplicates` [V] 재사용), YAML/SQL 중복.

### 1.4 비목표

- Ultimate 프로파일러와 같은 스냅샷 형식이나 UI 배치를 재현하는 것. 형식은 표준 JFR(`.jfr`)과 HPROF(`.hprof`)만 쓴다.
- Windows/Linux 전용 기능(perf_events, `ptrace` 기반 attach 우회). 대상은 macOS(aarch64)이고, Linux는 async-profiler 바이너리만 같이 넣는다 [A].
- 원격 디버거식 "line profiling"(바이트코드 계측). 샘플링만 한다.
- **시작 시 실행되는 파일 기반 인덱스**(`FileBasedIndexExtension`)로 중복을 미리 계산하는 것. Ultimate의 `HashFragmentIndex` 방식을 의도적으로 버린다 (2장).
- JS/TS/CSS/XML/HTML/Groovy 중복 탐지. Ultimate는 12개 언어에 `duplicateScope`를 등록한다 [`ult-lib-closed.md` 2.3, observed]. 우리는 Java와 Kotlin만 한다.
- 라이선스, 인증, 활성화 코드. 손대지 않는다. Ultimate와 같은 플러그인 ID, EP 이름, inspection short name(`DuplicatedCode`)을 쓰지 않는다.

---

## 2. Ultimate 대비 경량화 원칙

측정된 Ultimate 비용과 이 설계의 대응이다.

| # | Ultimate에서 관측된 비용 | 근거 | 이 설계의 대응 |
|---|---|---|---|
| L1 | 중복 탐지가 **`fileBasedIndex` `HashFragmentIndex`**로 구현되어, 프로젝트를 열거나 재스캔할 때마다 인덱싱 워커가 Java, Kotlin, Groovy, JS, TS, CSS, XML, HTML 파일을 거친다. 10-16 JFR에서 샘플은 6개(0.1%)로 작지만, 그중 5개가 `slowPathIfFileTypeHintUnsure` 입력 필터 경로였다. 디스크 크기는 미측정 | `ult-lib-closed.md` 2.3, 142–146행 (observed / unknown) | **인덱스를 만들지 않는다.** 사용자가 액션을 누를 때만 범위 안 파일의 PSI를 읽어 해시한다. 결과는 메모리에만 두고 프로젝트를 닫으면 버린다. 재스캔 경로에 우리 코드가 0줄이다 |
| L2 | `DuplicatedCode` inspection이 `enabledByDefault=true`, `runForWholeFile=true`라서 모든 파일 하이라이트 패스에 끼어든다 | `ult-lib-closed.md` 98행 (observed) | `PhantomDuplicatedCode`는 **기본 꺼짐**이고, 켜도 파일 내부 비교와 마지막 분석 결과 조회만 한다. 프로젝트 전역 조회를 하이라이트 패스에서 하지 않는다 |
| L3 | 시작 15 s 동안 머신 CPU 92–96% 포화. 첫 smart mode 뒤 전체 재스캔(약 50만 파일)이 사용 가능 시점을 16.2 s로 밀었다 | `README.md` 1번 (observed) | 두 플러그인 모두 `postStartupActivity`/`projectActivity`/`fileBasedIndex`/`stubIndex`를 **하나도** 등록하지 않는다. 4장 T0의 grep 검사로 강제한다 |
| L4 | 프로파일러 공통 모듈이 코어 플러그인의 content module로 **의존성만 맞으면 로드**된다 (`profiler.*` 속성 없음 = optional이지만 조건이 항상 참). 클래스 996개, 확장 등록이 1.5 MB짜리 단일 `plugin.xml`에 합쳐져 있다 | `ult-lib-closed.md` 63, 81–84행 (observed) | 프로파일러는 별도 번들 플러그인이라 Settings > Plugins에서 끌 수 있다. 실행기, 액션, 파일 타입, 도구 창 팩토리만 등록하고 서비스는 전부 light service(첫 사용 때 생성)다 |
| L5 | `Profiler` 도구 창이 항상 등록되어 있다 | 설명자 `toolWindowAllowlist id="Profiler"` [V] | 도구 창은 `ToolWindowFactory.isApplicableAsync`에서 **스냅샷이 하나라도 있을 때만** 보인다 (아래 3.2). 처음 녹화하면 `ToolWindowManager`로 등록한다 |
| L6 | async-profiler 네이티브 라이브러리가 IDE `lib/`에 상주 | 설치본 목록 [V] | 우리도 번들하지만 **IDE 프로세스에는 로드하지 않는다.** 대상 JVM의 `-agentpath`나 attach `loadAgentPath`로만 쓴다 |

공통 원칙:

1. **기술 감지 후 활성화.** 프로파일러 실행기는 `ProgramRunner.canRun`에서 실행 구성이 `JavaRunConfigurationBase`일 때만 참이다. JVM 프로젝트가 아니면 메뉴에 나타나지 않는다. 중복 탐지 inspection은 `language="JAVA"`와 `language="kotlin"`으로 등록해서 다른 언어 파일에는 인스턴스화조차 되지 않는다.
2. **증분 모델.** 스냅샷 모델은 한 번 파싱하면 불변(immutable) 트리이고, 필터나 이벤트 종류를 바꾸면 **파생 뷰만** 다시 만든다. 원본 `.jfr`을 다시 읽지 않는다. 중복 분석 결과는 파일 단위 해시 묶음이라 v0.2에서 바뀐 파일만 다시 해시할 수 있게 구조를 잡는다.
3. **프로젝트 전역 무효화 없음.** 어떤 `ModificationTracker`도 프로젝트 전역 트래커(`PsiModificationTracker.MODIFICATION_COUNT`)에 결과를 묶지 않는다. 중복 결과는 "분석 시점의 스냅샷"으로 표시하고, 파일이 바뀌면 그 파일의 발생 위치에만 "stale" 표시를 한다 (`PsiFile.modificationStamp` 비교).
4. **EDT에서 무거운 일 금지.** JFR 파싱, HPROF 파싱, PSI 해시, 소스 위치 해석은 전부 코루틴 `Dispatchers.Default`/`readAction`에서 한다. EDT는 이미 만들어진 모델을 그리기만 한다.
5. **IDE 힙 보호.** 스냅샷 모델은 프레임을 인터닝(같은 `(class, method, line)`은 같은 객체)해서 크기를 줄이고, 1 GB를 넘는 `.hprof`는 경고 후 분석한다. 분석 중 메모리 상한을 넘으면 중단한다 (3.6).

---

## 3. 아키텍처

### 3.1 모듈 배치

```
plugins/phantom-profiler/
├── intellij.phantom.profiler.iml                  # 메인 모듈 (설명자 전용)
├── resources/META-INF/plugin.xml                  # id: dev.phantom.profiler
├── core/      → intellij.phantom.profiler.core    # 스냅샷 모델, JFR/collapsed 파서, HPROF 히스토그램 (UI·PSI 없음)
├── ui/        → intellij.phantom.profiler.ui      # 뷰어(FileEditor), 플레임 그래프 컴포넌트, 도구 창
├── jvm/       → intellij.phantom.profiler.jvm     # 실행기, ProgramRunner, RunConfigurationExtension, attach, 소스 이동 (com.intellij.java 의존)
├── gradle/    → intellij.phantom.profiler.gradle  # Gradle 위임 실행 지원 (org.jetbrains.plugins.gradle 의존)
├── kotlin/    → intellij.phantom.profiler.kotlin  # Kotlin 프레임 → KtElement 해석 보강 (org.jetbrains.kotlin 의존)
├── native/                                        # async-profiler 바이너리 (Apache 2.0), plugin-local 리소스
│   ├── macos/libasyncProfiler.dylib
│   └── linux-{arm64,x64}/libasyncProfiler.so
└── tests/     → intellij.phantom.profiler.tests

plugins/phantom-duplicates/
├── intellij.phantom.duplicates.iml
├── resources/META-INF/plugin.xml                  # id: dev.phantom.duplicates
├── core/      → intellij.phantom.duplicates.core  # 분석 엔진(언어 무관), 결과 모델, 도구 창, 액션
├── java/      → intellij.phantom.duplicates.java  # Java 프로필 (com.intellij.java 의존)
├── kotlin/    → intellij.phantom.duplicates.kotlin# Kotlin 프로필 (org.jetbrains.kotlin 의존)
└── tests/     → intellij.phantom.duplicates.tests
```

- 콘텐츠 모듈은 기본 로딩(optional)이다. `core`만 다른 모듈이 의존하므로 같은 플러그인 안에서 `embedded` 후보지만, MVP는 build-wiring P2에 따라 기본값으로 둔다.
- 프로파일러 `core`에는 IntelliJ UI나 PSI 의존을 두지 않는다. 순수 Kotlin + `jdk.jfr`라서 단위 테스트가 빠르고, v0.2에서 CLI로도 쓸 수 있다.
- 바이너리는 3.7에서 다룬다.

### 3.2 사용하는 community 확장점과 API

**프로파일러**

| 용도 | 확장점 / API | 근거 |
|---|---|---|
| 실행기 | EP `com.intellij.executor` → `com.intellij.execution.Executor` | EP 선언 `platform/platform-resources/src/META-INF/LangExtensionPoints.xml:259` [V], 클래스 `platform/execution/src/com/intellij/execution/Executor.java` [V] |
| 실행기 묶음 | `ExecutorGroup` (JFR / async-profiler를 한 드롭다운으로) | `platform/execution/src/com/intellij/execution/executors/ExecutorGroup.kt` [V] [A: 그룹 하위 실행기를 실행 위젯에 보이는 방식은 첫 실행에서 확인] |
| 실행기 패턴 참고 | Coverage가 정확히 같은 구조다: `CoverageExecutor extends Executor`, `DefaultJavaCoverageRunner`(`programRunner`), `CoverageJavaRunConfigurationExtension`(`runConfigurationExtension`) | `plugins/coverage-common/src/com/intellij/coverage/CoverageExecutor.java:13`, `plugins/coverage-common/resources/intellij.platform.coverage.xml:48`, `plugins/coverage/resources/META-INF/plugin.xml:37,45` [V] |
| 러너 | EP `com.intellij.programRunner` → `ProgramRunner`, 구현은 `GenericProgramRunner`/`AsyncProgramRunner` | `LangExtensionPoints.xml:257` [V], `platform/execution/src/com/intellij/execution/runners/GenericProgramRunner.kt` [V] |
| JVM 옵션 주입 | `RunConfigurationExtension.updateJavaParameters(configuration, params, runnerSettings, executor)` | `java/execution/impl/src/com/intellij/execution/RunConfigurationExtension.java:33` [V], EP 선언 `java/execution/impl/resources/intellij.java.execution.impl.xml:26` [V] |
| 대안 주입 | `JavaProgramPatcher.patchJavaParameters(executor, configuration, javaParameters)` | `java/execution/openapi/src/com/intellij/execution/runners/JavaProgramPatcher.java:25` [V] |
| Kotlin 실행 구성 | `KotlinRunConfiguration : JavaRunConfigurationBase`가 `JavaRunConfigurationExtensionManager`를 부른다 | `plugins/kotlin/run-configurations/jvm/src/org/jetbrains/kotlin/idea/run/KotlinRunConfiguration.kt` (형제 문서 spring-boot.md 127행에서 확인) [V] |
| Gradle 위임 실행 | EP `org.jetbrains.plugins.gradle.executionEnvironmentProvider` → `GradleExecutionEnvironmentProvider`. 기본 구현 `GradleApplicationEnvironmentProvider`는 `open`이고 `argsString(params)`이 `protected open`이며 `params.vmParametersList`를 `jvmArgs`로 넘긴다 | EP `plugins/gradle/plugin-resources/intellij.gradle.xml:33` [V], `plugins/gradle/java/src/execution/build/GradleApplicationEnvironmentProvider.kt:15` [V], `GradleBaseApplicationEnvironmentProvider.kt:65-79` [V]. 선택은 `findFirstSafe { it.isApplicable(task) }` [V: `GradleProjectTaskRunner.kt:224`] |
| Gradle 태스크(bootRun) | EP `org.jetbrains.plugins.gradle.taskManager` → `GradleTaskManagerExtension.configureTasks(...)`, `GradleExecutionSettings.addInitScript(prefix, content)` | `intellij.gradle.xml:25` [V], `plugins/gradle/src/org/jetbrains/plugins/gradle/service/task/GradleTaskManagerExtension.java:48` [V], `GradleExecutionSettings.java:274` [V]. 디버거가 같은 방식으로 init script를 넣는다 [V: `GradleTaskManagerExtensionDebuggerBridge.kt:43`] |
| 프로세스 attach | `com.sun.tools.attach.VirtualMachine.list()/attach(pid)/loadAgentPath(...)` (JBR의 `jdk.attach` 모듈) | community 디버거가 이미 쓴다 [V: `java/debugger/impl/src/com/intellij/debugger/impl/attach/JavaAttachDebuggerProvider.java:159,264`], JBR 모듈 목록에 `jdk.attach`, `jdk.jfr`, `jdk.management.jfr` [V: 설치본 JBR `java --list-modules`] |
| attach UI 참고 | EP `com.intellij.xdebugger.attachHostProvider`/`attachDebuggerProvider` (프로세스 선택 팝업) | `platform/xdebugger-impl/resources/intellij.platform.debugger.impl.content.xml:11-12` [V]. MVP는 이 EP를 **쓰지 않고** 자체 팝업을 만든다(디버거 attach 메뉴에 섞이지 않게) |
| 스냅샷 에디터 | EP `com.intellij.fileEditorProvider`, `fileType`(`.jfr`, `.hprof`는 바이너리 파일 타입) | `PlatformExtensionPoints.xml:45,55` [V] |
| 도구 창 | EP `com.intellij.toolWindow` → `ToolWindowFactory.isApplicableAsync`, `shouldBeAvailable` | `PlatformExtensionPoints.xml:207` [V], `platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt:23,54` [V] |
| 트리 표 | `TreeTable`, `TreeTableView` (Call Tree, Method List) | `platform/platform-api/src/com/intellij/ui/treeStructure/treetable/TreeTable.java`, `ui/dualView/TreeTableView.java` [V] |
| 소스 이동 | `JavaPsiFacade.findClass`, `OpenFileDescriptor(project, file, line, col)`, `PsiNavigationSupport` | `platform/analysis-api/src/com/intellij/openapi/fileEditor/OpenFileDescriptor.java` [V], `platform/core-api/src/com/intellij/ide/util/PsiNavigationSupport.java` [V] |
| 비동기 PSI 접근 | `readAction {}`, `smartReadAction(project) {}` | `platform/core-api/src/com/intellij/openapi/application/coroutines.kt:32,45` [V] |
| HPROF 파서 | `HProfEventBasedParser`, `Histogram.create(parser, classStore)`, `ClassStore` | `platform/platform-impl/src/com/intellij/diagnostic/hprof/parser/HProfEventBasedParser.kt` (`@ApiStatus.Internal`) [V], `.../hprof/histogram/Histogram.kt:65` [V]. 패키지 전체가 `@Internal`이다 [V: `package-info.java`] |
| 프로세스 실행 | `GeneralCommandLine`, `ProcessHandlerFactory` (`jcmd` 호출용) | `platform/platform-api/src/com/intellij/execution/process/ProcessHandlerFactory.java` [V] |

**중복 탐지**

| 용도 | 확장점 / API | 근거 |
|---|---|---|
| 해시 엔진 | community 모듈 `intellij.platform.duplicates.analysis`: `DuplicatesProfileBase`(`getNodeCost`, `getLiterals`), `NodeSpecificHasherBase`, `TreeHasherBase.hash/hashCodeBlock`, `DuplocatorHashCallback(bound, discardCost)` → `getInfo(): DupInfo`, `PsiFragment`, `DuplocatorSettings`(`DISTINGUISH_VARIABLES/FIELDS/METHODS/TYPES/LITERALS`, `LOWER_BOUND=10`) | `platform/duplicates-analysis/src/com/intellij/dupLocator/...` 44개 파일 [V]. 이 모듈은 Apache 2.0 community 코드이고 community 모듈셋에 `embedded`로 이미 들어 있다 [V: `platform/platform-resources/generated/META-INF/intellij.moduleSets.ide.common.xml:397`] |
| 역할 기반 익명화 | `DuplicatesProfile.getRole(element): PsiElementRole` (변수/필드/메서드/타입 구분) | `DuplicatesProfile.java:99`, `PsiElementRole.java` [V] |
| 주의 | `DuplicatesProfile.EP_NAME = "com.intellij.duplicates.profile"`인데 이 EP는 **community에 선언되어 있지 않다** (Ultimate `intellij.platform.duplicates`가 선언한다 [V: 설명자]). 그래서 `DuplicatesProfile.findProfileForLanguage` 같은 정적 도우미는 쓰지 않고, 우리 EP `dev.phantom.duplicates.languageProfile`로 프로필을 찾는다 | [V: `DuplicatesProfile.java:18`, community XML에 EP 선언 없음(grep)] |
| 범위 선택 | `AnalysisScope`, `BaseAnalysisAction`(범위 대화상자) | `platform/analysis-api/src/com/intellij/analysis/AnalysisScope.java`, `platform/lang-impl/src/com/intellij/analysis/BaseAnalysisAction.java` [V] |
| inspection | `localInspection` → `LocalInspectionTool` | `platform/analysis-api/src/com/intellij/codeInspection/LocalInspectionTool.java` [V] |
| diff | `DiffManager`, `DiffContentFactory` (두 조각 비교) | `platform/diff-api/src/com/intellij/diff/DiffManager.java`, `DiffContentFactory.java` [V] |
| SSR 참고(직접 의존 안 함) | Java/Kotlin 노드 동등성 규칙의 참고: `JavaStructuralSearchProfile`, `KotlinStructuralSearchProfile`(K2), EP `equivalenceDescriptorProvider` | `java/structuralsearch-java/src/.../JavaStructuralSearchProfile.java`, `plugins/kotlin/code-insight/structural-search-k2/src/.../KotlinStructuralSearchProfile.kt`, `intellij.platform.duplicates.analysis.xml` [V] |

### 3.3 프로파일러 설계

#### 3.3.1 실행 흐름 (Run → Profile)

```
사용자: Kotlin/Application 실행 구성에서 "Profile with JFR"
  └─ PhantomProfilerRunner.canRun(executorId ∈ {JFR, ASYNC}, profile is JavaRunConfigurationBase)
      └─ execute(env): 스냅샷 경로 결정 system/phantom-profiler/snapshots/<run>-<ts>.jfr
          └─ PhantomProfilerRunConfigurationExtension.updateJavaParameters(..., executor)
               JFR  : -XX:StartFlightRecording=filename=<path>,settings=<profile.jfc>,dumponexit=true
               ASYNC: -agentpath:<native>/libasyncProfiler.dylib=start,event=cpu,alloc=512k,interval=10ms,file=<path>,jfr
          └─ 프로세스 종료 리스너(ProcessListener.processTerminated) → 파일 존재 확인 → 뷰어 열기
      (Gradle 위임일 때)
      └─ PhantomGradleApplicationEnvironmentProvider (order="first")
           isApplicable(task) = task.runnerSettings is PhantomProfilerRunnerSettings && super.isApplicable(task)
           argsString(params) = super.argsString(params + agentArgs)
```

- 녹화 중 실행 창 툴바에 **"Stop and Open Snapshot"** 액션을 둔다. JFR은 `jcmd <pid> JFR.dump`, async-profiler는 attach `loadAgentPath(lib, "stop,file=...")`로 앱을 죽이지 않고 결과를 꺼낸다. 앱이 끝나면 `dumponexit`/`file=` 설정으로 자동 저장된다.
- Spring Boot 앱은 보통 Ctrl+C(Stop)로 끝난다. Stop은 SIGTERM이므로 JVM 셧다운 훅이 돌아 JFR `dumponexit`이 동작한다 [A: IDE Stop 버튼이 macOS에서 SIGTERM을 먼저 보내는지 `KillableProcessHandler` 동작으로 확인]. 그래도 "Stop and Open Snapshot"을 기본 경로로 안내한다.
- Gradle 위임 경로의 `isApplicable`이 러너 설정으로 우리 실행인지 구분하는 부분은 [A]다. `ExecuteRunConfigurationTask`가 `getRunnerSettings()`를 가진 것은 [V: `platform/lang-api/src/com/intellij/task/ExecuteRunConfigurationTask.java:38`]. 실패하면 대안은 `GradleTaskManagerExtension.configureTasks`에서 init script로 `JavaExec` 태스크에 `jvmArgs`를 붙이고, 실행 ID(`ExternalSystemTaskId`)를 우리 서비스의 대기 목록과 맞추는 방식이다. 같은 대안으로 v0.2에서 Gradle `bootRun` 실행 구성도 지원한다.

#### 3.3.2 Attach (실행 중인 JVM)

1. "Attach Profiler to Process…" 액션 → 백그라운드에서 `VirtualMachine.list()` → 팝업(PID, 메인 클래스, 작업 디렉터리 `user.dir`). IDE 자신과 Gradle 데몬(`GradleDaemon`), Kotlin 데몬은 맨 아래로 내리고 회색 표시.
2. 시작: JFR은 `jcmd <pid> JFR.start name=phantom settings=profile` 또는 attach 후 `jdk.management.jfr`의 `FlightRecorderMXBean` [A: 로컬 JMX 커넥터를 `startLocalManagementAgent()`로 여는 방식이 JDK 17+ 대상에서 안정적인지]. MVP는 **`jcmd` 실행**을 택한다. 대상 JVM과 같은 버전의 `jcmd`가 필요 없고(진단 명령은 attach 프로토콜로 전달된다) IDE JBR의 `bin/jcmd`를 쓴다 [A: JBR에 `jcmd`가 포함되어 있는지. Ultimate JBR 기준으로 프로젝트 CLAUDE.md가 `jbr/Contents/Home/bin/`의 `jcmd`를 쓰라고 하므로 있다고 본다].
3. async-profiler는 `VirtualMachine.attach(pid).loadAgentPath(lib, "start,event=cpu,...")`, 중지는 `"stop,file=<path>,jfr"`.
4. 대상 JVM이 JDK 21+면 동적 에이전트 로딩 경고가 대상 콘솔에 찍힌다(JEP 451). async-profiler attach만 해당하고 JFR(`jcmd`)은 해당하지 않는다. 팝업에 한 줄로 알린다.

#### 3.3.3 스냅샷 데이터 모델 (`core`)

```kotlin
// dev.phantom.profiler.core.model
data class Frame(val className: String, val methodName: String, val descriptor: String?,
                 val line: Int, val kind: FrameKind)              // JAVA, INLINED, NATIVE, KERNEL, JIT_C1/C2
enum class EventKind { CPU, WALL, ALLOC_BYTES, ALLOC_COUNT, LOCK }

class CallTreeNode(val frame: Frame) {                            // 부모→자식, 자식은 frame 기준 병합
  val total = LongArray(EventKind.entries.size)                   // 이 노드 이하 합계
  val self  = LongArray(EventKind.entries.size)
  val children: MutableMap<Frame, CallTreeNode>                   // 파싱 끝나면 정렬된 배열로 고정(freeze)
}
class Snapshot(val source: Path, val threads: Map<Long, ThreadInfo>,
               val roots: Map<ThreadKey, CallTreeNode>,            // 스레드별 + ALL 병합
               val availableKinds: Set<EventKind>, val durationMs: Long)
```

- **파서는 하나다.** JFR과 async-profiler 모두 `.jfr`로 받는다(async-profiler의 `file=...,jfr` 또는 `.jfr` 확장자 출력 [공개 문서]). 읽기는 JDK 표준 `jdk.jfr.consumer.RecordingFile`로 한다. 쓰는 이벤트: `jdk.ExecutionSample`(CPU), `jdk.NativeMethodSample`(WALL 근사), `jdk.ObjectAllocationSample`/`jdk.ObjectAllocationInNewTLAB`/`jdk.ObjectAllocationOutsideTLAB`(할당), `jdk.JavaMonitorEnter`(LOCK, v0.2).
  - async-profiler가 쓰는 JFR 파일을 JDK `RecordingFile`이 그대로 읽는지는 [A]다. async-profiler는 JMC 호환을 목표로 하지만 커스텀 이벤트(예: wall-clock 샘플)가 있다. 실패하면 대안 2개: (a) async-profiler의 `collapsed` 텍스트 출력(`frame;frame;frame count`)을 따로 파싱한다. 형식이 단순해서 반나절이면 된다. (b) async-profiler 저장소의 `converter`(Apache 2.0)를 plugin-local 라이브러리로 넣는다. MVP 결정은 T2에서 실제 파일로 확인한 뒤 내린다.
- **프레임 인터닝**: `Frame`은 `HashMap<Frame, Frame>`으로 인터닝한다. 10분 녹화(약 6만 샘플 × 깊이 80)도 고유 프레임은 수만 개 수준이라 모델이 수십 MB 안에 든다 [A: 사용자 프로젝트에서 측정].
- **파생 뷰**: Flame Graph와 Call Tree는 같은 `CallTreeNode` 트리를 쓴다. Method List(hot methods)는 트리를 한 번 순회해 `Map<Frame(line 무시), self/total>`을 만든다(재귀 호출은 total 중복 집계를 막기 위해 경로 위 같은 메서드를 한 번만 센다). Back Traces(호출자 트리)와 Merged Callees는 선택한 메서드 기준으로 **요청 시** 새 트리를 만든다. 원본은 바꾸지 않는다.
- **"내 코드만" 필터**: 프로젝트 소스의 패키지 접두어 목록(`ProjectFileIndex`로 소스 루트 아래 최상위 패키지를 모은다)에 맞지 않는 연속 프레임을 하나의 "[library]" 노드로 접는다. 필터 결과는 원본에서 파생한 새 트리다.
- **스레드 그룹**: Spring Boot는 `http-nio-*-exec-*`, `HikariPool-*`, `scheduling-*` 같은 이름 패턴이 있다. 스레드 이름의 숫자 접미사를 지워 그룹으로 묶는 옵션을 둔다(정규식, 설정 가능).

#### 3.3.4 뷰어 UI (`ui`)

- `PhantomSnapshotEditorProvider`(`fileEditorProvider`, `.jfr`/`.hprof`)가 `FileEditor`를 만든다. 에디터는 열자마자 "Parsing…" 패널을 보여주고 코루틴(`Dispatchers.Default`)에서 파싱한 뒤 EDT로 모델만 넘긴다.
- **Flame Graph**: `JComponent` 하나에 직접 그린다(`paintComponent`). 노드 수가 많으므로 화면 픽셀 1개보다 좁은 노드는 그리지 않고, 보이는 깊이 범위만 그린다. 색은 `FrameKind`와 "프로젝트 코드 여부"로 정한다(`JBColor` 쌍으로 라이트/다크 대응). 클릭=확대(zoom), 더블클릭=소스 이동, 마우스오버=툴팁(self/total, %), 검색어 일치 노드 강조. Icicle(위→아래)/Flame(아래→위) 토글.
- **Call Tree / Method List**: `TreeTableView` + 열(Total %, Total, Self %, Self, Name). 정렬, 검색(`TreeSpeedSearch`), Focus on subtree/Exclude subtree(파생 트리 생성).
- 이벤트 콤보(CPU/Allocations/Wall), 스레드 콤보(All / 그룹 / 개별).
- 할당 값은 바이트 기본, 토글로 개수. 할당 뷰의 리프는 **할당된 타입**이다(`objectClass` 필드를 마지막 가상 프레임으로 붙인다).

#### 3.3.5 소스 이동 (`jvm`, `kotlin`)

프레임 → 소스 해석 규칙 (전부 `smartReadAction` 안에서, 결과는 프레임별로 캐시하고 `ProjectRootModificationTracker`가 바뀌면 버린다):

1. 클래스 이름 정규화: `Foo$$SpringCGLIB$$0`, `Foo$$EnhancerBySpringCGLIB$$…`, `Foo$HibernateProxy$…`, `$$Lambda/0x…`, `Foo$bar$1`(Kotlin 람다/익명 클래스), `Foo$Companion`, `FooKt`(파일 퍼사드)를 처리한다. 접미사를 지운 바깥 클래스를 찾는다.
2. `JavaPsiFacade.findClass(fqn, projectScope ∪ librariesScope)`. Kotlin 클래스와 파일 퍼사드는 light class로 찾힌다 [A: K2 모드에서 `FooKt` 퍼사드 light class가 `findClass`로 돌아오는지. 실패하면 `kotlin` 모듈이 Kotlin 인덱스로 파일을 찾는다].
3. 줄 번호가 있으면(JFR 프레임은 대부분 있다) `navigationElement`의 파일에서 `OpenFileDescriptor(project, file, line - 1, 0)`. Kotlin `inline` 함수는 줄 번호가 호출 파일의 SMAP 범위 밖일 수 있다. 이때는 메서드 이름으로 대체한다 [A].
4. 줄 번호가 없으면 `findMethodsByName(methodName)`의 첫 결과로 간다.
5. 라이브러리 클래스는 소스 jar가 있으면 그쪽, 없으면 community 디컴파일러 뷰가 열린다(플랫폼 기본 동작).

Kotlin 소스를 위해 K2 Analysis API는 **쓰지 않는다**. 이 작업은 이름과 줄 번호만으로 충분하다. 그래서 `kotlin` 콘텐츠 모듈은 Kotlin PSI(`KtFile`, `KtNamedFunction`)와 인덱스만 쓰고, K1/K2 어느 모드에서도 같은 코드가 돈다 [A: K2 모드에서 쓸 인덱스 이름은 구현 때 확인].

#### 3.3.6 힙 덤프 (`core` + `ui`)

- 캡처: 붙은 프로세스나 실행 중인 Profile 세션에서 `jcmd <pid> GC.heap_dump <path>`. 실행 중이 아니어도 attach 팝업에서 바로 뜰 수 있게 한다.
- 분석 MVP: community `HProfEventBasedParser` + `ClassStore` + `Histogram.create(...)`로 클래스별 인스턴스 수와 shallow 바이트를 만든다 [V: API 존재]. 이 API는 IDE 자신의 OOM 리포트용이고 `@ApiStatus.Internal`이다 [V]. 같은 모노레포 번들 플러그인이라 컴파일은 되지만 업스트림 변경에 깨질 수 있다. 그래서 `core`에 `HeapHistogramSource` 인터페이스를 두고 구현 하나만 community 파서에 묶는다.
- 대안(공개 오픈소스): HPROF 형식은 공개되어 있고 레코드가 단순하다(`HPROF_UTF8`, `LOAD_CLASS`, `HEAP_DUMP_SEGMENT` 안의 `CLASS_DUMP`/`INSTANCE_DUMP`/`OBJ_ARRAY_DUMP`/`PRIM_ARRAY_DUMP`). 히스토그램만이면 자체 스트리밍 파서가 400–600줄 정도다. Eclipse MAT 파서(EPL 2.0)나 NetBeans profiler `lib.profiler.heap`(GPL+CPE)도 있지만 라이선스와 크기 때문에 MVP에서는 들이지 않는다.
- 메모리: 파서는 `FileChannel` 매핑으로 스트리밍한다(community 구현이 이미 `FileChannel`을 받는다 [V: `HProfAnalysis.kt:39`]). 객체 그래프를 IDE 힙에 올리지 않는다. retained size는 v0.2에서 디스크 기반 임시 파일로 계산한다.
- 표: 클래스 이름, 인스턴스 수, shallow size, 비율. 프로젝트 클래스 강조, 이름 검색, 소스 이동(3.3.5 재사용). JPA 사용자를 위해 "엔티티 클래스만" 필터(클래스에 `@Entity`가 붙었는지 PSI로 확인, 결과 캐시)를 둔다.

#### 3.3.7 저장과 캐시

- 스냅샷 파일: `PathManager.getSystemPath()/phantom-profiler/snapshots/`. 30일 또는 2 GB를 넘으면 오래된 것부터 지운다(도구 창을 열 때만 검사, 시작 활동 없음).
- 파싱된 모델은 에디터가 열려 있는 동안만 메모리에 둔다(`FileEditor.dispose`에서 해제). 디스크 캐시는 만들지 않는다.
- 설정(`PersistentStateComponent`, 앱 수준 light service): 기본 이벤트, 샘플 간격, 할당 샘플 크기, 스레드 그룹 정규식, async-profiler 경로 덮어쓰기.

### 3.4 중복 탐지 설계

#### 3.4.1 흐름

```
Analyze Duplicates (BaseAnalysisAction → AnalysisScope)
  └─ withBackgroundProgress(project, "Analyzing duplicates")
      1. 범위 안 VirtualFile 수집: 소스 루트만, 생성 코드(build/, generated)·제외 폴더 제외,
         언어 프로필이 있는 파일만 (*.java, *.kt)
      2. 파일마다 readAction { psiFile → profile.createVisitor(callback).visitNode(psiFile) }
         · 파일 단위로 readAction을 끊어 쓰기 작업을 막지 않는다 (cancellable)
         · 병렬: 파일 묶음을 Dispatchers.Default에서 병렬 처리, callback은 스레드별로 두고 마지막에 병합 [A: DuplocatorHashCallback이 스레드 안전하지 않다고 보고 병합 단계를 둔다]
      3. callback.getInfo(): DupInfo → 그룹(pattern) = 해시가 같은 PsiFragment 목록
      4. 후처리: 같은 파일 안에서 겹치는 조각 제거, 포함 관계(큰 그룹이 작은 그룹을 덮으면 작은 것 숨김),
         최소 크기(cost) 미만 제거, 테스트 소스 포함 여부 옵션
      5. 결과 모델 → DuplicatesResultService(프로젝트 light service)에 보관 → 도구 창 갱신
```

#### 3.4.2 언어 프로필 (`java`, `kotlin`)

```kotlin
// core: 우리 EP. Ultimate EP 이름과 겹치지 않는다
// <extensionPoint qualifiedName="dev.phantom.duplicates.languageProfile"
//                 interface="dev.phantom.duplicates.core.PhantomDuplicatesProfile" dynamic="true"/>
abstract class PhantomDuplicatesProfile : DuplicatesProfileBase() {   // community 엔진 재사용
  abstract val fileTypes: Set<FileType>
  abstract fun isGeneratedOrIgnored(file: PsiFile): Boolean          // 예: Kotlin kapt/ksp 생성물
}
```

- **Java 프로필**: `getNodeCost`는 문(statement)과 식(expression) 노드에 1, 공백·주석·괄호·세미콜론 토큰에 0. `getLiterals`는 Java 리터럴 토큰 집합. `getRole`은 `PsiLocalVariable`/`PsiParameter` → VARIABLE, `PsiField` → FIELD, `PsiMethod`/메서드 호출 이름 → METHOD, `PsiTypeElement` → TYPE. 익명화 여부는 `DuplocatorSettings.DISTINGUISH_*`가 정한다 [V: 설정 필드 존재]. community에는 Java용 `DuplicatesProfile` 구현이 **없다** [V: grep 결과 구현체는 SSR 쪽 `StructuralSearchProfileBase`와 Groovy equivalence뿐]. 그래서 이 부분이 새로 쓰는 코드다.
- **Kotlin 프로필**: Kotlin PSI(`KtBlockExpression`, `KtCallExpression`, `KtProperty`, `KtParameter`, `KtNamedFunction`, `KtStringTemplateExpression`)만 쓴다. 해시는 **구문 트리만** 보면 되므로 K2 Analysis API, 타입 해석, UAST가 필요 없다. 이것이 Kotlin 지원을 가볍게 만드는 핵심이다. 그래서 K1/K2 모드와 무관하다. 문자열 템플릿 안 `${}`는 식으로 다룬다. 확장 함수 수신자, named argument는 구조로 본다.
  - 주의할 Kotlin 관용구: 데이터 클래스 선언, `@Entity` 클래스의 필드 목록, Spring `@RestController`의 짧은 위임 메서드는 구조상 서로 비슷하다. 거짓 양성을 줄이려고 기본 최소 cost를 Kotlin은 Java보다 높게(예: 20) 잡고, **클래스 본문 선언 목록(프로퍼티 나열)은 해시 대상에서 뺀다**. 해시 대상은 함수 본문과 블록이다.
- UAST를 쓰지 않는 이유: UAST는 Java/Kotlin을 한 모델로 보게 해 주지만, 노드 변환 비용이 있고 원본 텍스트 범위가 정확히 맞지 않는 경우가 있다. 언어 간(Java↔Kotlin) 중복은 v0.2 이후 검토한다.
- `DuplicatesProfileBase.createVisitor`는 앱 설정 `DuplocatorSettings.getInstance()`를 쓴다 [V: `DuplicatesProfileBase.java:15-17`]. 이 설정은 community 모듈의 `applicationService`/`applicationSettings`로 이미 등록되어 있다 [V: `intellij.platform.duplicates.analysis.xml`]. 설정 화면은 우리 `applicationConfigurable`(Editor > Phantom Duplicates)에서 이 값을 편집한다.
- 기본 `NodeSpecificHasherBase`의 노드 해시가 Java/Kotlin 식별자를 역할별로 제대로 익명화하는지는 [A]다. 부족하면 `NodeSpecificHasher`(추상 클래스, 53줄 [V])를 직접 상속한 언어별 해셔를 만든다. 엔진(`TreeHasherBase`, `DuplocatorHashCallback`)은 그대로 쓴다.

#### 3.4.3 결과 모델과 무효화

```kotlin
class DuplicateGroup(val hash: Int, val cost: Int, val occurrences: List<Occurrence>)
class Occurrence(val file: VirtualFile, val range: TextRange,
                 val pointer: SmartPsiFileRange,          // 편집 후에도 위치 추적
                 val stampAtAnalysis: Long)               // PsiFile.modificationStamp
class DuplicatesReport(val scope: String, val createdAt: Instant, val groups: List<DuplicateGroup>)
```

- 결과는 **분석 시점의 스냅샷**이다. 파일이 바뀌면 그 발생 위치에만 "(changed)" 표시를 하고 다시 계산하지 않는다. 다시 보려면 Re-run 버튼(같은 범위)을 누른다.
- 보관은 `DuplicatesResultService`(project-level light service)의 메모리. 디스크에 쓰지 않는다. 최근 1개 리포트만 둔다.
- `SmartPsiFileRange` 사용은 [A: `SmartPointerManager.createSmartPsiFileRangePointer`]. 수천 개 포인터가 편집 성능에 주는 비용을 피하려고, 그룹 수 상한(기본 500)과 "도구 창을 닫으면 포인터 해제"를 둔다.

#### 3.4.4 inspection `PhantomDuplicatedCode` (기본 꺼짐)

- `localInspection language="JAVA"`/`language="kotlin"`, `enabledByDefault="false"`, `level="WEAK WARNING"`, `runForWholeFile="false"`.
- 동작 1(파일 내부): 현재 파일만 해시해서 파일 안 중복을 보고한다. 파일 하나의 해시는 수 ms 수준이다 [A: 사용자 프로젝트 최대 파일에서 측정].
- 동작 2(교차 파일): `DuplicatesResultService`에 마지막 리포트가 있고 이 파일이 포함되어 있으면 그 결과를 **조회만** 한다. 하이라이트 패스에서 다른 파일을 해시하지 않는다.
- Quick fix: "Show all occurrences"(도구 창으로 이동), "Compare with…"(diff).

#### 3.4.5 도구 창 `Duplicates`

- `ToolWindowFactory.isApplicableAsync`는 false로 두고 첫 분석 때 `ToolWindowManager.registerToolWindow`로 등록한다 [V: `ToolWindowManager.kt:41,47`]. 시작 시 아무것도 없다.
- 왼쪽: 그룹 목록(cost × 발생 수 내림차순, 파일 수, 첫 줄 미리보기). 오른쪽: 선택한 두 발생의 diff(`DiffManager`), 위/아래 화살표로 발생 바꾸기(Ultimate의 SendToLeft/Right 상당).
- 필터: 테스트 소스 숨기기, 같은 파일 안 중복만, 최소 cost 슬라이더(재분석 없이 후처리 단계만 다시).

### 3.5 스레딩 규칙

| 작업 | 스레드 | 취소 |
|---|---|---|
| JFR/HPROF 파싱, 파생 트리 생성 | `Dispatchers.Default` (코루틴, 에디터 `Disposable`에 묶인 scope) | 에디터 닫으면 취소 |
| 소스 해석 | `smartReadAction` (dumb mode에서는 대기) | 툴팁/이동 요청마다 |
| `jcmd`, attach | `Dispatchers.IO` | 30 s 타임아웃 |
| 중복 해시 | 파일 단위 `readAction`, `withBackgroundProgress` | 사용자가 취소, 쓰기 작업이 오면 readAction 재시작 |
| UI 갱신 | EDT (`Dispatchers.EDT`), 모델 교체만 | — |

EDT에서 PSI 조회나 파일 I/O를 하지 않는다. 플레임 그래프 `paintComponent`는 이미 펼쳐 둔 사각형 배열만 그린다.

### 3.6 크기와 한계

- 모델 크기 상한: 고유 프레임 50만, 노드 500만을 넘으면 "깊이 N 이하로 자름" 경고를 띄우고 잘라서 연다.
- HPROF: 1 GB 초과는 확인 대화상자, 4 GB 초과는 MVP에서 지원하지 않는다고 안내한다(IDE `-Xmx` 기본 2 GB 가정 [A: Phantom 기본 vmoptions]).
- 중복 분석: 파일 5만 개 초과 범위는 확인 대화상자.

### 3.7 async-profiler 바이너리 배포

- 라이선스: async-profiler는 Apache License 2.0이다 [공개 문서]. `NOTICE`/`LICENSE`를 플러그인 디렉터리에 함께 넣는다.
- 버전은 공개 릴리스 하나로 고정하고 체크섬을 저장소에 남긴다. 빌드는 Bazel `http_file`로 받거나, MVP에서는 `native/`에 커밋한다 [A: 저장소에 바이너리를 커밋할지 build-wiring P7과 맞춰 결정].
- 배치: `PluginLayout`에 plugin-local 리소스 디렉터리로 넣는다 (build-wiring 3.4의 `pluginAutoWithCustomDirName` 경로) [A].
- macOS: 서명·공증된 DMG 안의 dylib는 대상 JVM이 `-agentpath`로 열 때 Gatekeeper 격리(quarantine) 문제가 없어야 한다. 앱 번들과 함께 서명되면 문제없다고 본다 [A]. 문제가 생기면 실행 전에 `system/` 아래로 복사해 쓴다.
- 바이너리가 없거나 로드에 실패하면 async-profiler 실행기를 비활성화하고 JFR만 남긴다. JFR은 외부 의존이 없다.

---

## 4. 구현 단계

각 단계의 수락 기준은 사용자가 IDE에서 직접 볼 수 있는 결과다. 일수는 1인 기준 대략치다.

| # | 작업 | 일 | 수락 기준 |
|---|---|---|---|
| T0 | 두 플러그인 골격(build-wiring 레시피), `bundledPluginModules` 등록, **금지 EP 검사**: `grep -E 'postStartupActivity|backgroundPostStartupActivity|projectActivity|fileBasedIndex|stubIndex' plugins/phantom-{profiler,duplicates}/**/*.xml`이 비어야 한다 | 1 | Settings > Plugins에 "Phantom Profiler", "Phantom Duplicates"가 Bundled로 보인다. 빈 프로젝트를 열었을 때 두 플러그인 클래스가 로드되지 않는다(`-verbose:class`나 JFR `jdk.ClassLoad`로 `dev.phantom.profiler`/`duplicates` 0개) |
| T1 | `core` 스냅샷 모델 + JFR 파서(`RecordingFile`) + Method List 계산 + 단위 테스트(작은 `.jfr` 테스트 데이터) | 2 | `File > Open`으로 `.jfr`을 열면(임시 텍스트 뷰라도) 상위 20개 hot method와 비율이 보인다 |
| T2 | async-profiler 출력 호환 확인: 같은 앱을 async-profiler `jfr` 출력으로 녹화해 T1 파서로 읽기. 실패 시 `collapsed` 파서 추가 | 1 | async-profiler 결과 파일도 같은 표로 열린다 |
| T3 | Profile 실행기 2개 + `ProgramRunner` + `RunConfigurationExtension` (IDEA 빌드/실행 모드) + 종료 시 자동 열기 + "Stop and Open Snapshot" | 3 | Gradle 위임을 끈 상태에서 사용자 프로젝트의 Spring Boot `Application` 실행 구성을 "Profile with JFR"로 띄우고 API를 몇 번 호출한 뒤 Stop and Open → 결과 탭이 열린다 |
| T4 | Gradle 위임 실행 지원(`PhantomGradleApplicationEnvironmentProvider`) | 2 | 사용자 프로젝트 기본 설정(Gradle 위임)에서 T3과 같은 결과. Run 콘솔에 Gradle 태스크 출력과 함께 `-XX:StartFlightRecording`이 적용된 것이 보인다 |
| T5 | 뷰어 UI: Call Tree + Method List(`TreeTableView`), 이벤트/스레드 콤보, 검색, "내 코드만" | 3 | 결과 탭에서 CPU ↔ Allocations 전환, `http-nio-*` 스레드 그룹 선택, "내 코드만"을 켜면 프로젝트 패키지 프레임만 남는다 |
| T6 | Flame Graph 컴포넌트(zoom, 툴팁, 검색 강조, 라이트/다크) | 3 | 플레임 그래프에서 컨트롤러 메서드를 클릭해 확대하고, 검색어 `Repository`가 강조된다. 10분 녹화에서도 스크롤·확대가 끊기지 않는다(60 fps 근처, 체감) |
| T7 | 소스 이동(Java/Kotlin, 프록시·람다·퍼사드 정규화) + `kotlin` 모듈 보강 | 2 | `$$SpringCGLIB$$` 프레임, Kotlin 람다 프레임, `FooKt` 프레임을 더블클릭하면 각각 원래 Kotlin 파일의 줄로 간다 |
| T8 | Attach 팝업 + JFR(`jcmd`) / async-profiler(`loadAgentPath`) 시작·중지 | 2 | 터미널에서 `./gradlew bootRun`으로 띄운 앱이 팝업에 보이고, Start → 30초 → Stop 후 결과가 열린다 |
| T9 | 힙 덤프 캡처 + 히스토그램 뷰(community 파서 래핑) + 엔티티 필터 | 2 | 붙은 앱에서 Capture Heap Dump → 표에 상위 클래스가 보이고, "엔티티만"을 켜면 `@Entity` 클래스만 남는다 |
| T10 | 도구 창 `Phantom Profiler`(스냅샷 목록, 지연 등록), 보관 정책, 설정 화면 | 1 | 첫 녹화 전에는 도구 창이 없고, 녹화 후 생긴다. 지난 스냅샷을 다시 연다 |
| **프로파일러 소계** | | **22** | |
| T11 | 중복 `core`: 우리 EP, 분석 서비스(범위 수집, 파일 단위 readAction, 병렬, 병합, 후처리), 결과 모델 | 2 | (내부) 테스트에서 두 파일의 같은 블록이 한 그룹으로 나온다 |
| T12 | Java 프로필 + light 테스트 | 1.5 | Java 테스트 프로젝트에서 이름만 다른 두 메서드가 묶인다 |
| T13 | Kotlin 프로필 + light 테스트 + 거짓 양성 규칙(선언 목록 제외, 높은 최소 cost) | 2 | 사용자 프로젝트 `command-core` 모듈 분석에서 복사된 서비스 로직이 그룹으로 나오고, 엔티티 프로퍼티 나열은 나오지 않는다 |
| T14 | Analyze Duplicates 액션 + 도구 창(그룹 목록, diff, 이동, 필터, 지연 등록) + 설정 화면 | 2.5 | Code > Analyze Duplicates… → 범위 "Module 'command-core'" → 도구 창에서 그룹을 고르면 diff가 나란히 보인다 |
| T15 | `PhantomDuplicatedCode` inspection(기본 꺼짐, 파일 내부 + 마지막 결과 조회) | 1 | 켜면 현재 파일 안 중복에 weak warning, 끄면 하이라이트 시간이 변하지 않는다 |
| **중복 탐지 소계** | | **9** | |
| T16 | 측정: 플러그인 켬/끔 시작 시간과 유휴 CPU 비교(2.L3 검증), 사용자 프로젝트 분석 시간 기록 | 1 | 두 플러그인을 켜도 첫 smart mode 시간 차이가 측정 오차(±3%) 안이다. 결과를 `docs/phantom/`에 남긴다 |
| **합계 (MVP)** | | **32** | |

순서 이유: T1–T2에서 형식 호환을 먼저 확정해야 T3 이후의 수집 경로가 하나로 정해진다. 중복 탐지는 프로파일러와 독립이라 T11부터 병렬로 진행할 수 있다.

---

## 5. 테스트 방법

### 5.1 자동 테스트 (community 테스트 프레임워크)

| 대상 | 방식 | 근거 |
|---|---|---|
| `core` 파서·모델 | 순수 JUnit5. `tests/testData/`에 작은 `.jfr`(수 초 녹화, 수십 KB)과 async-profiler `.jfr`/`collapsed` 샘플, 작은 `.hprof`(테스트에서 `HotSpotDiagnosticMXBean.dumpHeap`으로 생성)를 둔다. 검증: 샘플 수 합계, 특정 메서드 self/total, 재귀 집계, 필터 결과, 인터닝 후 프레임 수 | community HPROF 테스트가 같은 방식이다 [V: `platform/platform-tests/testSrc/com/intellij/diagnostic/hprof/HProfEventBasedParserTest.kt`] |
| 소스 이동 | `LightJavaCodeInsightFixtureTestCase`에 Java 클래스를 넣고 프레임 → `PsiElement` 해석 검증. Kotlin은 `KotlinLightCodeInsightFixtureTestCase`로 람다, `Companion`, 퍼사드 `FooKt` | `java/testFramework/src/com/intellij/testFramework/fixtures/LightJavaCodeInsightFixtureTestCase.java`, `plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/KotlinLightCodeInsightFixtureTestCase.kt` [V] |
| 실행기 주입 | `JavaParameters`를 직접 만들어 `updateJavaParameters`를 부르고 VM 옵션 문자열 검증. Gradle 쪽은 `argsString` 결과 검증 | Coverage 확장 테스트 방식 참고 [A: 해당 테스트 위치] |
| 중복 Java/Kotlin | `BasePlatformTestCase`/Kotlin light fixture에 파일 2–3개를 넣고 분석 서비스를 동기 호출. 기대 그룹을 `testData/*.expected.txt`(그룹별 파일:줄 범위)로 비교. 거짓 양성 케이스(데이터 클래스, 엔티티 필드 나열, 짧은 위임 메서드)는 "그룹 없음"을 기대 | `platform/testFramework/src/com/intellij/testFramework/fixtures/BasePlatformTestCase.java` [V] |
| inspection | `myFixture.enableInspections(PhantomDuplicatedCodeInspection())` + `checkHighlighting()` | 플랫폼 표준 |
| 금지 EP | T0의 grep을 테스트로 고정(설명자 XML을 읽어 금지 태그가 없음을 확인) | — |

실행: `./tests.cmd --module intellij.phantom.profiler.tests --test '*'`, `--module intellij.phantom.duplicates.tests` (build-wiring M5 방식) [A: 정확한 플래그는 build-wiring 문서 기준].

### 5.2 수동 확인 (사용자 프로젝트 `~/orca/workspaces/prototype-reservation-system`)

이 디렉터리는 worktree 여러 개(`docs-readme`, `feat-command-core`, `feat-contract`, `infro`)로 되어 있다 [V]. IDE에서는 그중 하나(예: `infro`)만 프로젝트로 연다.

1. **기본 비용 없음**: Phantom IDEA를 두 플러그인 켬/끔으로 각각 3회 cold start. idea.log의 smart mode 시각과 시작 직후 90초 JFR을 비교한다. 두 플러그인 클래스가 시작 중 로드되지 않았는지 JFR 스택에서 `dev.phantom.profiler`/`dev.phantom.duplicates`를 검색한다(0건이 기대값).
2. **프로파일링 시나리오**: Spring Boot 앱을 "Profile with JFR"로 띄우고 예약 생성 API를 `http` 클라이언트나 `curl`로 100회 호출 → Stop and Open. 확인: (a) 플레임 그래프에서 컨트롤러 → 서비스 → Repository → Hibernate → JDBC 경로가 보인다, (b) "내 코드만"으로 프로젝트 프레임만 남는다, (c) 서비스 메서드 프레임 더블클릭으로 Kotlin 소스 줄로 간다, (d) Allocations로 바꾸면 Jackson/Hibernate 할당이 상위에 보인다.
3. **async-profiler**: 같은 시나리오를 async-profiler 실행기로 반복. Wall-clock에서 DB 대기 시간(소켓 read)이 보이는지 확인한다.
4. **Gradle 위임**: Settings > Build Tools > Gradle에서 "Build and run using: Gradle"(기본)과 "IntelliJ IDEA"를 바꿔 가며 2번 반복.
5. **Attach**: 터미널 `./gradlew bootRun` → Attach Profiler → 30초 → 결과. 그다음 Capture Heap Dump → 히스토그램에서 엔티티 필터.
6. **중복**: Analyze Duplicates를 모듈 하나와 프로젝트 전체로 실행. 걸린 시간, 그룹 수, 상위 10개 그룹의 적중 여부(사람이 보고 "진짜 중복/거짓 양성" 표시)를 기록한다. 분석 중에도 에디터 입력이 막히지 않는지(타이핑 지연 체감, freeze 리포트 0건) 확인한다.
7. **유휴 비용**: 분석과 프로파일링을 한 번씩 한 뒤 5분 유휴 상태 JFR에서 두 플러그인 스레드/스택이 0인지 확인한다.

---

## 6. 리스크와 남은 의문

| # | 리스크 / 의문 | 영향 | 대응 | 상태 |
|---|---|---|---|---|
| R1 | async-profiler가 쓴 `.jfr`을 JDK `RecordingFile`이 그대로 읽는지 | 파서 2개가 필요할 수 있다 | T2에서 실제 파일로 확인, 실패 시 `collapsed` 파서(반나절) | [A] |
| R2 | Gradle 위임 실행에서 `ExecuteRunConfigurationTask.runnerSettings`로 우리 실행인지 구분할 수 있는지, `order="first"` 제공자가 기본 제공자보다 먼저 선택되는지 | 사용자 기본 설정에서 프로파일링이 안 된다 | 대안: `GradleTaskManagerExtension` init script로 `JavaExec.jvmArgs` 주입 | [A] |
| R3 | community HPROF 파서가 `@ApiStatus.Internal`이고 IDE 자체 분석용이다 | 업스트림 머지 때 깨질 수 있다 | `HeapHistogramSource` 뒤에 숨기고, 깨지면 자체 스트리밍 파서(400–600줄)로 교체 | [V: Internal 표시] |
| R4 | macOS에서 번들 dylib를 대상 JVM이 `-agentpath`로 열 때 서명/격리 문제 | async-profiler 실행기가 실패한다 | JFR 실행기는 영향 없음. 실패 시 `system/`으로 복사 후 로드, 그래도 실패하면 비활성화 | [A] |
| R5 | JDK 21+ 대상의 동적 에이전트 로딩 경고, 향후 기본 금지 가능성 | attach 모드 async-profiler가 막힐 수 있다 | JFR attach(`jcmd`)는 해당 없음. 경고를 UI에 안내, 실행기 모드(`-agentpath`)를 권장 | [공개 문서: JEP 451] |
| R6 | community `NodeSpecificHasherBase`의 기본 노드 해시가 Java/Kotlin 식별자 익명화에 충분한지 | 이름만 다른 중복을 놓치거나 과하게 묶는다 | 언어별 `NodeSpecificHasher` 하위 클래스로 교체(엔진 유지) | [A] |
| R7 | Kotlin 백엔드 코드의 구조적 유사성(엔티티, DTO, 위임 컨트롤러)으로 거짓 양성이 많을 수 있다 | 도구를 안 쓰게 된다 | 선언 목록 제외, Kotlin 최소 cost 상향, 테스트 소스 기본 제외, 수동 확인 6번에서 정밀도 기록 | [A] |
| R8 | `DuplocatorHashCallback`의 스레드 안전성 | 병렬 해시 결과 오류 | 스레드별 콜백 후 병합. 병합이 해시 내부 구조에 의존하면 MVP는 단일 스레드로 시작 | [A] |
| R9 | K2 모드에서 Kotlin 파일 퍼사드(`FooKt`) light class 조회, `inline` 함수 줄 번호(SMAP) | 일부 프레임 이동 실패 | 이름 기반 대체 경로, Kotlin 인덱스 조회 | [A] |
| R10 | `jcmd`가 Phantom 번들 JBR에 포함되어 있는지 | JFR attach 실패 | 없으면 프로젝트 SDK의 `bin/jcmd`를 쓰고, 둘 다 없으면 attach API + 진단 명령(`HotSpotVirtualMachine.executeJCmd`, 내부 API)로 대체 [A] | [A] |
| R11 | Ultimate가 `DuplicatedCode` 인덱스로 얻는 "편집 중 실시간 교차 파일 경고"를 우리는 의도적으로 포기한다 | 기능 차이 | 2장 L1/L2 근거로 수용. v0.2 증분 캐시로 일부 회복 | 설계 결정 |
| R12 | `HashFragmentIndex`의 실제 디스크 크기와 재스캔 시간 기여는 Ultimate에서도 미측정 | 경량화 이득의 크기를 정량화하지 못했다 | T16에서 Phantom 쪽 비용 0을 보이는 것으로 대신하고, Ultimate 쪽은 `system/index/hashfragmentindex*` 크기를 별도로 잰다 | [U] (`ult-lib-closed.md` 146, 210행) |
| R13 | 스냅샷 모델 메모리 (긴 녹화, 깊은 스택) | IDE 힙 압박 | 인터닝, 노드 상한, 에디터 닫으면 해제 | [A] |
| R14 | async-profiler 바이너리를 저장소에 커밋할지, 빌드 때 받을지 | 저장소 크기, 재현성 | build-wiring P7(새 서드파티는 plugin-local)과 함께 결정 | 미결 |
