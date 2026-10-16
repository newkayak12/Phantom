# phantom-api: OpenAPI, 클라이언트-서버 탐색, HTTP Client 확장 설계

- 날짜: 2026-10-16
- 대상: Phantom IDEA (intellij-community `idea/262.10968.63` 포크), Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자
- 상태: 설계 초안 (구현 전)
- 관련 설계: [`2026-10-16-http-client.md`](2026-10-16-http-client.md)(phantom-http), [`2026-10-16-spring-boot.md`](2026-10-16-spring-boot.md)(phantom-spring-boot, Endpoints)
- 표기: **[V]** = 이 저장소나 사용자 프로젝트에서 경로·API·사실을 직접 확인함, **[A]** = 가정 (공개 문서·일반 지식에 근거, 아직 확인 안 함), **[M]** = Ultimate 분석 노트의 측정값 인용

## 0. Clean-room 선언

이 설계에 쓴 자료는 다음뿐이다.

1. Ultimate 동작·비용 기술 노트: `Intellij-rea/reversed/README.md`, `03-decompiled/2026-10-16-ult-other-plugins.md` §2.3–2.4, `2026-10-16-ult-web-js.md`, `2026-10-16-ult-spring-javaee.md`, `02-runtime/2026-10-16-startup-jfr.md`
2. Ultimate `swagger`, `restClient` 플러그인의 **디스크립터**(`plugin.xml`). 확장 태그 종류와 content module 이름만 기능 체크리스트로 썼다.
3. 이 저장소(커뮤니티 소스)의 공개 API
4. 공개 스펙: OpenAPI 3.0/3.1, Swagger 2.0, JSON Schema, JetBrains HTTP Client 요청 파일 문법, GraphQL over HTTP, RFC 6455(WebSocket), gRPC over HTTP/2와 Server Reflection 프로토콜, Spring Framework 문서(`RestClient`, `WebClient`, `RestTemplate`, HTTP Interface `@HttpExchange`), springdoc-openapi 문서

`reversed/source/` 아래 파일은 열지 않았다. 디컴파일 코드는 보지도, 옮기지도 않았다. 라이선스·인증 코드는 범위 밖이다.

---

## 1. 목표와 범위

### 1.1 사용자가 실제로 쓰는 것 (근거)

사용자 프로젝트 `~/orca/workspaces/prototype-reservation-system`을 grep한 결과다 **[V]**.

| 항목 | 관측 | 설계에 주는 의미 |
|---|---|---|
| OpenAPI | `springdoc-openapi-starter-webmvc-ui` 사용, `springdoc.swagger-ui.path: /swagger-ui.html`. 저장소 안에 `openapi*.yaml/json` 파일 없음 | **code-first**다. 스펙 편집보다 "코드에서 스펙 뽑기"와 "실행 중 앱의 스펙 보기"가 더 자주 쓰인다 |
| HTTP 클라이언트 코드 | `RestClient` 28회, `@HttpExchange`·`@GetExchange` 각 4회 (`infrastructure-module/.../httpinterface/timetable/FindTimeTableOccupancyHttpInterface.kt`). `RestTemplate`, `WebClient`, `@FeignClient`는 0회 | 클라이언트→서버 탐색의 1순위는 **Spring HTTP Interface**다 |
| 서버 쪽 대응 | `adapter-module/.../rest/internal/timetable/TimeTableOccupancyUrl.kt`의 `const val FIND_INTERNAL = "${InternalUrl.PREFIX}${FIND_INTERNAL_PAYLOAD}"`, 컨트롤러 `FindTimeTableOccupancyInternalController.kt` | 클라이언트와 서버 경로가 **서로 다른 const 조합**으로 만들어지고 정규식 변수(`{timeTableId:[0-9a-fA-F\\-]{36}}`)가 들어 있다. 상수 평가와 변수 정규화가 필수다 |
| `.http`, `.proto`, `*.graphqls` | 저장소 안에 없음 | gRPC, GraphQL, WebSocket은 MVP에 넣지 않는다. 작업 지시에 "사용자가 protobuf 플러그인을 쓴다"는 말이 있지만 이 프로젝트에서는 확인되지 않았다 **[A]** |
| `@RestController` | 136개 파일 (워크트리 4개 합계) | 엔드포인트 수가 많다. 프로젝트 전체 스캔은 피한다 |

### 1.2 MVP (v0.1)

1. **클라이언트 → 서버 탐색 (Spring HTTP Interface)**: `@HttpExchange` 인터페이스의 메서드(`@GetExchange`, `@PostExchange`, `@PutExchange`, `@PatchExchange`, `@DeleteExchange`, `@HttpExchange(method=...)`)에 거터 아이콘. 누르면 같은 메서드+경로의 서버 핸들러(`@GetMapping` 등)로 간다. Kotlin과 Java 모두.
2. **클라이언트 코드 안 URL 문자열 참조**: `@HttpExchange` 계열 애너테이션의 경로 문자열, `RestClient`/`WebClient`의 `.uri("...")`, `RestTemplate`의 `getForObject/postForEntity/exchange("...")` 첫 인자에 URL 참조를 붙인다. Ctrl+클릭으로 서버 핸들러 이동, 경로 자동 완성, 서버 쪽에서 Find Usages를 하면 이 클라이언트 호출들이 나온다.
3. **OpenAPI 초안 내보내기**: Endpoints 툴 창(phantom-spring-boot)과 Tools 메뉴에 "Export OpenAPI Draft". 선택한 컨트롤러(또는 전체)의 경로, 메서드, path/query 파라미터, 요청 본문 스키마(`@RequestBody` 타입), 응답 스키마(반환 타입, `ResponseEntity<T>` 풀기), Bean Validation 제약 일부를 OpenAPI 3.1 YAML로 만든다. 결과는 scratch 파일로 연다.
4. **OpenAPI 스펙 편집 지원 (가벼운 수준)**: `openapi: 3.0.x/3.1.x` 또는 `swagger: "2.0"`을 최상위 키로 가진 YAML/JSON 파일에 공식 스키마 기반 완성·검증(커뮤니티 JSON Schema 엔진 재사용), `$ref` 이동·완성·미해결 경고.

### 1.3 v0.2 이후

- **v0.2**
  - OpenAPI 렌더링 미리보기 (JCEF, 자체 정적 HTML 생성기. Swagger UI 번들 없음)
  - 실행 중 앱의 `/v3/api-docs` 가져와 보기, 내보낸 초안과의 diff (springdoc 사용자용)
  - 스펙의 `paths` 항목 ↔ Spring 핸들러 양방향 거터
  - phantom-http 확장: 요청 전 스크립트(`< {% %}`), `jsonPath()` 도우미, `crypto` 해시 도우미
  - WebSocket 요청(`WEBSOCKET ws://...`), GraphQL over HTTP(`GRAPHQL http://...`)
  - `@FeignClient` 인터페이스, `WebClient` 체인 중 `uri(Function)` 형태 일부
  - 서버 핸들러에서 클라이언트로 가는 거터 (v0.1은 Find Usages로만 제공)
- **v0.3**
  - gRPC 요청(`GRPC host/pkg.Service/Method`): 서버 리플렉션, 그다음 빌드 산출 descriptor set. 단항, 서버 스트리밍
  - GraphQL subscription over WebSocket (`graphql-transport-ws`)
  - OpenAPI 미리보기의 "이 요청 보내기" → phantom-http scratch 생성

### 1.4 명시적 비목표

