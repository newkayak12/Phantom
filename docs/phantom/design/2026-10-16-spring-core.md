# phantom-spring: Spring Framework 핵심 지원 설계 (v0.1)

- 날짜: 2026-10-16
- 대상 트리: `phantom-idea` (intellij-community `idea/262.10968.63` 포크), Bazel 빌드
- 대상 사용자: Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자. 기준 프로젝트는 `~/orca/workspaces/prototype-reservation-system/infro`다
- 클린룸 원칙: Ultimate 쪽 입력으로는 (1) 성능 관측 노트(`Intellij-rea/reversed/README.md`, `02-runtime/2026-10-16-startup-jfr.md`, `03-decompiled/2026-10-16-ult-spring-javaee.md`)의 **동작·비용 서술**과 (2) 설치본 plugin.xml의 **기능 이름 목록**만 썼다. 디컴파일 소스(`reversed/source/`)는 열지 않았다. 아래 구조는 community API와 Spring 공개 문서를 기준으로 새로 설계한 것이다.

표기:
- **[검증]**: 이 트리에서 경로, 클래스, 시그니처를 직접 확인했다
- **[가정]**: 공개 문서나 일반 지식에 기댄 것으로, 구현 첫 단계에서 확인해야 한다
- **[관측]**: 측정 노트에서 가져온 값

---

## 1. 목표와 범위

### 1.1 사용자 프로젝트에서 실제로 쓰는 것 [관측]

`infro` 아래 Kotlin 파일 3,982개, Java 0개를 grep으로 셌다 (읽기 전용).

| 사용 패턴 | 횟수 | 설계에 주는 의미 |
|---|---:|---|
| `@Component` | 392 | 스테레오타입 빈이 기본 경로다 |
| `@Bean` | 540 | `@Configuration` 안의 팩토리 메서드도 기본 경로다 |
| `@UseCase` (직접 만든 애너테이션, `@Component`로 메타 애너테이션됨) | 172 | **메타 애너테이션 스테레오타입은 MVP 필수**다 |
| `@Qualifier` | 84 | 후보가 여럿일 때의 해소 규칙이 필요하다 |
| `@Autowired` | 164 | 대부분 테스트의 필드 주입이다. 생성자 주입이 주력이다 |
| `@Configuration` / `@TestConfiguration` | 108 / 40 | |
| `@ConfigurationProperties` / `@EnableConfigurationProperties` | 28 / 1 | 클래스 리터럴 배열로 등록한다 |
| `@Value` | 28 | |
| `@Import` / `@Primary` / `@Profile` / `@ConditionalOnProperty` | 4 / 1 / 2 / 1 | 조건부 빈은 소수다 |
| Spring Data `...Repository<...>` 인터페이스 | 16 파일 | **리포지토리 인터페이스도 빈**으로 봐야 "빈 없음" 오탐이 없다 |
| `@SpringBootApplication` | 2 (adapter-module, batch-module) | 루트 패키지 `com.reservation` 하나에서 멀티 모듈 전체를 스캔한다 |
| `kotlin("plugin.spring")` + `allOpen { annotation(...) }` | 2 + 3 모듈 | open/allopen을 인식해야 한다 |

구조상 중요한 점이 하나 있다. 헥사고날 구조여서 **포트 인터페이스는 `application-module`에, 구현 빈은 그 모듈에 의존하는 `adapter-module`에** 있다 [관측, 디렉터리 구조]. 주입 지점 모듈 기준으로 의존 모듈만 찾으면 "빈 없음" 오탐이 대량으로 난다. 3.5절의 "애플리케이션 루트 기준 스코프"가 이 문제를 다룬다.

### 1.2 MVP (v0.1) 기능

| # | 기능 | Ultimate 대응 기능 이름 (디스크립터 체크리스트) |
|---|---|---|
| F1 | **빈 모델**: `@Component`와 그 메타 스테레오타입(`@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`, 사용자 정의), `@Bean` 메서드, `@Import`, `@ComponentScan` / `@SpringBootApplication` 스캔 범위, `@EnableConfigurationProperties` / `@ConfigurationPropertiesScan`, Spring Data 리포지토리 인터페이스 | `SpringSemContributor`, `spring.customLocalComponentsDiscoverer`, `SpringDataSemContributor` (이름만 참고) |
| F2 | **주입 지점 해석**: 생성자 하나 (Kotlin 주 생성자, Java 단일 생성자), `@Autowired` 생성자·필드·세터·`lateinit var`, `@Bean` 메서드 파라미터. 래퍼 `Optional`, `ObjectProvider`, `Provider`, `List/Set/Collection/Map<String,T>`, 배열, Kotlin nullable 타입, 기본값 있는 파라미터 | `SpringJavaInjectionPointsAutowiringInspection` |
| F3 | **거터 내비게이션**: 주입 지점 → 후보 빈, 빈(클래스·`@Bean` 메서드) → 그 빈이 주입되는 지점 | `SpringAutowiredAnnotator`, `SpringBeanAnnotator`, `spring.beanClassLineMarker` |
| F4 | **autowiring 검사**: "빈 없음", "후보 여럿" (빠른 수정: `@Qualifier` 추가, 후보에 `@Primary` 추가) | `SpringJavaInjectionPointsAutowiringInspection`, `DuplicatedBeanNamesInspection` (일부) |
| F5 | **암시적 사용**: 빈 클래스, `@Bean` 메서드, 주입 생성자, `@Value` 필드, `@ConfigurationProperties` 프로퍼티를 "사용되지 않음"에서 제외한다 | `SpringImplicitUsageProvider`, `ConfigurationPropertiesImplicitUsageProvider` |
| F6 | **`@Value("${...}")` 링크**: 프로젝트의 `application*.yml` / `application*.properties` 키로 이동, 키 완성. 기본값(`:`)이 있으면 경고하지 않는다 | `SpringPlaceholdersInspection` (v0.1에서는 내비게이션만) |
| F7 | **`@ConfigurationProperties` 링크**: 프로퍼티 ↔ 설정 키 양방향 이동 (relaxed binding: kebab, camel, snake) | `ConfigurationPropertyRelatedItemLineMarkerProvider`, `ConfigurationPropertiesInspection` (일부) |
| F8 | **Kotlin open/allopen 인식**: `kotlin-spring`(allopen preset `spring`) 없이 final인 `@Configuration`(proxyBeanMethods=true), `@Transactional`, `@Async`, `@Cacheable` 클래스를 경고하고, allopen이 적용되면 불필요한 `open`을 약한 경고로 알린다 | `SpringCompilerPluginInspection`, `RedundantOpenModifierAllOpenCompilerPlugin` |

### 1.3 v0.2 이후

- **v0.2**
  - 라이브러리 자동 구성 빈: jar의 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `@ConditionalOnClass`만 평가한다. 이게 들어오기 전에는 라이브러리 타입 주입을 "모름"으로 처리한다 (3.6절)
  - 테스트 컨텍스트: `@SpringBootTest`, 슬라이스 테스트(`@WebMvcTest`, `@DataJpaTest`), `@MockkBean` / `@MockBean` / `@MockitoBean`을 빈 정의로 본다
  - `@Profile`, `@ConditionalOnProperty`를 활성 프로필 설정에 맞춰 평가한다
  - 영속 FileBasedIndex로 콜드 스타트를 데운다 (3.4.4절)
  - `@EventListener` ↔ `publishEvent` 거터, Beans 툴 창 (요청 시에만 계산)
