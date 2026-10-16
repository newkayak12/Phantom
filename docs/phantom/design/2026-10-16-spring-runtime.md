# phantom-spring-runtime 설계 (v0.1)

- 날짜: 2026-10-16
- 대상 트리: `phantom-idea` (intellij-community `idea/262.10968.63` 기반, branch `main`)
- 사용자: Kotlin + Spring Boot 3.4 + JPA + Gradle 백엔드 개발자. 검증 프로젝트는 `~/orca/workspaces/prototype-reservation-system`이다. 이 폴더에는 worktree가 여러 개 있다(`feat-contract`, `feat-command-core`, `infro`, `docs-readme`). 각 worktree에 Boot 앱 2개가 있다: `adapter-module`의 `com.reservation.ReservationApplication`(web, security, actuator)과 `batch-module`의 `ReservationBatchApplication`. 버전은 Spring Boot `3.4.5`, Kotlin `2.0.10`이다 [V: `feat-contract/gradle/libs.versions.toml` 2–3행, 65행에 `spring-boot-starter-actuator`]
- 표기
  - **[V]**: 이 트리나 대상 파일에서 경로, API, 값을 직접 확인함
  - **[A]**: 공개 문서나 일반 지식에 기댄 가정. 아직 확인하지 않음
  - **[관측]**: `Intellij-rea/reversed/` 노트에 기록된 Ultimate 실측값 또는 디스크립터 내용
- 관련 문서: `2026-10-16-spring-boot.md`(설정 키, 실행 구성 프로필 탭, `SpringBootDetector`), `2026-10-16-spring-core.md`(정적 빈 모델), `2026-10-16-jpa.md`, `2026-10-16-database.md`, `2026-10-16-build-wiring.md`(플러그인 배선 규칙), `../2026-10-16-feature-matrix.md` 22–25행(이 문서가 맡는 기능)

## 0. 클린룸 경계

- 입력은 네 가지만 썼다.
  1. `Intellij-rea/reversed/`의 행동·비용 기술 노트: README, `03-decompiled/2026-10-16-ult-spring-javaee.md`, `02-runtime/2026-10-16-startup-jfr.md`
  2. 기능 체크리스트로만 쓴 디스크립터 두 개
     - Ultimate 2024.3.7 `plugins/spring-boot-plugin/lib/spring-boot-plugin.jar`의 `META-INF/plugin.xml`. 이 파일에 콘텐츠 모듈 `intellij.spring.boot.run`, `intellij.spring.boot.run.diagram`, `intellij.spring.boot.mvc`가 인라인으로 들어 있다
     - 사용자가 설치한 `~/Library/Application Support/JetBrains/IntelliJIdea2026.2/plugins/spring-debugger/lib/spring-debugger.jar`의 `META-INF/plugin.xml`
  3. 이 트리의 커뮤니티 API
  4. 공개 스펙: Spring Boot Reference(Actuator, JMX, DevTools, Admin), Spring Framework Javadoc(`TransactionSynchronizationManager`, `TransactionAspectSupport`), JDI 명세
- `reversed/source/`는 열지 않았다. 디컴파일된 코드를 보지 않았다. 이 문서의 `dev.phantom.*` 클래스 이름은 모두 새로 지었다.
- 라이선스·인증 코드와는 관계가 없다. `phantom-idea`에서 바꾼 파일은 이 문서 하나뿐이다.

### 0.1 디컴파일 없이 디스크립터에서 얻은 기능 체크리스트 [관측]

| 출처 | 기능 이름 (EP 등록 / 설명 문구) | 이 설계에서 |
|---|---|---|
| `intellij.spring.boot.run` | `spring.boot.run.endpoint` id = `beans`, `health`, `env`, `conditions`. 같은 EP로 `intellij.spring.boot.mvc`에 `mappings`가 있다 | Beans, Health, Env, Mappings: MVP. Conditions: v0.2 |
| 같은 모듈 | `runDashboardCustomizer`(`order="before commonJavaCustomizer"`), `runDashboardChecker` | Services 표시: MVP |
| 같은 모듈 | `runningApplicationUpdaterProvider`와 업데이트 정책 4개(리소스 / 클래스+리소스 / 트리거 파일 / 클래스+트리거 파일). registry `spring.boot.run.config.devtools.update.enabled`("frame deactivation" 때 업데이트) | DevTools 업데이트: MVP. 프레임 비활성화 때 자동 업데이트: v0.2 |
| 같은 모듈 | `LiveBeansClassLineMarkerProvider`(language UAST), `RuntimeConditionLineMarkerProvider`(UAST), `liveBeansPanelContent` + diagram 모듈 | 편집기 런타임 gutter: v0.2 opt-in. 다이어그램: 비목표 |
| 같은 모듈 | env 탭의 "Show/Hide Values" 액션 | 값 마스킹 토글: MVP |
| spring-debugger 설명 | Context Visibility, Extended Watches, Direct Bean Method Invocation, Actual Values Display, Value Source Tracking, Loaded Configuration Files, Active DB Connection, Transaction Status, Entity State | 아래 1장 표에서 하나씩 정한다 |
| spring-debugger `core` | `debugger.additionalContextProvider`, `debugger.compoundRendererProvider`, `projectViewNodeDecorator`, Java·Kotlin `codeInsight.inlayProvider`(`isEnabledByDefault="true"`), `ExecutionListener`, `DebuggerManagerListener`, `FileEditorManagerListener` | 등록 방식을 비교하는 데 썼다(2장) |
| spring-debugger `jpa` | `JpaTransactionRendererProvider`, `JpaEntityRendererProvider`, `debugger.extraDebugNodesProvider`(`JpaTransactionExtraNodesProvider`), `database.activeConnectionInfoProvider` | Transaction 노드: MVP. Entity 상태와 DB 연결: v0.2 |
| spring-debugger `mvc` | Security inlay(Java/Kotlin) | 비목표 |

Ultimate 기능 목록에는 `loggers`나 `configprops` 엔드포인트 탭이 없다 [관측: `spring.boot.run.endpoint` 등록은 위의 5개뿐]. 작업 지시에 있어서 phantom에는 넣는다. Actuator 표준 엔드포인트라 추가 비용이 거의 없다.

## 1. 목표와 범위

### 1.1 MVP (v0.1): 앱을 띄우고 디버깅할 때 매일 쓰는 것