- **AsyncAPI** 편집과 미리보기 (Ultimate는 asyncapi react 컴포넌트 9.8 MB를 번들한다 **[M]**)
- **OpenAPI 코드 생성** (openapi-generator 실행 구성, Gradle/Maven 생성 소스 연동). 사용자는 Gradle 플러그인으로 직접 돌리면 된다
- **시각적 편집**과 마우스 리스너 startup activity (Ultimate의 `SwVisualEditingMouseListenerSetupActivity` **[M]**)
- **원격 스펙 자동 다운로드**와 그 background startup activity (`RemoteSpecificationDownloadActivity` **[M]**)
- **GraphQL 언어 지원** (스키마 기반 완성, 검사). 커뮤니티에 GraphQL 언어 플러그인이 없다 **[V: `plugins/` 목록]**. 쿼리는 일반 텍스트로 다룬다
- **`.proto` 편집기**. protobuf 언어 지원은 사용자가 쓰는 별도 플러그인에 맡긴다
- **GraalJS 번들**, JavaScript 언어 플러그인 의존
- 스펙 파일 VCS 이력 비교, 스펠링 사전, 라이브 템플릿 컨텍스트 4종 (Ultimate 디스크립터에 있는 부가 기능 **[V: descriptor]**)

---

## 2. Ultimate 대비 경량화 원칙

| Ultimate에서 관측한 비용 | 이 설계의 회피 방법 |
|---|---|
| `com.intellij.swagger`의 `fileBasedIndex` 2개(`SwReferencesIndex`, `OpenapiSpecificationContentIndex`, 내용 의존). **모든 YAML/JSON 파일**이 인덱싱 입력이 된다 **[M]** | 인덱스 0개. OpenAPI 판정은 열린 파일의 PSI에서 최상위 키만 보고 `CachedValue`에 둔다. 프로젝트 안의 스펙 목록이 필요한 기능(v0.2 스펙↔코드 거터)은 사용자가 설정한 스펙 경로 + 파일 이름 규칙(`openapi*`, `swagger*`, `api-docs*`)으로 `FilenameIndex`(플랫폼 기본 인덱스)만 조회한다 |
| startup JFR에서 `SwaggerElementPatternsKt.isQueryParameter`가 일반 JSON/YAML reference provider 매칭 아래에 2샘플 **[M]**. 모든 JSON/YAML 리터럴에 패턴이 돈다 | reference contributor의 패턴 첫 조건을 "부모 키 이름이 `$ref`"로 둔다. 문자열 비교 한 번으로 거의 모든 리터럴이 걸러진다. 파일 종류 확인은 그다음이다 |
| swagger 플러그인 32 MB 중 `swagger-ui` webjar 11.6 MB, asyncapi 9.8 MB **[M]** | 미리보기는 파싱한 모델에서 자체 HTML을 만든다. JS 프레임워크를 번들하지 않는다. 목표 플러그인 크기 1 MB 미만 **[A]** |
| restClient 122 MB 중 GraalJS 86.2 MB **[M]** | 스크립트 엔진은 커뮤니티에 이미 있는 Rhino(`rhino-runtime-1.7.15.1.jar`, 1.2 MB **[V: Bazel 캐시의 jar 크기]**)를 phantom-http와 함께 쓴다. §3.8 |
| `grpc-nio-worker-ELG-*` 상주 스레드 4개 (소유자 미확인) **[M]** | gRPC 채널은 첫 gRPC 요청 때 만들고 60초 유휴 후 `shutdown()`한다. 이벤트 루프 스레드 이름에 `phantom-grpc-` 접두사를 붙여 측정 가능하게 한다 |
| swagger의 `postStartupActivity` 1개, `backgroundPostStartupActivity` 1개 **[M]** | startup activity 0개. 모든 기능은 편집기 열기, 거터 계산, 액션 실행 때 시작된다 |
| Spring 모델 캐시가 파일 하나 수정에도 프로젝트 단위로 무효화되는 구조(추정) **[M]** | 클라이언트 호출 모델은 **파일 단위** `CachedValue`(그 PsiFile에만 의존)다. 서버 쪽 해석은 phantom-spring-boot의 파일 단위 캐시를 `UrlResolver`로 조회만 한다. 프로젝트 전체 무효화 지점이 없다 |
| 프레임워크 기능이 플러그인 설치만으로 로드되고 기능마다 라이브러리를 확인 **[M]** | 진입 조건을 맨 앞에 둔다. 클라이언트 탐색은 `JavaLibraryUtil.hasLibraryJar(module, "org.springframework:spring-web")` **[V: `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` 176행]**, OpenAPI 편집은 파일 내용, gRPC/WebSocket/GraphQL은 `.http` 파일에 해당 메서드 토큰이 있을 때만 그 content module 클래스가 로드된다 |

추가 원칙:

- **서비스는 모두 lazy**: `<projectService>`만 쓰고 `preload`를 쓰지 않는다. app 수준 서비스는 gRPC 채널 풀 하나뿐이고, 첫 gRPC 요청 때 만들어진다.
- **EDT에서 계산하지 않는다**: 거터, 참조, 완성은 데몬/완성 스레드의 read action에서 돈다. 액션은 `ReadAction.nonBlocking(...).inSmartMode(project)` **[V: `platform/core-api/src/com/intellij/openapi/application/NonBlockingReadAction.java`]**로 계산하고, EDT에서는 결과 파일 열기만 한다.
- **dumb mode에서는 아무것도 하지 않는다**: `DumbAware`를 붙이지 않는다. OpenAPI 스키마 제공자만 예외로 dumb mode에서도 동작해도 된다(인덱스를 안 쓰므로).

---

## 3. 아키텍처

### 3.1 모듈 배치

```
plugins/phantom-api/
├── BUILD.bazel, intellij.phantom.api.iml
├── resources/META-INF/plugin.xml               # id: phantom.api
├── core/      → intellij.phantom.api.core      # UrlPath 정규화, 경로 매칭, 상수 평가 도우미 (플랫폼 + UAST만 의존)
├── client/    → intellij.phantom.api.client    # Spring 클라이언트 호출 모델, 거터, URL 참조   (depends: intellij.java, spring-web 감지)
├── export/    → intellij.phantom.api.export    # Endpoints → OpenAPI 초안 YAML                 (depends: intellij.java)
├── openapi/   → intellij.phantom.api.openapi   # 판정, 스키마 제공, $ref 참조·검사              (depends: intellij.json.backend, intellij.yaml.backend)
├── preview/   → intellij.phantom.api.preview   # v0.2: TextEditorWithPreview + JCEF            (depends: intellij.platform.ui.jcef)
├── http.ws/      → intellij.phantom.api.http.ws       # v0.2: WEBSOCKET 실행기       (depends: plugin phantom.http)
├── http.graphql/ → intellij.phantom.api.http.graphql  # v0.2: GRAPHQL 실행기         (depends: plugin phantom.http)
├── http.grpc/    → intellij.phantom.api.http.grpc     # v0.3: GRPC 실행기 (depends: phantom.http, intellij.libraries.grpc, intellij.libraries.grpc.netty.shaded, intellij.libraries.protobuf)
└── testSrc/, testData/
```

- 라이브러리 모듈 `intellij.libraries.grpc`, `intellij.libraries.grpc.netty.shaded`, `intellij.libraries.protobuf`, `intellij.libraries.rhino`가 커뮤니티에 있다 **[V: `libraries/*/module-content.yaml`]**. 이미 배포본(`dist.all/lib`)에 들어 있는 jar라서 새로 늘어나는 디스크 크기는 없다. grpc-netty-shaded는 10.5 MB다 **[V: Bazel 캐시]**.
- content module 문법은 `plugins/yaml/resources/META-INF/plugin.xml`의 `<content><module name=.../>` 형식을 따른다 **[V]**. JSON 백엔드 모듈은 `intellij.json.backend`, YAML은 `intellij.yaml.backend`다 **[V]**.
- 번들 등록은 `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt` 61행의 `bundledPluginModules`에 `intellij.phantom.api`를 넣는다 **[V: 위치]**.
- `http.*` 모듈은 phantom-http 플러그인이 있을 때만 로드된다. phantom-http에 두 가지를 요청해야 한다(§6 Q1): 요청 메서드 토큰 `WEBSOCKET`, `GRAPHQL`, `GRPC`를 렉서가 받아들일 것, 그리고 실행기 확장 지점 `phantom.http.requestExecutor`. phantom-http 설계는 gRPC와 GraphQL을 "넣지 않는다"고 했다. 그 결정은 phantom-http **본체**에 대한 것이고, 이 설계는 별도 플러그인의 선택 모듈로 넣어 본체 비용을 0으로 유지한다.