- **v0.3**: `@Transactional` 자기 호출, `@Async`/`@Scheduled` 시그니처 검사, `@Cacheable` 검사 (체크리스트의 `SpringTransactionalMethodCallsInspection`, `AsyncMethodInspection`, `SpringCacheable*` 계열)
- **다른 설계 문서 몫**: Boot 설정 메타데이터(`spring-configuration-metadata.json`) 키 완성·문서 (phantom-spring-boot), MVC 엔드포인트·URL (phantom-spring-mvc), JPA 엔티티·JPQL (phantom-jpa), Spring Data 쿼리 메서드 검사, Security

### 1.4 하지 않는 것

- Spring XML 설정(`<beans>`, 커스텀 네임스페이스), Spring EL 언어, JSP/JSF/Groovy/Thymeleaf/FreeMarker/Velocity 연동
- AOP 포인트컷 언어, Spring Integration / Cloud / Modulith / AI / State Machine / Batch 전용 모델
- Spring 다이어그램, 런타임 Live Beans(Actuator) 연동, Initializr 마법사
- facet UI와 "컨텍스트 수동 설정" 화면. 컨텍스트는 코드에서만 추론한다
- 라이선스, 인증 관련 코드. 범위 밖이다

---

## 2. Ultimate 대비 경량화 원칙

측정된 비용과 이 설계의 대응을 한 줄씩 짝지었다.

| # | 측정된 Ultimate 비용 [관측] | 원인 (노트의 동작 서술) | phantom-spring의 대응 |
|---|---|---|---|
| C1 | 시작 JFR 240 s 동안 Spring 프레임 포함 샘플 543–587개(약 10–11%). 60–80 s 구간에서는 40%. 약 5–6 CPU초. 전부 daemon FJ 풀 | 열린 편집기의 inspection, line marker, 참조 검색이 처음 모델을 요구할 때 모델 전체를 만든다 | 전역 단계는 **파일별 요약을 합치기만** 한다. PSI 해석이 없다. 비싼 일은 파일 단위로 쪼개 캐시한다 (3.4절) |
| C2 | 애너테이션 하나 고치거나 파일 하나 추가해도 Spring 모델 전체가 무효화된다 (inferred). 외부 모델 추적기와 `PsiModificationTracker.MODIFICATION_COUNT`에 묶여 있다 | community의 `OuterModelsModificationTracker`는 파일 추가·이동·삭제, 애너테이션 편집마다 카운트를 올린다 [검증: 클래스 주석 61–71행] | 그 추적기를 **쓰지 않는다**. 대신 "더러운 파일 집합 + 요약 해시 비교" 추적기를 둔다. 요약이 실제로 바뀐 경우에만 버전을 올린다. 메서드 본문 편집, `@DisplayName` 수정, 테스트 파일의 `@Test` 추가로는 아무것도 무효화되지 않는다 |
| C3 | `UastSpringBeansScopeEnlarger.getAdditionalUseScope`가 Code Vision "N usages" → 참조 검색 연쇄에서 비용을 낸다 (JFR 29 샘플) | 참조 검색마다 Spring 모델로 사용 범위를 넓힌다 | `UseScopeEnlarger`를 **등록하지 않는다**. Kotlin 클래스는 기본 public이라 사용 범위가 이미 넓다. 빈 → 주입 지점 역방향은 레지스트리 맵 조회로 끝낸다 |
| C4 | `SpringBeanAnnotator.collectSlowLineMarkers` (22 샘플) | 화면의 모든 후보에 대해 느린 marker 패스에서 해석한다 | 아이콘 표시 여부는 **파일 자신의 요약**만 보고 정한다. 이동 대상은 `NavigationGutterIconBuilder.setTargets(NotNullLazyValue)`로 클릭할 때 만든다 [검증: 121행] |
| C5 | 안 쓰는 프레임워크 EP 구현체가 EP 열거만으로 인스턴스화된다 (`ImplicitUsageProvider` 59개 중 26개가 프레임워크 영역) | 플러그인이 설치돼 있으면 콘텐츠 모듈을 로드한다 | EP마다 **얇은 진입 클래스 하나**만 둔다. 진입 클래스는 `isSpringProject(project)` 확인 뒤에야 실제 구현 클래스를 건드린다 (3.2절) |
| C6 | Kotlin 프로젝트라 Spring 분석이 UAST와 K2 light class를 거친다 (`SymbolLightModifierList.hasAnnotation`, `LLPhase` 이벤트 8만 개) | 클래스마다 UAST/light class를 만들어 애너테이션과 타입을 묻는다 | 요약은 **Kotlin PSI/스텁에서 문법적으로** 뽑는다. light class와 Analysis API는 임포트로 풀리지 않는 타입(별칭, 와일드카드 임포트 충돌)에만 쓴다 |
| C7 | Spring 쪽 `preload` 서비스 0개, startup activity는 Spring 핵심에 없다 [관측] | 이 부분은 이미 지연 생성이다 | 같은 원칙을 지킨다. `postStartupActivity`와 `backgroundPostStartupActivity`는 **0개**다. 첫 하이라이팅 요청이 레지스트리 생성을 시작한다 |
| C8 | Spring 모델이 힙에 얼마나 붙는지 미확인 | PSI 사용자 데이터 안의 `CachedValue`에 흩어져 있다 | 레지스트리는 프로젝트 서비스 **하나**에 모은다. 크기를 직접 잴 수 있다 (`phantom.spring.debug.stats` 액션). 목표는 4천 파일 프로젝트 기준 5 MB 이하 [가정] |

원칙 요약:
1. **기술을 쓰는 프로젝트에서만 일한다.** `JavaLibraryUtil.hasLibraryJar(project, "org.springframework:spring-context")` [검증: `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` 155행]가 거짓이면 모든 EP가 즉시 반환한다. 이 결과는 community 쪽에서 `JavaLibraryModificationTracker`로 캐시된다 [관측 노트, 검증: 같은 패키지에 추적기 존재].
2. **증분 모델.** 무효화 단위는 파일이다. 전역 버전은 요약 해시가 바뀔 때만 오른다.
3. **프로젝트 전체 무효화 없음.** 전체 재계산은 라이브러리나 모듈 구조가 바뀔 때(`JavaLibraryModificationTracker`, `ProjectRootModificationTracker` [검증])만 한다. 그때도 파일별 요약 캐시는 살아 있으므로 합치기만 다시 한다.
4. **지연 서비스.** `<projectService>`만 쓴다. `preload` 없음. 서비스 생성자에서 PSI를 건드리지 않는다.
5. **시작 활동 없음.** 위 C7과 같다.
6. **EDT에서 일하지 않는다** (3.7절).

---

## 3. 아키텍처

### 3.1 모듈 배치

