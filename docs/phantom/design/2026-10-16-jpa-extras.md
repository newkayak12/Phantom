# phantom-jpa-extras 설계: 엔티티 생성, JPA 콘솔, 문자열 속 SQL

- 날짜: 2026-10-16
- 대상 트리: `phantom-idea` (intellij-community `idea/262.10968.63` 포크)
- 상태: 설계 초안. 이 문서 말고는 아무 소스도 바꾸지 않았다.
- 선행 설계: [`2026-10-16-jpa.md`](2026-10-16-jpa.md) (phantom-jpa: 엔티티 모델, JPQL 언어·주입), [`2026-10-16-database.md`](2026-10-16-database.md) (phantom-db: 데이터 소스, SQL 언어, 스키마 트리). 이 문서는 두 설계 위에 얹히고, 두 설계에 바꿔 달라고 요청할 점은 §3.8에 모았다.
- 표기: **[V]** = 이 트리, Ultimate 디스크립터, 사용자 프로젝트에서 경로·API·사실을 직접 확인함. **[A]** = 확인하지 않은 가정 (공개 문서 지식, 플랫폼 동작 추정, 설계 판단).
- 클린룸: 근거는 (1) `Intellij-rea/reversed/`의 동작·비용 설명 노트, (2) Ultimate 설치본 plugin.xml에 적힌 기능 이름, (3) 커뮤니티 API, (4) Jakarta Persistence 3.1, Hibernate ORM 6.x, Spring Boot 3.x 공개 문서다. `reversed/source/`는 열지 않았다. 라이선스·인증 코드는 다루지 않는다.

---

## 1. 목표와 범위

### 1.1 Ultimate에 있는 기능 (디스크립터 체크리스트) [V]

| Ultimate 플러그인 / 모듈 | 디스크립터에 적힌 기능 | 의존 (디스크립터) |
|---|---|---|
| `com.intellij.javaee.reverseEngineering` | `ReBulkReverseEngineering` 액션(DB → 엔티티 일괄 생성), "Entity Declaration" 설정 페이지, `ormReInfoProvider` EP | **Liquibase 플러그인**, Database, JPA Model, JPA, `intellij.database.dialects.{db2,h2,hsql,maria,mssql,mysql,oracle,postgres}` 8개 |
| JPA (`jpa-support.jar`) | "JPA console for running JPQL queries", `Jpa.OpenConsole`, `database.consoleProvider`(`JpaConsoleProvider`, `JakartaConsoleProvider`), `JpaConsoleClasspathExtender`(Groovy 모듈), `jpaSqlDialectDetector` 2개 | `intellij.database.jdbcConsole`, `intellij.javaee.jpa.ql` |
| JPA Kotlin 모듈 | `JpaKotlinTemplateManager`(Kotlin 엔티티 생성 템플릿) | |
| Hibernate | `HibernateConnectionManager`, 콘솔 jar 5개(`hibernate{,3,4,5,52}-console.jar`), `HibernateSqlAnnotationsLanguageInjector`(UAST 주입), `CriteriaApiResolveInspection` | Database, `intellij.grid.core.impl` |
| javaee-persistence-impl | `injectionConfig javaInjections.xml`, `SqlSourceToSinkFlow` 검사 | `intellij.platform.langInjection` |
| Database Tools | `injectionConfig META-INF/sqlInjections.xml` 1개 + `multiHostInjector` 10개 (노트 `ult-database.md` §2) | |

정리하면 세 기능 모두 Ultimate에서는 **Database 플러그인 전체(방언 60개, 3.8k 클래스, Metaspace 18.7 MB)** 를 전제로 한다 [V: 노트 `ult-database.md` 표 2행]. 엔티티 생성은 Liquibase와 JPA Model까지 끌고 온다.

### 1.2 사용자 프로젝트에서 이 기능들이 닿는 곳 [V]

`~/orca/workspaces/prototype-reservation-system/infro`를 grep했다. 버전: Spring Boot 3.4.5, Kotlin 2.0.10, `jakarta.persistence` 3.1.0, Gradle 캐시의 `hibernate-core` 6.6.13.Final.

| 항목 | 관측값 | 이 설계에 주는 의미 |
|---|---|---|
| `@Query(nativeQuery = true)`, `createNativeQuery` | **0개** | nativeQuery 주입은 이 사용자에게 당장 이득이 없다. 일반 기능으로만 넣고 우선순위를 낮춘다 |
| `@SQLRestriction("is_deleted = false")` | 2개 (`RestaurantEntity`, `MenuEntity`) | Hibernate SQL 조각 주입은 실제로 쓰인다 |
| `JdbcTemplate` | 2개 클래스. `const val GET_LOCK_QUERY = "SELECT GET_LOCK(?, 0)"` → `jdbcTemplate.queryForObject(GET_LOCK_QUERY, ...)` | phantom-db v0.2 영역. 여기서도 `const val` 간접 참조가 핵심이다 |
| `@Table(` / `@Column(` / `@JoinColumn` | 24 / 133 / 7 | 엔티티는 컬럼 이름을 **항상 명시**한다. 생성기 기본값도 그래야 한다 |
| 엔티티 스타일 (`MenuEntity.kt`) | 생성자 파라미터 + 본문 `var x: T = x` + `protected set`, `@Comment("...")`, `@Table(catalog=, name=, indexes=[Index(...)])`, `@DynamicUpdate`, 공통 상위 클래스 `TimeBasedPrimaryKey`, `@Embedded LogicalDelete` | 생성 템플릿의 기본 스타일 |
| `EntityManager` / `createQuery(` | 43 / 2 (배치 테스트) | JPQL 실행 확인은 지금 테스트 코드로 한다 |
| 로컬 설정 | `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect`, `ddl-auto: validate`, 드라이버 `com.p6spy.engine.spy.P6SpyDriver` | 콘솔이 Hibernate를 띄울 때 `ddl-auto`를 반드시 끄고, P6Spy를 벗겨야 한다 |
| 마이그레이션 | Flyway `V1_0__init.sql` … | 테이블은 마이그레이션이 먼저 생기고 엔티티를 나중에 쓴다. "테이블 → 엔티티" 흐름이 실제로 있다 |

솔직한 판단: 세 기능 모두 **매일** 쓰는 기능은 아니다. 매일 쓰는 것은 phantom-jpa의 JPQL 지원과 phantom-db 콘솔이다. 이 문서의 기능은 "새 테이블을 만들 때(주 1–2회)", "쿼리가 어떤 SQL로 나가는지 궁금할 때"에 쓰인다. 그래서 상시 비용은 0이어야 하고, 쓸 때만 비용을 낸다.

### 1.3 MVP (v0.1)

1. **SQL 주입 (JPA 위치)**: Java/Kotlin 문자열에 phantom-db의 SQL 언어를 주입한다.
   - `@org.springframework.data.jpa.repository.Query(value/countQuery, nativeQuery = true)`
   - `@jakarta.persistence.NamedNativeQuery(query)` (+ `javax.persistence`)
   - `EntityManager.createNativeQuery(String, ...)`의 첫 인자
   - Hibernate SQL 조각 어노테이션: `@SQLRestriction`, `@SQLOrder`, `@Formula`, `@Check`, `@ColumnTransformer(read/write)`, `@SQLInsert/@SQLUpdate/@SQLDelete(sql)`, 구버전 `@Where(clause)` [A: 이름은 Hibernate 6.x 공개 Javadoc 기준]
   - phantom-jpa와 같은 규칙으로 **같은 파일 `const val`/`static final` 상수 간접 참조**와 Kotlin 템플릿 자리표시자를 지원한다.
   - 방언: 모듈 설정에서 추정(§3.6). 실패하면 ANSI.
2. **DB 테이블 → Kotlin 엔티티 생성**: phantom-db 스키마 트리에서 테이블을 하나 이상 고르고 "Generate JPA Entities…"를 실행한다. 대상 패키지, 이름 접미사, 상위 클래스, FK 처리 방식을 고른다. 미리보기를 보고 생성한다. Kotlin만 지원한다 (사용자 `src/main`의 Java 파일 0개 [V: jpa 설계 §1.1]).
3. **JPA 콘솔, 번역 모드**: 모듈을 고르고 JPQL을 쓰면, 프로젝트 classpath의 Hibernate가 별도 JVM에서 **DB 없이** 만든 SQL을 보여 준다. 콘솔 입력에는 phantom-jpa의 JPQL 하이라이트·완성·참조가 그대로 동작한다.