#### plugin.xml 뼈대 (설계)

```xml
<idea-plugin>
  <id>phantom.api</id>
  <name>API Tools (Phantom)</name>
  <incompatible-with>com.intellij.swagger</incompatible-with>
  <dependencies>
    <plugin id="com.intellij.java"/>
  </dependencies>
  <content>
    <module name="intellij.phantom.api.core"/>
    <module name="intellij.phantom.api.client"/>
    <module name="intellij.phantom.api.export"/>
    <module name="intellij.phantom.api.openapi"/>
    <module name="intellij.phantom.api.preview"/>
    <module name="intellij.phantom.api.http.ws"/>
    <module name="intellij.phantom.api.http.graphql"/>
    <module name="intellij.phantom.api.http.grpc"/>
  </content>
</idea-plugin>
```

### 3.2 사용하는 커뮤니티 확장 지점과 API

| 용도 | EP / API | 위치 (모두 **[V]**) |
|---|---|---|
| URL 해석의 중심 | `com.intellij.microservices.urlResolverFactory` → `UrlResolverFactory.forProject(project): UrlResolver?`, `UrlResolver.resolve(UrlResolveRequest)`, `getVariants()` | EP `platform/platform-resources/src/META-INF/LangExtensionPoints.xml` 745–746행, `platform/lang-api/src/com/intellij/microservices/url/UrlResolverFactory.kt`, `.../url/UrlResolver.kt` |
| 전체 resolver 조회 | `UrlResolverManager.getInstance(project).resolve(request)` | `platform/lang-api/src/com/intellij/microservices/url/UrlResolverManager.kt` 36–67행 |
| URL 모델 | `UrlResolveRequest(schemeHint, authorityHint, path, method)`, `UrlTargetInfo`(`path`, `methods`, `resolveToPsiElement()`), `UrlPath.PathSegment.{Exact, Variable(name, regex), Composite, Undefined}` | `platform/lang-api/src/com/intellij/microservices/url/UrlPathModel.kt` 21–104행 |
| 엔드포인트 공급 (phantom-spring-boot가 구현) | `com.intellij.microservices.endpointsProvider`, `EndpointsUrlTargetProvider.getUrlTargetInfo(group, endpoint)`, `getOpenApiSpecification(group, endpoint)` | `LangExtensionPoints.xml` 735행, `platform/lang-api/src/com/intellij/microservices/endpoints/EndpointsProvider.kt` 139–144행 |
| 클라이언트 문자열에 URL 참조 붙이기 | `uastUrlReferenceProvider(schemes)`, `uastUrlReferenceProvider(injector)`, `UrlPathReferenceInjector.forPartialStringFrom(...)` | `java/java-analysis-impl/src/com/intellij/microservices/jvm/url/UastReferenceInjectorUtils.kt` 46–51행, `platform/lang-api/src/com/intellij/microservices/url/references/UrlPathReferenceInjector.kt` 52–56행 |
| UAST 참조 등록 | `PsiReferenceRegistrar.registerUastReferenceProvider(...)` | `uast/uast-common-ide/src/com/intellij/psi/UastReferenceRegistrar.kt` |
| 부분 상수 문자열 | `UStringConcatenationsFacade`, `PartiallyKnownString` | `uast/uast-common-ide/src/org/jetbrains/uast/expressions/UStringConcatenationsFacade.kt`, `platform/lang-api/src/com/intellij/psi/util/PartiallyKnownString.kt` |
| 공짜로 따라오는 것 | URL 경로 완성(`UrlPathReferenceCompletionContributor`), URL 경로 Find Usages(`UrlPathUsagesHandlerFactory`), URL 인레이(`UrlPathInlayHintsProviderFactory`) | `platform/platform-resources/src/META-INF/LangExtensions.xml` 1701, 1720, 1724행 |
| 거터 | `codeInsight.lineMarkerProvider` → `RelatedItemLineMarkerProvider` | `platform/lang-api/src/com/intellij/codeInsight/daemon/RelatedItemLineMarkerProvider.java`, `LineMarkerProvider.java` |
| OAS 모델 (초안 내보내기) | `OpenApiSpecification`, `OasEndpointPath.Builder`, `OasOperation.Builder`, `OasSchema`, `getSpecificationByUrls(urls)`, `squashOpenApiSpecifications(list)` | `platform/lang-api/src/com/intellij/microservices/oas/*.kt`, `.../oas/OasExportUtils.kt` 15, 63행 |
| JSON 스키마 연결 | `JavaScript.JsonSchema.ProviderFactory` → `JsonSchemaProviderFactory.getProviders(project)`, `JsonSchemaFileProvider`(`isAvailable`, `getSchemaFile`, `getSchemaVersion`), `JavaScript.JsonSchema.ContentAwareSchemaFileProvider` → `ContentAwareJsonSchemaFileProvider.getSchemaFile(psiFile)` | `json/backend/resources/intellij.json.backend.xml` 157–161행, `json/backend/src/com/jetbrains/jsonSchema/extension/JsonSchemaProviderFactory.java`, `JsonSchemaFileProvider.java`, `ContentAwareJsonSchemaFileProvider.java` |
| YAML에서 JSON 스키마 | YAML 플러그인이 JSON 스키마 검사·완성·문서를 YAML에 적용 | `plugins/yaml/backend/src/schema/YamlJsonSchemaHighlightingInspection.java`, `YamlJsonSchemaDocumentationProvider.java` |
| JSON pointer 이스케이프 | `JsonPointerUtil` | `json/backend/src/com/jetbrains/jsonSchema/JsonPointerUtil.java` |
| 참조 기여 | `psi.referenceContributor` → `PsiReferenceContributor` | `platform/core-api/src/com/intellij/psi/PsiReferenceContributor.java` |
| 캐시 | `CachedValuesManager.getCachedValue(psiElement, provider)` | `platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java` |
| 미리보기 (v0.2) | `TextEditorWithPreview`, `TextEditorWithPreviewProvider`, `FileEditorProvider`, `JBCefApp.isSupported()`, `JBCefBrowser`, `JBCefJSQuery` | `platform/platform-impl/src/com/intellij/openapi/fileEditor/TextEditorWithPreview.kt`, `TextEditorWithPreviewProvider.kt`, `platform/analysis-api/src/com/intellij/openapi/fileEditor/FileEditorProvider.java`, `platform/ui.jcef/jcef/JBCefApp.java`, `JBCefBrowser.java`, `JBCefJSQuery.java`. 참고 구현: `plugins/markdown/jcef/src/com/intellij/markdown/jcef/preview/MarkdownJCEFHtmlPanel.kt` |
| 라이브러리 감지 | `JavaLibraryUtil.hasLibraryJar(Module, String)`, `hasLibraryClass(Module, String)` | `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` 119, 176행 |

Ultimate swagger 디스크립터와 비교해 **쓰지 않는** 것 **[V: descriptor]**: `fileBasedIndex` 2개, `postStartupActivity`, `backgroundPostStartupActivity`, `httpRequestHandler` 2개(내장 서버로 미리보기 제공·CORS 우회), `configurationType`/`programRunner`(codegen), `generatedCodeProcessor` EP, `fileHistoryCollector` EP, `yaml/json.customStructureViewFactory`, `liveTemplateContext` 4개, `spellchecker.bundledDictionaryProvider`, `microservices.oasSerializationCompatibilityProvider`(커뮤니티에서도 `@Deprecated`, `@ApiStatus.Internal` **[V: `oas/serialization/OasSerializationUtils.kt`]**).

