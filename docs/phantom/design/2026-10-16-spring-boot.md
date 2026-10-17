# phantom-spring-boot 설계 (v0.1)

- 날짜: 2026-10-16
- 대상 트리: `phantom-idea` (intellij-community `idea/262.10968.63` 기반, branch `main`)
- 사용자: Kotlin + Spring Boot 3.4 + JPA + Gradle 백엔드 개발자. 검증 프로젝트는 `~/orca/workspaces/prototype-reservation-system` (Spring Boot `3.4.5`, `application.yaml` + `application-test.yaml`, `kotlin-kapt` 사용, starter web/data-jpa/data-jdbc/data-redis/security/validation/cache/actuator/batch, spring-kafka)
- 표기: **[V]** = 이 트리나 대상 파일에서 경로/API/값을 직접 확인함. **[A]** = 공개 문서나 일반 지식에 기댄 가정이고 아직 확인하지 않음.

## 0. 클린룸 경계

- 입력은 세 가지다. (1) `Intellij-rea/reversed/`의 **행동/비용 기술 노트** (README, `03-decompiled/2026-10-16-ult-*.md`, `02-runtime/2026-10-16-startup-jfr.md`), (2) Ultimate 2024.3.7 `spring-boot-plugin`, `spring-boot-initializr`의 `plugin.xml`과 콘텐츠 모듈 XML에 나오는 **기능 이름과 EP 이름** (기능 체크리스트로만 씀), (3) 이 트리의 커뮤니티 API와 공개 스펙(Spring Boot reference "Configuration Metadata" 부록, start.spring.io/Initializr API).
- `reversed/source/`는 열지 않았다. 디컴파일 코드를 보지 않았고, 이 문서의 클래스 이름은 전부 새로 지은 이름이다.
- 라이선스/인증 코드와 무관하다. 이 문서 외에 `phantom-idea`의 소스는 바꾸지 않았다.

## 1. 목표와 범위

### 1.1 MVP (v0.1): 매일 쓰는 것만

| # | 기능 | 사용자가 보는 것 |
|---|---|---|
| F1 | **설정 키 완성** (`application*.yaml|yml|properties`) | `spring.datasource.` 입력 시 `url`, `username`, `hikari.` … 후보와 타입, 기본값, 짧은 설명 |
| F2 | **값 완성** | boolean, enum 타입(`spring.jpa.hibernate.ddl-auto` → `none/validate/update/create/create-drop`), 메타데이터 `hints.values` |
| F3 | **키 → 정의로 이동** (Ctrl+B) | `@ConfigurationProperties` 클래스의 프로퍼티(Kotlin/Java 소스 또는 라이브러리 클래스)로 이동. 소스 타입이 없으면 메타데이터 JSON 해당 항목으로 이동 |
| F4 | **퀵 문서** (Ctrl+Q) | 설명, 타입, 기본값, 사용 중단 정보, 출처 jar |
| F5 | **사용 중단 키 경고 + 교체 quick-fix** | `deprecation.replacement`가 있으면 "Replace with `...`" |
| F6 | **사용자 메타데이터 반영** | `kapt`/annotation processor가 만든 `build/**/META-INF/spring-configuration-metadata.json`과 소스의 `META-INF/additional-spring-configuration-metadata.json`. 빌드 후 새 키가 바로 완성에 뜬다 |
| F7 | **프로필 인식** | `application-{profile}.*` 파일과 YAML 다중 문서의 `spring.config.activate.on-profile`에서 프로필 목록을 모은다. 실행 구성과 `spring.profiles.active` 값 완성에 쓴다 |
| F8 | **실행 구성의 Active profiles 필드** | Kotlin/Java `Application` 실행 구성 편집기에 "Spring Boot" 탭: Active profiles(완성 지원), 실행 시 `-Dspring.profiles.active=...` 주입 |
| F9 | **New Project 마법사: Spring Boot (start.spring.io)** | 커뮤니티의 promo 항목 자리. Kotlin + Gradle Kotlin DSL 기본, Boot 버전·Java 버전·의존성 선택 → zip을 받아 풀고 Gradle 연결 |
| F10 | **Endpoints 툴 창 (Spring MVC)** | `@RequestMapping`/`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` 목록 (HTTP 메서드, 경로, 컨트롤러), 더블클릭으로 이동, 텍스트 필터 |

### 1.2 v0.2 이후

- v0.2: 알 수 없는 키 inspection (기본 weak warning), `@Value("\${...}")`/`Environment.getProperty("...")` 문자열 안 키 완성과 이동 (UAST), 키 Find Usages(설정 파일 ↔ `@Value`), Map 키 힌트 provider(`logger-name`, `class-reference`, `handle-as`), Gradle `bootRun` 실행 구성의 프로필 주입, 합성(meta) 매핑 어노테이션과 `@RequestMapping` 상속 해석, Endpoints의 모듈/프레임워크 필터.
- v0.3: `spring.factories` / `META-INF/spring/*.imports` 파일 지원, URL 경로 인레이(커뮤니티 `microservices/url/inlay` 기반), Actuator 런타임 연동(실행 중인 앱의 `/actuator/mappings`, `/actuator/env` 조회), 프로필별 실효 값 미리보기.
- 별도 설계 문서로 분리: Spring 빈 모델(autowiring, 빈 gutter), Spring Data 리포지토리 메서드 이름, JPA/JPQL, HTTP Client, Database.

### 1.3 명시적 비목표

- **Spring 빈 그래프와 autowiring 검사는 이 플러그인에서 하지 않는다.** 런타임 분석에서 가장 비싼 부분(시작 후 Spring 모델 계산 5–6 CPU초, 어노테이션 하나 고쳐도 전체 재계산 inferred)이므로 범위를 분리해 따로 설계한다 (`reversed/03-decompiled/2026-10-16-ult-spring-javaee.md` §§ 5, 8).
- Boot 자동 구성 조건(`@ConditionalOn*`) 평가, 자동 구성 기반 빈 해석.
- Spring XML 설정, DOM 모델.
- Maven 지원 (사용자는 Gradle만 씀. 마법사는 Maven 옵션을 노출만 하고 연결은 v0.2).
- 런타임 Beans/Health/Env 탭, 실행 대시보드 커스터마이징, 다이어그램, Spring Cloud/Integration/Modulith, 템플릿 엔진(Thymeleaf/FreeMarker/Mustache) 연동, cron 표현식 도구, banner.txt 지원.
- Ultimate Spring Boot 플러그인과의 동시 설치 호환. 같은 IDE에 둘 다 있으면 phantom 쪽을 끈다(§ 3.1 `incompatible-with`).