```
plugins/phantom-spring/
├── intellij.phantom.spring.iml             # 메인 모듈 (plugin.xml 소유)
├── BUILD.bazel                             # iml에서 자동 생성 (lombok과 같은 방식)
├── plugin-content.yaml
├── resources/META-INF/plugin.xml           # id: dev.phantom.spring
├── core/                                   # intellij.phantom.spring.core  (Java PSI 전용, Kotlin 의존 없음)
│   └── src/dev/phantom/spring/core/
│       ├── model/        # BeanRecord, InjectionPointRecord, FileSummary, TypeRef ...
│       ├── summary/      # SummaryExtractor EP + Java 구현
│       ├── registry/     # SpringStructureTracker, BeanRegistry, ContextRoots
│       ├── resolve/      # InjectionResolver, TypeMatcher, QualifierRules
│       ├── gate/         # SpringProjectGate (라이브러리 존재 판정)
│       └── ide/          # 얇은 EP 진입 클래스: LineMarker, Inspection, ImplicitUsage
├── kotlin/                                 # intellij.phantom.spring.kotlin  (content module, loading="optional")
│   └── src/dev/phantom/spring/kotlin/
│       ├── KotlinSummaryExtractor.kt       # KtFile → FileSummary (스텁/문법 기반)
│       ├── KotlinTypeRefResolver.kt        # 임포트 해석, 실패 시에만 Analysis API
│       └── AllOpenInspections.kt           # F8
├── properties/                             # intellij.phantom.spring.properties (content module, optional)
│   └── src/dev/phantom/spring/properties/  # F6, F7: yaml/properties 키 인덱스와 참조
└── testSrc/ , testData/
```

- 콘텐츠 모듈 로딩 규칙은 `ModuleLoadingRule`의 `REQUIRED / EMBEDDED / OPTIONAL / ON_DEMAND`다 [검증: `platform/core-impl/src/com/intellij/ide/plugins/ModuleLoadingRule.kt`]. 그런데 `ON_DEMAND`는 **아직 구현되지 않아 `OPTIONAL`과 같다**고 소스 주석에 적혀 있다 [검증: 36행, IJPL-242789]. 그래서 "프로젝트가 Spring을 쓸 때만 모듈 로드"는 플랫폼 기능으로는 불가능하다. 대신 위 원칙 1의 런타임 게이트로 클래스 적재와 계산을 막는다.
- `kotlin` 모듈은 `org.jetbrains.kotlin` 플러그인이 있을 때만 켜진다. `properties` 모듈은 `org.jetbrains.plugins.yaml` [검증: `plugins/yaml/resources/META-INF/plugin.xml` id]과 `com.intellij.properties`가 있을 때만 켜진다.
- K2 지원 선언: `<extensions defaultExtensionNs="org.jetbrains.kotlin"><supportsKotlinPluginMode supportsK2="true" supportsK1="false"/></extensions>`. EP `supportsKotlinPluginMode`와 빈 클래스의 `supportsK1`/`supportsK2` 속성은 [검증: `plugins/kotlin/plugin/resources/META-INF/plugin.xml` 30행, `plugins/kotlin/base/plugin/src/org/jetbrains/kotlin/idea/base/plugin/SupportsKotlinPluginMode.kt` 30–34행]. K1은 지원하지 않는다.
- 번들링: `platform/build-scripts/src/org/jetbrains/intellij/build/CommunityRepositoryModules.kt`에 lombok처럼 `pluginAuto(listOf("intellij.phantom.spring", ...))`를 추가한다 [검증: 246행의 lombok 등록 방식]. 이 단계는 빌드 담당 설계와 맞춰야 한다.
- `BUILD.bazel`은 "auto-generated section" 마커가 있어 iml에서 생성된다 [검증: `plugins/lombok/BUILD.bazel`]. 손으로 쓰지 않는다.

### 3.2 확장점 등록 (전부 community에 있는 EP)

| EP (`com.intellij.` 네임스페이스) | phantom 클래스 | 근거 경로 [검증] | 비고 |
|---|---|---|---|
| `codeInsight.lineMarkerProvider` (language JAVA, kotlin) | `BeanLineMarkerProvider` | `platform/lang-api/src/com/intellij/codeInsight/daemon/RelatedItemLineMarkerProvider.java`, `RelatedItemLineMarkerInfo.java`, `platform/lang-impl/src/com/intellij/codeInsight/navigation/NavigationGutterIconBuilder.java` | 빠른 패스 `getLineMarkerInfo`만 쓴다. 느린 패스를 쓰지 않는다 |
| `localInspection` (language `UAST`) | `PhantomAutowiringInspection`, `PhantomFinalSpringClassInspection`(kotlin 전용) | `java/java-analysis-api/src/com/intellij/codeInspection/AbstractBaseUastLocalInspectionTool.java`. `language="UAST"` 등록 예는 `java/java-backend/resources/META-INF/Inspections.xml` | UAST 트리를 순회하지 않는다. 파일 요약에서 문제 위치만 꺼내 `ProblemsHolder`에 넣는다 (3.6절) |
| `implicitUsageProvider` | `SpringImplicitUsage` | `platform/analysis-api/src/com/intellij/codeInsight/daemon/ImplicitUsageProvider.java` | 해당 요소의 애너테이션만 본다. 레지스트리를 쓰지 않는다 |
| `psi.referenceContributor` (properties 모듈) | `ValuePlaceholderReferenceContributor` | `platform/core-api/src/com/intellij/psi/PsiReferenceContributor.java`, `uast/uast-common-ide/src/com/intellij/psi/UastReferenceRegistrar.kt` | `@Value` 문자열 안의 `${key}`에 참조를 건다 |
| `fileBasedIndex` (properties 모듈) | `ConfigKeyIndex` | `platform/indexing-api/src/com/intellij/util/indexing/FileBasedIndexExtension.java`, `DefaultFileTypeSpecificWithProjectInputFilter.java` | 파일 이름이 `application*`/`bootstrap*`인 yml/properties만. 정규화한 키 → 오프셋 |
| `projectService` | `SpringProjectGate`, `SpringStructureTracker`, `BeanRegistry` | — | `preload` 없음 |
| `psi.treeChangeListener`는 쓰지 않음 | `PsiManager.addPsiTreeChangeListener(listener, disposable)`를 레지스트리 서비스가 처음 만들어질 때 건다 | `platform/core-api/src/com/intellij/psi/PsiManager.java` 128행 | Spring 프로젝트가 아니면 서비스가 생기지 않아 리스너도 없다 |
| `vfs.asyncListener` | `SpringFileEventsListener` | `platform/core-api/src/com/intellij/openapi/vfs/AsyncFileListener.java` | 생성·삭제·이동만 본다. 서비스가 이미 있을 때만 일한다 |
| 내부 EP `dev.phantom.spring.summaryExtractor` | Java 구현(core), Kotlin 구현(kotlin 모듈) | — | 언어별 요약기를 끼우는 지점이다 |
| 내부 EP `dev.phantom.spring.beanSourceProvider` | Data 리포지토리(core), 이후 Boot 자동 구성(phantom-spring-boot) | — | 다른 phantom 플러그인이 빈 출처를 추가하는 API다 |

등록하지 **않는** 것: `useScopeEnlarger`, `semContributor`, `postStartupActivity`, `backgroundPostStartupActivity`, `codeInsight.inlayProvider`, `toolWindow` (v0.1).

**게이트**: 모든 진입 클래스는 첫 줄에서 `SpringProjectGate.isActive(project)`를 부른다. 게이트는 `hasLibraryJar(project, "org.springframework:spring-context")`를 그대로 쓴다. 진입 클래스는 `model`/`registry` 패키지를 import하지 않는 별도 파일로 둔다. 그래야 Spring이 없는 프로젝트에서 구현 클래스가 적재되지 않는다 [가정: JVM 지연 클래스 로딩이므로 참조만 없으면 적재되지 않는다].

### 3.3 데이터 모델

모든 레코드는 불변이고 PSI를 들고 있지 않다. 위치는 `(VirtualFile, textOffset)`과 이름으로 기억하고, UI에 낼 때만 PSI로 되찾는다.