### 3.3 클라이언트 → 서버 탐색 (client 모듈)

#### 데이터 모델

```kotlin
// core 모듈
enum class ClientKind { HTTP_INTERFACE, REST_CLIENT, WEB_CLIENT, REST_TEMPLATE, FEIGN /* v0.2 */ }

class ClientCall(
  val kind: ClientKind,
  val method: String?,                 // 대문자 HTTP 메서드. 알 수 없으면 null (= 모든 메서드와 비교)
  val path: UrlPath,                   // 커뮤니티 UrlPath. 상수 평가 후 정규화된 경로
  val pathText: String,                // 표시용 원문 ("/api/v1/internal/timetable/{timeTableId}/...")
  val anchor: SmartPsiElementPointer<PsiElement>,  // 거터를 붙일 식별자 (메서드 이름 또는 uri 호출)
)

// 파일 단위 캐시: CachedValuesManager.getCachedValue(ktFile) { Result(scan(file), file) }
class FileClientCalls(val calls: List<ClientCall>)
```

#### 수집 규칙

- **HTTP Interface** (Spring 6.0+, `org.springframework.web.service.annotation.*` **[A: 패키지, 사용자 코드 import로 V]**)
  - 클래스 레벨 `@HttpExchange(url|value = "...")` + 메서드 레벨 `@XxxExchange(url|value = "...")`를 이어 붙인다. 메서드는 애너테이션 종류에서, `@HttpExchange(method = "...")`면 그 값에서 얻는다.
  - UAST(`UClass`, `UMethod`, `UAnnotation.findAttributeValue`)로 Kotlin/Java를 같이 다룬다.
- **RestClient / WebClient**: `UCallExpression` 중 메서드 이름이 `uri`이고, 수신자 타입이 `org.springframework.web.client.RestClient.UriSpec` 또는 `org.springframework.web.reactive.function.client.WebClient.UriSpec` 계열인 호출의 첫 문자열 인자 **[A: 내부 인터페이스 이름은 Spring 문서 기억]**. HTTP 메서드는 체인 앞쪽의 `.get()`, `.post()` 등 수신자 호출 이름에서 얻는다. `.method(HttpMethod.X)`는 상수 이름으로 판정한다.
- **RestTemplate**: `getForObject`, `getForEntity`, `postForObject`, `postForEntity`, `put`, `delete`, `patchForObject`, `exchange`의 첫 `String` 인자. 메서드는 이름에서, `exchange`는 두 번째 인자 `HttpMethod.X`에서 얻는다.
- 패턴 순서(비용): 메서드 이름 문자열 비교 → 인자 위치 → 수신자 타입 해석. 타입 해석이 마지막이라 대부분의 호출은 이름 비교에서 끝난다.

#### 상수 평가와 정규화

- `PartiallyKnownString`으로 문자열 템플릿과 `+` 연결을 펼친다. const 참조(`$TIME_TABLE`, `${InternalUrl.PREFIX}`)는 `UExpression.evaluate()`로 값을 얻는다 **[A: K2 UAST에서 다른 파일 companion/object의 `const val` 평가가 되는지. §6 R2]**.
- 정규화: `{name:regex}` → `PathSegment.Variable(name, regex)`. 비교할 때 변수 이름은 무시한다(클라이언트와 서버 이름이 다를 수 있다). 정규식은 표시에만 쓴다. 커뮤니티 `Variable.accepts()`는 TODO로 항상 `true`다 **[V: `UrlPathModel.kt` 95행]**.
- 끝 슬래시, 중복 슬래시, 쿼리 문자열은 떼고 비교한다.

#### 서버 쪽 해석

- 서버 후보는 직접 만들지 않는다. `UrlResolverManager.getInstance(project).resolve(UrlResolveRequest(null, null, path, method))`로 얻는다.
- 이 resolver는 누군가 `urlResolverFactory`로 Spring MVC 대상을 공급해야 동작한다. phantom-spring-boot 설계는 `EndpointsProvider`만 구현한다 **[V: spring-boot 설계 §3.5 F10]**. 그래서 **브리지**를 둔다.
  - 1안(권장): phantom-spring-boot의 `SpringMvcEndpointsProvider`가 `EndpointsUrlTargetProvider`를 구현해 `getUrlTargetInfo`를 준다. 그리고 phantom-spring-boot가 `EndpointsUrlResolverFactory`(모든 `EndpointsUrlTargetProvider`를 `UrlResolver`로 감싸는 일반 브리지)를 등록한다. phantom-http 설계 §3.9도 같은 resolver를 가정하므로 한 곳에 두는 것이 맞다.
  - 2안(대체): phantom-spring-boot가 못 하면 `core` 모듈이 같은 브리지를 등록한다. 중복 등록을 막기 위해 `UrlResolverManager.meaningfulResolvers`에 같은 클래스 이름이 있으면 `forProject`가 `null`을 돌려준다.
- 매칭 순서: (1) 메서드+경로 정확 일치 (2) 경로만 일치(메서드 미상) (3) 서버 경로가 클라이언트 경로의 **접미**인 경우. (3)은 클라이언트 `baseUrl`에 context path가 들어 있을 수 있어서다. 이때 결과 목록에 "(접두 경로 무시)"를 표시한다. `baseUrl`(예: `RestClient.builder().baseUrl(internal.baseUrl)` **[V: 사용자 `RestClientConfig.kt`]**)은 설정 값이라 정적으로 알 수 없으므로 authority는 비교하지 않는다.

#### UI

- 거터 `PhantomClientCallLineMarkerProvider : RelatedItemLineMarkerProvider`. 대상이 1개면 바로 이동, 여러 개면 팝업. 대상이 0개면 아이콘을 회색 "서버 핸들러 없음"으로 보이되, 이 판정은 거터를 그릴 때가 아니라 **누를 때** 한다(거터 계산에서 resolver를 돌리지 않는다). 그래서 거터 계산 비용은 파일 캐시 조회뿐이다.
- 문자열 참조: `registerUastReferenceProvider(injectionHostUExpression()..., uastUrlReferenceProvider(listOf("http","https")))`. 커뮤니티 기능이 Ctrl+클릭, 완성, Find Usages를 처리한다. Find Usages가 서버 → 클라이언트 방향을 맡는다.

### 3.4 OpenAPI 초안 내보내기 (export 모듈)

- 입력: `EndpointsProvider.getAvailableProviders(project)` **[V: `EndpointsProvider.kt` 111행]** 중 `EndpointsUrlTargetProvider`. 공급자가 `getOpenApiSpecification`을 주면 그것을 쓰고, 없으면 `getUrlTargetInfo`에서 `getSpecificationByUrls`로 경로·메서드만 얻은 뒤 아래 보강을 한다.
- 보강 (UAST, 핸들러 메서드마다):
  - `@PathVariable`, `@RequestParam(required, defaultValue)`, `@RequestHeader` → `OasParameter`(`in` = path/query/header)
  - `@RequestBody T` → `requestBody.content["application/json"].schema = $ref: '#/components/schemas/T'`
  - 반환 타입: `ResponseEntity<T>`, `Mono<T>` 풀기, `List<T>` → `array`. `@ResponseStatus`가 있으면 그 코드, 없으면 `200`
  - 스키마 생성: Kotlin data class/Java 클래스의 프로퍼티. 기본 타입 매핑(`String`→string, `Int/Long`→integer(int32/int64), `UUID`→string(uuid), `LocalDate`→string(date), `OffsetDateTime/LocalDateTime`→string(date-time), `BigDecimal`→number, enum→string+enum). Kotlin 비-null 타입은 `required`. Bean Validation `@NotBlank/@NotNull`→required, `@Size(min,max)`→minLength/maxLength 또는 minItems/maxItems, `@Min/@Max`→minimum/maximum, `@Pattern`→pattern
  - 순환 참조는 `$ref`로 끊고, 깊이 6을 넘으면 `type: object`로 둔다