## 2. Ultimate 대비 경량화 원칙

측정된 Ultimate 비용과 이 설계의 대응이다. 비용 수치는 모두 `reversed/` 노트의 관측값이다.

| Ultimate에서 관측된 비용 | 근거 | phantom-spring-boot의 대응 |
|---|---|---|
| 콘텐츠 모듈이 "프로젝트가 그 기술을 쓰는지"와 상관없이 의존 플러그인만 있으면 로드된다. Spring Boot 플러그인 하나가 클래스 로더 17개, 런타임 적재 클래스 672개, Metaspace 약 1.7 MB | README Q4, ult-spring-javaee § 1.1, § 4 | 콘텐츠 모듈 6개로 나누되 **클래스 수 자체를 작게** 유지한다(목표: 프로젝트 열고 Boot 파일을 열기 전 적재 클래스 < 50). 모든 EP 구현은 진입 첫 줄에서 `SpringBootDetector.isBootModule(module)`을 확인한다. 이 값은 커뮤니티 `JavaLibraryUtil.hasLibraryJar(module, "org.springframework.boot:spring-boot")`[V]이며 `JavaLibraryModificationTracker`[V]로만 무효화되는 `CachedValue`라 반복 비용은 맵 조회 수준이다 |
| Spring Boot 전용 `fileBasedIndex` 4개(`.imports`, `spring.factories`, 메타데이터 JSON 등)가 인덱싱 파이프라인에 붙는다 | ult-spring-javaee § 2 표 | **v0.1은 새 `FileBasedIndex`를 추가하지 않는다.** 라이브러리 jar의 메타데이터는 jar 루트에서 고정 경로(`META-INF/spring-configuration-metadata.json`)를 직접 조회한다. 소스 쪽 `additional-*.json`은 이미 있는 플랫폼 `FilenameIndex`로 찾는다. 인덱스 입력 필터가 모든 후보 파일에 호출되는 비용(JPA Buddy `DtoAndProjectionFileIndex.getInputFilter` 사례, ult-spring-javaee § 6)이 없다 |
| 어노테이션 하나, 파일 하나 바뀌어도 Spring 모델 캐시 전체가 무효화된다 (inferred) | ult-spring-javaee § 5 (`getOuterModelsDependencies` 의존 목록 기술) | 무효화 단위를 셋으로 나눈다. (1) 라이브러리 메타데이터: `JavaLibraryModificationTracker`만. (2) 빌드 출력/소스 메타데이터 JSON: 그 파일들의 VFS 이벤트만 세는 자체 `MetadataFilesTracker`. (3) Endpoints: Kotlin/Java PSI 변경 중 **매핑 어노테이션이 달린 파일**만 재스캔 (파일 단위 캐시). 일반 PSI 변경(`PsiModificationTracker` 전역 카운트)에는 묶지 않는다 |
| 시작 활동: JPA Model/JavaEE 등 `postStartupActivity` 3개, `backgroundPostStartupActivity` 2개 | ult-spring-javaee § 2 | **startup activity 0개.** 메타데이터 모델은 첫 완성/하이라이팅 요청 때 백그라운드에서 만든다. 마법사, 툴 창은 열 때만 |
| 서비스는 `preload` 없이 첫 호출 때 생성 (Ultimate도 동일) | ult-spring-javaee § 2 | 동일하게 유지하고, 서비스 생성자에서 아무 계산도 하지 않는다 |
| inspection 317개, lineMarker 82개, 인레이 22개가 daemon 하이라이팅에 붙는다(Spring Boot만 inspection 11, lineMarker 22, 인레이 10) | ult-spring-javaee § 2 | v0.1은 inspection 1개(F5), annotator 0개, line marker 0개, 인레이 0개. 키 이동은 reference로만 제공해 하이라이팅 패스에 비용을 얹지 않는다 |
| 15 s 이후 백그라운드 CPU의 약 10%가 Spring 모델(`UastSpringBeansScopeEnlarger.getAdditionalUseScope` 등 참조 검색 범위 확장 포함) | startup-jfr § 3 | `UseScopeEnlarger`, `referencesSearch` 확장을 v0.1에 넣지 않는다. v0.2 Find Usages도 설정 파일 범위 안에서만 찾는다 |
| Initializr는 적재 0 클래스 (이미 요청 시 로드) | ult-spring-javaee § 1.1 | 같은 방식. 마법사 모듈은 New Project 대화상자에서만 로드 |

추가 원칙:

1. **EDT 금지**: 메타데이터 파싱, jar 조회, 어노테이션 검색, HTTP 호출은 모두 백그라운드(`readAction`/`smartReadAction` 코루틴 또는 completion/daemon 스레드)에서 한다. EDT에서는 이미 계산된 결과만 그린다.
2. **dumb mode 정책**: 메타데이터 키 완성은 인덱스가 필요 없으므로 `DumbAware`로 동작한다(라이브러리 jar 직접 조회). 소스 이동(F3)과 Endpoints는 smart mode에서만.
3. **증분 모델**: 모듈별 키 집합 = 그 모듈이 보는 jar별 키 집합(공유 캐시)의 합. jar 하나가 바뀌면 그 jar 항목만 다시 파싱한다.
4. **메모리 상한**: Boot 3.4 starter 전체의 메타데이터 키는 수천 개 규모로 가정한다[A]. 문자열 intern과 compact 레코드로 모듈당 수 MB 이내를 목표로 하고, 측정은 § 5.3에서 한다.

## 3. 아키텍처

### 3.1 모듈 레이아웃

```
plugins/phantom-spring-boot/
├── BUILD.bazel                      # 플러그인 메인 모듈 (descriptor만)
├── phantom.springBoot.iml
├── resources/META-INF/plugin.xml    # id: phantom.spring.boot
├── core/        → phantom.springBoot.core        (메타데이터 모델, 감지, 프로필)
├── yaml/        → phantom.springBoot.yaml        (YAML 완성/참조/문서/inspection)
├── properties/  → phantom.springBoot.properties  (.properties 완성/참조/문서/inspection)
├── run/         → phantom.springBoot.run         (RunConfigurationExtension)
├── endpoints/   → phantom.springBoot.endpoints   (EndpointsProvider + 툴 창)
├── initializr/  → phantom.springBoot.initializr  (New Project generator)
└── testSrc/ , testData/
```