### 1.4 v0.2 이후

| 버전 | 기능 |
|---|---|
| v0.2 | JPA 콘솔 **실행 모드**: phantom-db 데이터 소스(또는 H2 인메모리)에 연결해 JPQL을 실제로 실행하고 결과를 표로 본다. DML은 기본 롤백 |
| v0.2 | Java 엔티티 생성기, `@ManyToOne`과 함께 역방향 `@OneToMany(mappedBy)` 생성 옵션, MySQL `ENUM` → Kotlin `enum class` 생성 |
| v0.2 | **Flyway 마이그레이션의 `CREATE TABLE` → 엔티티**: DB 연결 없이 phantom-db 렉서 토큰으로 DDL을 읽어 같은 생성기를 탄다 |
| v0.2 | 기존 엔티티와 테이블의 차이를 diff 창으로 보여 주고 필드 추가만 반영 (`DiffManager` [A]) |
| v0.2 | `@Query` 위의 거터 액션 "Show SQL" → 그 쿼리를 콘솔 번역 모드로 보낸다 |
| v0.3 | 콘솔 결과에 커뮤니티 grid 사용(phantom-db가 grid 연동을 안정화한 뒤), 파라미터 값 기록 |
| v0.3 | Hibernate 5(javax) 런타임 지원. 필요할 때만 |

### 1.5 비목표

- **실행 중인 Spring Boot 앱의 `EntityManager`에 붙기.** 공개된 표준 경로가 없다(Actuator에 JPQL 실행 엔드포인트 없음 [A]). 디버거 평가(JDI)로 하는 방법은 복잡하고 앱 상태를 바꾼다. 하지 않는다.
- **Spring 컨텍스트 전체를 콘솔용으로 띄우기.** 사용자 앱은 Kafka, Redis, Batch, Security를 같이 띄운다 [V: `libs.versions.toml`]. 콘솔 하나에 그 비용과 부작용을 낼 이유가 없다. Hibernate만 띄운다(§3.5).
- Liquibase changelog 생성, DDL 생성(엔티티 → 테이블), 스키마 검증, ER 다이어그램, DTO/프로젝션 생성.
- HQL 전용 콘솔(Hibernate 고유 `Session` API), Criteria API, `persistence.xml`·`hbm.xml`.
- 방언 추가. phantom-db가 지원하는 MySQL, PostgreSQL, H2, ANSI만 쓴다.
- SQL 소스→싱크 taint 분석(`SqlSourceToSinkFlow` 같은 보안 검사).
- 라이선스·인증 관련 코드.

---

## 2. Ultimate 대비 경량화 원칙

| Ultimate에서 관측된 비용 | phantom-jpa-extras의 대응 |
|---|---|
| 역공학 플러그인이 Liquibase, JPA Model, 방언 모듈 8개에 하드 의존 [V: 디스크립터] | 하드 의존은 `phantom.jpa` 하나. phantom-db는 **optional 콘텐츠 모듈**로만 붙는다. phantom-db가 없으면 SQL 주입과 생성 모듈은 아예 로드되지 않는다 |
| JPA·Hibernate가 Database 플러그인에 최상위 의존 → DB 툴을 끌 수 없음 [V: 노트 `ult-database.md` §4 2행] | phantom-jpa → phantom-db 방향 의존 없음. 역방향도 없음. 둘 다 있을 때만 이 플러그인의 모듈이 다리를 놓는다 |
| JDBC 실행용 자식 JVM이 **IDE의 `-Xmx2048m`을 물려받음** [V: 노트 `ult-database.md` §2] | 콘솔 자식 JVM은 기본 `-Xmx256m`, `-XX:TieredStopAtLevel=1`. 콘솔을 처음 실행할 때만 띄우고 유휴 10분 뒤 종료 |
| Hibernate 버전별 콘솔 jar 5개 번들 [V: `plugins/hibernate/lib`] | 콘솔 런타임 jar 1개(Hibernate 6.x / Jakarta 전용, 공개 API만 사용). Hibernate 자체는 번들하지 않고 **사용자 프로젝트 classpath의 것**을 쓴다 |
| Database Tools의 `multiHostInjector` 10개 + `injectionConfig`가 모든 문자열에 질의 [V: 노트 §2], Cron 주입기가 문자열 리터럴마다 호출되는 비용 1샘플 [V: 노트 `ult-other-plugins.md`] | 주입기 1개(Java·Kotlin 등록 2개). 첫 단계에서 텍스트만으로 거르고(§3.6), `JavaLibraryUtil.hasLibraryClass`로 모듈 활성화 확인. phantom-jpa의 JPQL 주입기와 **호스트 판정 코드를 공유**해서 같은 문자열을 두 번 분석하지 않는다 |
| JPA Model의 startup activity 3개, `BulkFileListener`, 고유 `fileBasedIndex` [V: 노트 `ult-spring-javaee.md`] | startup activity 0, 메시지 버스 리스너 0, 새 인덱스 0. 서비스는 전부 light service이고 액션이나 콘솔이 처음 부를 때 생긴다 |
| 콘텐츠 모듈이 프로젝트가 기술을 쓰는지와 무관하게 로드 [V: README Q4] | 모듈 로드 자체는 막을 수 없다. 액션 `update()`는 `ActionUpdateThread.BGT`에서 캐시된 활성화 플래그만 보고, 주입기는 첫 줄에서 모듈 활성화를 본다 |

공통 규칙:
1. **EDT 작업 없음.** 메타데이터 조회, 코드 생성 텍스트 렌더링, 자식 프로세스 시작·통신은 백그라운드 코루틴에서 한다. EDT는 대화상자 표시와 PSI 쓰기(`WriteCommandAction`)만 한다. PSI 쓰기도 텍스트를 미리 만든 뒤 파일 생성 + 포맷 + import 정리만 한다.
2. **증분·지역 무효화.** 주입 결과는 플랫폼이 호스트 단위로 관리한다. 이 플러그인의 캐시는 (a) 파일 단위 "쿼리 상수 집합"(phantom-jpa 것을 공유), (b) 모듈 단위 방언 추정(`JavaLibraryModificationTracker` + 설정 파일 수정 카운트), (c) 콘솔 프로세스의 classpath 지문뿐이다. 전역 `PsiModificationTracker`는 쓰지 않는다.
3. **요청 시 비용.** 생성기는 사용자가 고른 테이블만 조회한다. 콘솔 프로세스는 실행 버튼을 누를 때 생긴다.

---

## 3. 아키텍처

### 3.1 모듈 배치

phantom-jpa와 같은 콘텐츠 모듈 방식(TOML 플러그인 구성)을 따른다 [V: `plugins/toml/resources/META-INF/plugin.xml`의 `<content>`]. 별도 플러그인으로 두는 이유는 사용자가 Settings → Plugins에서 통째로 끌 수 있게 하기 위해서다.

```
plugins/phantom-jpa-extras/
├── resources/META-INF/plugin.xml     # id = "phantom.jpa.extras"; <dependencies><plugin id="phantom.jpa"/>
├── plugin-content.yaml, BUILD.bazel, phantom.jpa.extras.iml
├── common/   → phantom.jpa.extras.common   (loading="embedded")
│   └── 활성화 판정, 모듈 설정 읽기(데이터소스/방언 추정), 이름 변환 유틸
├── sql/      → phantom.jpa.extras.sql      (optional)
│   │  deps: <module name="phantom.db.sql"/>, <module name="phantom.jpa.core"/>
│   └── NativeSqlInjector (Java), 방언 선택
├── sql-kotlin/ → phantom.jpa.extras.sql.kotlin (optional)
│   │  deps: 위 + <plugin id="org.jetbrains.kotlin"/>
│   └── KtStringTemplateExpression 등록
├── reverse/  → phantom.jpa.extras.reverse  (optional)
│   │  deps: <module name="phantom.db.api"/>, <plugin id="org.jetbrains.kotlin"/>
│   └── EntitySpec 모델, 타입 매핑, KotlinEntityRenderer, 대화상자, 액션
├── console/  → phantom.jpa.extras.console  (optional)
│   │  deps: <module name="phantom.jpa.core"/>, <plugin id="com.intellij.java"/>
│   └── 콘솔 UI, ConsoleProcessManager, 프로토콜 클라이언트
├── console-rt/ → phantom.jpa.extras.console.rt   # 콘텐츠 모듈 아님. 자식 JVM용 jar
│   └── Java 17, IDE 의존 0. PROVIDED: jakarta.persistence-api 3.1, hibernate-core 6.x
└── tests/    → phantom.jpa.extras.tests
```