| # | 기능 | 사용자가 보는 것 | 전송 경로 |
|---|---|---|---|
| R1 | **Boot 실행 감지와 계측 옵션 주입** | Kotlin/Java `Application` 실행 구성으로 Boot 메인 클래스를 실행하면, 실행 구성 편집기의 "Spring Boot" 탭(phantom-spring-boot가 만든 탭)에 "Enable runtime integration (JMX)" 체크박스가 보인다. 기본값은 켜짐이다. 콘솔 첫 줄 명령행에 `-Dspring.jmx.enabled=true` 등이 들어간다 | — |
| R2 | **Services 창 표시** | Services 창의 `Spring Boot` 그룹. 아이콘으로 starting / ready / stopped 상태를 보여 준다. 노드 옆 `:8080` 링크를 누르면 브라우저가 열린다. 포트는 앱이 실제로 바인딩한 값(`local.server.port`)이다 | JMX Admin MBean |
| R3 | **Actuator 탭: Beans** | 실행 콘텐츠에 "Actuator" 탭이 붙는다. 하위 탭 Beans는 컨텍스트별 빈 목록이다(이름, 타입, scope, 의존 빈). 텍스트 필터와 "내 코드만"(프로젝트 소스에 클래스가 있는 빈) 토글이 있다. 더블클릭하면 소스로 이동한다 | JMX `Beans` |
| R4 | **Actuator 탭: Health** | 상태 트리(`UP/DOWN`, 컴포넌트별 details: db, redis, diskSpace, kafka…). 새로 고침 버튼이 있다 | JMX `Health` |
| R5 | **Actuator 탭: Mappings** | DispatcherServlet 매핑 표(메서드, 패턴, 핸들러). 핸들러 문자열을 클릭하면 컨트롤러 메서드로 이동한다 | JMX `Mappings` |
| R6 | **Actuator 탭: Environment (실효 값과 출처)** | property source 순서대로 키, 값, origin(`class path resource [persistence/hibernate.yaml] - 3:7`)을 보여 준다. origin을 클릭하면 그 파일의 줄로 이동한다. 상단 검색창에 키를 넣으면 "실제로 이긴 값"과 그 값을 가진 source가 맨 위에 강조된다. 값 표시/숨김 토글이 있다 | JMX `Env` (`environment(pattern)`, `environmentEntry(toMatch)`) |
| R7 | **Actuator 탭: ConfigProps** | `@ConfigurationProperties` 빈별 prefix, 바인딩된 값, 입력 origin | JMX `Configprops` |
| R8 | **Actuator 탭: Loggers** | 로거 트리와 effective/configured level. 콤보박스로 레벨을 바꾸면 앱에 즉시 반영된다 | JMX `Loggers` |
| R9 | **DevTools 업데이트** | `spring-boot-devtools`가 런타임 클래스패스에 있을 때만 커뮤니티 "Update Running Application" 액션(Ctrl+F10)에 "Phantom: Update classes and resources" / "Update resources" / "Touch trigger file"이 뜬다. 빌드가 끝나면 DevTools가 재시작한다 | `ProjectTaskManager` + 파일 시스템 |
| R10 | **디버거: Transaction 노드** | 중단점에서 멈췄을 때 Variables 뷰 맨 위에 `⟨Spring TX⟩ active, REQUIRED, READ_COMMITTED, readOnly=false` 노드가 생긴다. 펼치면 현재 트랜잭션 이름, 중첩된 `@Transactional` 호출 스택(메서드, propagation, isolation, rollback 규칙), 바인딩된 리소스(DataSource → Connection, EntityManagerFactory → EntityManager)가 보인다 | JDI 평가 (현재 스레드) |
| R11 | **디버거: 빈 메서드 호출** | 중단점에서 Evaluate(Alt+F8)에 `springContext_DebugLabel.getBean(ReservationService::class.java).findById(1)`처럼 쓸 수 있다. 첫 정지 때 앱의 ApplicationContext 객체에 커뮤니티 "Mark Object" 레이블 `springContext`를 자동으로 붙인다. Kotlin과 Java 평가 모두에서 동작한다 | JDI + 커뮤니티 marked object |

### 1.2 v0.2 이후

- v0.2
  - 디버거 Beans 노드: 컨텍스트의 싱글턴을 이름별로 보여 준다(`getSingleton`만 쓰고 생성은 하지 않는다)
  - 디버거 `springProperty("key")` 평가 도우미: `Environment.getProperty`를 호출한다
  - JPA 엔티티 상태 렌더러(managed/detached/new): phantom-jpa의 `JpaFacade` 선택 의존
  - Actuator Conditions 탭
  - Gradle `bootRun` 실행 지원
  - 프레임 비활성화 때 자동 DevTools 업데이트(옵션)
  - HTTP 전송 경로(원격 앱이나 Docker 컨테이너 안의 앱, `management.server.port` 사용)
- v0.3
  - 편집기 런타임 표시(opt-in): 실행 중인 앱에 실제로 빈으로 등록된 클래스에 gutter 아이콘. `application.yaml` 키 옆에 실효 값 inlay(code vision)
  - 활성 DataSource 연결 정보를 phantom-database에 넘긴다(Testcontainers 포함)
  - Kotlin 코루틴 스레드에서 트랜잭션 컨텍스트 표시
- 하지 않음(비목표)
  - 라이브 빈 다이어그램, Security inlay, JUnit/TestNG/Gradle 테스트 실행 중 Spring Debugger
  - Spring Cloud 대시보드, 원격 Kubernetes 앱 자동 탐색(docker-k8s 설계로 넘김)
  - 디버거 에이전트(`-javaagent`) 주입
  - `@Transactional` 정적 분석(phantom-spring-core 몫)
  - Ultimate Spring 플러그인과 같은 IDE에 함께 설치하는 경우. `incompatible-with com.intellij.spring.boot`로 막는다

### 1.3 사용자 프로젝트에서 MVP를 고른 근거

- `adapter-module`은 starter-web + security + actuator를 쓴다 [V]. 그래서 Actuator 기능은 바로 쓸 수 있다. 다만 **HTTP 경로에는 `SecurityConfig.filterChain`이 걸려 있다** [V: `adapter-module/src/main/kotlin/com/reservation/config/security/SecurityConfig.kt` 89–106행, role 기반 `requestMatchers`]. 그래서 IDE가 `/actuator/**`에 인증 없이 접근할 수 있다고 가정할 수 없다. MVP 전송 경로를 **JMX**로 정한 가장 큰 이유다. Spring Security는 JMX에 관여하지 않는다 [A: Spring Security는 서블릿 필터 체인에서만 동작함].
- 설정이 `spring.config.import`로 11개 파일에 나뉘어 있고, 기본 활성 프로필이 4개다(`temporary`, `local`, `stage`, `production`) [V: `adapter-module/src/main/resources/application.yaml` 1–21행]. 정적 분석만으로는 "어떤 값이 이겼는지" 알기 어려운 구조다. 그래서 R6(실효 값 + origin)이 MVP에 들어간다.
- spring-data-jpa, data-jdbc, data-redis, kafka를 함께 쓴다. 트랜잭션 매니저가 여러 개일 가능성이 있다 [A]. R10에서 리소스 맵(어느 DataSource/EMF에 묶였는지)을 보여 주는 이유다.
- `spring-boot-devtools`는 버전 카탈로그에 없다 [V: `libs.versions.toml`에서 `devtools` grep 결과 없음]. R9는 의존성이 있을 때만 켜지고, 이 프로젝트에서는 아무것도 하지 않아야 한다. 이 동작 자체가 수락 기준이다(4장 T7).

## 2. Ultimate 대비 경량화 원칙