`plugin.xml` 골격(가정, 커뮤니티 콘텐츠 모듈 문법은 `plugins/yaml/resources/META-INF/plugin.xml`[V]과 `plugins/properties/properties-common/resources/META-INF/plugin.xml`[V]를 따름):

```xml
<idea-plugin>
  <id>phantom.spring.boot</id>
  <name>Spring Boot (Phantom)</name>
  <incompatible-with>com.intellij.spring.boot</incompatible-with>
  <dependencies>
    <plugin id="com.intellij.java"/>
  </dependencies>
  <content>
    <module name="phantom.springBoot.core"/>
    <module name="phantom.springBoot.yaml"/>        <!-- depends: intellij.yaml.backend -->
    <module name="phantom.springBoot.properties"/>  <!-- depends: intellij.properties.backend.psi -->
    <module name="phantom.springBoot.run"/>         <!-- depends: intellij.java.execution.impl -->
    <module name="phantom.springBoot.endpoints"/>
    <module name="phantom.springBoot.initializr"/>  <!-- depends: gradle.java (optional) -->
  </content>
</idea-plugin>
```

- YAML 플러그인 id는 `org.jetbrains.plugins.yaml`, 콘텐츠 모듈 `intellij.yaml`, `intellij.yaml.backend`가 있다 [V].
- Properties 플러그인 id는 `com.intellij.properties`, 콘텐츠 모듈 `intellij.properties.backend.psi`, `intellij.properties.backend`, `intellij.properties.psi`가 있다 [V].
- 번들 등록: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`의 `productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf(...)`(61행)에 `phantom.springBoot`을 추가한다 [V: 위치, A: 콘텐츠 모듈이 자동 포함되는지는 첫 빌드에서 확인].
- 커뮤니티 promo 정리(별도 커밋): `java/java-promo/resources/META-INF/community-integration.xml`의 `<moduleBuilder builderClass="com.intellij.java.promo.PromoSpringModuleBuilder"/>`(37행)와 `<pluginSuggestionProvider implementation="com.intellij.java.promo.FrameworkSuggestionProvider"/>`(3행, Boot 라이브러리를 보면 `com.intellij.spring.boot` 설치를 권함)를 제거한다 [V: 두 등록이 이 트리에 아직 있음]. 과제 설명은 "제거된 promo 항목"이라 했지만, 이 트리에서는 아직 제거되지 않았다.

Ultimate의 콘텐츠 모듈 이름(`intellij.spring.boot.configYaml`, `.core.ide`, `.run`, `.mvc`, `.gradle.shared` 등 25개, descriptor [V])은 기능 체크리스트로만 썼다. phantom은 6개로 시작한다.

### 3.2 커뮤니티 확장 지점 (모두 [V]: 경로 확인)

| 용도 | EP / API | 경로 |
|---|---|---|
| Boot 감지 | `JavaLibraryUtil.hasLibraryJar(Module, String)`, `getLibraryVersion(Module, String)`; `JavaLibraryModificationTracker.getInstance(Project)` | `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` (155, 176, 197행), `.../JavaLibraryModificationTracker.java` |
| YAML 키 경로 | `YAMLUtil.getConfigFullName`, `getConfigFullNameParts`, `getQualifiedKeyInFile` | `plugins/yaml/src/YAMLUtil.java` (package `org.jetbrains.yaml`) |
| YAML PSI | `YAMLKeyValue`, `YAMLFile`, `YAMLDocument` | `plugins/yaml/src/psi/` |
| YAML 메타 모델 (검토 대상) | `YamlMetaTypeProvider(ModelAccess, ModificationTracker)`, `YamlMetaTypeCompletionProviderBase`, `YamlUnknownKeysInspectionBase`, `YamlDeprecatedKeysInspectionBase`, `YamlDocumentationProviderBase`, `YamlKeyInsertHandler` | `plugins/yaml/backend/src/meta/impl/`, `plugins/yaml/backend/src/meta/model/` |
| Properties PSI | `PropertiesFile`, `IProperty` | `plugins/properties/properties-psi-api/src/com/intellij/lang/properties/` |
| 완성 | `CompletionContributor` (`completion.contributor`) | `platform/analysis-api/src/com/intellij/codeInsight/completion/CompletionContributor.java` |
| 참조(이동) | `psi.referenceContributor`; Kotlin/Java 문자열은 `UastReferenceRegistrar` (v0.2) | `uast/uast-common-ide/src/com/intellij/psi/UastReferenceRegistrar.kt` |
| 소스 파일 찾기 | `FilenameIndex` (플랫폼 기존 인덱스) | platform indexing |
| 실행 구성 확장 | `com.intellij.runConfigurationExtension` → `RunConfigurationExtension.updateJavaParameters`, `isEnabledFor`, 편집기 탭 | EP 선언 `java/execution/impl/resources/intellij.java.execution.impl.xml` 26행, 클래스 `java/execution/impl/src/com/intellij/execution/RunConfigurationExtension.java` |
| Kotlin 실행 구성 | `KotlinRunConfiguration : JavaRunConfigurationBase`, 생성자/직렬화/편집기에서 `JavaRunConfigurationExtensionManager` 호출 (84, 95행) | `plugins/kotlin/run-configurations/jvm/src/org/jetbrains/kotlin/idea/run/KotlinRunConfiguration.kt` |
| Java 실행 구성 | `ApplicationConfiguration` | `java/execution/impl/src/com/intellij/execution/application/ApplicationConfiguration.java` |
| Gradle 실행 (v0.2) | `GradleRunConfiguration` | `plugins/gradle/src/org/jetbrains/plugins/gradle/service/execution/GradleRunConfiguration.java` |
| 새 프로젝트 마법사 | `com.intellij.newProjectWizard.generator` → `GeneratorNewProjectWizard`(`id`, `name`, `icon`, `ordinal`, `description`, `groupName`, `isEnabled()`, `createStep(WizardContext)`) | EP `platform/platform-resources/src/META-INF/PlatformExtensionPoints.xml` 550행, 클래스 `platform/platform-impl/src/com/intellij/ide/wizard/GeneratorNewProjectWizard.kt`, 어댑터 `GeneratorNewProjectWizardBuilderAdapter.kt` |
| Gradle 연결 | `linkAndSyncGradleProject(project, projectFile)` (suspend) | `plugins/gradle/src/org/jetbrains/plugins/gradle/service/project/open/GradleProjectImportUtil.kt` 62–66행 |
| HTTP | `HttpRequests` | `platform/ide-core/src/com/intellij/util/io/HttpRequests.java` |
| Endpoints 데이터 | `com.intellij.microservices.endpointsProvider` → `EndpointsProvider<G, E>` (`getStatus`, `getEndpointGroups`, `getEndpoints`, `getEndpointPresentation`, `getModificationTracker`, `getNavigationElement`) | EP `platform/platform-resources/src/META-INF/LangExtensionPoints.xml` 735–736행, 인터페이스 `platform/lang-api/src/com/intellij/microservices/endpoints/EndpointsProvider.kt` |
| Endpoints 열기 | `EndpointsViewOpener` (인터페이스. 구현은 커뮤니티에 없음) | `platform/lang-api/src/com/intellij/microservices/endpoints/EndpointsViewOpener.kt` |
| 어노테이션 검색 | `AnnotatedElementsSearch` (Kotlin은 `KotlinAnnotatedElementsSearcher`가 받아 줌), 저수준 `JavaAnnotationIndex`, `KotlinAnnotationsIndex` | `java/java-indexing-api/src/com/intellij/psi/search/searches/AnnotatedElementsSearch.java`, `plugins/kotlin/kotlin.searching/base/src/org/jetbrains/kotlin/idea/base/searching/KotlinAnnotatedElementsSearcher.kt`, `java/java-indexing-impl/.../JavaAnnotationIndex.java`, `plugins/kotlin/base/indices/src/org/jetbrains/kotlin/idea/stubindex/KotlinAnnotationsIndex.kt` |
| 툴 창 | `ToolWindowFactory` | `platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt` |
| (쓰지 않음) 시작 활동 | `ProjectActivity` | `platform/core-api/src/com/intellij/openapi/startup/StartupActivity.kt` |

**Endpoints 툴 창 사실관계**: 커뮤니티에는 EP와 모델 인터페이스(`EndpointsProvider`, `EndpointsSidePanelProvider`, `EndpointsProjectModel`, `EndpointsFilter`, `EndpointsChangeTracker`)만 있고 툴 창 UI는 없다 [V: `id="Endpoints"` 툴 창 등록이나 `EndpointsViewOpener` 구현이 트리에 없음. 원격 드라이버 테스트 SDK의 `EndpointsToolWindowUi.kt`만 있음]. Ultimate는 이 UI를 `microservices-plugin`(`com.intellij.microservices.ui`, 208 클래스)으로 따로 둔다 (ult-spring-javaee § 1.1). 그래서 phantom은 **EP를 소비하는 최소 툴 창을 직접 만들고**, 데이터는 Spring MVC용 `EndpointsProvider` 구현으로 공급한다. 다른 phantom 플러그인(예: Ktor를 쓰게 될 경우)도 같은 EP로 꽂을 수 있다.

### 3.3 데이터 모델

공개 스펙(Spring Boot reference, "Configuration Metadata" 부록)의 JSON 구조를 그대로 옮긴다 [A: 필드 목록은 문서 기억에 기반, § 5.1 테스트 데이터로 확인].

```kotlin
// core 모듈. 모두 불변, 문자열은 intern.
class ConfigMetadataFile(            // jar 또는 출력 디렉터리의 JSON 하나
  val origin: MetadataOrigin,        // LibraryJar(path, coords?) | BuildOutput(module) | AdditionalSource(file)
  val groups: List<ConfigGroup>,
  val properties: List<ConfigProperty>,
  val hints: List<ConfigHint>,
)
class ConfigGroup(val name: String, val type: String?, val sourceType: String?, val sourceMethod: String?, val description: String?)
class ConfigProperty(
  val name: String,                  // kebab-case canonical (예: spring.jpa.open-in-view)
  val type: String?,                 // java.lang.Boolean, java.util.Map<java.lang.String,java.lang.String> ...
  val description: String?, val sourceType: String?,
  val defaultValue: Any?,            // JSON 원값 (문자열/숫자/불리언/배열)
  val deprecation: Deprecation?,     // level(warning|error), reason, replacement, since
)
class ConfigHint(val name: String, val values: List<HintValue>, val providers: List<HintProvider>)  // name이 `.keys`/`.values` 접미 가능