```kotlin
// 타입 참조: 파일 안에서 문법적으로 풀 수 있는 만큼 푼다
sealed interface TypeRef {
  data class Resolved(val fqn: String, val args: List<TypeRef>, val nullable: Boolean) : TypeRef
  data class Unresolved(val text: String, val candidates: List<String>) : TypeRef // 같은 패키지/스타 임포트 후보
}

data class AnnotationUse(val fqnOrShort: String, val attrs: Map<String, AttrValue>) // value, basePackages, name, prefix ...

data class ClassDecl(                      // 파일의 모든 최상위/중첩 클래스 (빈이 아니어도)
  val fqn: String, val kind: Kind,         // CLASS, INTERFACE, ANNOTATION, OBJECT
  val supertypes: List<TypeRef>,           // 타입 매칭용 계층 정보
  val annotations: List<AnnotationUse>,
  val isFinalAsWritten: Boolean,           // Kotlin: open/abstract 수식어 유무 (allopen 적용 전)
  val offset: Int)

data class BeanMethodDecl(val ownerFqn: String, val name: String, val returnType: TypeRef,
                          val annotations: List<AnnotationUse>, val params: List<InjectionPointRecord>, val offset: Int)

data class InjectionPointRecord(
  val ownerFqn: String, val kind: IpKind,  // CTOR_PARAM, FIELD, SETTER, BEAN_METHOD_PARAM
  val name: String, val type: TypeRef,
  val qualifier: String?, val required: Boolean, // @Autowired(required=false), T?, 기본값 → false
  val offset: Int)

data class FileSummary(
  val packageName: String,
  val classes: List<ClassDecl>, val beanMethods: List<BeanMethodDecl>,
  val injectionPoints: List<InjectionPointRecord>,
  val valuePlaceholders: List<PlaceholderUse>,      // @Value("${a.b:x}")
  val contentHash: Long)                             // 위 필드만으로 계산한 해시
```

전역 구조:

```kotlin
class BeanRegistrySnapshot(           // 불변 스냅숏, 통째로 교체
  val version: Long,
  val summaries: PersistentMap<VirtualFile, FileSummary>,
  val classesByFqn: Map<String, ClassDecl>,
  val stereotypeAnnotations: Set<String>,             // @Component의 메타 닫힘 (사용자 정의 포함)
  val contextRoots: List<ContextRoot>,                 // @SpringBootApplication / @ComponentScan 루트
  val beansByType: Map<String, List<BeanDef>>,         // 모든 상위 타입 FQN으로 색인
  val injectionPointsByType: Map<String, List<IpRef>>) // 역방향 거터용

data class BeanDef(val name: String, val type: String, val source: BeanSource, // STEREOTYPE, BEAN_METHOD, IMPORT, CONFIG_PROPS, DATA_REPOSITORY
                   val primary: Boolean, val qualifiers: Set<String>, val conditional: Boolean,
                   val roots: BitSet /* 어느 ContextRoot에 들어가는가 */, val location: Location)
```

- 빈 이름 규칙 [가정, Spring 문서]: 스테레오타입 값 > 클래스 단순 이름의 첫 글자 소문자화(`AnnotationBeanNameGenerator`, 첫 두 글자가 대문자면 그대로) > `@Bean(name=...)` > 메서드 이름.
- 메타 스테레오타입 닫힘은 `ClassDecl.kind == ANNOTATION`인 레코드에서 계산한다. 라이브러리 쪽 메타 애너테이션(`@Service` → `@Component`, `@RestController` → `@Controller` → `@Component`)은 `MetaAnnotationUtil.getAnnotationNamesWithChildren(module, "org.springframework.stereotype.Component", false)` [검증: `java/openapi/src/com/intellij/codeInsight/MetaAnnotationUtil.java` 118행]로 한 번 구하고 `JavaLibraryModificationTracker`에 묶어 캐시한다.

### 3.4 캐시와 무효화 (핵심)

#### 3.4.1 세 층

```
[층 1] FileSummary (파일 단위)
   └─ 입력: 그 파일의 PSI (Kotlin은 스텁에서 읽을 수 있는 만큼)
   └─ 무효화: 그 파일이 바뀔 때만 (더러운 파일 집합)
[층 2] BeanRegistrySnapshot (프로젝트 단위, 불변)
   └─ 입력: 모든 FileSummary + 라이브러리 메타 스테레오타입 + 모듈 그래프
   └─ 갱신: 더러운 파일의 요약 해시가 실제로 바뀌었을 때만 version+1
   └─ 전체 재계산: 라이브러리/루트 변경 (합치기만 다시 함, 층 1은 유지)
[층 3] 주입 지점 해석 결과, 거터 대상 (요소 단위)
   └─ CachedValue, 의존성 = [SpringStructureTracker, 해당 PsiFile]
```

#### 3.4.2 `SpringStructureTracker`

`ModificationTracker` [검증: `platform/util/src/com/intellij/openapi/util/ModificationTracker.java`] 구현이다. `getModificationCount()`는 `snapshot.version`을 돌려준다.

갱신 흐름:
1. `PsiTreeChangeListener` [검증: `platform/core-api/src/com/intellij/psi/PsiTreeChangeListener.java`]는 이벤트가 오면 Java/Kotlin 파일이고 소스 루트 안이면 `dirty.add(file)`만 한다. O(1)이고 해석하지 않는다.
2. `AsyncFileListener`는 생성·삭제·이동·이름 변경에서 `dirty.add`를 하거나, 삭제면 `removed.add`를 한다.
3. 레지스트리를 읽는 쪽(검사, 거터, 참조)이 `BeanRegistry.current()`를 부르면 이렇게 한다.
   - `dirty`가 비어 있으면 스냅숏을 바로 돌려준다.
   - 비어 있지 않으면 더러운 파일만 요약을 다시 뽑는다. 4천 파일 프로젝트에서 한 번에 1–3개 정도다.
   - 새 `contentHash`가 이전 값과 **모두 같으면** 스냅숏을 그대로 두고 `dirty`만 비운다. version은 오르지 않으므로 층 3 캐시가 전부 산다.
   - 하나라도 다르면 그 파일들의 기여분을 빼고 새로 더한 스냅숏을 만들고 version+1 한다. 색인 맵은 영속 자료구조(`kotlinx.collections.immutable`은 트리에 이미 있는지 확인 필요, 없으면 `fastutil` + copy-on-write) [가정]로 부분 갱신한다.
4. 요약 재추출은 read action 안에서 하고 취소 가능하다(`ProgressManager.checkCanceled()`). 취소되면 `dirty`를 되돌려 놓는다.

`contentHash`에 넣는 것: 클래스 선언 헤더(이름, 종류, 상위 타입, 애너테이션과 그 속성), `@Bean` 메서드 시그니처, 주입 지점(이름, 타입 텍스트, 한정자), `@Value` 플레이스홀더 문자열. 넣지 않는 것: 메서드 본문, 지역 변수, 주석, 공백, 빈과 무관한 메서드.

결과: 다음 편집은 **어떤 Spring 캐시도 무효화하지 않는다** (C2 해결).
- 메서드 본문, 프로퍼티 초기화식 편집
- 빈이 아닌 클래스의 메서드 추가·삭제
- 테스트의 `@Test`, `@DisplayName`, `@Nested` 추가 (JUnit 애너테이션은 해시에는 들어가지만 결과 레지스트리를 바꾸지 않는다. 더 줄이고 싶으면 "Spring 관련 애너테이션 + 사용자 메타 스테레오타입 후보"만 해시에 넣는다. 4.3 단계에서 측정해 결정한다)