- 콘텐츠 모듈 디스크립터의 `<dependencies><module .../><plugin .../></dependencies>` 형식 [V: `plugins/env-files-support/kotlin/resources/intellij.dotenv.kotlin.xml`]. 다른 플러그인의 콘텐츠 모듈에 의존하는 optional 모듈은 그 플러그인이 꺼져 있으면 로드되지 않는다 [A: 플랫폼 콘텐츠 모듈 규칙. T0에서 phantom-db를 끈 상태로 확인].
- `console-rt` 패키징은 `junit_rt`와 같은 방식이다: `.iml`은 `LANGUAGE_LEVEL`만 정하고 IDE 모듈에 의존하지 않으며, 플러그인 레이아웃에서 `spec.withModule("phantom.jpa.extras.console.rt", "phantom-jpa-console-rt.jar")`로 별도 jar를 만든다 [V: `plugins/junit_rt/intellij.junit.rt.iml`, `CommunityRepositoryModules.kt:199`의 `spec.withModule("intellij.junit.rt", "junit-rt.jar")`].
- 번들 등록은 `BaseIdeaProperties.kt`의 `IDEA_BUNDLED_PLUGINS`에 추가한다 [V: 목록 존재, L14].

### 3.2 커뮤니티 확장점과 API

| 용도 | 확장점 / API | 경로 | 상태 |
|---|---|---|---|
| SQL 주입 | `com.intellij.multiHostInjector`, `MultiHostInjector`, `MultiHostRegistrar.startInjecting/addPlace/doneInjecting` | `platform/core-api/src/com/intellij/lang/injection/MultiHostInjector.java`, `MultiHostRegistrar.java` | [V] |
| (대안) 일반 주입 | `languageInjectionContributor` EP, `LanguageInjectionContributor`, `SimpleInjection` | `platform/core-api/resources/META-INF/Core.xml:28`, `platform/core-api/src/com/intellij/lang/injection/general/` | [V]. 상수 간접 참조를 표현하기 어려워 쓰지 않는다 (jpa 설계 §3.2와 같은 이유) |
| 주입 결과 조회 (테스트) | `InjectedLanguageManager` | `platform/core-api/src/com/intellij/lang/injection/InjectedLanguageManager.java` | [V] |
| Kotlin 주입 기반 (참고) | `KotlinLanguageInjectionContributorBase.injectInAnnotationCall` | `plugins/kotlin/injection/base/src/org/jetbrains/kotlin/idea/base/injection/KotlinLanguageInjectionContributorBase.kt:483` | [V]. `@Language("SQL")`는 이 경로로 자동 동작할 가능성이 높다 [A] |
| UAST 상수 평가 | `UInjectionHost.evaluateToString()` | `uast/uast-common/src/org/jetbrains/uast/expressions/UInjectionHost.kt:19` | [V] |
| 활성화 | `JavaLibraryUtil.hasLibraryClass(Module, fqn)` | `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java:119` | [V] |
| 코드 생성: 파일 | `PsiFileFactory.createFileFromText(name, fileType, text)` | `platform/core-api/src/com/intellij/psi/PsiFileFactory.java:34` | [V] |
| 코드 생성: 쓰기 | `WriteCommandAction` | `platform/core-api/src/com/intellij/openapi/command/WriteCommandAction.java` | [V] |
| 포맷 | `CodeStyleManager.reformat` | `platform/core-api/src/com/intellij/psi/codeStyle/CodeStyleManager.java` | [V] |
| Kotlin import 정리 | `ShortenReferencesFacility.getInstance().shorten(KtFile, TextRange)` | `plugins/kotlin/base/code-insight/src/org/jetbrains/kotlin/idea/base/codeInsight/ShortenReferencesFacility.kt:11,26` | [V] |
| Java import 정리 (v0.2) | `JavaCodeStyleManager.shortenClassReferences` | `java/java-psi-api/src/com/intellij/psi/codeStyle/JavaCodeStyleManager.java:65` | [V] |
| 대화상자 / 미리보기 | `DialogWrapper`, `EditorTextField`, `LightVirtualFile` | `platform/platform-api/src/com/intellij/openapi/ui/DialogWrapper.java`, `platform/platform-impl/src/com/intellij/ui/EditorTextField.java`, `platform/core-api/src/com/intellij/testFramework/LightVirtualFile.java` | [V] |
| 액션 스레드 | `ActionUpdateThread.BGT` | `platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionUpdateThread.java` | [V] |
| 자식 JVM 구성 | `SimpleJavaParameters.setJdk/setMainClass/getClassPath/createOSProcessHandler` | `platform/lang-core/src/com/intellij/execution/configurations/SimpleJavaParameters.java:47,55,59,212` | [V] |
| 프로세스 핸들러 | `OSProcessHandler` | `platform/platform-util-io/src/com/intellij/execution/process/OSProcessHandler.java` | [V] |
| 모듈 런타임 classpath | `OrderEnumerator.orderEntries(module).runtimeOnly().recursively().classes().getPathsList()` | `platform/projectModel-api/src/com/intellij/openapi/roots/OrderEnumerator.java:47,83`, `OrderRootsEnumerator.java:43` | [V] |
| rt jar 위치 | `PathManager.getJarPathForClass` | `platform/util/src/com/intellij/openapi/application/PathManager.java:985` | [V] |
| 실행 전 빌드 | `ProjectTaskManager.build(Module...)` (Promise) | `platform/lang-api/src/com/intellij/task/ProjectTaskManager.java:67` | [V]. Gradle 위임 빌드로 동작 [A] |
| 콘솔 에디터 | `LanguageConsoleBuilder.psiFileFactory(...)`, `initActions(BaseConsoleExecuteActionHandler, historyType)` | `platform/lang-impl/src/com/intellij/execution/console/LanguageConsoleBuilder.java:84,89` | [V] |
| 콘솔 실행 액션 | `ConsoleExecuteAction`, `ProcessBackedConsoleExecuteActionHandler` | `platform/lang-impl/src/com/intellij/execution/console/ConsoleExecuteAction.java`, `ProcessBackedConsoleExecuteActionHandler.java` | [V] |
| 결과 표 | `JBTable` | `platform/platform-api/src/com/intellij/ui/table/JBTable.java` | [V] |
| 백그라운드 진행 | `withBackgroundProgress` | `platform/progress/shared/src/TaskSupport.kt` | [V: 정의 존재] |
| 모듈 찾기 | `ModuleUtilCore.findModuleForPsiElement` | `platform/projectModel-api/src/com/intellij/openapi/module/ModuleUtilCore.java` | [V] |
| 테스트 | `LightJavaCodeInsightFixtureTestCase`, `KotlinLightCodeInsightFixtureTestCase` | `java/testFramework/src/com/intellij/testFramework/fixtures/`, `plugins/kotlin/test-framework/test/...` | [V: jpa 설계 §5.1에서 확인한 경로] |

### 3.3 기능 A: 문자열 속 SQL (`NativeSqlInjector`)

**소유권 분담.** 같은 문자열을 두 주입기가 동시에 노리면 안 된다. 그래서 위치별로 주인을 하나씩 정한다.

| 위치 | 주인 | 언어 |
|---|---|---|
| `@Query(nativeQuery = false)`, `@NamedQuery`, `createQuery` | phantom-jpa `JpqlQueryInjector` | JPQL |
| `@Query(nativeQuery = true)`, `@NamedNativeQuery`, `createNativeQuery`, Hibernate SQL 어노테이션 | **이 설계 `NativeSqlInjector`** | SQL |
| `JdbcTemplate.*`, `NamedParameterJdbcTemplate.*`, `JdbcClient.sql`, `@Language("SQL")`, `// language=SQL` | phantom-db `injection` 모듈 (v0.2) | SQL |