// 모듈 단위 조회 뷰
class ModuleConfigKeys(
  val byName: Map<String, ConfigProperty>,      // 정확 일치
  val sortedNames: Array<String>,               // 접두 검색(이진 탐색)
  val mapPrefixes: Set<String>,                 // Map 타입 키: `logging.level` → 하위 임의 키 허용
  val groups: Map<String, ConfigGroup>,
  val hints: Map<String, ConfigHint>,
)
```

- **키 정규화 (relaxed binding)**: 비교는 소문자 kebab-case로 한다. `camelCase`, `snake_case`, `UPPER_CASE`를 kebab으로 바꾸고 `[0]` 인덱스 표기는 떼어낸다. 공개 문서상 바인딩 규칙에 근거 [A].
- **병합 규칙**: 같은 키가 여러 출처에 있으면 BuildOutput > AdditionalSource > LibraryJar 순으로 앞선 것을 쓰되, `description`과 `deprecation`이 비어 있으면 다음 출처에서 채운다.
- **프로필 모델**: `ProfileIndex(module) = Set<String>`. 출처는 (a) 리소스 루트의 `application-{p}.(yaml|yml|properties)` 파일 이름, (b) YAML 문서의 `spring.config.activate.on-profile` 값(쉼표, `!`, `&`, `|` 식은 토큰만 추출), (c) `spring.profiles.group.*` 키. 사용자 프로젝트에서는 `test`가 (a)로 잡혀야 한다 [V: `application-test.yaml` 존재].

### 3.4 메타데이터 수집과 캐시/무효화

```
MetadataService (project service, 생성자 비어 있음)
 ├─ libraryCache: ConcurrentMap<VirtualFile(jar root), Versioned<ConfigMetadataFile?>>
 │    키: jar 루트, 값의 버전: jar VirtualFile.modificationStamp
 ├─ outputCache:  per Module, CachedValue(deps = MetadataFilesTracker)
 └─ moduleView:   per Module, CachedValue<ModuleConfigKeys>(
                    deps = JavaLibraryModificationTracker, MetadataFilesTracker)