| Ultimate에서 관측된 비용 / 등록 방식 | 근거 | phantom-spring-runtime의 대응 |
|---|---|---|
| 런타임 gutter(`LiveBeansClassLineMarkerProvider`, `RuntimeConditionLineMarkerProvider`)가 **UAST 언어 전체**에 등록된다. 앱을 실행하지 않아도 모든 Kotlin/Java 파일의 하이라이팅 패스에 provider가 참여한다 | 디스크립터 [관측] | MVP에는 line marker, inlay, code vision이 **0개**다. v0.3의 편집기 표시는 opt-in 설정이다. 켜도 "실행 중인 Boot 세션이 하나 이상 있을 때"만 provider가 일을 한다. 첫 줄에서 `SpringRuntimeSessions.hasLive()`를 확인한다(필드 하나 읽기) |
| spring-debugger가 Java와 Kotlin 각각에 `inlayProvider`를 `isEnabledByDefault="true"`로 등록하고, `projectViewNodeDecorator` 2개와 `FileEditorManagerListener`를 붙인다 | 디스크립터 [관측] | project view decorator 0개, 편집기 리스너 0개. 디버거 정보는 디버거 UI(Variables 뷰, Evaluate) 안에만 둔다 |
| 장기 실행 IDE의 freeze dump 8개 모두에 `BeansViewService.addChangeListener`, `SpringDebuggerFileEditorService` 수집 코루틴이 프로젝트마다 상주한다 | `ult-spring-javaee.md` §2.3 표 "상주 코루틴" [관측] | **상주 코루틴 0개.** 세션 서비스는 Boot 프로세스가 시작될 때 만들고, `processTerminated`에서 coroutine scope를 취소한다. 앱이 없으면 코루틴도 없다 |
| 안 쓰는 프레임워크도 확장점이 열거되는 순간 클래스가 적재된다(Spring 계열 Metaspace 약 17 MB 이상, 클래스 로더 약 100개) | `ult-spring-javaee.md` §3.2, README Q4 [관측] | 콘텐츠 모듈을 4개로 나눈다(3.1). 앱을 실행하기 전에 적재되는 클래스는 `RunConfigurationExtension` 구현 1개와 그 settings bean 1개뿐이다(목표 3개 미만). Actuator UI, JMX 클라이언트, 디버거 노드 클래스는 Boot 세션이 처음 생길 때 적재된다 |
| 시작 시 Spring 모델 계산 5–6 CPU초 | `startup-jfr.md` §3, `ult-spring-javaee.md` §3.1 [관측] | 이 플러그인은 정적 Spring 모델을 **전혀 만들지 않는다.** 빈 목록, 매핑, 설정 값은 런타임 앱이 계산한 결과를 받아서 보여 주기만 한다. 소스로 이동할 때는 FQN으로 `JavaPsiFacade.findClass` 한 번만 호출한다 |
| `postStartupActivity` / `backgroundPostStartupActivity`가 Spring 영역에 등록돼 있다 | `ult-spring-javaee.md` §2 [관측] | startup activity 0개, `preload` 서비스 0개 |
| Spring 모델 캐시는 PSI 변경마다 전체 무효화된다(inferred) | `ult-spring-javaee.md` §2.4 | 캐시 대상은 런타임 스냅숏뿐이다. 무효화 기준은 시간이 아니라 **앱 이벤트**다. 앱 Ready, DevTools 재시작(새 PID 또는 Admin MBean 재등록), 사용자의 새로 고침. PSI 변경과는 묶지 않는다 |
| (일반 원칙) 폴링 | — | 폴링은 앱 시작 직후 Ready 대기 한 번뿐이다(500 ms 간격, 최대 120 s). 그 뒤로는 탭이 보일 때 사용자가 요청한 것만 가져온다. Health 자동 새로 고침은 기본으로 꺼져 있다 |

요약하면 **"Boot 실행 구성을 시작하기 전에는 아무것도 하지 않는다"**. 측정 방법은 5.3에 있다.

## 3. 아키텍처

### 3.1 모듈 레이아웃

이름 규칙은 `2026-10-16-build-wiring.md` §3.2를 따른다(디렉터리 `plugins/phantom-<kebab>`, 모듈 `intellij.phantom.<camel>.<sub>`, 플러그인 ID `dev.phantom.*`, 패키지 `dev.phantom.<name>.<sub>`).

```
plugins/phantom-spring-runtime/
├── intellij.phantom.springRuntime.iml
├── resources/META-INF/plugin.xml          # id: dev.phantom.spring.runtime
├── launch/      → intellij.phantom.springRuntime.launch    (RunConfigurationExtension, 옵션 주입. 앱 실행 전에 적재되는 유일한 모듈)
├── session/     → intellij.phantom.springRuntime.session   (세션 서비스, JMX 클라이언트, Services 표시, Actuator 탭 UI, DevTools 업데이트)
├── debugger/    → intellij.phantom.springRuntime.debugger  (Transaction 노드, springContext 레이블)
└── tests/       → intellij.phantom.springRuntime.tests
```

`plugin.xml` 골격:

```xml
<idea-plugin package="dev.phantom.springruntime">
  <id>dev.phantom.spring.runtime</id>
  <name>Phantom Spring Runtime</name>
  <incompatible-with>com.intellij.spring.boot</incompatible-with>
  <dependencies>
    <plugin id="com.intellij.java"/>
  </dependencies>
  <content>
    <module name="intellij.phantom.springRuntime.launch"/>    <!-- deps: intellij.java.execution.impl, intellij.phantom.springBoot.core -->
    <module name="intellij.phantom.springRuntime.session"/>   <!-- deps: launch, intellij.platform.execution.dashboard, intellij.libraries.jackson.databind(불필요할 수 있음, 3.4) -->
    <module name="intellij.phantom.springRuntime.debugger"/>  <!-- deps: session, intellij.java.debugger.impl -->
  </content>
</idea-plugin>
```

- `intellij.phantom.springBoot.core`(phantom-spring-boot 설계 §3.1)에 의존하는 이유는 하나다. `SpringBootDetector.isBootModule(module)`을 재사용하기 위해서다. phantom-spring-boot가 늦어지면 `launch` 안에 같은 판정(아래 3.2 표의 `JavaLibraryUtil`)을 10줄로 복제해 둘 수 있다.
- Ultimate의 `intellij.spring.boot.run`은 `intellij.spring`(정적 모델 전체), `intellij.spring.boot`, `intellij.java.debugger.impl`에 하드 의존한다 [관측: 디스크립터 `<dependencies>`]. phantom은 정적 Spring 모델(phantom-spring)에 의존하지 않는다. 런타임 기능만 쓰는 사용자는 phantom-spring을 꺼도 된다.

### 3.2 커뮤니티 확장 지점과 API (모두 [V]: 경로 확인)