- 출력: OpenAPI 3.1 YAML. 직렬화기는 자체 작성한 작은 YAML writer다(키 순서 고정: `openapi, info, servers, paths, components`). 커뮤니티의 `generateOasDraft`는 Ultimate 확장에 위임하는 deprecated internal API라 쓰지 않는다 **[V]**. 커뮤니티 `OpenApiSpecification.isEmpty()`는 `paths.iterator().hasNext()`를 돌려줘 의미가 반대다 **[V: `OpenApiSpecification.kt` 9행]**. 이 메서드는 호출하지 않는다.
- 결과는 scratch 파일(`openapi-draft.yaml`)로 연다. 프로젝트 파일에 쓰지 않으므로 VFS 갱신이나 인덱싱이 일어나지 않는다.
- springdoc 애너테이션(`@Operation`, `@Tag`, `@Schema`)은 사용자 프로젝트에서 쓰이지 않아(grep 0건 **[V]**) v0.1에서 읽지 않는다. v0.2에서 `summary`, `description`, `tags`만 읽는다.

### 3.5 OpenAPI 스펙 편집 (openapi 모듈)

#### 판정

```kotlin
enum class OasKind { SWAGGER_20, OAS_30, OAS_31 }

object OasFileDetector {
  // YAMLFile 또는 JsonFile의 첫 문서, 최상위 매핑의 키만 본다. 하위 트리를 걷지 않는다.
  fun kind(file: PsiFile): OasKind? = CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(detect(file), file)
  }
}
```

- 최상위 `openapi` 값이 `3.0.`으로 시작하면 OAS_30, `3.1.`이면 OAS_31, `swagger: "2.0"`이면 SWAGGER_20.
- YAML/JSON이 아닌 파일, 프로젝트 밖 라이브러리 파일은 바로 `null`.

#### 스키마 연결

- `PhantomOasSchemaProvider : ContentAwareJsonSchemaFileProvider`. PsiFile을 받으므로 판정 결과로 번들 스키마 3개 중 하나를 고른다. `isAvailable(VirtualFile)`만 있는 `JsonSchemaFileProvider` 방식은 내용 판정 때마다 문서를 다시 읽어야 해서 쓰지 않는다.
- 번들 스키마: OpenAPI Initiative가 공개한 3.0(draft-04 기반), 3.1(2020-12 기반), Swagger 2.0 JSON Schema. Apache-2.0 라이선스 **[A: 배포 전에 저장소 LICENSE 확인]**. 2020-12 기능은 커뮤니티 엔진이 지원한다 **[V: `plugins/yaml/backend/testSrc/schema/YamlJsonSchema2020FeaturesTest.kt`가 있음. 3.1 스키마의 `$dynamicRef`까지 되는지는 A]**.
- 커뮤니티 `JsonSchemaCatalogImplicitProviderFactory`(SchemaStore 카탈로그, `order="last"` **[V: `intellij.json.backend.xml` 145행]**)가 같은 파일에 다른 OpenAPI 스키마를 붙이면 결과가 둘이 된다 **[A]**. 테스트로 확인하고, 필요하면 카탈로그 매칭 이름을 사용자 설정에서 끈다.

#### `$ref`

- `PhantomOasRefReferenceContributor`: YAML 스칼라와 JSON 문자열 리터럴. 패턴 첫 조건은 "부모 키 텍스트 == `$ref`", 둘째가 `OasFileDetector.kind(file) != null`.
- 해석
  - `#/a/b~1c` → 같은 파일 안의 JSON pointer. `JsonPointerUtil`로 `~0`, `~1`을 푼다.
  - `other.yaml#/components/schemas/X` → `file.virtualFile.parent.findFileByRelativePath("other.yaml")` 후 pointer.
  - `http(s)://...` → 해석하지 않는다(네트워크 없음). 참조는 soft라서 경고도 내지 않는다.
- 완성: `#/components/schemas/`, `#/components/parameters/` 등 접두에서 해당 매핑의 키 목록.
- 검사 `PhantomOasUnresolvedRefInspection`: 로컬·상대 파일 `$ref`가 안 풀리면 경고. 순환 `$ref`는 오류가 아니다(스펙상 허용).
- 이름 바꾸기(v0.2): `components/schemas/X` 키 이름 변경 시 같은 파일과 상대 참조 파일의 `$ref` 갱신.

### 3.6 미리보기 (preview 모듈, v0.2)

- `PhantomOasPreviewProvider : TextEditorWithPreviewProvider`. `accept`는 `OasFileDetector`를 쓰지 않고 파일 확장자만 본다(`FileEditorProvider.accept`는 PSI 없이 빨리 끝나야 한다 **[A]**). 그다음 미리보기 패널을 만들 때 판정해서 OpenAPI가 아니면 미리보기 없이 텍스트 편집기만 보인다.
- 렌더러: 파싱한 PSI → `OasDocModel`(info, servers, tags, operations, schemas) → 정적 HTML 문자열. CSS 변수로 IDE 라이트/다크 테마 색을 넣는다. 스크립트는 `JBCefJSQuery` 하나("operation 클릭 → 편집기의 해당 줄로 이동")뿐이다.
- 갱신: 문서 변경을 300 ms 디바운스한 뒤 non-blocking read action으로 모델을 다시 만들고 `loadHTML`. 패널이 보이지 않을 때는 갱신하지 않는다.
- `JBCefApp.isSupported()`가 거짓이면 Swing `JEditorPane`에 같은 HTML의 단순판을 보인다.
- "Try it"(v0.3): operation 옆 링크 → phantom-http scratch에 요청 블록을 만든다.

### 3.7 실행 중 앱의 스펙 (v0.2)

- springdoc 기본 경로 `/v3/api-docs`(설정 키 `springdoc.api-docs.path` **[A]**)를 JDK `HttpClient`로 가져온다. 포트는 phantom-spring-boot가 실행 구성에서 아는 `server.port`를 쓴다(§6 Q3).
- 결과는 scratch 파일로 열어 openapi 모듈의 검증·미리보기를 그대로 받는다. "Compare with Draft"는 플랫폼 diff로 3.4의 초안과 비교한다.

### 3.8 HTTP Client 스크립트 엔진 결정

phantom-http 설계 §3.8은 응답 핸들러에 Rhino를 이미 골랐다. 이 설계는 그 결정을 비용 기준으로 다시 검토하고 확장 API만 더한다.

| 후보 | 배포 크기 | 첫 실행 지연 | 기존 `.http` 스크립트 호환 | 판정 |
|---|---|---|---|---|
| GraalJS (Ultimate) | 86.2 MB **[M]** | 컨텍스트 생성 수백 ms, 인터프리터 모드(JIT 없는 JBR) **[A]** | 가장 높음 (최신 ES) | 제외. 크기가 phantom 목표와 맞지 않는다 |
| **Rhino 1.7.15.1** (커뮤니티 `intellij.libraries.rhino`) | 1.2 MB, 이미 배포본에 있음 **[V]** | 수십 ms **[A]** | 중간. ES5 + ES2015 일부 | **채택**. 추가 크기 0 |
| 자체 단순 DSL (`status == 200`, `$.token -> global.token`) | 수십 KB | 거의 0 | 없음. 기존 JS 핸들러를 고쳐 써야 함 | 제외. 공개 문법(JS)과 어긋난다 |
| Kotlin script (JSR-223, `plugins/kotlin/scripting` **[V: 디렉터리 존재]**) | 컴파일러를 이미 들고 있음 | 첫 평가 수 초, 메모리 수백 MB **[A]** | 없음 | 제외. 요청 하나에 컴파일러를 띄우는 비용 |
| QuickJS 등 네이티브 | 1–2 MB, 플랫폼별 바이너리 | 짧음 | 높음 | 제외. 네이티브 바이너리 배포·서명 부담 |