```

1. **라이브러리 jar**: `OrderEnumerator.orderEntries(module).recursively().librariesOnly().classes().roots`[A: API 이름은 플랫폼 표준, 첫 구현 때 확인]로 루트를 얻고, 루트마다 `findFileByRelativePath("META-INF/spring-configuration-metadata.json")`와 `META-INF/additional-spring-configuration-metadata.json`을 본다. jar 하나에 대해 결과(없음 포함)를 캐시한다. 대부분의 jar는 "없음"이므로 두 번째부터는 맵 조회다.
2. **빌드 출력**: Gradle 모듈의 컴파일 출력 루트(`CompilerModuleExtension`/Gradle 소스 세트 출력. 기본값은 `build/classes/java/main`, `build/classes/kotlin/main`, kapt는 `build/tmp/kapt3/classes/main` 아래 `META-INF/`)[A: kapt 경로는 kapt 기본 동작 기억에 기반. 사용자 프로젝트에서 빌드 후 확인]. 커뮤니티의 `CompilerModuleExtension` 출력 경로 + 위 kapt 후보 경로를 함께 본다.
3. **소스의 additional 파일**: `FilenameIndex`에서 `additional-spring-configuration-metadata.json`을 모듈 범위로 조회한다. 이 파일은 편집 중일 수 있으므로 `Document` 기준으로 파싱하고, 파싱 실패 시 직전 성공 결과를 유지한다.
4. **`MetadataFilesTracker`**: `BulkFileListener`(`VFS_CHANGES` 토픽 구독, 프로젝트 서비스가 처음 생성될 때만 등록)에서 이벤트 파일 이름이 `spring-configuration-metadata.json` 또는 `additional-spring-configuration-metadata.json`일 때만 카운트를 올린다. 이름 비교 한 번이라 이벤트당 비용이 무시할 만하다.
5. **파싱**: 플랫폼에 번들된 Gson/Jackson이 아니라 스트리밍 파서(`com.google.gson.stream.JsonReader`, 플랫폼 라이브러리에 있다고 가정 [A])로 필요한 필드만 읽는다. 파싱은 `ProgressManager.checkCanceled()`를 섞어 취소 가능하게 한다.
6. **dumb mode**: 1, 2, 4는 인덱스가 필요 없다. 3은 `DumbService.isDumb`이면 건너뛰고 다음 smart 전환에서 반영한다.

### 3.5 기능별 동작

**F1/F2 YAML 키·값 완성** (`yaml` 모듈)
- `CompletionContributor`를 `YAMLLanguage`에 등록. 진입 조건: 파일 이름이 `application*.yml|yaml` 또는 `bootstrap*.yml|yaml`, 그리고 `isBootModule`.
- 키 위치: 커서의 부모 `YAMLKeyValue`들로부터 `YAMLUtil.getConfigFullNameParts`로 경로를 만든다. 다중 문서(`---`)는 문서별로 독립.
- 후보: `sortedNames`에서 `prefix + "."` 범위를 이진 탐색해 **다음 세그먼트만** 보여 준다(`spring.` 아래에서 `datasource`, `jpa` …). 말단 키는 타입·기본값을 tail text로.
- 삽입: 말단이면 `key: `, 그룹이면 `key:` + 줄바꿈 + 들여쓰기. 커뮤니티 `YamlKeyInsertHandler`를 쓸 수 있는지 1단계 스파이크에서 확인한다 [V: 클래스 존재, A: 재사용 가능 여부].
- 값: `type`이 `java.lang.Boolean` → `true/false`. enum 클래스 FQN → `JavaPsiFacade.findClass`로 상수 목록(라이브러리 클래스라 Kotlin/Java 구분 없음). `hints.values`가 있으면 우선. `spring.profiles.active`, `spring.profiles.include` 값은 `ProfileIndex`.
- **YAML 메타 모델 대안**: 커뮤니티 `YamlMetaTypeProvider`/`ModelAccess`에 메타데이터를 트리로 매핑하면 완성, unknown/deprecated key inspection, 문서 제공 기반 클래스를 재사용할 수 있다 [V: 기반 클래스 존재]. 다만 점 표기 평면 키와 Map 키, relaxed binding을 메타 클래스 트리로 옮기는 비용과, 메타 모델이 문서 전체를 타입 해석하는 비용을 모른다 [unknown]. 1단계에서 0.5일 스파이크로 정하고, 결론이 불명확하면 자체 contributor로 간다.

**F1/F2 Properties 완성** (`properties` 모듈): 키는 이미 평면이라 `sortedNames` 접두 검색 그대로. 값 완성은 YAML과 같은 로직을 공유(`core`의 `ValueCompletionModel`).

**F3 이동 / F4 문서**
- `psi.referenceContributor`로 YAML 키(말단과 중간 세그먼트)와 properties 키에 `ConfigKeyReference`를 단다. 하이라이팅 비용을 줄이려고 reference는 `isSoft = true`, resolve는 지연.
- resolve 순서: (1) `sourceType` 클래스를 `JavaPsiFacade.findClass(fqn, module.getModuleWithDependenciesAndLibrariesScope(false))`로 찾고, kebab → camel 이름으로 필드/게터/생성자 파라미터(Kotlin data class `val`은 light class의 게터로 노출) 중 첫 번째. (2) 실패하면 메타데이터 JSON 파일의 해당 `"name": "..."` 위치(파싱 시 오프셋을 기록하지 않고, resolve 때 그 파일 텍스트에서 검색).
- Kotlin K2: `@ConfigurationProperties` 클래스가 Kotlin 소스여도 `JavaPsiFacade`가 light class를 돌려주고, `navigationElement`가 `KtParameter`/`KtProperty`로 이어진다 [A: K2 light class의 생성자 파라미터 navigation 동작은 테스트로 확인].
- 문서: `lang.documentationProvider`(또는 신형 `DocumentationTarget` API)로 YAML/properties 키에 설명, 타입, 기본값, 출처, deprecation을 HTML로.

**F5 사용 중단 inspection**: `localInspection` 1개(`PhantomBootDeprecatedConfigKey`), YAML·properties 공용. 키가 `deprecation`을 가지면 level에 따라 WARNING/ERROR, `replacement`가 있으면 키 이름 교체 quick-fix(YAML은 경로 재구성이 필요하므로 v0.1은 properties와 YAML 말단 이름 교체만, 중첩 이동은 v0.2).

**F7 프로필**: `core`의 `ProfileIndex`. `FilenameIndex`로 `application-*.yaml|yml|properties`를 모듈 리소스 범위에서 찾는다. 캐시 의존성은 `MetadataFilesTracker`가 아니라 `ProjectRootModificationTracker` + 해당 파일들의 VFS 이벤트용 별도 카운터(`ConfigFilesTracker`, 이름 패턴 `application*`/`bootstrap*` 필터).

**F8 실행 구성** (`run` 모듈)
- `RunConfigurationExtension` 하나. `isApplicableFor`: `ApplicationConfiguration` 또는 `KotlinRunConfiguration`이고, 메인 클래스가 Boot 모듈에 있을 때.
- 설정은 확장 데이터로 직렬화(`readExternal`/`writeExternal`): `activeProfiles: String`, `injectAs: VM_OPTION | ENV`.
- `updateJavaParameters`: 비어 있지 않으면 `-Dspring.profiles.active=<값>` 추가(이미 사용자가 VM 옵션에 같은 키를 넣었으면 건드리지 않음).
- 편집기: `SettingsEditor` 탭 "Spring Boot". Active profiles 필드는 `TextFieldWithAutoCompletion`으로 `ProfileIndex` 후보 제공 [A: 컴포넌트 이름].
- Ultimate는 별도 실행 구성 타입(`SpringBootApplicationConfigurationType`, descriptor [V])을 두지만, phantom은 **새 타입을 만들지 않는다.** Kotlin `fun main`에서 기존 producer가 만드는 Kotlin 실행 구성에 탭만 붙는다. 사용자가 실행 구성을 새로 배울 필요가 없고 클래스도 적다.

**F9 마법사** (`initializr` 모듈)
- `GeneratorNewProjectWizard` 구현 `SpringInitializrGenerator`(`id = "phantom.spring.initializr"`, `name = "Spring Boot"`). `createStep`은 Kotlin UI DSL 단계: 서버 URL(기본 `https://start.spring.io`), 이름/위치(공통 단계 재사용), 언어(Kotlin 기본), 빌드(Gradle - Kotlin 기본), Boot 버전, Java 버전, group/artifact/package, 의존성 트리(검색 필터).
- 메타데이터: `GET {server}/` + `Accept: application/vnd.initializr.v2.2+json` → `bootVersion`, `type`, `language`, `javaVersion`, `packaging`, `dependencies.values[].values[]`(id, name, description, versionRange) [A: 공개 Initializr API 기억 기반, v2.2/v2.1 협상은 구현 때 응답 헤더로 확인]. 응답은 앱 레벨에 1시간 캐시, 실패 시 마지막 성공본을 디스크(`PathManager.getSystemPath()/phantom-spring/initializr-meta.json`)에서 읽는다.
- 생성: `GET {server}/starter.zip?type=gradle-project-kotlin&language=kotlin&bootVersion=..&groupId=..&artifactId=..&name=..&packageName=..&javaVersion=..&dependencies=web,data-jpa,...` [A: 파라미터 이름]. 백그라운드 모달 진행(`runWithModalProgressBlocking`)으로 받고 `Decompressor.Zip`으로 풀기 [A: 클래스 이름]. 그다음 `linkAndSyncGradleProject(project, buildFile)` [V].
- 의존성 `versionRange`와 선택한 Boot 버전이 맞지 않는 항목은 비활성 표시.
- 네트워크는 사용자가 마법사를 열었을 때만. IDE 시작이나 플러그인 로드 시 호출하지 않는다.