| 용도 | EP / API | 위치 |
|---|---|---|
| JVM 옵션 주입, 설정 탭, 프로세스 연결 | `com.intellij.runConfigurationExtension` → `RunConfigurationExtension.updateJavaParameters`, `isApplicableFor`, `isEnabledFor`, `attachToProcess`, `readExternal/writeExternal`, `createFragments` | EP: `java/execution/impl/resources/intellij.java.execution.impl.xml` 26행. 클래스: `java/execution/impl/src/com/intellij/execution/RunConfigurationExtension.java`, 기반 클래스 `platform/execution/src/com/intellij/execution/configuration/RunConfigurationExtensionBase.java`(82, 90, 132행) |
| Kotlin 실행 구성 | `KotlinRunConfiguration`(type id `JetRunConfigurationType`) | `plugins/kotlin/run-configurations/jvm/src/org/jetbrains/kotlin/idea/run/KotlinRunConfiguration.kt`, `KotlinRunConfigurationType.kt` 16행 |
| Java 실행 구성 | `ApplicationConfiguration` | `java/execution/impl/src/com/intellij/execution/application/ApplicationConfiguration.java` |
| 프로세스 생명주기 | 토픽 `ExecutionListener.processStarted / processTerminated` | `platform/execution/src/com/intellij/execution/ExecutionListener.java` 24–52행 |
| 실행 콘텐츠에 탭 추가 | `RunContentDescriptor.getRunnerLayoutUi()` → `RunnerLayoutUi.createContent/addContent` | `platform/execution/src/com/intellij/execution/ui/RunContentDescriptor.java` 354행 |
| Services 창 표시 | `com.intellij.runDashboardCustomizer` → `RunDashboardCustomizer.isApplicable`, `updatePresentation(RunDashboardCustomizationBuilder, …)`(`addText`, `setIcon`, `addLink`) | EP: `platform/platform-resources/src/META-INF/LangExtensionPoints.xml` 284행. 클래스: `platform/lang-api/src/com/intellij/execution/dashboard/RunDashboardCustomizer.java` 29–41행. 빌더 구현: `platform/execution.dashboard/.../RunDashboardCustomizationBuilderImpl`(`addText`, `setIcon`, `addLink`) |
| Services 대상 판정 | `com.intellij.runDashboardChecker`(`@ApiStatus.Experimental`) | `platform/execution.dashboard/backend/resources/intellij.platform.execution.dashboard.backend.xml` 29행, `platform/lang-api/src/com/intellij/execution/dashboard/RunDashboardChecker.kt` |
| Services에 기본으로 넣을 실행 구성 타입 | `com.intellij.runDashboardDefaultTypesProvider` | `LangExtensionPoints.xml` 286행, `platform/lang-api/src/com/intellij/execution/dashboard/RunDashboardDefaultTypesProvider.java` |
| 정렬 기준 | 커뮤니티 `CommonJavaRunDashboardCustomizer`(id `commonJavaCustomizer`) | `java/execution/impl/resources/intellij.java.execution.impl.xml` 71행. 우리 customizer는 `order="before commonJavaCustomizer"` |
| DevTools 업데이트 액션 | `com.intellij.runningApplicationUpdaterProvider` → `RunningApplicationUpdaterProvider.createUpdater(Project, ProcessHandler)`, `RunningApplicationUpdater.performUpdate` | `LangExtensionPoints.xml` 683행, `platform/execution-impl/src/com/intellij/execution/update/RunningApplicationUpdaterProvider.java`, `RunningApplicationUpdater.java` |
| 빌드 실행과 완료 | `ProjectTaskManager.build(Module...)`, 토픽 `ProjectTaskListener.finished` | `platform/lang-api/src/com/intellij/task/ProjectTaskManager.java`, `ProjectTaskListener.java` 14–19행 |
| 빈 포트 찾기 | `NetUtils.findAvailableSocketPort()` | `platform/platform-util-io/src/com/intellij/util/net/NetUtils.java` 135행 |
| PID | `BaseProcessHandler.getProcess()` → `Process.pid()` | `platform/util/src/com/intellij/execution/process/BaseProcessHandler.java` 48행 |
| HTTP (v0.2) | `HttpRequests` | `platform/ide-core/src/com/intellij/util/io/HttpRequests.java` |
| 라이브러리 판정 | `JavaLibraryUtil.hasLibraryJar(module, "org.springframework.boot:spring-boot-actuator")` 등 | `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` |
| 디버거 Variables에 노드 추가 | `com.intellij.debugger.extraDebugNodesProvider` → `ExtraDebugNodesProvider.addExtraNodes(EvaluationContext, XValueChildrenList)` (`@ApiStatus.Experimental`) | EP: `java/debugger/impl/resources/META-INF/java-debugger.xml` 67행. 클래스: `java/debugger/impl/src/com/intellij/debugger/ui/tree/ExtraDebugNodesProvider.kt` |
| 디버거 렌더러 (v0.2) | `com.intellij.debugger.compoundRendererProvider` → `CompoundRendererProvider` | `java-debugger.xml` 17행, `java/debugger/impl/src/com/intellij/debugger/ui/tree/render/CompoundRendererProvider.java` |
| 평가 컨텍스트 변수 (검토 후 보류) | `com.intellij.debugger.additionalContextProvider` → `AdditionalContextProvider`(`@ApiStatus.Internal`). 언어 래퍼는 `JavaEvaluationContextWrapper`만 있다 | `java-debugger.xml` 48행, `java/debugger/openapi/src/com/intellij/debugger/engine/evaluation/EvaluationContextWrapper.kt` 27행, `java/debugger/impl/src/com/intellij/debugger/engine/evaluation/JavaEvaluationContextWrapper.kt` |
| 객체 레이블 (R11 채택) | `XValueMarkers.markValue(XValue, ValueMarkup)` | `platform/xdebugger-impl/shared/src/com/intellij/xdebugger/impl/frame/XValueMarkers.java` 58행. Kotlin 평가기 레이블 지원 근거: `plugins/kotlin/jvm-debugger/test/testData/evaluation/singleBreakpoint/labels/lCallOnLabeledObj.kt` 등 테스트 데이터 |
| 메서드 호출 | `DebugProcess.invokeMethod(EvaluationContext, ClassType/ObjectReference, Method, List)` | `java/debugger/openapi/src/com/intellij/debugger/engine/DebugProcess.java` 83, 91행 |
| 클래스 조회 | `VirtualMachineProxyImpl.classesByName(String)` | `java/debugger/impl/src/com/intellij/debugger/jdi/VirtualMachineProxyImpl.java` 117행 |
| 디버거 스레드 | `DebuggerManagerThreadImpl`, `SuspendContextCommandImpl` | `java/debugger/impl/src/com/intellij/debugger/engine/DebuggerManagerThreadImpl.kt`, `.../engine/events/SuspendContextCommandImpl.kt` |
| 디버그 세션 생명주기 | 토픽 `DebuggerManagerListener`, `XDebuggerManagerListener`, `DebugProcessListener.paused` | `java/debugger/impl/src/com/intellij/debugger/impl/DebuggerManagerListener.java`, `platform/xdebugger-api/src/com/intellij/xdebugger/XDebuggerManagerListener.java`, `java/debugger/openapi/src/com/intellij/debugger/engine/DebugProcessListener.java` |
| 프레임 비활성화 (v0.2) | `ApplicationActivationListener` | `platform/ide-core/src/com/intellij/openapi/application/ApplicationActivationListener.java` |

JMX 클라이언트는 플랫폼 API가 아니라 JDK 표준 `javax.management.remote.JMXConnectorFactory`를 쓴다. 커뮤니티에도 사용 예가 있다 [V: `platform/remote-driver/client/src/com/intellij/driver/client/impl/JmxCallHandler.java`]. 번들 JBR에 `java.management.rmi` 모듈이 들어 있다고 가정한다 [A: 첫 빌드에서 `java --list-modules`로 확인].

### 3.3 실행 시 주입하는 옵션 (R1)

`SpringRuntimeLaunchExtension : RunConfigurationExtension`

- `isApplicableFor(cfg)`: `cfg is JavaRunConfigurationBase`인지만 본다. 직렬화할 때마다 불리므로 라이브러리 검사를 하지 않는다 [A: `JavaRunConfigurationExtensionManager`가 read/write 때 호출함. phantom-spring-boot 문서 §3.2가 84, 95행을 근거로 듦].
- `isEnabledFor(cfg, runnerSettings)`: 설정 체크박스가 켜져 있고, 실행 모듈이 `SpringBootDetector.isBootModule`이면 true다.
- `updateJavaParameters`: `JavaParameters.vmParametersList`에 다음을 넣는다. 사용자가 같은 키를 이미 넣었으면 덮어쓰지 않는다.