다음 편집은 version을 올리지만 **재계산은 파일 하나 분량**이다.
- `@Component` 추가·삭제, 생성자 파라미터 타입 변경, `@Bean` 반환 타입 변경, 상위 타입 변경, `@Qualifier` 변경

전체 합치기 재계산(층 1 유지)은 다음 경우에만 한다.
- `JavaLibraryModificationTracker` 변화 (Gradle 동기화 후 라이브러리 변경)
- `ProjectRootModificationTracker` 변화 (모듈, 소스 루트 변경) [검증: `platform/core-api/src/com/intellij/openapi/roots/ProjectRootModificationTracker.java`]
- 사용자 메타 스테레오타입 애너테이션 자체(`annotation class UseCase`)의 메타 애너테이션 변경. 스테레오타입 닫힘이 바뀌어 모든 요약의 "빈인가" 판정을 다시 해야 한다. 그래도 PSI는 다시 읽지 않고 레코드만 다시 판정한다.

#### 3.4.3 요약 추출기 (층 1)

- **Java**: `PsiJavaFile`의 스텁 기반 PSI로 읽는다. 타입은 `PsiTypeElement.getText()`와 import 목록으로 FQN을 만들고, 필요할 때만 `PsiJavaCodeReferenceElement.resolve()`를 부른다. 사용자 프로젝트에는 Java 파일이 없어 우선순위가 낮다.
- **Kotlin**: `KtFile` → `KtClassOrObject`, `KtPrimaryConstructor.valueParameters`, `KtNamedFunction`, `KtAnnotationEntry`를 읽는다. 타입 참조의 문법 텍스트(`KtTypeReference.text`)를 다음 순서로 푼다.
  1. 명시적 import (별칭 포함)
  2. 같은 파일에 선언된 클래스
  3. 같은 패키지 클래스. 층 2의 `classesByFqn`에서 확인하므로 `Unresolved(candidates=[pkg.Name])`로 남겨 둔다
  4. 스타 import. 후보 목록으로 남긴다
  5. Kotlin 기본 import(`kotlin.*`, `kotlin.collections.*`)
  6. 여기서도 못 풀면 그때만 Analysis API로 해석한다 (`analyze(ktTypeReference) { ... }`). 호출 횟수는 디버그 통계로 센다

  Kotlin 스텁에는 애너테이션 엔트리, 상위 타입 목록, 파라미터 타입 참조가 들어 있다 [가정: `KotlinAnnotationsIndex`, `KotlinSuperClassIndex`가 스텁 인덱스이므로 그 정보가 스텁에 있다. 검증: `plugins/kotlin/base/indices/src/org/jetbrains/kotlin/idea/stubindex/KotlinAnnotationsIndex.kt`, `KotlinSuperClassIndex.kt`]. 그래서 열리지 않은 파일은 AST를 만들지 않고 요약할 수 있다. 애너테이션 인자(`basePackages = ["..."]`)처럼 스텁에 없는 정보는 그 애너테이션이 Spring 애너테이션일 때만 AST에서 읽는다.
- light class(`SymbolLightClass`)와 UAST 변환은 요약 경로에서 **쓰지 않는다** (C6).

#### 3.4.4 콜드 스타트

- **v0.1**: 첫 요청에서 후보 파일만 요약한다. 후보는 스텁 인덱스로 고른다.
  - `KotlinAnnotationsIndex`, `JavaAnnotationIndex` [검증: `java/java-indexing-impl/src/com/intellij/psi/impl/java/stubs/index/JavaAnnotationIndex.java`]에서 스테레오타입 단순 이름 집합(`Component`, `Service`, `Repository`, `Controller`, `RestController`, `Configuration`, `Bean`, `Import`, `ComponentScan`, `SpringBootApplication`, `EnableConfigurationProperties`, `ConfigurationProperties`, `Autowired`, `Value`, 사용자 메타 스테레오타입 이름)을 조회한다.
  - 리포지토리 인터페이스는 `KotlinSuperClassIndex`에서 `Repository`, `CrudRepository`, `JpaRepository`, `ListCrudRepository`, `PagingAndSortingRepository` 등 단순 이름으로 고른다.
  - 사용자 프로젝트 기준 후보는 약 600–900 파일이다 [가정: 위 grep 수치에서 추정]. 대상을 smart mode 안에서 `ReadAction.nonBlocking(...).inSmartMode(project).submit(AppExecutorUtil.getAppExecutorService())` [검증: `platform/core-api/src/com/intellij/openapi/application/ReadAction.java`, `NonBlockingReadAction.java`]로 계산한다.
  - 계산 중에는 거터와 검사가 **아무것도 내지 않는다**(기다리지 않음). 끝나면 열린 파일에 대해 `DaemonCodeAnalyzer.restart(file)`을 부른다.
- **v0.2**: `FileSummary`를 직렬화하는 `SingleEntryFileBasedIndexExtension` [검증: `platform/indexing-api/src/com/intellij/util/indexing/SingleEntryFileBasedIndexExtension.java`]로 영속화한다. 인덱스 값이 같으면 `MapReduceIndex`의 수정 스탬프가 오르지 않는다 [검증: `platform/util/src/com/intellij/util/indexing/impl/MapReduceIndex.java` 383–386행, 항목 ADDED/UPDATED/REMOVED일 때만 `incrementModificationStamp()`]. 그래서 `FileBasedIndex.getIndexModificationStamp(id, project)` [검증: `FileBasedIndex.java` 183행]를 `SpringStructureTracker`의 바탕으로 쓸 수 있다. 단, 모든 프로젝트의 모든 Java/Kotlin 파일 인덱싱 비용이 늘어나므로 v0.1 측정 결과를 보고 결정한다 (6절 R3).

#### 3.4.5 층 3

- 주입 지점 해석: `CachedValuesManager.getCachedValue(psiAnchor) { Result(resolve(ip), SpringStructureTracker, psiAnchor.containingFile) }` [검증: `platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java`]
- 결과는 `List<BeanDef>`의 짧은 목록이고 PSI는 들고 있지 않다.

### 3.5 컨텍스트 루트와 스캔 범위

`ContextRoot`는 main 소스의 `@SpringBootApplication`, `@SpringBootConfiguration`, `@ComponentScan`, `@EnableAutoConfiguration` 클래스다.

- 스캔 패키지: `scanBasePackages` / `basePackages` / `value` / `basePackageClasses`. 없으면 그 클래스의 패키지다 [가정, Spring 문서].
- 루트 스코프: `GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(rootModule)` [검증: `platform/core-api/src/com/intellij/psi/search/GlobalSearchScope.java` 232행]. 사용자 프로젝트에서는 adapter-module 루트가 application-module, core-module 등을 포함한다.
- 빈 포함 규칙: 스테레오타입 클래스면 (루트 스코프 안) ∧ (스캔 패키지 접두사 일치). `@Bean` 메서드는 그 소유 `@Configuration`이 포함될 때. `@Import` 대상은 무조건 포함한다. 리포지토리는 `@EnableJpaRepositories(basePackages, basePackageClasses)`가 있으면 그 범위, 없으면 루트 패키지다 (Boot 자동 구성 기본값) [가정].
- **주입 지점 → 루트 선택** (헥사고날 대응): 주입 지점 모듈 M을 **의존 닫힘에 포함하는** 루트 전부를 고른다. 후보 빈은 그 루트들에 포함된 빈의 합집합이다. application-module의 포트 주입은 adapter-module 루트에서 보이는 구현 빈으로 풀린다.
  - M을 포함하는 루트가 하나도 없으면(라이브러리성 모듈) 프로젝트 전체 소스 빈을 후보로 쓰고, "빈 없음"은 보고하지 않는다.
  - 루트가 여럿(adapter, batch)이고 어느 한쪽에서만 빈이 없으면 "빈 없음"을 보고하지 않는다. 모든 루트에서 없을 때만 보고한다. 루트별 진단은 v0.2 옵션이다.