`nativeQuery`의 판정은 두 주입기가 같은 함수(`QueryHostLocator`, §3.8 요청 1)를 써서 결과가 반드시 반대가 되게 한다.

**동작** (호스트 = Java `PsiLiteralExpression`, Kotlin `KtStringTemplateExpression`):
1. 길이 < 4 또는 문자열이 아니면 종료.
2. `JpaExtrasActivation.sqlEnabled(module)`가 아니면 종료. 판정은 `hasLibraryClass(module, "jakarta.persistence.Entity" | "javax.persistence.Entity" | "org.hibernate.annotations.SQLRestriction")` 중 하나 [V: API]. 결과는 `JavaLibraryUtil` 내부 캐시를 탄다.
3. `QueryHostLocator.locate(host)`를 부른다. 결과는 `QuerySite(kind, annotationFqn?, attribute?, isNative, fragmentKind)`다. 직접 인자든 같은 파일 상수 초기값이든 phantom-jpa와 같은 규칙으로 찾는다(jpa 설계 §3.6 3–4단계). 짧은 이름 텍스트 비교로 먼저 거르고, FQN 확인은 마지막에 한다.
4. 우리 위치가 아니면 종료.
5. `fragmentKind`에 따라 prefix/suffix를 붙여 주입한다. SQL 렉서·평평한 PSI는 phantom-db 것이라 문법 시작 규칙이 없다. 그래서 조각은 렉서 수준 하이라이트만 받으면 된다. prefix는 사용자에게 보이지 않는다 [V: `addPlace(prefix, suffix, host, range)` 시그니처].
   - 전체 문장(`nativeQuery`, `@NamedNativeQuery`, `createNativeQuery`, `@SQLInsert/Update/Delete`): prefix 없음
   - WHERE 조각(`@SQLRestriction`, `@Where`): prefix `SELECT 1 FROM t WHERE `
   - 식 조각(`@Formula`, `@ColumnTransformer`, `@Check`): prefix `SELECT `, suffix ` FROM t`
   - ORDER 조각(`@SQLOrder`): prefix `SELECT 1 FROM t ORDER BY `
6. Kotlin 템플릿 엔트리 `$X`/`${...}`는 주입 범위에서 빼고 그 자리에 `__PH__` 식별자를 둔다(phantom-jpa와 같은 방식). SQL 렉서는 이를 `IDENT`로 읽으므로 따로 처리할 필요가 없다.
7. 바인드 파라미터 `?1`, `:name`은 phantom-db 렉서의 `PARAM` 토큰이 이미 다룬다 [A: phantom-db 설계 §3.3 토큰 목록].

**v0.1에서 주입된 SQL이 받는 것**: 하이라이트, 괄호 매칭, phantom-db 키워드 완성, 그리고 "캐시된 테이블/컬럼 완성"(phantom-db가 데이터 소스를 이미 펼쳐 둔 경우만, 네트워크 호출 없음). 검사(inspection)는 없다.

### 3.4 기능 B: 테이블 → Kotlin 엔티티 생성

**입구.**
1. phantom-db Database 툴 윈도우의 테이블 노드 우클릭 → "Generate JPA Entities…" (다중 선택 가능). phantom-db가 그룹 ID와 데이터 키를 공개해야 한다(§3.8 요청 2).
2. Project 뷰의 패키지 우클릭 → New → "JPA Entities from Data Source…". 데이터 소스와 테이블 선택 목록을 띄운다. 대상 패키지는 우클릭한 패키지다.

**흐름.**
```
선택 테이블 ──(IO 코루틴)──▶ SchemaApi.tableDetails()  ──▶ TableDetails
                                                         │
JpaFacade(phantom-jpa) ─ 기존 엔티티/상위 클래스 조회 ────┤ (read action, 스마트 모드)
                                                         ▼
                        EntitySpecBuilder (순수 Kotlin, PSI 없음) ──▶ List<EntitySpec>
                                                         ▼
                        KotlinEntityRenderer (문자열) ──▶ 미리보기 대화상자 (EDT)
                                                         ▼  [Generate]
                        WriteCommandAction: createFileFromText → directory.add
                                            → reformat → ShortenReferencesFacility.shorten
```

**데이터 모델 (phantom-db api에 요청하는 형태, §3.8 요청 3).**
```kotlin
data class TableDetails(
  val catalog: String?, val schema: String?, val name: String, val comment: String?,
  val columns: List<ColumnInfo>, val primaryKey: List<String>,
  val foreignKeys: List<ForeignKeyInfo>, val indexes: List<IndexInfo>,
  val dbms: Dbms,
)
data class ColumnInfo(
  val name: String, val jdbcType: Int, val typeName: String,     // DatabaseMetaData.getColumns: DATA_TYPE, TYPE_NAME
  val size: Int?, val decimalDigits: Int?, val nullable: Boolean,
  val autoIncrement: Boolean, val defaultValue: String?, val comment: String?,  // IS_AUTOINCREMENT, COLUMN_DEF, REMARKS
)
data class ForeignKeyInfo(val name: String?, val columns: List<String>, val refTable: String, val refColumns: List<String>)
data class IndexInfo(val name: String, val columns: List<String>, val unique: Boolean)
```
필드 이름은 JDBC `DatabaseMetaData.getColumns/getPrimaryKeys/getImportedKeys/getIndexInfo` 결과 열에 맞췄다 [A: JDBC 4.x 공개 명세]. MySQL은 `REMARKS`에 컬럼 주석이 들어온다 [A].

```kotlin
// reverse 모듈 내부. 렌더러 입력. 테스트에서 직접 만든다
data class EntitySpec(
  val packageName: String, val className: String,
  val table: TableRef,                          // catalog/name, indexes(옵션)
  val superClass: SuperClassRef?,               // FQN + 이 상위 클래스가 이미 매핑한 컬럼 집합
  val ctorParams: List<PropertySpec>,           // non-null, 기본값 없음, 생성 키 아님
  val bodyProps: List<PropertySpec>,
  val annotations: List<AnnotationSpec>,        // @Entity, @Table, (옵션) @DynamicUpdate
  val warnings: List<String>,                   // 미리보기에 표시: 매핑 못 한 타입 등
)
data class PropertySpec(
  val name: String, val type: KType, val nullable: Boolean, val mutable: Boolean,
  val column: ColumnRef, val kind: PropKind,    // ID, BASIC, MANY_TO_ONE, ID_REF(FK를 스칼라로)
  val comment: String?, val defaultExpr: String?,
)
```

**생성 규칙 (기본값은 사용자 코드 스타일에서 뽑았다 [V: §1.2]).**

| 항목 | 규칙 | 옵션 |
|---|---|---|
| 클래스 이름 | `menu_photo` → `MenuPhoto` + 접미사. 접미사 기본값은 **모듈의 기존 `@Entity` 이름 과반의 공통 접미사**(사용자 프로젝트면 `Entity`) | 접미사, 테이블 접두어 제거(`tb_`) |
| 프로퍼티 이름 | snake → camel (`is_deleted` → `isDeleted`). Kotlin 키워드는 백틱 | |
| `@Column(name = ...)` | **항상 쓴다** (사용자 코드 133개 전부 명시). `length`(문자열), `precision/scale`(DECIMAL), `nullable = false`(NOT NULL) | "기본 네이밍과 같으면 생략" — Spring Boot 기본 `CamelCaseToUnderscoresNamingStrategy` 기준 [A] |
| `@Comment` | 컬럼 주석이 있고 `org.hibernate.annotations.Comment`가 classpath에 있으면 붙인다 | 끄기 |
| `@Table` | `name`, catalog(데이터 소스 기본값과 다를 때 또는 사용자 스타일처럼 명시), `indexes = [Index(...)]` | 인덱스 생략 |
| 상위 클래스 | phantom-jpa `JpaFacade`로 모듈의 `@MappedSuperclass` 목록을 뽑아 콤보로 보여 준다. 고른 상위 클래스가 이미 매핑한 컬럼 이름(`@Column(name)` 또는 네이밍 규칙)은 생성에서 뺀다. 예: `TimeBasedPrimaryKey`를 고르면 `id` 컬럼을 건너뛴다 | 없음 |
| ID | PK 1개: `@Id` + (auto increment면 `@GeneratedValue(strategy = IDENTITY)`) + `var id: Long? = null` 본문. 복합 PK는 v0.1에서 경고만 내고 `@EmbeddedId` 생성은 v0.2 | |
| FK | 기본 **스칼라 ID**(`val restaurantId: String`) — 사용자 코드가 대부분 이 형식 (`MenuEntity.restaurantId`). 옵션으로 `@ManyToOne(fetch = LAZY) @JoinColumn(name = ...)`. 대상 엔티티는 (1) 같은 실행에서 생성되는 것, (2) `@Table(name)`이 참조 테이블과 같은 기존 엔티티 순서로 찾는다. 못 찾으면 스칼라로 되돌리고 경고 | FK 방식 |
| 프로퍼티 배치 | NOT NULL이고 DB 기본값이 없고 생성 키가 아닌 컬럼은 생성자 파라미터 + 본문 `var x: T = x` + `protected set`. 나머지는 본문에 기본값과 함께 (`Boolean`은 `false`, nullable은 `null`) | "생성자 `var` 스타일", `protected set` 끄기 |
| 불변 | PK와 FK 스칼라는 `val` (사용자 스타일 `val restaurantId`) | |
| 파일 | `<패키지 폴더>/<ClassName>.kt`. 이미 있으면 덮어쓰지 않고 미리보기에서 "건너뜀"으로 표시 | |