| 옵션 | 이유 | 출처 |
|---|---|---|
| `-Dspring.jmx.enabled=true` | Boot 2.2부터 JMX가 기본으로 꺼져 있다 | [A: Boot Reference "Monitoring and Management over JMX"] |
| `-Dspring.application.admin.enabled=true` | `org.springframework.boot:type=Admin,name=SpringApplication` MBean을 켠다. 속성 `Ready`, 연산 `getProperty(String)`을 쓴다 | [A: Boot Reference "Admin Features"] |
| `-Dmanagement.endpoints.jmx.exposure.include=*` (actuator jar가 있을 때만) | Boot 3에서 JMX 노출 기본값이 `health`만으로 줄었다 | [A: Boot 3.0 release notes] |
| `-Dmanagement.endpoint.env.show-values=ALWAYS`, `-Dmanagement.endpoint.configprops.show-values=ALWAYS` | Boot 3에서 값이 기본으로 `******`로 마스킹된다. **설정의 "Show property values" 체크박스가 켜졌을 때만** 넣는다. 기본값은 꺼짐이다 | [A: Boot 3.0 "sanitization" 변경] |
| `-Dcom.sun.management.jmxremote.port=<free>` `-Dcom.sun.management.jmxremote.rmi.port=<same>` `-Dcom.sun.management.jmxremote.authenticate=false` `-Dcom.sun.management.jmxremote.ssl=false` `-Dcom.sun.management.jmxremote.local.only=true` `-Djava.rmi.server.hostname=127.0.0.1` | 원격 접속 없이 로컬 루프백으로만 연결한다. 포트는 `NetUtils.findAvailableSocketPort()`로 실행할 때마다 새로 고른다 | [A: JDK "Monitoring and Management Using JMX"] |

- Java system property는 `application.yaml`보다 우선순위가 높다. 그래서 위 값은 프로젝트 설정을 덮어쓴다 [A: Boot externalized config 순서]. 사용자 프로젝트가 `spring.jmx.enabled=false`를 명시해도 IDE 실행 중에는 켜진다. 설정 탭에 이 점을 문구로 알린다.
- 포트 대신 Attach API(`VirtualMachine.attach(pid).startLocalManagementAgent()`)로 붙는 방법도 있다. 명령행 옵션이 줄어드는 장점이 있지만 `jdk.attach` 의존과 macOS 권한 문제가 있을 수 있다 [A]. 이 방법은 v0.2 폴백으로 둔다.
- `attachToProcess(cfg, handler, runnerSettings)`: `ProcessHandler`의 user data에 `SpringRuntimeLaunchInfo(port, hasActuator, hasDevtools, module, showValues)`를 넣는다. 다른 모듈은 이 값만 읽는다. **여기서 세션 클래스를 참조하지 않는다.** launch 모듈은 session 모듈의 클래스를 적재하지 않는다.

### 3.4 데이터 모델

```kotlin
// session 모듈
class SpringRuntimeSession(            // ProcessHandler 1개당 1개, Disposable
  val handler: ProcessHandler,
  val info: SpringRuntimeLaunchInfo,
  val scope: CoroutineScope,           // 프로젝트 서비스 scope의 child. processTerminated에서 cancel
) {
  val state: StateFlow<AppState>       // Starting | Ready(port, contextPath) | Failed(reason) | Stopped
  val generation: Int                  // DevTools 재시작마다 +1
  suspend fun <T> endpoint(e: ActuatorEndpoint<T>, force: Boolean = false): Snapshot<T>
}

data class Snapshot<T>(val value: T, val generation: Int, val fetchedAt: Long)

sealed interface ActuatorEndpoint<T> {
  val mbean: String                    // "org.springframework.boot:type=Endpoint,name=Beans"
  val operation: String                // "beans"
  fun parse(raw: Any?): T              // raw는 JMX가 돌려준 Map/List/String 트리
}
// 구현: Beans, Health, Mappings, Env, EnvEntry(key), ConfigProps, Loggers, SetLoggerLevel(name, level)

data class BeanRow(val context: String, val name: String, val type: String?, val scope: String,
                   val dependencies: List<String>, val resource: String?)
data class EnvSource(val name: String, val props: List<EnvProp>)
data class EnvProp(val key: String, val value: String?, val origin: Origin?)
data class Origin(val raw: String, val resourcePath: String?, val line: Int?, val column: Int?)
data class MappingRow(val methods: Set<String>, val patterns: List<String>, val handler: String,
                      val handlerClass: String?, val handlerMethod: String?)
data class TxView(val active: Boolean, val name: String?, val readOnly: Boolean, val isolation: String?,
                  val frames: List<TxFrame>, val resources: List<Pair<String, String>>)
data class TxFrame(val joinpoint: String, val propagation: String, val isolation: String, val rollbackOn: String?)
```

- Actuator의 JMX 연산 결과는 Jackson으로 변환된 표준 `Map`/`List`/`String`/`Number`다 [A: Boot `JacksonJmxOperationResponseMapper`의 문서상 동작]. 그래서 IDE 쪽에는 Spring 클래스가 필요 없다. 이 점이 HTTP JSON을 받는 것보다 단순한 이유다. JSON 파서가 필요 없으므로 jackson 의존을 빼는 것이 목표다(HTTP 경로는 v0.2).
- Origin 파싱: Boot `TextResourceOrigin.toString()` 형식인 `"<resource description> - <line>:<column>"`을 정규식으로 나눈다 [A: 형식은 공개 소스·문서 기준]. `class path resource [x/y.yaml]`이면 실행 모듈의 리소스 루트(`ModuleRootManager` 소스 루트 중 resources 타입)에서 `x/y.yaml`을 찾는다. `file [...]`이면 절대 경로로 찾는다. 찾지 못하면 링크 없이 텍스트만 보여 준다. 줄 번호는 1부터 시작한다고 가정한다 [A].
- 이동(R3, R5): 빈 타입과 핸들러 문자열(`com.reservation.x.FooController#find(Long)`)에서 FQN을 뽑아 `JavaPsiFacade.findClass(fqn, module.getModuleWithDependenciesAndLibrariesScope)`를 호출한다. Kotlin 클래스도 light class로 찾을 수 있다 [A: K2에서도 `KtLightClass` 경유]. CGLIB 접미사(`$$SpringCGLIB$$0`)는 잘라 낸다.

### 3.5 캐시와 무효화

- 캐시 단위는 `(session, endpoint) → Snapshot`이다. 값이 무효해지는 조건은 세 가지다.
  1. `generation`이 바뀜: DevTools 재시작. Admin MBean의 `Ready`가 false→true로 다시 바뀌는 것을 감지하거나, R9 업데이트 직후 Ready를 다시 기다린다
  2. 사용자가 탭의 새로 고침을 누름
  3. 세션 종료: 스냅숏을 버리고 탭을 "stopped" 상태로 바꾼다. 마지막 값은 회색으로 남겨 둔다
- 탭은 처음 보일 때(`ContentManagerListener.selectionChanged`) 한 번만 가져온다. 보이지 않는 탭은 가져오지 않는다.
- Env 전체 응답은 수 MB가 될 수 있다 [A]. 받은 뒤 바로 `EnvSource` 모델로 줄이고 원본 Map은 버린다. 세션당 스냅숏 상한은 endpoint별 1개다.
- 디버거 쪽(R10, R11)은 **캐시하지 않는다.** 트랜잭션 상태는 정지할 때마다 달라지기 때문이다. 다만 "이 VM에 `TransactionSynchronizationManager` 클래스가 로드됐는가"는 `DebugProcessImpl` user data에 저장한다. 이 값은 로드되지 않음 → 로드됨으로만 바뀐다. 로드되지 않았으면 다음 정지 때 다시 확인한다(`classesByName` 1회).
- PSI, VFS, `PsiModificationTracker`에 묶인 값은 하나도 없다.

### 3.6 기능별 동작

**R2 Services**