- 테스트 소스(v0.1): 빈 정의와 내비게이션은 지원한다. `@TestConfiguration`의 `@Bean`도 후보에 넣는다. 검사 F4는 테스트 소스에서 "후보 여럿"만 보고한다. 테스트 컨텍스트 모델은 v0.2다.

### 3.6 주입 해석 규칙

순서는 Spring 문서의 `DefaultListableBeanFactory` 후보 선택 [가정]과 같다.

1. **타입 풀기**: `Optional<T>`, `ObjectProvider<T>`, `jakarta.inject.Provider<T>`, Kotlin `T?` → 선택적 주입(required=false). `List/Set/Collection<T>`, `Array<T>`, `Map<String,T>` → 컬렉션 주입. 0개도 오류가 아니다 (Spring 6에서는 빈 컬렉션 주입 [가정]). Kotlin 파라미터에 기본값이 있으면 required=false다.
2. **후보**: `beansByType[T.fqn]` ∩ 선택된 루트. 타입 매칭은 원시 타입(FQN) 기준이다. 상위 타입 닫힘은 프로젝트 클래스는 `ClassDecl.supertypes`로, 라이브러리 클래스는 `JavaPsiFacade.findClass` + `InheritanceUtil` [검증: `java/java-psi-api/src/com/intellij/psi/util/InheritanceUtil.java`]로 구한다. 라이브러리 쪽 결과는 `JavaLibraryModificationTracker`에 묶어 캐시한다.
3. **제네릭**: 후보가 여럿이고 주입 타입에 타입 인자가 있으면 인자 FQN으로 다시 거른다. 판정할 수 없으면 "모름"으로 처리하고 보고하지 않는다.
4. **한정자**: `@Qualifier("x")` → 빈 이름 또는 빈의 `@Qualifier` 값. 사용자 정의 한정자 애너테이션(`@Qualifier`로 메타 애너테이션된 것)도 지원한다. Kotlin 생성자 파라미터의 애너테이션 사용처 지정(`@param:Qualifier`, 없으면 파라미터에 붙음)을 처리한다 [가정: Kotlin 기본 대상 규칙].
5. **`@Primary`** 하나면 그것을 고른다.
6. **이름 폴백**: 파라미터·필드 이름이 빈 이름과 같으면 그것을 고른다 [가정: Spring의 파라미터 이름 폴백. Kotlin은 kotlin-reflect로 이름을 얻는다].
7. **판정**: 1개면 OK. 0개면 "빈 없음", 여럿이면 "모호함".
   - 라이브러리 타입(FQN이 프로젝트 소스에 없음)은 v0.1에서 **보고하지 않는다**. 자동 구성 빈(`DataSource`, `ObjectMapper`, `RedisTemplate` 등)을 모르기 때문이다. 다만 후보가 프로젝트 빈 둘 이상이면 모호함은 보고한다. 사용자 `JacksonConfig`의 `@Primary objectMapper`가 이 경우다.
   - 후보 중 `conditional=true`(`@Profile`, `@Conditional*`)가 섞이면 모호함을 보고하지 않는다.

### 3.7 스레딩

- **EDT에서 하는 일은 없다.**
  - 거터 아이콘: `LineMarkersPass`는 백그라운드 daemon 스레드에서 돈다 [가정]. 레지스트리 스냅숏이 준비돼 있지 않으면 `null`을 돌려주고 기다리지 않는다.
  - 거터 클릭: 이동 대상은 `NotNullLazyValue`에서 레코드 → `Location` → PSI로 바꾼다. 맵 조회와 `findFile` 정도라 EDT에서 돌아도 수 ms다. 대상이 50개를 넘으면 `ReadAction.nonBlocking`으로 돌리고 팝업은 결과가 올 때 띄운다 [가정: 사용자 프로젝트 기준 대상은 대부분 1–3개].
  - 빠른 수정(`@Qualifier` 추가): `LocalQuickFix`의 쓰기 작업만 EDT에서 한다. 후보 목록은 문제를 만들 때 이미 계산돼 있다.
- **레지스트리 계산**: `NonBlockingReadAction`(취소 가능, smart mode 전용), `coalesceBy(project)`로 중복 요청을 합친다.
- **dumb mode**: 모든 진입점은 `DumbService.isDumb(project)`[검증: `platform/core-api/src/com/intellij/openapi/project/DumbService.kt`]면 즉시 반환한다. `DumbAware`를 붙이지 않는다.
- **락**: 스냅숏은 `@Volatile` 참조 하나로 교체한다. 더러운 파일 집합은 `ConcurrentHashMap.newKeySet()`이다. 전역 `synchronized`는 쓰지 않는다. Ultimate의 shared-index 전역 락 경합(13.8 s 대기 [관측])을 되풀이하지 않기 위해서다.

### 3.8 `@Value` / `@ConfigurationProperties` (properties 모듈)

- `ConfigKeyIndex`(FileBasedIndex): 입력은 `application*.yml|yaml|properties`, `bootstrap*`이고 프로젝트 소스·리소스 루트 안의 파일이다 (`DefaultFileTypeSpecificWithProjectInputFilter` [검증]). 키는 relaxed-binding 정규형(소문자, `-`/`_` 제거, 인덱스 `[0]` 유지)이고 값은 원래 키와 오프셋이다. YAML 전체 키는 `YAMLUtil.getConfigFullName` [검증: `plugins/yaml/src/YAMLUtil.java` 52행]로 만들고, properties는 `Property.getKey()`로 만든다. 문서 구분(`---`)과 `spring.config.activate.on-profile`은 값에 프로필 태그로 넣는다.
- `@Value("${a.b-c:def}")`: `UastReferenceRegistrar`로 문자열 리터럴에 `PsiPolyVariantReference`를 건다. 결과는 인덱스의 모든 프로필 정의다. 키 완성은 인덱스 키 목록에서 한다.
- `@ConfigurationProperties(prefix="jwt")`: 클래스의 생성자 파라미터와 프로퍼티 → `jwt.<kebab(name)>`. 중첩 데이터 클래스는 재귀로 1단계까지 푼다. 거터는 프로퍼티 → yml 키다. yml 키에서 코드로 가는 역방향은 `gotoDeclarationHandler`로 연결하고, 레지스트리의 `configPropsByPrefix` 맵을 조회한다.
- v0.1에는 "알 수 없는 키" 검사가 **없다**. 라이브러리 메타데이터 없이 내면 `spring.*` 키가 전부 오탐이 된다. phantom-spring-boot가 메타데이터를 제공하면 그쪽에서 켠다.

### 3.9 Kotlin open/allopen (F8)