**타입 매핑 (기본, MySQL 기준. PostgreSQL·H2는 같은 JDBC 타입 경로를 탄다) [A: JDBC 타입 / Hibernate 6 기본 매핑 공개 문서].**

| DB 타입 | JDBC 타입 | Kotlin 타입 |
|---|---|---|
| BIGINT / INT / SMALLINT | BIGINT / INTEGER / SMALLINT | `Long` / `Int` / `Short` |
| TINYINT(1), BIT(1), BOOLEAN | BIT / BOOLEAN / TINYINT(size=1) | `Boolean` |
| DECIMAL(p,s) / NUMERIC | DECIMAL / NUMERIC | `BigDecimal` (+`precision`, `scale`) |
| FLOAT / DOUBLE | REAL / DOUBLE | `Float` / `Double` |
| CHAR / VARCHAR / TEXT | CHAR / VARCHAR / LONGVARCHAR | `String` (+`length`) |
| CHAR(36) 중 이름이 `*_uuid` / BINARY(16) | | `String` 기본, 옵션 `UUID` |
| DATE / TIME | DATE / TIME | `LocalDate` / `LocalTime` |
| DATETIME / TIMESTAMP | TIMESTAMP | `LocalDateTime` (옵션 `Instant`) |
| BLOB / VARBINARY | BLOB / VARBINARY | `ByteArray` (+`@Lob`은 BLOB만) |
| JSON | `typeName == "JSON"` | `String` + 경고(v0.2: `@JdbcTypeCode(SqlTypes.JSON)`) |
| ENUM('A','B') | CHAR + `typeName == "ENUM"` | `String` + 경고(v0.2: `enum class` + `@Enumerated(STRING)`) |
| 그 밖 | | `Any` 대신 **생성하지 않고 경고** |

렌더러는 `StringBuilder`로 텍스트를 만든다. PSI 팩토리로 노드를 하나씩 붙이지 않는다. 이유는 (1) 렌더러를 PSI 없이 단위 테스트할 수 있고, (2) 쓰기 액션 안의 시간이 짧아지기 때문이다. 모든 타입은 FQN으로 쓰고 마지막에 `ShortenReferencesFacility`가 import로 바꾼다 [V: API].

### 3.5 기능 C: JPA 콘솔

**선택지 평가.**

| 방식 | 장점 | 단점 | 판단 |
|---|---|---|---|
| (1) 실행 중 앱의 `EntityManager` 사용 | 실제 설정 그대로 | 표준 접속 경로 없음, 앱 상태 오염 | 비목표 |
| (2) 자식 JVM에서 Spring Boot 컨텍스트 전체 기동 | 설정·컨버터·리스너가 실제와 같음 | 기동 수십 초, Kafka/Redis 연결 시도, 부작용 | 하지 않음 |
| (3) **자식 JVM에서 Hibernate만 기동** (프로젝트 classpath, 엔티티 목록은 IDE가 넘김) | 수 초, 외부 연결 없음(번역 모드), 공개 API만으로 가능 | Spring이 바꾸는 기본값을 우리가 흉내 내야 함 | **채택** |
| (4) IDE 안에서 JPQL → SQL을 직접 번역 | 프로세스 없음 | Hibernate SQL 생성기를 다시 만드는 일. 결과가 실제와 다를 수 있음 | 하지 않음 |

**자식 JVM 구성 (`ConsoleProcessManager`, 프로젝트 light service).**
- JDK: 모듈 SDK, 없으면 프로젝트 SDK (`SimpleJavaParameters.setJdk`) [V: API].
- classpath: `OrderEnumerator.orderEntries(module).runtimeOnly().recursively().classes().getPathsList()` + `PathManager.getJarPathForClass(ConsoleRtMain::class.java)` [V: API]. Gradle로 가져온 모듈의 출력 폴더(`build/classes/kotlin/main` 등)가 이 목록에 들어온다고 본다 [A: T6에서 확인].
- VM 옵션: `-Xmx256m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dfile.encoding=UTF-8`. 설정에서 바꿀 수 있다.
- 시작 전 빌드: 기본값 "출력 폴더가 소스보다 오래됐으면 묻기". 빌드는 `ProjectTaskManager.build(module)` [V: API].
- 수명: 콘솔에서 처음 실행할 때 시작한다. classpath 지문(출력 폴더들의 최신 수정 시각 + 라이브러리 목록 해시)이 바뀌면 다음 실행 때 재시작한다. 유휴 10분 또는 콘솔을 닫으면 종료한다. 리스너는 두지 않고, 실행 직전에 지문을 비교한다.
- 비밀 값(실행 모드 v0.2의 비밀번호)은 명령줄에 넣지 않고 표준 입력으로 보낸다.

**프로토콜.** stdin/stdout 한 줄 JSON. `console-rt`는 사용자 classpath와 충돌하지 않게 JSON 라이브러리 없이 작은 수제 인코더/디코더를 쓴다(사용자 앱이 Jackson 버전을 고정하고 있기 때문). stderr는 콘솔 로그 패널로 그대로 흘린다.
```
→ {"op":"boot","entities":["com.x.MenuEntity",...],"converters":[...],"props":{...}}
← {"ok":true,"bootMs":2140,"warnings":[...]}
→ {"op":"translate","id":7,"jpql":"select m from MenuEntity m where m.restaurantId = :rid"}
← {"id":7,"sql":["select me1_0.id, ... from prototype_reservation.menu me1_0 where ..."],"params":["rid"]}
→ {"op":"execute",...}   // v0.2
```

**Hibernate 기동 (`console-rt`, 공개 SPI만).**
- `jakarta.persistence.spi.PersistenceUnitInfo`를 직접 구현하고 `org.hibernate.jpa.HibernatePersistenceProvider().createContainerEntityManagerFactory(info, props)`를 부른다. Spring Boot 프로젝트에는 `persistence.xml`이 없으므로 이 컨테이너 방식이 맞다 [A: Jakarta Persistence 3.1 §8/§9 컨테이너 계약, Hibernate 6 공개 API].
- 관리 클래스 목록: phantom-jpa `JpaFacade`로 찾은 모듈 범위의 `@Entity`·`@MappedSuperclass`·`@Embeddable` FQN, 그리고 `@Converter` 클래스 FQN. 모듈 범위로 찾는다. 사용자 작업 공간에 같은 FQN 사본이 4벌 있기 때문이다 [V: jpa 설계 §1.1].
- 속성 (Spring Boot가 기본으로 바꾸는 것을 흉내 냄):
  - `hibernate.physical_naming_strategy = org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy`, `hibernate.implicit_naming_strategy = org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy`(classpath에 있을 때) [A: Spring Boot 3 기본값]
  - `hibernate.dialect`: 모듈 설정의 `spring.jpa.database-platform` 또는 `spring.jpa.properties.hibernate.dialect`(§3.6), 없으면 데이터 소스 DBMS
  - 사용자 설정의 `spring.jpa.properties.hibernate.*`는 그대로 넘긴다. 단 아래 안전 속성이 항상 이긴다.
  - **안전 속성 (강제)**: `hibernate.hbm2ddl.auto=none`, `jakarta.persistence.schema-generation.database.action=none`, `hibernate.boot.allow_jdbc_metadata_access=false` [A: Hibernate 6.x 설정 이름. 버전별 차이는 T6 스파이크에서 확인]