확장 API(v0.2, phantom-http의 script 모듈에 기여하는 형태):

- 요청 전 스크립트 `< {% ... %}`: `request.variables.set(name, value)`, `client.global.*`
- `jsonPath(obj, "$.a.b[0]")`: 점, 대괄호, 인덱스, `*`만 지원하는 자체 평가기(필터식 `?()`는 v0.3). 커뮤니티 `plugins/jsonpath`는 JSONPath 언어(PSI) 지원이라 런타임 평가기로 쓰지 않는다 **[V: 디렉터리 존재, A: 런타임 평가기가 없음]**
- `crypto.sha256()`, `crypto.hmac.sha256().withTextSecret(...)`, `crypto.base64` 등 공개 문서의 일부 **[A: 이름]**. JDK `MessageDigest`, `Mac`으로 구현
- 이 확장도 Rhino 모듈 안에 있으므로 핸들러가 없는 요청만 쓰면 Rhino 클래스는 로드되지 않는다.

### 3.9 WebSocket, GraphQL, gRPC 요청 (http.* 모듈)

요청 파일 문법은 JetBrains HTTP Client 공개 문서를 따른다 **[A: 세부 문법]**.

```http
### WebSocket (v0.2)
WEBSOCKET ws://localhost:8080/ws/echo
Content-Type: application/json

{"type": "ping"}
=== wait-for-server
{"type": "subscribe", "topic": "timetable"}

### GraphQL over HTTP (v0.2)
GRAPHQL http://localhost:8080/graphql

query Find($id: ID!) { timeTable(id: $id) { id status } }

{ "id": "{{timeTableId}}" }

### gRPC (v0.3)
GRPC localhost:9090/reservation.TimeTableService/Find

{ "timeTableId": "{{timeTableId}}" }
```

- **WebSocket**: JDK `java.net.http.WebSocket`(추가 라이브러리 0). 응답 패널에 송수신 메시지 로그와 입력 줄. `=== wait-for-server`는 다음 서버 메시지를 기다린다. 연결은 사용자가 닫거나 패널을 닫으면 끝난다. 커뮤니티 `platform/built-in-server/src/org/jetbrains/io/webSocket/WebSocketClient.java` **[V]**는 내장 서버(Netty) 쪽 구현이라 쓰지 않는다.
- **GraphQL**: 본문의 첫 부분은 쿼리, 마지막 JSON 블록이 있으면 variables. `POST`, `Content-Type: application/json`, `{"query", "variables", "operationName"}`(GraphQL over HTTP). 응답은 JSON 뷰어. `errors` 배열이 있으면 Tests 탭에 실패로 보인다. 쿼리 하이라이트는 v0.2에서 하지 않는다.
- **gRPC**: JDK `HttpClient`는 HTTP/2 trailer를 노출하지 않아 gRPC 상태를 읽을 수 없다 **[A]**. 그래서 커뮤니티 `grpc-netty-shaded`를 쓴다.
  - 서비스 정보 출처 순서: (1) 서버 리플렉션 `grpc.reflection.v1.ServerReflection`, 실패하면 `v1alpha` (2) 빌드 산출 descriptor set(Gradle protobuf 플러그인의 `generateDescriptorSet` 산출물 경로를 설정에서 지정) (3) `protoc`가 PATH나 Gradle 캐시에 있으면 `.proto`로 `--descriptor_set_out` 실행 **[A: 사용자 환경]**.
  - 리플렉션 요청 메시지는 생성 코드 없이 `DescriptorProtos`로 리플렉션 서비스 descriptor를 코드에서 만든 뒤 `DynamicMessage`로 주고받는다. 요청 JSON → `JsonFormat.parser()`가 필요하므로 `protobuf-java-util`이 커뮤니티 라이브러리에 있는지 확인해야 한다(§6 Q4).
  - 단항과 서버 스트리밍만. 채널은 authority당 하나, 60초 유휴 후 종료.
  - 완성: 리플렉션 결과를 authority 단위로 5분 캐시해 `GRPC host/` 뒤에 서비스·메서드 이름 완성.

### 3.10 캐싱과 무효화

| 데이터 | 범위 | 무효화 | 저장 |
|---|---|---|---|
| `FileClientCalls` | 파일 | 그 PsiFile 수정 | `CachedValue` (메모리) |
| 서버 대상 해석 결과 | 없음 | 매번 `UrlResolverManager`에 묻는다. 캐시는 phantom-spring-boot 쪽 파일 단위 캐시가 맡는다 | — |
| `OasFileDetector.kind` | 파일 | 그 PsiFile 수정 | `CachedValue` |
| `$ref` 해석 | 참조 | PSI 수정 시 플랫폼이 다시 해석 | — |
| 번들 OpenAPI 스키마 | 앱 | 없음(불변) | jar 리소스 → `VirtualFile`(JSON 스키마 서비스가 캐시) |
| 미리보기 모델 | 편집기 | 문서 변경 300 ms 디바운스 | 패널 필드 |
| gRPC 리플렉션 결과 | app, authority별 | 5분 TTL, 수동 새로고침 | 메모리 |
| OpenAPI 초안 | 없음 | 매번 새로 생성 | scratch 파일 |

프로젝트 전체를 무효화하는 지점이 없다. `PsiModificationTracker.getModificationCount()`(모든 PSI 변경에 증가)에 묶는 캐시도 두지 않는다.

### 3.11 스레딩

- 거터, 참조, 완성: 데몬·완성 스레드의 read action. 이 경로에서 네트워크와 파일 쓰기를 하지 않는다.
- 거터 클릭 시 서버 해석: `ReadAction.nonBlocking { resolve }.inSmartMode(project).finishOnUiThread(ModalityState.defaultModalityState()) { showPopup }` **[A: 체인 메서드 이름]**.
- 초안 내보내기: `withBackgroundProgress`로 non-blocking read action에서 모델 생성 → EDT에서 scratch 파일 생성·열기만.
- 미리보기: 모델 생성은 non-blocking read action, `loadHTML`은 JCEF가 자체 스레드로 처리.
- WebSocket/GraphQL/gRPC: phantom-http의 bounded executor(`phantom-http`, 4) 위에서. gRPC Netty 이벤트 루프는 `phantom-grpc-` 이름, 채널이 없으면 0개.

### 3.12 Kotlin K2 / UAST

- 클라이언트 수집, 초안 보강은 전부 UAST(`UMethod`, `UAnnotation`, `UCallExpression`, `UExpression.evaluate()`)로만 쓴다. Kotlin 전용 K1 API(`BindingContext`, `KotlinType`)는 쓰지 않는다.
- 수신자 타입 판정은 `UCallExpression.receiverType`(PsiType)로 한다. K2에서는 Analysis API 위에서 UAST가 PsiType을 계산한다 **[A]**.
- Kotlin nullability(`String?` vs `String`)는 초안 스키마의 `required`에 필요하다. UAST에서 `UParameter`/`UField`의 `@Nullable`/`@NotNull` 합성 애너테이션(K2 UAST가 붙이는 것)을 읽는다 **[A: K2에서도 붙는지 테스트로 확인]**.
- 테스트는 K2 모드에서 돌린다(§5.1).

---

## 4. 구현 단계