- K2는 allopen 컴파일러 플러그인을 IDE 안에 번들한다 [검증: `plugins/kotlin/bundled-compiler-plugins-support/src/org/jetbrains/kotlin/idea/fir/extensions/KotlinK2BundledCompilerPlugins.kt`에 `AllOpenComponentRegistrar` 등록]. 그래서 facet에 allopen 옵션이 있으면 분석 결과의 modality에 이미 반영된다.
- 모듈별 allopen 애너테이션 목록은 `Module.getSpecialAnnotations(prefix)` [검증: `plugins/kotlin/compiler-plugins/compiler-plugin-support/common/src/org/jetbrains/kotlin/idea/compilerPlugin/idePluginUtils.kt` 14행]와 같은 방식으로 facet 컴파일러 인자에서 읽는다. 캐시는 `CachedAnnotationNames`처럼 `ProjectRootModificationTracker`에 묶는다 [검증: 같은 폴더 `CachedAnnotationNames.kt`]. 옵션 접두사(`plugin:org.jetbrains.kotlin.allopen:annotation=`, `...:preset=spring`)는 [가정]이며, 1단계에서 사용자 프로젝트 facet을 열어 확인한다.
- 검사 `PhantomFinalSpringClass`: `@Configuration`(proxyBeanMethods 기본 true), `@Transactional`/`@Async`/`@Cacheable`/`@Validated`가 있는 클래스나 메서드가 (문법상 final) ∧ (allopen 목록과 preset이 그 애너테이션을 덮지 않음)이면 경고한다. 빠른 수정: `open` 추가, 또는 "build.gradle.kts에 `kotlin("plugin.spring")` 추가" 안내. Analysis API를 부르지 않고 문법과 facet 정보만 쓴다.
- 검사 `PhantomRedundantOpen`: allopen이 덮는데도 `open`이 적혀 있으면 약한 경고를 낸다. 기본 비활성.

---

## 4. 구현 단계

| # | 작업 | 일 | 사용자가 IDE에서 볼 수 있는 완료 기준 |
|---|---|---:|---|
| 1 | 플러그인 뼈대: iml, plugin.xml, 콘텐츠 모듈 3개, 번들 등록, `SpringProjectGate`. allopen facet 옵션 접두사를 사용자 프로젝트에서 확인 | 1.5 | `./installers.cmd` 산출 IDE의 Settings > Plugins에 "Phantom Spring"이 보인다. Spring 없는 프로젝트에서 `phantom.spring.debug.stats` 액션이 "inactive, classes loaded: gate only"를 보여 준다 |
| 2 | 데이터 모델 + Kotlin 요약 추출기 + Java 요약 추출기, 해시 | 3 | 디버그 액션 "Show Spring summary for current file"이 열린 Kotlin 파일의 빈, 주입 지점, FQN 해석 결과를 보여 준다. `@UseCase` 클래스가 후보로 나온다 |
| 3 | `SpringStructureTracker` + `BeanRegistry`(더러운 파일, 해시 비교, 스냅숏 교체) + 콜드 스타트 후보 선택 | 3 | 디버그 통계에 `version`, `summaries`, `rebuilds`가 보인다. 메서드 본문을 50번 고쳐도 `version`이 그대로이고, `@Component` 하나를 지우면 정확히 1 오른다 |
| 4 | 컨텍스트 루트, 스캔 범위, 루트 선택(헥사고날), 리포지토리와 `@EnableConfigurationProperties` 빈 출처 | 2 | 통계에 루트 2개(`ReservationApplication`, `ReservationBatchApplication`)와 각 루트의 빈 수가 나온다 |
| 5 | 주입 해석(래퍼, 한정자, `@Primary`, 이름 폴백, 제네릭 보류) | 3 | 디버그 액션 "Resolve injection at caret"이 생성자 파라미터 하나의 후보와 판정 이유를 보여 준다 |
| 6 | 거터 양방향 (빠른 패스, 지연 대상) | 2 | 포트 인터페이스 타입의 생성자 파라미터 옆 아이콘을 누르면 adapter-module의 구현 빈으로 간다. `@Component` 클래스 아이콘을 누르면 주입되는 곳 목록이 뜬다 |
| 7 | autowiring 검사 + 빠른 수정 2종 | 2 | 테스트 파일에서 `@Qualifier`를 지우면 "Multiple beans: a, b" 경고와 "Add @Qualifier" 수정이 나온다. 없는 프로젝트 타입을 주입하면 "No bean of type" 오류가 난다 |
| 8 | `ImplicitUsageProvider` | 0.5 | `@Component` 클래스와 `@Bean` 메서드에 "unused" 회색 표시가 없다 |
| 9 | properties 모듈: `ConfigKeyIndex`, `@Value` 참조·완성, `@ConfigurationProperties` 거터 | 3 | `@Value("${jwt.` 안에서 완성이 뜨고, Ctrl+클릭으로 application.yml의 키로 간다. `JWTProperties`의 프로퍼티 거터에서 yml 키로 간다 |
| 10 | Kotlin open/allopen 검사 2종 | 1 | `kotlin("plugin.spring")`을 지운 모듈에서 `@Configuration class` 이름에 경고가 뜬다. 되돌리면 사라진다 |
| 11 | 성능 검증과 조정 (5절의 수치 측정, 해시 범위 결정) | 1.5 | 5.3절 표의 목표치를 만족하는 JFR 결과가 `docs/phantom/perf/`에 남는다 |
| | **합계** | **22.5** | |

순서 의존: 1 → 2 → 3 → (4, 5) → (6, 7, 8) → 9, 10 → 11. 9와 10은 3 이후 병렬로 할 수 있다.

---

## 5. 테스트 방법

### 5.1 자동 테스트 (community 테스트 프레임워크)

- 기반 클래스: Java는 `LightJavaCodeInsightFixtureTestCase` [검증: `java/testFramework/src/com/intellij/testFramework/fixtures/LightJavaCodeInsightFixtureTestCase.java`], Kotlin은 `KotlinLightCodeInsightFixtureTestCase` [검증: `plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/KotlinLightCodeInsightFixtureTestCase.kt`]를 K2 모드로 쓴다.
- Spring jar: `MavenDependencyUtil.addFromMaven(model, "org.springframework:spring-context:6.2.x")` [검증: `java/testFramework/shared/src/com/intellij/testFramework/fixtures/MavenDependencyUtil.java`. 같은 방식이 `java/java-tests/testSrc/com/intellij/spring/SpringJSpecifyLightHighlightingTestCase.kt`에 있다]. spring-data-commons, spring-boot-autoconfigure도 같은 식으로 넣는다.
- 테스트 묶음:
  - `SummaryExtractorTest`: testData의 `.kt`/`.java` 파일 → 요약 JSON 골든 파일 비교
  - `StructureTrackerTest`: `myFixture.type(...)`으로 본문을 편집한 뒤 `version` 불변, 애너테이션을 추가한 뒤 +1, 파일 삭제 뒤 +1을 확인한다. **C2 회귀 방지의 핵심 테스트**다
  - `AutowiringHighlightingTest`: `myFixture.enableInspections(...)` + `<error descr="...">` 마크업으로 `checkHighlighting()`
  - `GutterNavigationTest`: `myFixture.findGuttersAtCaret()` → 대상 이름 비교
  - `MultiModuleRootTest`: 헤비 테스트(`HeavyPlatformTestCase` 계열)로 모듈 3개(port, adapter, app)를 만들어 헥사고날 해석을 확인한다
  - `ConfigKeyReferenceTest`: yml과 properties의 relaxed binding 케이스
  - `GateTest`: Spring 라이브러리 없는 프로젝트에서 marker, inspection 결과가 0이고 `BeanRegistry` 서비스가 생성되지 않음을 확인한다 (`project.getServiceIfCreated(...) == null`)