- `SpringBootRunDashboardCustomizer.isApplicable(settings, descriptor)`: `descriptor?.processHandler`의 user data에 `SpringRuntimeLaunchInfo`가 있는지만 본다. 실행되지 않은 구성은 false이므로 클래스 한 개만 적재된다.
- `updatePresentation(builder, settings, descriptor)`: 상태 아이콘, `addLink(":$port") { BrowserUtil.browse(url) }`.
- `RunDashboardDefaultTypesProvider`: `Application`과 `JetRunConfigurationType`을 기본 대상에 넣을지는 사용자에게 묻는 알림으로 처리한다. 모든 Kotlin main 실행이 Services 창에 들어가면 시끄럽기 때문이다. MVP에서는 "Boot 앱을 처음 실행할 때 한 번 제안 → 승인하면 `RunDashboardManager`의 types에 추가"로 한다 [A: `RunDashboardManager.setTypes` 사용 가능 여부는 구현 때 확인. 파일은 `platform/execution/src/com/intellij/execution/dashboard/RunDashboardManager.java` [V]].

**R3–R8 Actuator 탭**

- `processStarted`(토픽 리스너)에서 `info`가 있으면 `SpringRuntimeSessions`(project service)가 세션을 만든다. `RunContentManager.findContentDescriptor(env, handler)`나 `env.contentToReuse`로 `RunContentDescriptor`를 얻고, `getRunnerLayoutUi()`에 "Actuator" 콘텐츠를 **지연 컴포넌트**로 붙인다. 컴포넌트는 탭이 처음 선택될 때 만든다.
- Debug 실행에서도 같은 콘텐츠가 붙는다. 디버그 탭 레이아웃은 `XDebugSessionTab`이 관리하므로 `RunnerLayoutUi` 경로가 다를 수 있다 [A: 6장 Q2].
- Loggers 레벨 변경: `configureLogLevel(name, level)` 연산을 호출하고 결과로 그 로거 행만 다시 가져온다.

**R9 DevTools 업데이트**

- `PhantomDevtoolsUpdaterProvider.createUpdater(project, handler)`: `info.hasDevtools`가 아니면 null이다. 이 판정은 launch 시점에 `JavaLibraryUtil.hasLibraryJar(module, "org.springframework.boot:spring-boot-devtools")`로 계산해 둔다. 그래서 액션을 열 때 비용이 없다.
- 정책 3개
  - (a) Update classes and resources: `ProjectTaskManager.build(module)`. Gradle 위임 빌드면 Gradle `classes` 태스크가 출력 디렉터리를 갱신하고, DevTools가 classpath 변경을 감지해 재시작한다
  - (b) Update resources: 리소스만 복사한다. DevTools는 기본 `spring.devtools.restart.exclude`(`static/**`, `templates/**` 등)에서 재시작하지 않는다 [A]
  - (c) Touch trigger file: `spring.devtools.restart.trigger-file` 값을 실행 중인 앱의 Admin `getProperty`로 읽어 그 파일의 mtime만 바꾼다. 값이 없으면 이 정책은 숨긴다
- 업데이트 뒤 세션은 Ready를 다시 기다리고 `generation`을 올린다.

**R10 Transaction 노드** (`SpringTxExtraNodesProvider : ExtraDebugNodesProvider`)

1. `addExtraNodes(ctx, children)`는 디버거 매니저 스레드에서 불린다 [A: Variables 계산 경로]. 먼저 `ctx.debugProcess`의 user data 캐시나 `classesByName("org.springframework.transaction.support.TransactionSynchronizationManager")`로 클래스가 있는지 본다. 없으면 즉시 반환한다. **Spring이 아닌 모든 디버그 세션의 비용은 여기서 끝난다.**
2. 노드 제목용 호출 1회: `isActualTransactionActive()`(static, public).
3. 노드를 펼칠 때(`XValue.computeChildren`) 나머지를 부른다. 모두 정지한 스레드의 ThreadLocal을 읽는 getter다.
   - `getCurrentTransactionName()`, `isCurrentTransactionReadOnly()`, `getCurrentTransactionIsolationLevel()`(Integer → `Isolation` 이름 매핑, null이면 `DEFAULT`), `getResourceMap()`(키와 값의 `toString` 요약)
   - `org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionInfo()`(protected static) → `TransactionInfo`. 여기서 `getTransactionAttribute()`의 `getPropagationBehavior()`, `getIsolationLevel()`, `getJoinpointIdentification()`을 읽는다. `oldTransactionInfo` 필드를 따라가면 중첩 `@Transactional` 스택이 나온다. 필드는 JDI `getValue`로 읽으므로 호출이 필요 없다
   - [A] JDI `invokeMethod`가 protected 메서드에 접근 제어를 적용하지 않는다고 가정한다. 안 되면 private static ThreadLocal 필드 `transactionInfoHolder`를 JDI로 읽고 `ThreadLocal.get()`을 호출한다
   - [A] 메서드와 필드 이름은 Spring Framework 6.2 공개 소스 기준이다. 버전마다 `classesByName`과 메서드 존재를 확인하고, 없으면 "unsupported Spring version" 텍스트 노드를 보여 준다
4. 정수 상수를 이름으로 바꾸는 매핑은 `TransactionDefinition`의 공개 상수 값표(`PROPAGATION_REQUIRED=0` …)를 쓴다.
5. 프로그래밍 방식의 `TransactionTemplate`도 같은 ThreadLocal을 쓰므로 active/isolation은 보인다. propagation 스택은 `TransactionInfo`가 없어서 비어 있다. 노드에 "(programmatic)"이라고 표시한다 [A].

**R11 springContext 레이블**

- 첫 `DebugProcessListener.paused` 때 백그라운드 디버거 명령을 한 번 실행한다. 단, `info`가 있는 세션이고 설정 "Auto-label Spring context"가 켜져 있을 때만이다.
  1. `org.springframework.boot.SpringApplication`의 static 필드 `shutdownHook`(`SpringApplicationShutdownHook`) → `contexts` 집합에서 활성 컨텍스트를 고른다. 부모가 없는 것을 우선한다 [A: Boot 3.4 공개 소스의 필드 이름. 다르면 2로]
  2. 폴백: `classesByName`으로 알려진 구현 타입(`AnnotationConfigServletWebServerApplicationContext`, `AnnotationConfigReactiveWebServerApplicationContext`, `AnnotationConfigApplicationContext`)을 찾고 `ReferenceType.instances(4)`를 부른다. `VirtualMachine.canGetInstanceInfo()`가 false면 이 단계를 건너뛴다
- 찾은 `ObjectReference`를 커뮤니티 marked object 메커니즘으로 `springContext` 레이블에 등록한다. 등록에는 `XValueMarkers`와 `JavaValue`를 쓴다. 등록 뒤에는 Kotlin과 Java 평가에서 `springContext_DebugLabel`을 쓸 수 있다.
- `AdditionalContextProvider`를 쓰지 않는 이유가 두 가지 있다. `@ApiStatus.Internal`이고, 언어 래퍼가 Java(`JavaEvaluationContextWrapper`)에만 있어서 Kotlin 평가에서 보이지 않는다 [V: 위 3.2 표].
- 빈 메서드 호출은 일반 Evaluate와 같다. 앱 상태를 바꿀 수 있다는 점은 커뮤니티 평가와 다르지 않으므로 별도 경고를 하지 않는다. 다만 자동 레이블 단계 자체는 **메서드를 호출하지 않는다**(필드 읽기와 `instances`만 쓴다).

### 3.7 스레딩 (EDT에서 일하지 않음)