| # | 작업 | 일수 | IDE에서 사용자가 확인할 수 있는 수용 기준 |
|---|---|---|---|
| T0 | 뼈대: `plugins/phantom-api`, `.iml`, `plugin.xml`, content module 4개(core, client, export, openapi), 번들 등록, `./installers.cmd` 통과 | 1 | Settings → Plugins에 "API Tools (Phantom)"(bundled). Help → Diagnostic Tools → Startup Performance에 이 플러그인 activity 0개 |
| T1 | core: `UrlPath` 정규화, 매칭 3단계, 상수 평가 도우미 | 1.5 | (단위 테스트) `"/api/v1/internal/timetable/{timeTableId:[0-9a-fA-F\\-]{36}}/occupancy/{x}"`와 서버의 `${PREFIX}${PAYLOAD}` 결과가 일치 |
| T2 | 서버 해석 브리지: phantom-spring-boot에 `EndpointsUrlTargetProvider` 구현과 `EndpointsUrlResolverFactory` 요청. 늦으면 2안을 core에 | 1 | Spring 컨트롤러 파일의 `@GetMapping("...")` 문자열에 커뮤니티 URL 인레이가 보이고, 아무 Kotlin 문자열에 `http-url-reference`를 주입하면 경로 완성에 프로젝트 엔드포인트가 나온다 |
| T3 | client: HTTP Interface 수집, 파일 캐시, 거터 | 2 | `FindTimeTableOccupancyHttpInterface.kt`의 `findTimeTableOccupancyInternally` 옆 거터 → `FindTimeTableOccupancyInternalController`로 이동 |
| T4 | client: 애너테이션·`uri()`·`RestTemplate` 문자열에 UAST URL 참조 | 2 | `@GetExchange(URL)`의 `URL` 상수 문자열을 Ctrl+클릭하면 서버 핸들러로 이동. 서버 핸들러 경로에서 Find Usages → 클라이언트 인터페이스 메서드가 결과에 나온다 |
| T5 | openapi: 판정, 번들 스키마 3개, `ContentAwareJsonSchemaFileProvider` | 1.5 | 새 `openapi.yaml`에 `openapi: 3.1.0` 입력 후 `paths:` 아래에서 Ctrl+Space → `get`, `post` 등 완성. `info` 누락 시 경고. 일반 `application.yaml`에는 아무 변화 없음 |
| T6 | openapi: `$ref` 참조·완성·미해결 검사 | 2 | `$ref: '#/components/schemas/Ti|'`에서 스키마 이름 완성, Ctrl+클릭 이동, 없는 이름은 노란 밑줄 |
| T7 | export: 초안 생성(파라미터, 본문, 응답, 스키마, 검증 제약), YAML writer, 액션 2곳 | 3 | Endpoints 툴 창에서 컨트롤러 선택 → "Export OpenAPI Draft" → scratch `openapi-draft.yaml`이 열리고 T5 검증에서 오류 0개 |
| T8 | 경량성 측정과 회귀 테스트 정리 (§5.3) | 1.5 | §5.3 표의 목표값 충족 |
| **MVP 합계** | | **16** | |
| T9 (v0.2) | preview: 자체 HTML 렌더러 + JCEF + Swing 대체 | 4 | 스펙 파일 오른쪽에 operation 목록 미리보기, 클릭 시 편집기 줄 이동, 다크 테마 반영 |
| T10 (v0.2) | 실행 중 앱 `/v3/api-docs` 가져오기 + 초안 diff | 2 | 앱 실행 중 액션 → scratch 스펙 열림, "Compare with Draft"로 diff |
| T11 (v0.2) | 스펙 `paths` ↔ 핸들러 거터 (openapi 모듈이 `UrlResolverFactory` 하나 등록) | 2 | 스펙의 `/timetable/{id}:` 옆 거터 → 컨트롤러, 반대 방향도 |
| T12 (v0.2) | phantom-http 실행기 EP 합의 + WebSocket 모듈 | 3 | `WEBSOCKET` 요청 실행 → 메시지 로그, 입력 줄에서 추가 전송 |
| T13 (v0.2) | GraphQL over HTTP 모듈 | 1.5 | `GRAPHQL` 요청 → JSON 응답, `errors`가 있으면 Tests 실패 |
| T14 (v0.2) | 스크립트 확장(요청 전 스크립트, `jsonPath`, `crypto`) | 2 | `< {% request.variables.set("ts", Date.now()) %}` 후 `{{ts}}` 치환. `jsonPath(response.body, "$.data[0].id")` 결과가 global 변수에 들어감 |
| T15 (v0.2) | Feign, 서버→클라이언트 거터 | 2 | `@FeignClient` 인터페이스 메서드에 거터 |
| T16 (v0.3) | gRPC 모듈: 리플렉션, descriptor set, 단항/서버 스트리밍, 완성 | 6 | 리플렉션이 켜진 로컬 서버에 `GRPC` 요청 → JSON 응답. 60초 뒤 `phantom-grpc-` 스레드 0개 |

---

## 5. 테스트 방법

### 5.1 자동 테스트 (커뮤니티 테스트 프레임워크)

- 기반 클래스: `BasePlatformTestCase` **[V: `platform/testFramework/src/com/intellij/testFramework/fixtures/BasePlatformTestCase.java`]**, Java는 `LightJavaCodeInsightFixtureTestCase` **[V: `java/testFramework/src/.../LightJavaCodeInsightFixtureTestCase.java`]**, Kotlin은 `KotlinLightCodeInsightFixtureTestCase` **[V: `plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/KotlinLightCodeInsightFixtureTestCase.kt`]**를 K2 모드로 **[A: 모드 전환 방법은 기존 K2 테스트를 따라 확인]**.
- Spring 애너테이션은 실제 jar 대신 `testData/stubs/`에 최소 스텁(`@HttpExchange`, `@GetExchange`, `@RestController`, `@GetMapping`, `RestClient`의 `uri` 시그니처)을 둔다.
- 테스트 목록
  - `UrlPathNormalizerTest`: 정규식 변수, 끝 슬래시, 이중 슬래시, 템플릿 상수 합성
  - `HttpInterfaceClientCallsTest`(Kotlin, Java): 클래스+메서드 경로 합성, `method` 속성, 다른 파일 `const val` 평가
  - `ClientToServerNavigationTest`: `myFixture.configureByFiles(client.kt, controller.kt)` → `findGuttersAtCaret()` → 대상이 컨트롤러 메서드
  - `ClientUrlReferenceTest`: `uri("/api/<caret>")` 완성에 서버 경로, `findUsages`에 클라이언트 호출 포함
  - `OasSchemaHighlightingTest`, `YamlOasSchemaCompletionTest`: `JsonSchemaHighlightingTestBase` **[V: `json/backend/tests/test/com/jetbrains/jsonSchema/JsonSchemaHighlightingTestBase.java`]**와 `YamlByJsonSchemaHighlightingTest` **[V]** 방식
  - `OasRefReferenceTest`: 로컬 pointer, `~1` 이스케이프, 상대 파일, 미해결 경고, 원격 URL은 경고 없음
  - `OasDetectorNegativeTest`: `application.yaml`, `package.json`, `docker-compose.yml`에서 provider가 `null`, reference contributor가 참조를 만들지 않음
  - `OpenApiDraftExportTest`: 골든 파일 비교(`testData/export/*.expected.yaml`), 생성 결과를 다시 `OasSchemaHighlightingTest` 경로로 검증해 오류 0개
  - v0.2+: `GraphQlRequestBodyTest`(본문 분리), `JsonPathMiniTest`, WebSocket/gRPC는 in-process 서버(gRPC는 `grpc-inprocess` 사용 가능 여부 §6 Q4)로 통합 테스트

### 5.2 사용자 프로젝트 수동 확인 (`~/orca/workspaces/prototype-reservation-system`)