### 5.2 수동 확인 (`~/orca/workspaces/prototype-reservation-system/infro`)

1. 프로젝트를 열고 Gradle 동기화를 끝낸다.
2. `ReservationApplication.kt` 옆에 루트 아이콘이 보이는지, `@UseCase` 클래스에 빈 아이콘이 보이는지 확인한다.
3. application-module의 유스케이스 생성자 파라미터(포트 타입)에서 거터를 눌러 adapter-module 구현으로 가는지 본다.
4. `JacksonConfig`의 `@Primary`를 잠시 지우고, `ObjectMapper`를 주입받는 곳에 모호함 경고가 생기는지 본다.
5. `PropertiesConfig`의 `@EnableConfigurationProperties` 목록에서 `JWTProperties::class`를 빼면 `JWTProperties` 주입 지점에 "빈 없음"이 뜨는지 본다.
6. `@Value` 28곳 가운데 몇 곳에서 Ctrl+클릭으로 yml 키로 가는지 확인한다.
7. 잘못 보고하는 곳 확인: Problems 뷰에서 phantom 검사 결과를 전부 보고 **오탐 0**을 목표로 한다. 오탐마다 testData 케이스를 추가한다.

### 5.3 성능 확인

`Intellij-rea`의 측정 절차(JFR `settings=profile`, 240 s, 스택 깊이 256)를 phantom-idea 빌드에 그대로 적용한다.

| 지표 | Ultimate [관측] | 목표 |
|---|---|---|
| 시작 240 s 중 Spring 관련 프레임이 있는 샘플 | 543–587 (10–11%) | **≤ 100 (2%)** [가정 목표] |
| 콜드 레지스트리 생성 시간 (디버그 통계) | 측정 불가 (분산) | ≤ 1.5 s 벽시계, 백그라운드 |
| 메서드 본문 1회 편집 후 Spring 재계산 | 전체 모델 (inferred) | **0** (version 불변) |
| `@Component` 추가 1회 후 재계산 | 전체 모델 | 파일 1개 요약 + 맵 부분 갱신, ≤ 20 ms |
| EDT 위 phantom 프레임 | 0 | 0 |
| startup activity 수 | Spring 핵심 0, 영역 전체 5 | 0 |
| Spring 없는 프로젝트에서 적재되는 phantom 클래스 | — | 진입 클래스만 (≤ 15) |
| Analysis API 호출 수 (콜드 1회) | — | 후보 파일 수의 5% 이하 |

---

## 6. 리스크와 남은 의문

| # | 리스크 | 영향 | 대응 |
|---|---|---|---|
| R1 | 문법적 타입 해석이 틀린다 (타입 별칭, 스타 import 충돌, 중첩 클래스 이름 가림) | 잘못된 "빈 없음" | 판정할 수 없으면 "모름"으로 처리하고 보고하지 않는다. 디버그 통계에 미해석 비율을 낸다. 5%를 넘으면 해당 패턴만 Analysis API 경로로 보낸다 |
| R2 | 자동 구성 빈 미지원으로 라이브러리 타입 주입을 검사하지 못한다 | 기능 공백 (오탐은 아님) | v0.2에서 `AutoConfiguration.imports` + `@ConditionalOnClass`만 평가하는 `beanSourceProvider` (phantom-spring-boot와 공유) |
| R3 | v0.2 영속 인덱스가 Spring 없는 프로젝트에도 인덱싱 비용을 더한다 | 시작 인덱싱 증가 | 입력 필터에서 파일 텍스트에 `@`가 없으면 건너뛴다. 그래도 비싸면 v0.1 방식(스텁 인덱스 + 메모리 캐시)을 유지한다. 결정은 4단계 11번 측정 뒤 |
| R4 | `PsiTreeChangeListener`가 모든 키 입력마다 불린다 | 미미한 CPU | 리스너는 `dirty.add`만 한다. Spring 프로젝트가 아니면 등록되지 않는다 |
| R5 | `ON_DEMAND` 콘텐츠 모듈이 플랫폼에서 구현되지 않았다 [검증] | 클래스 적재를 플랫폼 수준에서 막지 못한다 | 진입 클래스 분리와 게이트로 막는다. 플랫폼에 구현되면 `loading="on-demand"`로 바꾼다 |
| R6 | 멀티 루트(adapter, batch)에서 한 루트에만 빈이 있는 경우를 v0.1이 보고하지 않는다 | 놓침 | 의도한 보수적 선택이다. v0.2에서 루트별 진단 옵션을 둔다 |
| R7 | Kotlin 스텁에 애너테이션 인자 값이 없어 AST를 읽어야 한다 | 콜드 스타트 비용 | Spring 애너테이션 인자만 읽고, 그런 애너테이션은 파일당 몇 개뿐이다. 측정 뒤 판단한다 |
| R8 | `getModificationCount()` 경로에서 요약 재추출을 하면 `CachedValue` 검사 중 무거운 일이 생긴다 | 하이라이팅 지연 | 재추출은 `BeanRegistry.current()`에서만 하고, 추적기의 `getModificationCount()`는 스냅숏 version만 읽는다. 층 3 캐시는 항상 `current()` 호출 뒤에 의존성을 검사한다 |
| R9 | community API 일부가 `@ApiStatus.Internal`/Experimental이다 (`ProjectSpecificInputFilter`가 experimental API 덤프에 있음 [검증: `platform/indexing-api/api-dump-experimental.txt`]) | 포크 업그레이드 시 깨짐 | 번들 플러그인이라 같은 트리에서 컴파일된다. 업그레이드 체크리스트에 넣는다 |
| R10 | Ultimate와 사용자 플러그인(Spring Debugger 등)이 같은 애너테이션에 거터를 낸다 | 중복 아이콘 | phantom-idea에는 Ultimate 플러그인이 없다. 3rd-party Spring 플러그인이 있으면 게이트에서 끈다 (설정 토글) |

남은 의문:
- [가정] allopen facet 옵션 문자열의 정확한 형식과 `kotlin("plugin.spring")`이 preset으로 넘어가는지, 아니면 애너테이션 목록으로 펼쳐지는지. 1단계에서 사용자 프로젝트 facet을 읽어 확인한다.
- [가정] Kotlin 스텁에 생성자 파라미터 타입 참조가 들어 있어 AST 없이 읽을 수 있는지. 2단계 첫날 `StubBasedPsiElement` 경로로 확인한다.
- [가정] 미저장 문서 편집이 `PsiTreeChangeListener`로 바로 들어오는지, 커밋 뒤에만 들어오는지. 커밋 뒤라면 거터 갱신이 커밋 지연(기본 수백 ms)만큼 늦는다. 수용 가능하다.
- [미확인] Ultimate가 Spring 모델로 힙을 얼마나 쓰는지 (관측 노트 6절). 비교 기준이 없으므로 phantom 쪽 절대값만 잰다.
- [미확인] Spring Data 리포지토리 프래그먼트(`...RepositoryCustom` + `Impl`)를 빈으로 잡아야 오탐이 없는지. 사용자 프로젝트 16개 리포지토리 파일을 4단계에서 확인한다.
- 다른 설계와의 경계: `beanSourceProvider`와 `configKeyProvider` 내부 EP를 phantom-spring-boot, phantom-spring-mvc, phantom-jpa가 쓴다. 이름과 시그니처를 그 문서들과 맞춰야 한다.