- 번역 모드의 SQL 캡처: `hibernate.connection.provider_class`에 rt의 `CapturingConnectionProvider`(공개 SPI `org.hibernate.engine.jdbc.connections.spi.ConnectionProvider`)를 넣는다. 이것은 `java.lang.reflect.Proxy`로 만든 가짜 `Connection`을 돌려준다. `prepareStatement(sql)`가 불리면 SQL을 기록하고 `TranslationCaptured` 예외를 던진다. rt는 이 예외를 잡아 기록된 SQL을 응답한다. 예비 수단으로 `hibernate.session_factory.statement_inspector`(공개 SPI `StatementInspector`)에서도 SQL을 기록한다 [A: 두 SPI 모두 Hibernate 6 공개 문서에 있음. 호출 순서와 가짜 연결 메타데이터 요구 사항은 T6 스파이크 대상, §6 R1].
- `UPDATE`/`DELETE` JPQL은 `executeUpdate()` 경로로 번역한다. 가짜 연결이라 실제로 실행되지 않는다.
- 엔티티 리스너(`@EntityListeners(AuditingEntityListener::class)`)는 Hibernate가 리플렉션으로 만든다. 번역 모드에서는 불리지 않는다 [A].

**콘솔 UI.**
- 입구: 엔티티 클래스나 리포지토리 파일의 에디터 우클릭 → "Open JPA Console", Tools 메뉴. 모듈은 현재 파일의 모듈, 없으면 모듈 선택 팝업(엔티티가 있는 모듈만).
- 에디터: `LanguageConsoleBuilder().psiFileFactory { vf, project -> JPQL PsiFile }`로 phantom-jpa의 JPQL 언어 파일을 만든다 [V: `psiFileFactory` 메서드 존재]. 콘솔 파일에 "문맥 모듈"을 사용자 데이터로 달아서 phantom-jpa가 엔티티 이름을 그 모듈 범위로 해석하게 한다(§3.8 요청 4).
- `Ctrl+Enter`: 캐럿 문장(빈 줄 기준) 또는 선택 영역을 보낸다. 결과 패널: 번역 모드는 SQL 텍스트(phantom-db가 있으면 SQL 하이라이트가 붙은 읽기 전용 에디터, 없으면 일반 텍스트) + 파라미터 목록. 오류는 Hibernate 예외 메시지 첫 줄과 원인 체인.
- v0.2 실행 모드: 결과를 `JBTable`로 보인다(최대 500행). 엔티티 결과는 `EntityManagerFactory.getMetamodel()`의 단일 속성만 펼친다. 연관은 `PersistenceUnitUtil.getIdentifier`로 `MenuPhotoEntity#123` 형태로만 보여 준다. 지연 로딩을 건드리지 않기 위해서다 [A: JPA 공개 API]. DML은 트랜잭션 안에서 실행하고 기본으로 롤백한다. "Commit" 체크를 켰을 때만 커밋한다. 연결 정보는 phantom-db 데이터 소스에서 받고, `jdbc:p6spy:` 접두어는 벗긴다(phantom-db 설계 R7).

### 3.6 방언·설정 추정 (`ModuleJpaSettings`, common 모듈)

SQL 주입의 방언과 콘솔의 Hibernate 속성은 같은 정보를 쓴다.
1. 모듈 리소스 루트의 `application*.yml|yaml|properties`와 그 안의 `spring.config.import`가 가리키는 파일에서 `spring.jpa.database-platform`, `spring.jpa.properties.hibernate.dialect`, `spring.datasource.url`, `spring.jpa.properties.*`를 읽는다. 사용자 프로젝트는 `persistence/datasource/local.yaml`처럼 프로파일별로 나뉜다 [V]. 그래서 콘솔에서는 **프로파일 선택 콤보**(기본 `local`)를 둔다. 주입 방언은 프로파일과 무관하게 "가장 흔한 DBMS"를 쓴다.
2. 1이 없으면 모듈 라이브러리의 드라이버(`mysql-connector-j` → MySQL, `postgresql` → PostgreSQL, `h2` → H2).
3. 그래도 없으면 ANSI.
- 파싱은 정규식이 아닌 YAML/Properties PSI(커뮤니티 `plugins/yaml`, `plugins/properties` [V: 디렉터리 존재])로 하되, 읽기 전용 PSI 순회만 한다. 결과는 모듈 단위 `CachedValue`이고, 의존성은 **그 설정 파일들의 `PsiFile`** + `JavaLibraryModificationTracker`다. 그래서 다른 파일을 고쳐도 무효화되지 않는다.
- 사용자 프로젝트 주의점: 로컬 프로파일은 H2Dialect인데 운영은 MySQL이다 [V]. 주입 방언을 H2로 잡으면 `GET_LOCK` 같은 MySQL 함수가 이상해 보일 수 있다. phantom-db 렉서는 함수 이름을 키워드로 보지 않으므로 하이라이트에서 차이가 거의 없다 [A].

### 3.7 스레딩

| 작업 | 실행 위치 |
|---|---|
| 주입 판정 | 데몬이 이미 잡은 read action (플랫폼이 호출) |
| 액션 `update()` | `ActionUpdateThread.BGT`, 캐시된 활성화 플래그만 |
| 메타데이터 조회 (`tableDetails`) | phantom-db가 주는 `suspend` 함수, `Dispatchers.IO` |
| 엔티티 목록·상위 클래스 조회 | `readAction {}` (스마트 모드에서, dumb이면 "인덱싱 후 다시" 안내) |
| 렌더링 | `Dispatchers.Default`, PSI 없음 |
| 파일 생성·포맷·import | EDT의 `WriteCommandAction` 한 번 (파일당) |
| 프로세스 시작·통신 | `Dispatchers.IO`, `withBackgroundProgress`로 취소 가능 |
| 콘솔 결과 반영 | EDT, 모델 설정만 |

모든 코루틴은 서비스가 주입받은 `CoroutineScope`의 자식이다. 프로젝트를 닫으면 자식 JVM도 종료한다(`Disposable` 부모 = 프로젝트 서비스).

**Kotlin K2/UAST.** 주입기는 Kotlin PSI(`KtStringTemplateExpression`, `KtAnnotationEntry`, `KtProperty.isConst`)와 UAST(`UInjectionHost.evaluateToString`)만 쓴다. Analysis API(`analyze {}`)를 직접 부르지 않는다. 그래서 K1/K2 양쪽에서 같은 코드가 돈다 [V: K2 UAST 구현 `plugins/kotlin/uast/uast-kotlin-fir` 존재, jpa 설계 §3.9]. 엔티티 생성은 텍스트로 Kotlin 파일을 만들고 `ShortenReferencesFacility`(K2 구현이 등록돼 있다고 가정 [A])로 import를 정리한다.

### 3.8 선행 설계에 요청하는 변경