1. Phantom IDEA로 `infro` 워크트리를 열고 smart mode를 기다린다.
2. `infrastructure-module/.../httpinterface/timetable/FindTimeTableOccupancyHttpInterface.kt`를 연다. `findTimeTableOccupancyInternally` 옆 거터 → `adapter-module/.../rest/internal/timetable/find/FindTimeTableOccupancyInternalController.kt`의 핸들러로 이동하는지 본다. 이 경우 클라이언트는 `"/api/v1/internal/timetable/$TIME_TABLE/..."`, 서버는 `"${InternalUrl.PREFIX}${FIND_INTERNAL_PAYLOAD}"`로 경로가 다르게 만들어져 있어 상수 평가를 검증하는 좋은 사례다 **[V: 두 파일 내용]**.
3. 서버 핸들러의 매핑 문자열에서 Find Usages → 클라이언트 인터페이스가 나오는지 본다.
4. `RestClientConfig.kt`의 `RestClient` 체인 안 `uri(...)`가 있다면 같은 확인(현재 이 파일은 `baseUrl`만 쓴다 **[V]**. 확인용 호출은 scratch Kotlin 파일로).
5. Endpoints 툴 창에서 `rest/internal` 컨트롤러들을 선택해 "Export OpenAPI Draft" → 결과를 앱 실행 중 `http://localhost:<port>/v3/api-docs`(springdoc) 출력과 눈으로 비교한다. 경로·메서드·파라미터 수가 같아야 한다.
6. `application.yaml`, `compose.yaml`, `detekt.yaml`, `codecov.yaml`을 열어 OpenAPI 검증·완성·`$ref`가 붙지 않는 것을 확인한다.

### 5.3 경량성 측정 (Ultimate 분석과 같은 방법)

| 항목 | 방법 | 목표 |
|---|---|---|
| startup activity | Startup Performance 덤프 | phantom-api 0개 |
| 인덱스 | Indexing diagnostics | phantom-api 소유 인덱스 0개 (Ultimate swagger 2개 **[M]**) |
| 시작 직후 로드 클래스 | `jcmd <pid> VM.classloader_stats`, 플러그인 클래스로더별 | 프로젝트를 열고 아무 파일도 안 열었을 때 50개 미만 **[A]** |
| YAML/JSON 패턴 비용 | startup JFR, `jfr print --events jdk.ExecutionSample`에서 `com.phantom.api` 프레임 | 0샘플 (Ultimate swagger 2샘플 **[M]**) |
| 유휴 상주 스레드 | `jcmd Thread.print`에서 `phantom-grpc-`, `phantom-http` | 마지막 요청 60초 뒤 0개 |
| 배포 크기 | 빌드 산출 `plugins/phantom-api/lib` | 1 MB 미만 (Ultimate swagger 32 MB, restClient 122 MB **[M]**) |
| 번들 jar 로드 여부 | 핸들러·gRPC를 안 쓴 세션에서 `VM.classloader_stats` | Rhino, grpc, protobuf 클래스 0개 |

---

## 6. 리스크와 남은 의문

### 리스크

- **R1 서버 해석 의존.** 클라이언트→서버 탐색 전체가 `urlResolverFactory`로 Spring MVC 대상을 공급하는 쪽(phantom-spring-boot)에 달려 있다. phantom-spring-boot 설계에는 아직 `EndpointsUrlTargetProvider`/resolver가 없다 **[V: spring-boot 설계 검색]**. 완화: T2에서 두 설계를 맞추고, 늦어지면 2안 브리지를 core에 둔다.
- **R2 K2 UAST 상수 평가.** 다른 파일 `object`/`companion`의 `const val`을 문자열 템플릿 안에서 평가하는 것이 K2 UAST에서 항상 되는지 확인하지 않았다 **[A]**. 사용자 코드가 정확히 이 형태다. 완화: 평가 실패 시 `PartiallyKnownString`의 미지 구간을 `PathSegment.Undefined`로 두고, 미지 구간은 아무 세그먼트와 맞는 것으로 비교해 "후보" 표시로 낮춘다.
- **R3 phantom-spring-boot와의 클라이언트 오분류.** `@FeignClient` 인터페이스는 `@GetMapping`을 쓴다. phantom-spring-boot의 엔드포인트 수집이 이것을 서버 엔드포인트로 셀 수 있다 **[A]**. 완화: 수집에서 `@FeignClient` 타입을 제외하도록 요청. HTTP Interface는 다른 애너테이션이라 영향이 없다.
- **R4 OpenAPI 스키마 이중 적용.** SchemaStore 카탈로그 provider가 같은 파일에 다른 스키마를 붙일 수 있다 **[A]**. T5 테스트로 확인한다.
- **R5 3.1 스키마와 커뮤니티 엔진.** OAS 3.1 공식 스키마는 `$dynamicRef`와 dialect 메타스키마를 쓴다. 엔진이 일부만 지원하면 오탐이 생긴다 **[A]**. 완화: 3.1은 단순화한 자체 파생 스키마로 시작한다(구조 검사 중심).
- **R6 Rhino 호환성.** Ultimate에서 쓰던 핸들러 스크립트(최신 ES 문법)가 안 돌 수 있다. phantom-http R3와 같은 리스크다. 사용자 프로젝트에 `.http` 파일이 없어 지금은 영향이 작다 **[V]**.
- **R7 gRPC 비용.** grpc-netty-shaded는 10.5 MB jar이고 Netty 이벤트 루프 스레드를 만든다 **[V: 크기]**. 사용자가 gRPC를 쓰는지 확인되지 않았다. 완화: v0.3으로 미루고, content module이라 쓰지 않으면 클래스 로드 0.
- **R8 초안의 정확도.** springdoc는 런타임에 Jackson 설정, `@JsonProperty`, 커스텀 `OpenApiCustomizer`까지 반영한다. 정적 초안은 이를 모른다. 완화: 초안을 "draft"로 명시하고, v0.2에서 실행 중 앱의 스펙과 diff를 제공한다.
- **R9 커뮤니티 microservices API 안정성.** `UrlResolverFactory.EP_NAME`은 `@ApiStatus.Internal`이다 **[V]**. 포크라서 깨지면 우리가 고칠 수 있지만, 업스트림 갱신 때 확인해야 한다.

### 남은 의문

- **Q1** phantom-http가 실행기 확장 지점(`phantom.http.requestExecutor`)과 `WEBSOCKET`/`GRAPHQL`/`GRPC` 메서드 토큰을 받아들일 수 있는가. 렉서 상태 `REQUEST_LINE`에서 메서드 토큰을 닫힌 목록으로 두었다면 변경이 필요하다.
- **Q2** 클라이언트 `baseUrl`에 context path가 들어 있는 경우, phantom-spring-boot가 서버 쪽 `server.servlet.context-path`를 `UrlTargetInfo.path`에 이미 붙여 주는가. 붙인다면 접미 매칭(3단계)이 오히려 오탐을 낼 수 있다.
- **Q3** 실행 중 앱의 포트를 phantom-spring-boot 실행 구성에서 얻을 수 있는가(phantom-http Q4와 같은 질문).
- **Q4** `protobuf-java-util`(`JsonFormat`)과 `grpc-inprocess`가 커뮤니티 라이브러리 모듈에 들어 있는가. `libraries/protobuf/module-content.yaml`에는 `protobuf-java`만 보인다 **[V]**. Bazel 캐시에 `protobuf-java-util-*.jar`와 `grpc-inprocess-1.65.1.jar`가 있지만 어느 모듈이 쓰는지는 확인하지 않았다 **[V: 캐시 존재, A: 모듈 소속]**.
- **Q5** 사용자가 정말 gRPC와 protobuf 플러그인을 쓰는가. 이 프로젝트에는 `.proto`가 없다 **[V]**. 다른 프로젝트 기준이라면 v0.3 우선순위를 사용자에게 다시 묻는다.
- **Q6** 미리보기 렌더러를 자체 HTML로 만들 때 사용자가 Swagger UI 수준의 "Try it out"을 기대하는가. 기대한다면 phantom-http 연동(v0.3)이 그 자리를 채운다는 점을 사용자와 합의해야 한다.