**F10 Endpoints** (`endpoints` 모듈)
- `SpringMvcEndpointsProvider : EndpointsProvider<ControllerGroup, MappingEndpoint>`.
  - `getStatus`: `hasLibraryJar(project, "org.springframework:spring-web")`가 아니면 `UNAVAILABLE` [V: Status 존재, A: 정확한 enum 상수 이름].
  - 수집: `AnnotatedElementsSearch.searchPsiMethods(annotationClass, projectProductionScope)`를 7개 어노테이션(`RequestMapping`, `GetMapping`, `PostMapping`, `PutMapping`, `DeleteMapping`, `PatchMapping`, Spring 6.1+의 `HttpExchange` 계열은 v0.2)에 대해 실행. Kotlin은 `KotlinAnnotatedElementsSearcher`가 응답한다 [V: 클래스 존재].
  - 경로 추출: UAST(`UMethod`, `UAnnotation.findAttributeValue("value"|"path")`, `UClass`의 클래스 레벨 `@RequestMapping`)로 Kotlin/Java 공통 처리. 상수 참조는 `UExpression.evaluate()`로 평가하고 실패하면 원문 표시.
  - 캐시: 파일 단위 `CachedValue<List<MappingEndpoint>>`(의존성: 그 파일의 `PsiFile` 자체 = 파일 수정 시만 무효화). 프로젝트 목록은 "매핑 어노테이션을 가진 파일 집합"(검색 결과)과 파일별 캐시의 합. 집합은 `PsiModificationTracker.forLanguages(Kotlin, Java)`가 아니라 `getModificationTracker`가 반환하는 자체 tracker로 묶고, 툴 창이 보일 때만 재계산한다.
- 툴 창 `PhantomEndpointsToolWindowFactory`: `JBTable` 한 장(메서드, 경로, 핸들러, 모듈), 상단 검색 필드, 더블클릭/F4로 `getNavigationElement`. `EndpointsProvider.EP_NAME`의 모든 제공자를 소비. `isApplicableAsync`로 Spring Web이 있는 프로젝트에서만 툴 창 버튼을 보인다 [A: 플랫폼의 비동기 applicability API 이름].
- 커뮤니티 `EndpointsViewOpener` 구현도 제공해 다른 곳의 "Show endpoints" 진입점이 우리 툴 창을 열게 한다.

### 3.6 스레딩

| 작업 | 스레드 |
|---|---|
| 메타데이터 수집/파싱 | 완성 스레드(이미 BGT, 읽기 액션) 또는 `smartReadAction` 코루틴. 첫 요청이 길면 완성 결과는 부분 결과(라이브러리 먼저)로 반환하고 `CompletionResultSet.restartCompletionOnAnyPrefixChange`에 기대지 않는다 |
| 참조 resolve, 문서 | 플랫폼이 부르는 BGT |
| Endpoints 수집 | 툴 창 서비스 코루틴 스코프에서 `smartReadAction`, 결과를 EDT로 전달해 테이블 모델만 갱신 |
| Initializr HTTP, zip 해제 | `Dispatchers.IO` + 모달 진행 표시 |
| 실행 구성 `updateJavaParameters` | 실행 준비 스레드. 프로필 값은 문자열 그대로라 계산 없음 |

EDT에서 PSI 검색, jar 접근, 네트워크 호출을 하지 않는다. 이를 CI에서 잡기 위해 테스트에서 `ThreadingAssertions`(플랫폼)를 켠다 [A].