| # | 대상 | 요청 | 이유 |
|---|---|---|---|
| 1 | phantom-jpa core | `QueryHostLocator.locate(host): QuerySite?`를 `api` 패키지로 공개. 직접 인자/같은 파일 상수/조각 판정과 `isNative` 판정을 담는다 | JPQL과 SQL 주입기가 같은 문자열을 두 번 분석하지 않고, `nativeQuery` 판정이 항상 서로 반대가 되게 하려고 |
| 2 | phantom-db ui | 테이블 노드 팝업 그룹 ID(`PhantomDb.TableNodePopup`)와 데이터 키(`PhantomDbDataKeys.SELECTED_TABLES: List<TableRef>`, `DATA_SOURCE_ID`) 공개 | 생성 액션을 `add-to-group`으로 붙이려고 |
| 3 | phantom-db api | v0.3 계획의 `phantom.db.api`를 **v0.1 끝**으로 당기고, 캐시 조회 외에 사용자가 시작한 요청용 `suspend fun tableDetails(dataSourceId, TableRef): TableDetails`를 추가 (연결이 닫혀 있으면 열어도 됨) | 생성기는 사용자가 명시적으로 고른 테이블만 조회하므로 "캐시만" 규칙의 예외로 둘 수 있다 |
| 4 | phantom-jpa core | JPQL 파일에 `JpqlContext.MODULE` 사용자 데이터 키를 두고, 엔티티 이름 해석이 호스트가 없을 때 이 키를 쓰게 | 콘솔 파일(`LightVirtualFile`)은 모듈이 없다 |
| 5 | phantom-jpa core | `JpaFacade.managedClassFqns(module): List<String>` (엔티티 + MappedSuperclass + Embeddable + Converter) | 콘솔 기동 목록 |
| 6 | phantom-db sql | 렉서의 방언을 주입된 파일에서도 정할 수 있는 방법. 후보: 주입 시 prefix 첫 줄 주석 `-- phantom:dialect=mysql`을 렉서가 읽기, 또는 `SyntaxHighlighterFactory`가 `VirtualFileWindow`의 호스트 파일 모듈로 판정 | phantom-db는 방언을 언어가 아닌 렉서 플래그로 설계했다 [V: phantom-db §3.3] |

---

## 4. 구현 단계 (MVP v0.1)

| # | 작업 | 일 | 사용자가 IDE에서 보는 합격 기준 |
|---|---|---|---|
| T0 | 플러그인 골격: 콘텐츠 모듈 5개 + `console-rt` jar, `.iml`/`BUILD.bazel`, 번들 등록, `./installers.cmd` 빌드 | 1 | Settings → Plugins에 "Phantom JPA Extras"가 번들로 보인다. phantom-db를 끄고 재시작하면 `idea.log`의 플러그인 로드 줄에 `extras.sql`, `extras.reverse`가 없다 |
| T1 | §3.8 요청 1, 4, 5를 phantom-jpa에 반영 (API 추출만, 동작 변화 없음) | 1 | phantom-jpa 기존 테스트가 모두 통과한다 |
| T2 | `NativeSqlInjector` (Java + Kotlin), 위치별 prefix/suffix, 상수 간접 참조, 템플릿 자리표시자, `ModuleJpaSettings` 방언 추정 | 3 | `MenuEntity.kt`의 `@SQLRestriction(value = "is_deleted = false")` 안에서 `is_deleted`와 `false`가 SQL 색으로 보이고, Alt+Enter에 "Edit SQL fragment"가 뜬다. 테스트용 `@Query("select * from menu", nativeQuery = true)`는 SQL, `nativeQuery`를 지우면 JPQL로 바뀐다 |
| T3 | §3.8 요청 2, 3에 맞춰 phantom-db와 인터페이스 확정, `TableDetails` 조회 구현(phantom-db 쪽 작업 포함) | 1.5 | 스키마 트리에서 테이블을 고르고 숨은 디버그 액션 "Dump table details"를 실행하면 컬럼·PK·FK·인덱스·주석이 로그에 찍힌다 |
| T4 | `EntitySpecBuilder` + 타입 매핑 + 이름 규칙 + 상위 클래스·FK 처리 (순수 Kotlin) | 2.5 | 단위 테스트만: Flyway의 `menu` 테이블 정의를 손으로 옮긴 `TableDetails` → `EntitySpec`이 기대값과 같다 |
| T5 | `KotlinEntityRenderer`, 미리보기 대화상자(옵션: 패키지, 접미사, 상위 클래스, FK 방식, `@Comment`, 인덱스), 쓰기·포맷·import 정리, 두 입구 액션 | 3 | 로컬 DB의 `menu` 테이블로 생성한 파일이 기존 `MenuEntity.kt`와 **구조가 같다**: `@Table(catalog, name, indexes)`, `TimeBasedPrimaryKey` 상속으로 `id` 생략, `val restaurantId`, `var title ... protected set`, `@Comment`. 생성 직후 phantom-jpa 검사 경고 0, Gradle 컴파일 성공 |
| T6 | `console-rt` 스파이크 → 구현: `PersistenceUnitInfo`, 강제 안전 속성, `CapturingConnectionProvider`, JSON 프로토콜 | 3 | 터미널에서 rt를 사용자 모듈 classpath로 직접 실행해 `boot` + `translate`를 보내면 SQL이 나온다. DB 프로세스가 없어도 된다 |
| T7 | `ConsoleProcessManager` (JDK, classpath, 빌드 확인, 지문, 유휴 종료) + 콘솔 UI(JPQL 에디터, 모듈/프로파일 선택, SQL 결과 패널, 오류 표시) | 3 | `infrastructure-module`에서 "Open JPA Console" → `select m from MenuEntity m where m.restaurantId = :rid`를 `Ctrl+Enter` → 몇 초 안에 `... from prototype_reservation.menu me1_0 where ... and (me1_0.is_deleted = false)`처럼 `@SQLRestriction`이 반영된 SQL이 보인다. 콘솔에서 `MenuEn` 완성, 속성 Ctrl+클릭이 동작한다. 콘솔을 닫으면 `jps`에서 rt 프로세스가 사라진다 |
| T8 | 테스트 정리, 비용 측정(§5.3), 문서 갱신 | 2 | §5의 모든 항목 통과 |
| | **합계** | **20** | |

v0.2 예상: 실행 모드 4일, Java 렌더러 1.5일, 역방향 연관·enum 생성 1.5일, Flyway DDL → 엔티티 2일, diff 반영 2일, "Show SQL" 거터 0.5일 → 약 11.5일.

순서 이유: T2(주입)는 작고 독립적이라 먼저 끝내고, T3–T5(생성)는 phantom-db API 확정이 필요하며, T6(rt 스파이크)은 가장 불확실해서 일찍 위험을 확인하고 싶다. T6을 T3과 병렬로 시작해도 된다.

---

## 5. 테스트 방법

### 5.1 자동 테스트

- **주입** (`LightJavaCodeInsightFixtureTestCase`, `KotlinLightCodeInsightFixtureTestCase` K2 모드 [V: 클래스 존재]): JPA·Hibernate 어노테이션은 `myFixture.addClass("package org.hibernate.annotations; public @interface SQLRestriction { String value(); }")` 같은 스텁으로 넣는다. `InjectedLanguageManager.findInjectedElementAt`로 캐럿 위치 언어가 SQL/JPQL/없음인지 확인한다. 경우: `nativeQuery` 참/거짓/생략, 상수 간접 참조, Kotlin 템플릿, `@SQLRestriction`, `@Formula`, JPA 라이브러리 없는 모듈(주입 없음), phantom-db SQL 언어 없는 구성(모듈 미로드 → 주입 없음).
- **충돌 테스트**: 같은 호스트에 JPQL과 SQL이 동시에 주입되는 경우가 0인지, 대표 입력 50개를 돌며 확인한다.
- **생성기**: `EntitySpecBuilder`와 `KotlinEntityRenderer`는 PSI 없는 순수 테스트. 골든 파일 `testData/reverse/*.kt`. 경우: 복합 PK(경고), FK 스칼라/연관, 상위 클래스 컬럼 생략, nullable, DECIMAL, ENUM/JSON 경고, Kotlin 키워드 컬럼 이름(`object`, `when`), 기존 파일 건너뛰기.
- **생성 결과 컴파일 가능성**: 픽스처에서 생성 파일을 연 뒤 `myFixture.checkHighlighting()`으로 미해결 참조 0개를 확인한다(JPA 스텁 포함).
- **console-rt**: IDE와 무관한 JUnit 테스트. 테스트 classpath에 `hibernate-core` 6.6.x와 `jakarta.persistence-api` 3.1, H2를 둔다. 작은 엔티티 3개로 `boot` → `translate` 결과에 테이블·컬럼 이름이 Spring 네이밍 규칙대로 나오는지, `hbm2ddl`이 실행되지 않는지(가짜 연결에 DDL이 기록되지 않음), `TranslationCaptured`가 밖으로 새지 않는지 본다. Hibernate 6.2 / 6.4 / 6.6 세 버전 매트릭스 [A: 오프라인 저장소에 아티팩트를 둘 수 있는지 확인 필요].
- **프로세스 관리**: `ConsoleProcessManager`를 가짜 `ProcessHandler`로 테스트한다. 지문 변경 시 재시작, 유휴 종료, 프로젝트 dispose 시 종료.
- **스레딩**: 실행 경로에 `ThreadingAssertions.assertBackgroundThread()` 류의 단언을 넣는다 [A: 정확한 클래스 이름은 구현 때 확인].