| 작업 | 스레드 |
|---|---|
| `updateJavaParameters`, 포트 선택 | 실행 준비 스레드(플랫폼이 정함). 라이브러리 판정은 `CachedValue` 조회뿐이다 |
| JMX 연결, Ready 폴링, 연산 호출, 파싱 | 세션 `CoroutineScope` + `Dispatchers.IO`. 연산마다 타임아웃 5 s(Env는 15 s) |
| 테이블 모델 교체 | `withContext(Dispatchers.EDT)`에서 이미 파싱된 행 리스트만 넣는다 |
| FQN → PSI 이동 | `readAction { }`(non-blocking, `smartReadAction`). Dumb mode면 "indexing…" 알림 후 재시도 |
| Origin 파일 찾기 | 백그라운드 read action. 이동만 EDT에서 한다 |
| 디버거 노드, 레이블 | 디버거 매니저 스레드(`SuspendContextCommandImpl`). JDI 호출을 EDT에서 하지 않는다 |
| DevTools 빌드 | `ProjectTaskManager`가 관리. 완료 콜백은 Promise / `ProjectTaskListener` |

### 3.8 Kotlin 지원

- 실행: `KotlinRunConfiguration`이 `JavaRunConfigurationBase`를 상속하므로 같은 확장이 적용된다 [V: phantom-spring-boot 문서 §3.2가 같은 근거를 인용, 파일 경로 확인함].
- 이동: FQN → `JavaPsiFacade.findClass` → Kotlin light class → `navigationElement`(`KtClass`). 핸들러 메서드는 light method 이름과 파라미터 개수로 고른다. K2 모드에서 light class가 `KtClass`로 이동하는지는 테스트로 확인한다 [A].
- 디버거: Kotlin 프레임도 `JavaStackFrame` 계열이라 `ExtraDebugNodesProvider`가 불린다고 가정한다 [A]. 레이블 평가는 Kotlin 평가기 테스트 데이터로 근거를 확인했다 [V].
- 코루틴: `suspend` 함수 안에서 정지하면 트랜잭션 ThreadLocal이 현재 스레드와 맞지 않을 수 있다. MVP는 "현재 스레드 기준"이라고 노드에 적는다. 코루틴 컨텍스트 지원은 v0.3이다.
- UAST, K2 Analysis API는 MVP에서 쓰지 않는다. 런타임 데이터를 쓰므로 소스 분석이 필요 없다.

## 4. 구현 단계

선행 조건: build-wiring T1–T8(플러그인 배선 레시피), phantom-spring-boot의 `core` 모듈(`SpringBootDetector`) 또는 그 복제.

| # | 작업 | 일 | 수락 기준 (IDE에서 보이는 것) |
|---|---|---|---|
| T0 | 모듈 4개, `plugin.xml`, `.iml`, Bazel 생성, 번들 등록 | 0.5 | `./bazel.cmd run //:main_run`으로 뜬 IDE의 Plugins 목록에 "Phantom Spring Runtime"이 있다 |
| T1 | `SpringRuntimeLaunchExtension`: 설정 직렬화, Spring Boot 탭 fragment, 옵션 주입, `attachToProcess`에서 user data 설정 | 1.5 | `adapter-module`의 `ReservationApplication`을 실행하면 콘솔 명령행에 `-Dspring.jmx.enabled=true … jmxremote.port=<n>`이 보인다. 체크박스를 끄면 사라진다. Boot가 아닌 Kotlin main에는 없다 |
| T2 | `SpringRuntimeSessions` + JMX 연결 + Ready 폴링 + Admin `getProperty("local.server.port")` | 1.5 | Run 탭 상단 알림 영역에 "Spring Boot ready on :8080 (2.3 s after start)"이 뜬다. 앱을 끄면 세션이 사라진다. JMX 비활성 상태에서는 "runtime integration unavailable" 한 줄만 나온다 |
| T3 | Services customizer와 checker, 기본 타입 제안 | 1 | Services 창에 Boot 앱 노드, 상태 아이콘, `:8080` 링크가 있다. 링크를 누르면 브라우저가 열린다 |
| T4 | Actuator 탭 프레임(지연 컴포넌트, 새로 고침, stopped 상태) + Beans + Health | 2 | Beans 탭에서 `reservationService` 같은 사용자 빈을 필터로 찾고, 더블클릭하면 Kotlin 소스가 열린다. Health 탭에 db/redis 상태가 보인다 |
| T5 | Env + EnvEntry 검색 + Origin 이동 + 값 마스킹 토글, ConfigProps | 2 | `spring.datasource.url`을 검색하면 이긴 source(예: `persistence/datasource/index.yaml`)가 맨 위에 보이고, 클릭하면 그 YAML 줄로 이동한다. "Show values"가 꺼져 있으면 `******`가 보이고 켜는 방법이 안내된다 |
| T6 | Mappings 탭, Loggers 탭(레벨 변경) | 1 | Mappings에서 `POST /api/...` 행을 누르면 컨트롤러 메서드로 이동한다. Loggers에서 `com.reservation`을 DEBUG로 바꾸면 콘솔에 DEBUG 로그가 바로 찍힌다 |
| T7 | DevTools updater 3정책 | 1 | devtools를 추가한 임시 브랜치에서 Ctrl+F10 → "Update classes and resources" → 빌드 → 콘솔에 Boot 재시작 로그가 보이고, Actuator 탭 값이 새 generation으로 바뀐다. **devtools가 없는 원래 프로젝트에서는 Ctrl+F10 목록에 phantom 항목이 없다** |
| T8 | Transaction 노드 | 2 | `@Transactional` 서비스 메서드 안에서 멈추면 Variables에 `⟨Spring TX⟩ active, REQUIRED, …`가 보인다. 펼치면 중첩 호출 스택과 DataSource 리소스가 보인다. 트랜잭션 밖(컨트롤러)에서 멈추면 `⟨Spring TX⟩ none`이 보인다. Spring이 아닌 Kotlin 앱 디버깅에서는 노드가 없다 |
| T9 | springContext 자동 레이블 | 1 | 첫 정지 뒤 Evaluate에 `springContext_DebugLabel.getBeanDefinitionCount()`를 넣으면 숫자가 나온다. Kotlin 파일 프레임에서도 된다 |
| T10 | 테스트와 경량화 측정(5장) | 1.5 | 5.1 테스트가 통과한다. 5.3 측정표에서 "Boot 실행 전 적재 클래스 < 3"을 만족한다 |
| 합계 | | **15** | |

순서: T0 → T1 → T2 → (T3, T4 병렬) → T5 → T6 → T7. T8과 T9는 T1 이후 따로 진행할 수 있다(JMX에 의존하지 않음). T10은 마지막에 한다.

## 5. 테스트 방법

### 5.1 자동 테스트 (커뮤니티 테스트 프레임워크)

- **파서 단위 테스트**(플랫폼 없음, JUnit 5): Actuator 응답 픽스처를 손으로 작성한 `Map` 트리로 두고 `parse()`를 검증한다. Beans(다중 컨텍스트), Env(origin 있음/없음/`******`), Mappings(`dispatcherServlets` 구조), Loggers. 픽스처는 공개 Actuator API 문서의 예시 형태를 따른다 [A]. `Origin` 정규식 경계 사례도 확인한다(Windows 경로, `file [...]`, 줄 번호 없음).
- **실행 구성 테스트**: `LightJavaCodeInsightFixtureTestCase` 계열에 spring-boot 가짜 jar(`PsiTestUtil.addLibrary`)를 붙이고, `ApplicationConfiguration`과 `KotlinRunConfiguration`에 대해 `updateJavaParameters` 결과 VM 옵션을 검증한다. 사용자가 이미 넣은 옵션을 보존하는지, actuator jar가 없을 때 exposure 옵션이 빠지는지 본다.
- **JMX 통합 테스트**: 테스트 JVM 안에서 `MBeanServer`에 가짜 `Admin`과 `Endpoint` MBean을 등록하고 `JMXConnectorServer`를 루프백 포트에 띄운다. 세션의 Ready 대기, 타임아웃, 재연결, generation 증가를 검증한다. 실제 Spring은 쓰지 않는다.
- **디버거 테스트**: 커뮤니티 `java/debugger/impl` 테스트 기반(`ExecutionWithDebuggerToolsTestCase` 계열 [A: 이름은 구현 때 확인])으로 작은 Java 프로그램을 디버그한다. 프로그램에는 `TransactionSynchronizationManager`와 같은 이름, 같은 static 메서드 시그니처를 가진 **스텁 클래스**를 테스트 데이터에 넣는다. 정지 지점에서 노드 텍스트를 검증한다. Spring jar 없이 계약만 시험한다.
- **경계 테스트**: Spring이 없는 디버그 세션에서 `addExtraNodes`가 `classesByName` 1회 외에 아무 JDI 호출도 하지 않는지 확인한다. 테스트에서는 VM 프록시를 감싸 호출 수를 센다.