## 4. 구현 단계

| # | 작업 | 예상(일) | IDE에서 볼 수 있는 수용 기준 |
|---|---|---:|---|
| 0 | 모듈 골격, `BUILD.bazel`/`.iml`, plugin.xml, `IdeaCommunityProperties.kt` 번들 등록, promo 2건 제거 | 1.0 | `./installers.cmd` 빌드본의 Settings > Plugins에 "Spring Boot (Phantom)"가 번들로 보이고, New Project 목록에 Ultimate 홍보 "Spring" 항목이 없다 |
| 1 | `core`: `SpringBootDetector`, 메타데이터 파서, `MetadataService`(라이브러리 jar), YAML 메타 모델 스파이크(0.5일 포함) | 2.5 | 내부 액션 "Phantom: Dump Boot Config Keys"가 사용자 프로젝트 `adapter-module`에서 키 개수와 출처 jar 상위 10개를 알림으로 보여 준다 (예상: spring-boot-autoconfigure jar가 최다) |
| 2 | `yaml`: 키 완성(F1), 값 완성(F2) | 2.0 | `application.yaml`에서 `spring:` 아래 `jpa:` → `hibernate:` → `ddl-auto: ` 입력 시 `none/validate/update/create/create-drop` 제안 |
| 3 | `properties`: 키·값 완성 | 0.5 | 빈 `application.properties`에 `server.po` → `server.port` (타입 Integer, 기본 8080) |
| 4 | 참조/이동(F3), 퀵 문서(F4) | 2.0 | `spring.datasource.url`에서 Ctrl+B → 라이브러리 `DataSourceProperties` 클래스의 `url` 프로퍼티. Ctrl+Q에 설명과 타입 |
| 5 | 빌드 출력/additional 메타데이터(F6), `MetadataFilesTracker` | 1.5 | 사용자 프로젝트에 `@ConfigurationProperties("app.reservation")` Kotlin 클래스가 있으면(없으면 테스트용으로 하나 만듦) Gradle 빌드 후 IDE 재시작 없이 `app.reservation.` 완성이 뜨고, Ctrl+B가 Kotlin 소스의 생성자 파라미터로 간다 |
| 6 | 사용 중단 inspection(F5) | 1.0 | `server.max-http-header-size`(Boot 3에서 사용 중단 → `server.max-http-request-header-size`)[A: 키 예시] 에 경고와 교체 quick-fix |
| 7 | 프로필(F7) + 실행 구성 탭(F8) | 2.0 | Kotlin `main`의 실행 구성 편집기에 "Spring Boot" 탭, Active profiles 필드에서 `test` 완성, 실행 로그에 `The following 1 profile is active: "test"` |
| 8 | Endpoints 제공자 + 툴 창(F10) | 3.0 | View > Tool Windows > Endpoints에 사용자 프로젝트 컨트롤러의 `GET /...` 목록, 필터 입력 시 좁혀짐, 더블클릭 시 Kotlin 메서드로 이동. Spring Web이 없는 프로젝트에서는 툴 창 버튼이 없다 |
| 9 | Initializr 마법사(F9) | 3.0 | New Project > Spring Boot에서 Kotlin/Gradle-Kotlin/Java 21/`web,data-jpa` 선택 → 프로젝트가 열리고 Gradle 동기화가 끝난 뒤 `Application.kt` 실행 성공. 오프라인이면 마지막 캐시 메타데이터로 목록이 뜨고 생성 버튼에서 명확한 오류 |
| 10 | 경량화 측정과 수정 | 1.5 | § 5.3 기준표 전부 통과 |
| | **합계** | **20.0** | |

## 5. 테스트 방법

### 5.1 자동 테스트 (커뮤니티 테스트 프레임워크)

- 기반 클래스 [V]: `BasePlatformTestCase`(`platform/testFramework/src/com/intellij/testFramework/fixtures/`), Java 라이브러리가 필요하면 `LightJavaCodeInsightFixtureTestCase`(`java/testFramework/src/...`), Kotlin 소스는 `KotlinLightCodeInsightFixtureTestCase`(`plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/`), Maven 좌표 라이브러리 부착은 `MavenDependencyUtil`(`java/testFramework/shared/src/...`).
- 메타데이터 jar: 실제 Boot jar를 테스트에 넣지 않고, `testData/metadata/`에 손으로 쓴 작은 `spring-configuration-metadata.json`(그룹 3, 프로퍼티 20, 힌트 3, 사용 중단 2, Map 키 1)을 jar로 묶어 라이브러리로 붙인다. 라이브러리 이름을 `org.springframework.boot:spring-boot:3.4.5`로 해서 `hasLibraryJar` 감지도 함께 시험한다 [A: 테스트에서 Maven 좌표 속성을 붙이는 방법은 `MavenDependencyUtil` 확인 후 결정].
- 테스트 목록:
  - `MetadataParserTest`: 스펙의 모든 필드, 알 수 없는 필드 무시, 깨진 JSON에서 예외 대신 빈 결과.
  - `RelaxedNameTest`: `spring.jpa.openInView`, `SPRING_JPA_OPEN_IN_VIEW`, `spring.jpa.open_in_view` → 같은 키.
  - `YamlKeyCompletionTest`, `PropertiesKeyCompletionTest`: `myFixture.completeBasic()` + `lookupElementStrings`. 다중 문서, 들여쓰기 삽입 결과는 `checkResultByFile`.
  - `ValueCompletionTest`: boolean, enum(테스트 라이브러리의 enum), hints, 프로필.
  - `ConfigKeyNavigationTest`: Java/Kotlin `@ConfigurationProperties` 소스로 resolve, 실패 시 JSON으로 fallback.
  - `DeprecatedKeyInspectionTest`: `myFixture.enableInspections` + `testHighlighting`, quick-fix `checkResult`.
  - `MetadataInvalidationTest`: 출력 디렉터리 JSON을 VFS로 바꾼 뒤 새 키가 보이는지, 관계없는 `.kt` 수정으로는 `ModuleConfigKeys`가 재계산되지 않는지(캐시 인스턴스 동일성 검사).
  - `SpringMvcEndpointsProviderTest`: Kotlin/Java 컨트롤러, 클래스 레벨 + 메서드 레벨 경로 합성, 상수 경로, 메서드 배열.
  - `ActiveProfilesExtensionTest`: `updateJavaParameters` 결과 VM 옵션, 사용자 지정 옵션과 충돌 시 미주입.
  - `InitializrMetadataParserTest`: 저장해 둔 응답 JSON 샘플(직접 작성)로 파싱. 네트워크 테스트는 기본 비활성.