### 5.2 사용자 프로젝트 수동 확인 (`~/orca/workspaces/prototype-reservation-system/infro`)

1. 프로젝트를 열고 인덱싱이 끝난 뒤 `RestaurantEntity.kt`, `MenuEntity.kt`의 `@SQLRestriction` 문자열에 SQL 하이라이트가 있는지 본다.
2. `ReservationJpaRepository.kt` 등 JPQL 상수에 SQL 주입이 **섞이지 않는지** 본다(JPQL 하이라이트만).
3. 로컬 MySQL(testcontainers 또는 docker)에 phantom-db 데이터 소스를 만들고 `menu`, `menu_photo`를 골라 "Generate JPA Entities…"를 실행한다. 대상 패키지는 임시 패키지(`com.reservation.persistence.tmpgen`). 기존 `MenuEntity.kt`, `MenuPhotoEntity.kt`와 diff를 떠서 차이가 이름·순서 수준인지 확인한다. 확인 뒤 임시 패키지를 지운다.
4. JPA 콘솔에서 사용자 `@Query` 상수 몇 개(상수 값 그대로)를 붙여 번역한다. 같은 쿼리를 테스트로 실행했을 때 P6Spy 로그에 찍히는 SQL과 비교한다. 테이블·컬럼·조인·`@SQLRestriction` 반영이 같아야 한다(별칭 이름은 달라도 됨).
5. 콘솔을 닫고 10초 뒤 `jps -l`에 rt 메인 클래스가 없는지 본다.
6. IDE를 재시작하고 콘솔이나 생성기를 쓰지 않은 상태에서 §5.3의 "미사용 비용"을 잰다.

### 5.3 비용 측정 (`reversed/02-runtime/`와 같은 방법, 번들 JBR `jcmd`/`jfr`)

| 지표 | 방법 | 목표 |
|---|---|---|
| 미사용 시 적재 클래스 | `jcmd <pid> GC.class_histogram`에서 `phantom.jpa.extras` 접두어 | < 40 (확장 등록 클래스 + 주입기) |
| 콘솔 사용 후 적재 클래스 | 같은 방법 | < 300 |
| 시작 경로 기여 | 시작 JFR의 `jdk.ExecutionSample` 중 `phantom.jpa.extras` 프레임 | 0 |
| 타이핑 중 주입기 비용 | 큰 Kotlin 리포지토리 파일 편집 JFR에서 `NativeSqlInjector.getLanguagesToInject` 누적 비율 | 데몬 전체의 < 0.5% [A: 목표치] |
| 콘솔 자식 JVM | `jcmd <rt-pid> GC.heap_info`, RSS | 힙 상한 256 MB, 첫 번역까지 < 5 s [A: 목표치] |
| 유휴 CPU | 90초 유휴 JFR | `phantom.jpa.extras` 프레임 0, rt 프로세스 없음(유휴 종료 뒤) |

---

## 6. 리스크와 남은 의문

| # | 리스크 / 의문 | 영향 | 대응 |
|---|---|---|---|
| R1 | 가짜 `Connection`으로 Hibernate가 SQL을 만들 때까지 가는지 모름. 부트 단계에서 `DatabaseMetaData` 호출, 시퀀스 조회, `Connection.unwrap` 등이 필요할 수 있다 [A] | 번역 모드 전체 | T6을 스파이크로 시작한다. 막히면 (b) 테스트 classpath의 H2를 `MODE=MySQL` 인메모리로 띄워 `hbm2ddl=create`로 빈 스키마를 만들고 `StatementInspector`로 SQL을 캡처하는 방식으로 바꾼다. H2는 사용자 프로젝트에 이미 있다 [V] |
| R2 | Spring Boot가 Hibernate에 넣는 설정(네이밍 전략, `AttributeConverter` 빈, `hibernate.type` 설정, Spring Data 감사 리스너)을 흉내 내는 데 빠짐이 있으면 SQL이 실제와 다르다 | 잘못된 확신 | 결과 패널에 "Spring 컨텍스트 없이 Hibernate만 기동함, 적용한 속성 목록"을 항상 보여 준다. §5.2-4의 P6Spy 대조를 회귀 확인으로 둔다 |
| R3 | Hibernate 버전별 공개 SPI·설정 이름 차이(6.0–6.6, 7.x) [A] | 특정 버전에서 기동 실패 | rt는 리플렉션 없이 공개 API만 쓰고, 설정 이름은 버전별 표로 관리한다. Hibernate 5/javax는 v0.3 이후 |
| R4 | Gradle 가져오기 모델의 `runtimeOnly()` classpath가 Gradle `runtimeClasspath`와 다를 수 있다(kapt 생성물, `compileOnly`, 테스트 고정 의존) [A] | `ClassNotFoundException` | 기동 실패 시 빠진 클래스를 보여 주고, v0.2에서 "Gradle에서 classpath 받기"(작은 Gradle 태스크 실행) 옵션을 검토한다 |
| R5 | 콘솔을 쓰려면 모듈이 컴파일돼 있어야 한다. Gradle 빌드는 수십 초 걸릴 수 있다 | 첫 사용 체감 | 빌드는 묻고 나서만 한다. 출력이 최신이면 바로 기동한다 |
| R6 | 주입된 SQL의 방언을 렉서에 전달하는 방법이 정해지지 않았다(§3.8 요청 6) | 방언별 토큰 차이(백틱, `#` 주석)가 틀릴 수 있음 | v0.1은 MySQL+ANSI 합집합 규칙으로 렉싱해도 실용상 문제가 적다 [A]. phantom-db와 함께 결정 |
| R7 | phantom-db api가 v0.3 계획이었다. 생성기가 이를 v0.1로 당겨 달라고 요청한다 | 일정 의존 | T3을 phantom-db 쪽 작업으로 같이 잡는다. 늦어지면 생성기를 v0.2의 "Flyway DDL → 엔티티"로 먼저 내는 대안이 있다(DB 연결 불필요) |
| R8 | 생성기 기본 스타일이 이 사용자 코드에 맞춰져 있다(항상 `@Column(name)`, `protected set`, 스칼라 FK). 다른 프로젝트에는 어색할 수 있다 | 범용성 | 옵션으로 노출하고 마지막 선택을 프로젝트 설정에 기억한다 |
| R9 | `ShortenReferencesFacility`의 K2 구현이 쓰기 액션 안에서 FQN 어노테이션 인자(`[Index(...)]`)까지 정리하는지 [A] | 생성 코드에 FQN이 남음 | 남으면 렌더러가 import 목록을 직접 만들고 짧은 이름으로 쓰도록 바꾼다(충돌 이름만 FQN) |
| R10 | 실행 모드(v0.2)에서 DML 롤백 기본값이라도, 드라이버나 DB가 DDL을 암묵 커밋하는 경우(MySQL DDL) | 데이터 변경 | 실행 모드는 JPQL만 받으므로 DDL이 없다. 네이티브 SQL은 콘솔에서 받지 않는다(phantom-db 콘솔의 일) |
| Q1 | 사용자가 nativeQuery를 쓰지 않으므로 SQL 주입의 실제 가치는 `@SQLRestriction` 2곳과 phantom-db 쪽 `JdbcTemplate` 상수뿐이다 | 우선순위 | T2를 3일 이상 끌지 않는다. 넘으면 Hibernate 조각만 남기고 나머지는 v0.2로 |
| Q2 | 콘솔 결과에 커뮤니티 grid를 쓸지, `JBTable`로 충분할지 | v0.2 UX | phantom-db의 grid 스파이크(phantom-db R2) 결과를 보고 정한다 |
| Q3 | 같은 FQN 엔티티가 여러 Gradle 빌드에 있을 때 콘솔이 고른 모듈의 출력만 classpath에 들어가는지 | 엉뚱한 사본으로 번역 | T7 합격 기준에 `infro/`와 `feat-contract/` 각각에서 콘솔을 열어 보는 항목을 추가한다 |