### 5.2 사용자 프로젝트 수동 확인 (`~/orca/workspaces/prototype-reservation-system/feat-contract`)

1. `compose.yaml`로 MySQL/Redis/Kafka를 띄우고 `ReservationApplication`을 Run한다. T1–T6 수락 기준을 차례로 확인한다.
2. 같은 앱을 Debug로 실행한다. 예약 생성 API(`@Transactional` 서비스)에 중단점을 걸고 HTTP 요청을 보낸 뒤 T8과 T9를 확인한다. 프로필 4개가 모두 활성인 상태에서 Env 탭으로 `spring.profiles.active`의 실효 값을 확인한다.
3. `batch-module`의 `ReservationBatchApplication`(웹 서버 없음)을 실행한다. Services 노드에 포트 링크가 **없고**, 상태는 Ready로 바뀌는지 확인한다. Mappings 탭이 "no web application"을 보여 주는지도 본다.
4. 보안 확인: 앱을 실행 중일 때 `lsof -iTCP -sTCP:LISTEN -P | grep java`로 JMX 포트가 `127.0.0.1`에만 바인딩됐는지 확인한다.

### 5.3 경량화 측정 (Ultimate와 같은 방법)

`reversed/02-runtime`에서 쓴 방식을 그대로 쓴다.

| 측정 | 방법 | 목표 |
|---|---|---|
| 프로젝트를 열고 아무것도 실행하지 않은 상태의 적재 클래스 | `jcmd <pid> VM.class_hierarchy` 또는 `-verbose:class` 로그에서 `dev.phantom.springruntime.*` 개수 | 3개 미만(launch 확장과 settings) |
| 상주 코루틴/스레드 | 앱을 실행하지 않은 상태와 앱 종료 뒤의 `jcmd Thread.print` + coroutine dump | `dev.phantom.springruntime` 프레임 0개 |
| 하이라이팅 비용 | Kotlin 파일 편집 중 JFR 60 s, `ExecutionSample`에서 `dev.phantom.springruntime` 프레임 | 0 샘플 |
| Spring이 아닌 디버그 세션 | 일반 Kotlin main을 디버그하고 정지를 20번 반복. JFR | `SpringTxExtraNodesProvider` 샘플이 무시할 수준(정지 1회당 `classesByName` 1회) |

## 6. 리스크와 남은 의문

1. **JMX 노출 기본값과 옵션 이름** [A]: Boot 3.4에서 `management.endpoints.jmx.exposure.include` 기본값과 `show-values` 키 이름을 실제 앱으로 확인해야 한다. T1 첫날에 `jconsole`로 확인한다.
2. **Q2: 디버그 실행에서 Actuator 탭을 붙이는 위치** [unknown]: Debug 실행의 `RunContentDescriptor.getRunnerLayoutUi()`가 `XDebugSessionTab`의 레이아웃과 같은지 확인해야 한다. 다르면 `XDebugSessionListener`/`XDebuggerManagerListener.processStarted`에서 세션 탭 UI에 붙인다.
3. **Services 창 frontend/backend 분리** [V 부분]: 262의 dashboard는 `RunDashboardCustomizationBuilderImpl.buildDto`로 DTO를 만든다. customizer가 할 수 있는 일은 텍스트, 아이콘, 링크뿐이다. Services 오른쪽 패널에 Actuator 탭을 직접 넣을 수 없을 수 있다. 실행 콘텐츠 탭이 Services 패널에도 그대로 보이는지 확인해야 한다 [A].
4. **실험적/내부 API**: `ExtraDebugNodesProvider`와 `RunDashboardChecker`는 `@ApiStatus.Experimental`이다 [V]. 포크를 고정 태그에 두므로 당장은 문제가 아니다. 업스트림을 리베이스할 때 깨질 수 있다. 우리 쪽 어댑터 클래스 하나에 격리한다.
5. **JDI 접근 제어와 Spring 내부 이름** [A]: `TransactionAspectSupport.currentTransactionInfo()`(protected)와 `TransactionInfo.oldTransactionInfo`, Boot `SpringApplication.shutdownHook.contexts`는 공개 API가 아니다. 버전마다 실패 경로(텍스트 노드, 폴백)를 둔다. 실패해도 예외를 디버거 UI로 올리지 않는다.
6. **평가 부작용과 일시정지 정책**: `invokeMethod`는 정지한 스레드를 잠깐 재개한다. suspend-all 정책에서는 다른 스레드도 잠깐 움직일 수 있다 [A]. 그래서 노드 제목에는 호출을 1회만 쓰고 나머지는 펼칠 때 부른다. 설정으로 노드 전체를 끌 수 있게 한다.
7. **값 노출**: `show-values=ALWAYS`는 비밀번호와 JWT 키 같은 값을 IDE 화면에 보여 준다. 기본은 꺼짐이다. 켤 때 경고 문구를 띄운다. 스냅숏을 디스크에 저장하지 않는다.
8. **JMX 포트 보안** [A]: 인증 없는 JMX는 로컬 사용자라면 누구나 붙을 수 있다. `local.only=true`, 루프백 바인딩, 실행마다 새 포트를 쓰는 것이 완화책이다. v0.2에서 Attach API 경로(포트 없음)를 기본으로 바꿀지 검토한다.
9. **Gradle `bootRun`**: 사용자가 Gradle 태스크로 앱을 실행하면 `RunConfigurationExtension`이 적용되지 않는다 [A: `GradleRunConfiguration`은 `ExternalSystemRunConfiguration` 계열 [V]]. MVP 문서와 설정 탭에 "Application 실행 구성을 쓰라"고 안내한다. v0.2에서 Gradle 초기화 스크립트로 `jvmArgs`를 주입하는 방법을 조사한다.
10. **Kotlin light class 이동** [A]: phantom-spring-boot 설계 R1과 같은 위험이다. 같은 테스트로 함께 확인한다.
11. **다른 설계와의 경계**: 설정 탭 UI는 phantom-spring-boot의 "Spring Boot" fragment에 체크박스를 추가하는 방식이다. 두 플러그인이 각자 탭을 만들지 않도록 phantom-spring-boot에 `dev.phantom.springBoot.runSettingsContributor` 같은 작은 내부 EP를 요청해야 한다 [A: 아직 그 설계에 없음]. 그 EP가 생기기 전에는 별도 탭 "Spring Runtime"으로 시작한다.
12. **플러그인 ID 통일**: `dev.phantom.spring.runtime`을 쓴다. build-wiring 문서 R8의 `dev.phantom.*` 통일 제안을 따른 것이다.