- 실행: Bazel 테스트 타깃 [A: 트리의 테스트 타깃 규칙 확인 필요] 또는 IDE에서 JUnit 실행.

### 5.2 사용자 프로젝트 수동 확인 (`~/orca/workspaces/prototype-reservation-system`)

1. 빌드본으로 `infro`(또는 다른 워크스페이스 하나)를 연다. Gradle 동기화 후 `adapter-module/src/main/resources/application.yaml`을 연다 [V: 파일 존재].
2. § 4의 단계별 수용 기준을 차례로 확인한다. 특히: `spring.datasource.hikari.` 완성, `spring.jpa.properties.hibernate.`(Map 키: 임의 하위 키 허용, 알 수 없는 키 경고 없음), `spring.data.redis.`, `spring.kafka.consumer.`, `spring.batch.job.`, `management.endpoints.web.exposure.include` 값.
3. `batch-module`과 `adapter-module`이 서로 다른 키 집합을 보는지(모듈별 클래스패스. 예: batch 전용 starter 키는 `adapter-module`에서 안 보여야 함).
4. 실행 구성에 `test` 프로필을 넣고 실행 → 콘솔 로그로 활성 프로필 확인.
5. Endpoints 툴 창에서 컨트롤러 개수가 `grep -rn "@\(Get\|Post\|Put\|Delete\|Patch\|Request\)Mapping" --include=*.kt`로 센 메서드 수와 같은지 비교(클래스 레벨 `@RequestMapping` 제외).

### 5.3 경량화 측정 (Ultimate와 같은 방법)

`reversed/02-runtime`과 같은 절차(번들 JBR의 `jcmd`, 시작 JFR 240 s)로 측정한다.

| 항목 | 기준 |
|---|---|
| Boot 파일을 열기 전 이 플러그인의 적재 클래스 수 (`jcmd VM.class_hierarchy` 또는 `-verbose:class` 집계) | < 50 |
| 사용자 프로젝트 전체 사용 후 적재 클래스 | < 400 (Ultimate Boot 672 + MVC 300 대비) |
| startup activity, 새 FileBasedIndex | 0개 (plugin.xml 검사로 확인) |
| 시작 JFR 240 s의 phantom 프레임 포함 샘플 | Boot 파일을 열지 않았으면 0, 열었으면 전체의 1% 미만 |
| 메타데이터 모델 메모리 (힙 덤프의 `ModuleConfigKeys` retained) | 모듈당 < 5 MB [A: 키 수 가정에 의존] |
| `.kt` 파일 하나 편집 후 메타데이터 재계산 | 0회 (로그 카운터) |
| Spring Web 없는 프로젝트(예: 순수 Kotlin 라이브러리)에서 Endpoints 툴 창 | 버튼 없음, 제공자 호출 0 |

## 6. 리스크와 남은 의문

1. **Kotlin K2 light class 경로** [unknown]: Kotlin `@ConfigurationProperties` data class의 생성자 파라미터로 이동이 light class 게터를 거쳐 제대로 `KtParameter`에 닿는지. 안 되면 Kotlin 전용 resolve(`KtClass` 탐색)를 따로 둬야 하고 `plugins/kotlin` 모듈 의존이 생긴다(콘텐츠 모듈 `phantom.springBoot.kotlin` 추가).
2. **kapt 출력 경로** [A]: 사용자 프로젝트는 `kotlin-kapt`를 쓴다 [V: libs.versions.toml 133행]. `spring-boot-configuration-processor`를 kapt로 돌릴 때 JSON 위치가 `build/tmp/kapt3/classes/main/META-INF`인지 실제 빌드로 확인해야 한다. 프로젝트가 configuration-processor를 아예 안 쓸 수도 있다(이번에 빌드 스크립트에서 확인하지 못함). 그 경우 F6는 사용자에게 당장 효과가 없다.
3. **Gradle이 IDE 빌드 출력을 어디에 두는지** [unknown]: "Build and run using: Gradle"이면 출력은 Gradle `build/` 아래, IntelliJ 빌더면 `out/`. 둘 다 후보로 본다.
4. **YAML 메타 모델 재사용 여부** [unknown]: 스파이크 결론에 따라 1–2일 차이.
5. **Endpoints UI 범위**: 커뮤니티 EP 모델(`EndpointsProjectModel`, `EndpointsSidePanelProvider`)을 어디까지 존중할지. v0.1은 목록 + 이동만 하고 사이드 패널은 무시한다. 다른 플러그인(Ktor 등)이 EP로 들어오면 표시가 어긋날 수 있다.
6. **Initializr API 변동** [A]: 미디어 타입 버전, 파라미터 이름, `type` id(`gradle-project-kotlin`)는 기억에 의존한다. 첫 구현 때 실제 응답으로 확인하고 테스트 샘플을 갱신한다. 회사망/오프라인에서 마법사는 캐시 없이 동작하지 않는다.
7. **번들 빌드 연결** [A]: `bundledPluginModules`에 메인 모듈만 넣으면 콘텐츠 모듈이 따라 들어가는지, `plugin-content.yaml` 같은 레이아웃 파일(yaml 플러그인에 존재 [V])을 직접 만들어야 하는지.
8. **promo 제거의 부작용**: `FrameworkSuggestionProvider`를 지우면 다른 프레임워크(예: Liquibase 제외한 나머지) 제안도 사라진다. phantom 목적상 문제없다고 판단하지만, 같은 파일의 다른 promo 항목과 함께 정리할지는 별도 결정.
9. **Spring 빈 모델이 없다는 한계**: `@Value` 키 이동(v0.2), `@ConfigurationProperties` 클래스 쪽 gutter, 사용되지 않는 키 표시 등은 빈 모델 없이도 되지만, "이 키를 실제로 읽는 빈" 같은 기능은 별도 Spring 모델 설계 결과에 달려 있다.
10. **메타데이터 규모와 메모리** [A]: 키 수, 문자열 크기는 측정 전 가정이다. § 5.3에서 기준을 넘으면 description을 지연 로드(파일 오프셋만 저장)로 바꾼다.
11. **Ultimate와 동시 설치**: `incompatible-with`로 막지만, 사용자가 Ultimate Spring Boot를 Marketplace에서 설치할 수는 없다(Ultimate 전용)는 점에서 실제 충돌 가능성은 낮다 [A].
