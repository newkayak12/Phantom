# phantom-http: `.http` 파일용 HTTP Client 설계

- 날짜: 2026-10-16
- 대상: Phantom IDEA (intellij-community `idea/262.10968.63` 포크), Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자
- 상태: 설계 초안 (구현 전)
- 표기: **[V]** = 이 저장소에서 경로·API가 있는지 직접 확인함, **[A]** = 가정 (공개 문서·일반 지식에 근거하고 아직 확인하지 않음), **[M]** = Ultimate 분석 노트의 측정값 인용

## 0. Clean-room 선언

이 설계는 다음 자료만 썼다.

1. Ultimate의 동작·비용 기술 노트: `Intellij-rea/reversed/README.md`, `03-decompiled/2026-10-16-ult-other-plugins.md` §2.3, `2026-10-16-ult-web-js.md` §4–5, `02-runtime/2026-10-16-startup-jfr.md`
2. Ultimate `restClient` 플러그인의 **디스크립터**(`restClient.jar!/META-INF/plugin.xml`). 확장 태그 종류, 액션 id, 파일 타입 이름, content module 이름만 기능 체크리스트로 썼다.
3. 이 저장소(커뮤니티 소스)의 공개 API
4. JetBrains HTTP Client 공개 문서(요청 파일 문법, 환경 파일, 응답 핸들러 API), RFC 9110/9112, Spring Framework 문서

`reversed/source/` 아래 파일은 열지 않았다. 디컴파일한 코드는 보지도, 옮기지도 않았다. 라이선스·인증 코드는 범위 밖이다.

---

## 1. 목표와 범위

### 1.1 사용자가 매일 하는 일 (근거)

사용자 프로젝트 `~/orca/workspaces/prototype-reservation-system`에서 확인한 내용이다 **[V]**.

- 워크트리마다 `adapter-module`에 Spring MVC 컨트롤러가 있다. 매핑 애너테이션은 `@GetMapping` 56, `@PostMapping` 36, `@PatchMapping` 20, `@PutMapping` 16, `@DeleteMapping` 4개다. 워크트리 4개를 합친 숫자이고, 클래스 수준 `@RequestMapping`은 없다.
- 경로는 문자열 리터럴이 아니라 **Kotlin 상수 + 문자열 템플릿**이다. 예: `HolidayUrl.CREATE = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"`. 경로 변수에 **정규식**이 붙어 있다.
- `@RequestBody @Valid`(JSON), `@RequestPart(name = "request")` + `consumes = MULTIPART_FORM_DATA_VALUE`(multipart), `@RequestHeader header: HttpHeaders`(인증 헤더)를 쓴다.
- `auth-server-module`이 따로 있다. 로그인으로 토큰을 받은 뒤 다른 요청에 넣는 흐름이 필요하다 **[A]**.
- 지금은 프로젝트에 `.http` 파일이 없다. 그러니 "기존 파일 호환"보다 **"컨트롤러에서 요청을 바로 만들고 실행"**하는 흐름이 더 중요하다.

### 1.2 MVP (v0.1)

| # | 기능 | 설명 |
|---|---|---|
| F1 | `.http`/`.rest` 언어 | 렉서, 파서, PSI, 하이라이트, 괄호·따옴표 짝, 주석 토글, `###` 블록 접기, 구조 뷰(요청 목록) |
| F2 | 요청 문법 | 요청 줄(`METHOD target [HTTP/1.1\|HTTP/2]`), 메서드 생략 시 GET, 여러 줄 query 연속(`  ?a=1`, `  &b=2`), 헤더, 빈 줄 뒤 본문, `< ./file` 본문 포함, `multipart/form-data` 경계 파트, `###` 구분자와 이름(`### Login`), `# @name`, 주석 `#`·`//` |
| F3 | 지시어 | `# @no-redirect`, `# @no-cookie-jar`, `# @timeout <sec>`, `# @connection-timeout <sec>`, `# @no-log` |
| F4 | 변수 | `{{var}}`, 파일 변수 `@var = value`, 동적 변수 `{{$uuid}}`, `{{$timestamp}}`, `{{$isoTimestamp}}`, `{{$randomInt}}`, 환경 변수 `{{$env.NAME}}` **[A: `$env.` 이름은 공개 문서 기준]**. 미해결 변수 경고, 완성, 이동(Ctrl+B로 env 파일의 정의로) |
| F5 | 환경 파일 | `http-client.env.json`(공용), `http-client.private.env.json`(비공개, 같은 키면 이 값이 이긴다), `$shared` 블록. 에디터 툴바 콤보로 환경 선택. JSON Schema로 env 파일 완성 |
| F6 | 실행 | 요청 줄 옆 Run 거터, `Ctrl+Enter`(실행), "Run All Requests in File". 취소 가능, 진행 표시 |
| F7 | 응답 뷰어 | `HTTP Client` 툴 창의 탭. 상태 줄(코드, 이유, 소요 ms, 크기), 헤더 표, 본문(JSON/XML/HTML/text는 하이라이트, JSON은 pretty print + 접기). 이미지 미리보기. 큰 본문은 앞부분만 보여 주고 "파일로 열기" |
| F8 | 응답 핸들러 (핵심 부분집합) | `> {% … %}`와 `> ./handler.js`. `client.global.set/get/clear`, `client.test`, `client.assert`, `client.log`, `response.status/headers/body/contentType`. **로그인 → 토큰 저장 → 다음 요청에서 `{{token}}`** 흐름을 MVP에서 지원 |
| F9 | 히스토리와 쿠키 | 프로젝트별 최근 50개 요청·응답. "Show HTTP Requests History"로 열람·재실행. 쿠키 저장(요청 단위 끄기는 F3) |
| F10 | Spring 연동 | (a) 컨트롤러 메서드에서 "Open in HTTP Client"로 요청 생성(경로 변수 `{{id}}`, JSON 본문 뼈대, multipart 파트, `@RequestHeader` 자리), (b) `.http` 요청 URL 경로에서 Ctrl+B로 핸들러 메서드 이동, 경로 완성 |
| F11 | 복사 | "Copy as cURL" |

### 1.3 v0.2 이후

- v0.2: cURL 붙여넣기 변환(`ConvertCurlToHttpRequestAction`에 해당), 포매터·코드 스타일, 요청 위 인레이로 마지막 결과(`200 OK · 34 ms`) 표시, 응답 비교(diff), `>> file` / `>>! file` 응답 저장, 실행 구성(Run Configuration) 타입 "HTTP Request"로 Run 툴바 재실행·before-launch, 사전 요청 스크립트(`< {% %}`), `$auth`/SSL 설정(비공개 env의 클라이언트 인증서), Postman collection 가져오기
- v0.3: OpenAPI 스펙에서 요청 생성, `phantom-spring-boot` Endpoints 뷰 안의 "HTTP 요청" 패널, 응답 JSON에 JSONPath 질의(`plugins/jsonpath` 재사용 **[V]**)
- 후순위: WebSocket, Server-Sent Events 스트리밍 표시

### 1.4 명시적 비목표

- **gRPC, GraphQL**: Ultimate는 `intellij.httpClient.grpc` 모듈과 `grpc-netty` 계열, protobuf 플러그인 의존을 가진다 **[M]**. 백엔드 REST 사용자에게 필요 없다. 넣지 않는다.
- **JS 언어 플러그인 연동**: 핸들러 스크립트 안의 JS 완성·검사는 하지 않는다. Ultimate에서는 `.http` 핸들러 때문에 JS 플러그인 전체(클래스 ~4.3k, Metaspace ≥23 MB **[M]**)를 끌 수 없었다. 이 구조를 피하는 것이 목적이다. 스크립트는 구문 하이라이트(TextMate 또는 간단한 렉서) 정도만 한다.
- **JCEF HTML 미리보기**: JCEF 프로세스를 띄우지 않는다. HTML은 텍스트로 보이고 "브라우저에서 열기"만 둔다.
- **Ultimate 파일·설정 100% 호환**: 문법은 공개 스펙을 따르지만, 미지원 구문은 파싱 오류 대신 "지원 안 함" 경고로 표시한다.
- **Retrofit/OkHttp/Ktor 클라이언트 코드 → `.http` 변환**: Ultimate 디스크립터에는 `okhttp.minimal`, `retrofit.minimal`, `ktor.client.minimal` 모듈이 있다. 사용자 프로젝트에 해당 코드가 없으므로 뺀다.
- 라이선스·계정 연동(디스크립터의 `http.client.always.require.jba` 류) 일체.

---

## 2. Ultimate 대비 경량화 원칙

| Ultimate에서 측정·관측된 비용 | 이 설계의 대응 |
|---|---|
| `restClient` 배포 크기 122 MB, 그중 **GraalJS 86.2 MB**, `reactor-netty-http` 8.2 MB, gRPC 모듈 **[M]** | 스크립트 엔진은 커뮤니티에 이미 있는 **Rhino**(`libraries/rhino`, `intellij.libraries.rhino` **[V]**)를 쓴다. 전송은 JDK `java.net.http.HttpClient`다. 새 대형 라이브러리를 추가하지 않는다. 목표 배포 크기는 플러그인 jar 2 MB 미만 **[A]** |
| 시작 시 로드 클래스 309개, Metaspace 1.4 MB, 코드 스타일 설정 초기화 1샘플 **[M]** | 시작 경로에 아무것도 두지 않는다: `postStartupActivity`/`ProjectActivity` 0개, `projectService`는 처음 쓸 때 생성, `CodeStyleSettingsProvider`는 MVP에 없음. 목표 시작 로드 클래스 < 60개 (파일 타입, 아이콘, 액션 스텁) **[A]** |
| `FileBasedIndex` 4개(`HttpRequestEnvironmentIndex`, `HttpRequestHostIndex`, `HttpRequestHeadersValuesIndex`, `HttpRequestNameIndex`) **[M]** | **새 인덱스 0개.** env 파일은 `.http` 파일 디렉터리에서 content root까지 위로 올라가며 찾는다. 결과는 디렉터리별로 캐시하고 VFS 이벤트로 무효화한다. 요청 이름은 같은 파일 안에서만 찾는다(PSI 순회). 호스트·헤더 값 완성은 히스토리와 `HttpHeadersDictionary`(커뮤니티 **[V]**)에서 가져온다 |
| 시작 직후 **"File type change" 전체 무효화 2회** → 약 50만 파일 재스캔, 사용 가능 시점 5.5 s → 16.2 s **[M]** (트리거 미확인) | 파일 타입은 `plugin.xml`의 `<fileType extensions="http;rest">`로 **정적으로만** 등록한다. 런타임에 연관을 바꾸지 않고 `FileTypeOverrider`도 쓰지 않는다. env 파일은 새 파일 타입을 만들지 않고 JSON 그대로 둔다. 스키마만 `JsonSchemaProviderFactory`로 붙인다 |
| 응답·히스토리를 프로젝트 안(`.idea/httpRequests/` 류)에 쓰면 VFS 갱신과 파일 감시 대상이 된다 **[A]** | 응답 본문과 히스토리는 `PathManager.getSystemDir()/phantom-http/<projectLocationHash>/`에 쓴다. content root 밖이므로 인덱싱과 VFS refresh 대상이 아니다. 열 때만 `LocalFileSystem.refreshAndFindFileByNioFile`로 한 파일을 가져온다 |
| 콘텐츠 모듈이 "프로젝트가 그 기술을 쓰는지"와 상관없이 로드된다 **[M]** | Spring 연동은 별도 optional content module에 둔다. 그 안의 확장도 진입 시점마다 "이 프로젝트에 Spring Web이 있는가"(클래스 `org.springframework.web.bind.annotation.RequestMapping` 존재 여부, `ProjectRootModificationTracker`로 캐시)를 먼저 확인한다. 없으면 아무 일도 하지 않는다 |
| `grpc-nio-worker-ELG-*` 스레드 4개 상주(소유자 미확인) **[M]** | 상주 스레드 0개. `HttpClient`는 첫 실행 때 만들고, 실행기는 공용 `AppExecutorUtil` 기반 bounded executor를 쓴다. 60초 동안 쓰지 않으면 `HttpClient.close()`(JDK 21+)로 닫는다 |
| Spring 모델 캐시가 파일 하나만 바뀌어도 프로젝트 전체 무효화(추정) **[M]** | phantom-http는 Spring 모델을 직접 만들지 않는다. `phantom-spring-boot`가 제공하는 `UrlResolver`를 요청 시점에만 호출한다. 생성 결과(JSON 뼈대)는 캐시하지 않는다. 한 번 만들고 끝나는 작업이기 때문이다 |
| `ActionUpdater … to grab EDT` 경고 75회 **[M]** | 모든 액션은 `getActionUpdateThread() = BGT`로 둔다(`ActionUpdateThread` **[V]**). `update()`에서 PSI만 가볍게 본다 |
| 메인 스레드 동기 HTTPS 호출 1.3–1.8 s **[M]** | 네트워크는 오직 사용자가 실행을 눌렀을 때만 한다. EDT에서 네트워크·파일 I/O를 하지 않는다 |

요약 원칙:

1. **쓸 때만 존재한다.** `.http` 파일을 열거나 Spring 컨트롤러에서 액션을 부르기 전에는 클래스가 거의 로드되지 않는다.
2. **증분 모델.** env 파일 하나가 바뀌면 그 파일로 만든 캐시만 버린다. 파일 변수는 PSI `CachedValue`에 파일 단위로 둔다.
3. **프로젝트 전역 무효화 없음.** `PsiModificationTracker.MODIFICATION_COUNT` 같은 전역 트래커에 캐시를 걸지 않는다. 파일 단위 의존(`PsiFile` 자체)이나 env 파일 전용 `SimpleModificationTracker`를 쓴다.
4. **EDT에서 일하지 않는다.** 아래 §3.6 참고.

---

## 3. 아키텍처

### 3.1 모듈 배치

```
plugins/phantom-http/
├── phantom.http.iml             # 플러그인 메인 모듈 (plugin.xml만)
├── resources/META-INF/plugin.xml
├── plugin-content.yaml
├── core/                                 # content module: phantom.http.core (required)
│   ├── src/main/grammars/HttpRequest.flex, HttpRequest.bnf
│   ├── gen/…                             # JFlex·Grammar-Kit 생성 코드 (커밋)
│   └── src/com/phantom/http/
│       ├── lang/      HttpRequestLanguage, HttpRequestFileType, ParserDefinition, highlighter, brace matcher, commenter, folding, structure view
│       ├── psi/       HttpRequestFile, HttpRequestBlock, HttpRequestLine, HttpHeaderField, HttpBody, HttpMultipartPart, HttpVariableRef, HttpFileVariable, HttpDirective, HttpResponseHandler
│       ├── env/       HttpEnvironmentFiles, HttpEnvironment, HttpEnvironmentSelector(툴바 콤보), JSON schema provider
│       └── vars/      HttpVariableResolver, DynamicVariables, completion·reference·inspection
├── exec/                                 # content module: phantom.http.exec (required, 지연 로드 대상 클래스)
│   └── src/com/phantom/http/exec/
│       ├── HttpRequestModel.kt           # PSI와 무관한 불변 요청 스냅샷
│       ├── HttpRequestBuilder.kt         # PSI → 모델 (read action 안)
│       ├── HttpExecutor.kt               # java.net.http 전송, 취소, 타임아웃, 쿠키
│       ├── HttpExecutionService.kt       # @Service(PROJECT), CoroutineScope 주입
│       ├── history/                      # HttpHistoryStore (system dir JSON)
│       └── ui/                           # RunLineMarkerContributor, 툴 창, 응답 패널, 액션
├── script/                               # content module: phantom.http.script (optional, rhino 의존)
│   └── src/com/phantom/http/script/      # RhinoResponseHandlerRunner, client/response 바인딩
└── spring/                               # content module: phantom.http.spring (optional)
    └── src/com/phantom/http/spring/      # UrlPathInlayAction, RequestNavigator, 요청 생성기, URL 참조 주입
```

- 레이아웃은 커뮤니티 `plugins/toml`의 방식(`core/src/main/grammars/*.flex|*.bnf`와 `core/src/gen/` 생성 코드 커밋, `plugin-content.yaml`, `.iml`, 자동 생성 `BUILD.bazel`)을 따른다 **[V]**: `plugins/toml/core/src/main/grammars/TomlParser.bnf`, `plugins/toml/core/src/gen/org/toml/lang/parse/TomlParser.java`, `plugins/toml/BUILD.bazel`의 `### auto-generated section` 표식.
- `BUILD.bazel`은 `.iml`에서 생성되는 구조로 보인다 **[A]** (`jps-to-bazel` 생성기 위치는 확인하지 않음).
- 번들 등록: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`의 `productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf(...)`(61행)에 `"phantom.http"`를 추가한다 **[V]**. 여러 content module을 가진 플러그인이라 `CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS`에 `pluginLayout`이 필요할 수 있다 **[A]**.
- 패키지 접두사 `com.phantom.http`는 JetBrains 패키지(`com.intellij.httpClient`)와 겹치지 않게 고른 것이다.

#### plugin.xml 뼈대 (설계)

```xml
<idea-plugin package="com.phantom.http">
  <id>phantom.http</id>
  <name>Phantom HTTP Client</name>
  <dependencies>
    <plugin id="com.intellij.modules.lang"/>
    <module name="intellij.json.backend"/>      <!-- 응답 JSON 하이라이트, env 스키마 -->
  </dependencies>
  <content>
    <module name="phantom.http.core" loading="required"/>
    <module name="phantom.http.exec" loading="required"/>
    <module name="phantom.http.script"/>   <!-- depends: intellij.libraries.rhino -->
    <module name="phantom.http.spring"/>   <!-- depends: com.intellij.java, intellij.platform.uast -->
  </content>
</idea-plugin>
```

- `intellij.json.backend`는 Ultimate 디스크립터의 의존 목록에도 있다. 커뮤니티 경로는 `json/backend/` **[V]**.
- `spring` 모듈은 Kotlin 플러그인에 직접 의존하지 않는다. UAST로만 코드를 읽으므로 Kotlin 플러그인이 있으면 K2 UAST(`plugins/kotlin/uast/uast-kotlin-fir` **[V]**)가 자동으로 붙는다. 그래서 `supportsKotlinPluginMode` 선언(EP 정의 `plugins/kotlin/plugin/resources/META-INF/plugin.xml:30` **[V]**)은 필요 없을 것으로 본다 **[A]**. Kotlin 전용 API를 쓰게 되면 그때 `supportsK2="true"`로 선언한다.

### 3.2 사용하는 커뮤니티 확장 지점과 API

| 용도 | EP / API | 경로 | 확인 |
|---|---|---|---|
| 파일 타입·언어 | `fileType`, `LanguageFileType` | `platform/core-api/src/com/intellij/openapi/fileTypes/LanguageFileType.java` | V |
| 렉서 | JFlex + `FlexAdapter` | `platform/core-impl/src/com/intellij/lexer/FlexAdapter.java` | V |
| 파서 | Grammar-Kit 생성 + `GeneratedParserUtilBase` | `platform/analysis-impl/src/com/intellij/lang/parser/GeneratedParserUtilBase.java` | V |
| 실행 거터 | `runLineMarkerContributor`, `RunLineMarkerContributor` | `platform/execution-impl/src/com/intellij/execution/lineMarker/RunLineMarkerContributor.java` | V |
| 응답 툴 창 | `toolWindow`, `ToolWindowFactory`, `ContentFactory` | `platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt`, `platform/ide-core/src/com/intellij/ui/content/ContentFactory.java` | V |
| 헤더 이름·값 사전, 헤더 문서 | `HttpHeadersDictionary`, `HttpHeaderDocumentation` | `platform/lang-impl/src/com/intellij/microservices/http/HttpHeadersDictionary.java` | V |
| MIME 타입 | `MimeTypes` | `platform/lang-api/src/com/intellij/microservices/mime/MimeTypes.java` | V |
| env 파일 JSON 스키마 | `JsonSchemaProviderFactory`, `JsonSchemaFileProvider` | `json/backend/src/com/jetbrains/jsonSchema/extension/` | V |
| 응답 JSON 질의(v0.3) | JSONPath 언어 | `plugins/jsonpath/` | V |
| 핸들러 스크립트 엔진 | Rhino 라이브러리 모듈 | `libraries/rhino/module-content.yaml` (`intellij.libraries.rhino`) | V |
| 프록시 | `IdeProxySelector`, `JdkProxyProvider`, `ProxyAuthentication` | `platform/platform-api/src/com/intellij/util/net/` | V |
| 인증서 신뢰 | `CertificateManager` | `platform/platform-api/src/com/intellij/util/net/ssl/CertificateManager.kt` | V |
| 비공개 env 값을 키체인에(v0.2) | `PasswordSafe` | `platform/credential-store/src/ide/passwordSafe/PasswordSafe.kt` | V |
| 히스토리 가상 파일 | `LightVirtualFile` | `platform/core-api/src/com/intellij/testFramework/LightVirtualFile.java` | V |
| 스크래치에서 새 요청 | `ScratchRootType`, `ScratchFileService` | `platform/lang-impl/src/com/intellij/ide/scratch/ScratchRootType.java` | V |
| 캐시 | `CachedValuesManager` | `platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java` | V |
| 경로 해석(Spring 쪽이 등록) | `com.intellij.microservices.urlResolverFactory`, `UrlResolverManager`, `UrlTargetInfo`, `UrlPath.PathSegment.Variable(name, regex)` | `platform/lang-api/src/com/intellij/microservices/url/` (EP 선언 `platform/platform-resources/src/META-INF/LangExtensionPoints.xml:745`) | V |
| 컨트롤러 URL 인레이 액션 | `com.intellij.microservices.urlInlayAction`, `UrlPathInlayAction` | `platform/lang-api/src/com/intellij/microservices/url/inlay/UrlPathInlayAction.kt` (EP `LangExtensionPoints.xml:747`) | V |
| Endpoints 뷰 → 요청 열기 | `com.intellij.microservices.requestNavigator`, `RequestNavigator`, `NavigatorHttpRequest(url, method, headers, params)` | `platform/lang-api/src/com/intellij/microservices/http/request/` (EP `LangExtensionPoints.xml:751`, 기본 구현 `DefaultRequestNavigator`는 `order="last"`로 `LangExtensions.xml:1745`에 등록) | V |
| `.http` URL → 컨트롤러 이동·완성 | `UrlPathReferenceInjector`, `UrlPathContext` | `platform/lang-api/src/com/intellij/microservices/url/references/` | V |
| Kotlin 문자열 템플릿 + 상수 평가 | `UStringConcatenationsFacade`, `PartiallyKnownString` | `uast/uast-common-ide/src/org/jetbrains/uast/expressions/UStringConcatenationsFacade.kt`, `platform/lang-api/src/com/intellij/psi/util/PartiallyKnownString.kt` | V |
| UAST 공통 | `UastContext`, `UastUtils` | `uast/uast-common/src/org/jetbrains/uast/` | V |
| 액션 갱신 스레드 | `ActionUpdateThread.BGT` | `platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionUpdateThread.java` | V |
| 에디터 상단 알림(미지원 구문) | `EditorNotificationProvider` | `platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java` | V |

Ultimate 디스크립터와 비교해 **쓰지 않는** 것: `fileBasedIndex` 4개, `stubElementRegistryExtension`, `semContributor` 2개, `multiHostInjector` 4개(JS·GraphQL 주입용으로 추정 **[A]**), `highlightingPassFactory` 2개, `runDashboardCustomizer`, `predefinedLibraryProvider`(JS 타입 정의), `microservices.endpointsSidePanelProvider`(v0.3로 미룸), `scratch.creationHelper`(v0.2).

### 3.3 파일 형식 (공개 스펙 기준)

```
# 파일 변수                                   ← @name = value (요청 블록 밖)
@host = http://localhost:8080

### Login                                     ← 구분자 + 요청 이름
# @no-log                                     ← 지시어
POST {{host}}/api/v1/auth/login               ← 요청 줄: METHOD target [HTTP-version]
Content-Type: application/json                ← 헤더 (name: value), 값에 {{var}}
                                              ← 빈 줄 = 헤더 끝
{ "email": "{{email}}", "password": "{{pw}}" }  ← 본문 (또는 < ./body.json)

> {%                                          ← 응답 핸들러
  client.global.set("token", response.body.accessToken);
%}

###
GET {{host}}/api/v1/restaurants
    ?page=0
    &size=20
Authorization: Bearer {{token}}
```

렉서 상태(JFlex): `YYINITIAL`(블록 사이), `REQUEST_LINE`, `QUERY_CONTINUATION`, `HEADERS`, `BODY`, `MULTIPART`, `HANDLER_SCRIPT`(`{% … %}` 원문 토큰), `VARIABLE`(`{{` … `}}`, 어느 상태에서든 중첩 진입). 본문은 의미 분석을 하지 않고 하나의 토큰 범위로 둔다. JSON 본문 하이라이트는 v0.1에서 `Content-Type`을 보고 JSON lexer를 **하이라이트 레이어로만** 씌운다(`LayeredLexer` 방식 **[A]**, 언어 주입보다 가볍다). 주입(`MultiHostInjector`)은 JSON 본문 안 완성이 필요해질 때 v0.2에서 검토한다.

PSI 트리(Grammar-Kit `.bnf`의 최상위 규칙):

```
file        ::= (file_variable | request_block | separator | comment)*
request_block ::= separator? directive* request_line header_field* body? response_handler* response_redirect*
request_line ::= method? request_target http_version?
request_target ::= (url_part | variable_ref)+ query_continuation*
header_field ::= header_name ':' header_value
body        ::= body_text | input_file_ref | multipart_body
```

파서는 오류 복구가 중요하다. 사용자는 요청을 반쯤 쓴 상태에서도 다른 블록을 실행한다. 블록 경계(`###`)를 `recoverWhile`의 기준으로 삼아, 한 블록의 오류가 다음 블록으로 번지지 않게 한다.

### 3.4 데이터 모델

```kotlin
// exec/HttpRequestModel.kt — PSI 참조 없음, 스레드 간 전달 가능
data class HttpRequestModel(
  val name: String?,                  // ### 이름 또는 # @name
  val method: String,                 // 생략 시 "GET"
  val uri: URI,                       // 변수 치환, 쿼리 연속 줄 결합 후
  val version: HttpClient.Version?,   // HTTP/2 지정 시
  val headers: List<Pair<String, String>>,
  val body: BodySource,               // None | Text(String) | File(Path) | Multipart(List<Part>)
  val directives: Directives,         // noRedirect, noCookieJar, timeout, connectTimeout, noLog
  val handlers: List<ScriptSource>,   // Inline(text) | File(Path)
  val origin: Origin,                 // 파일 경로 + 블록 시작 offset (히스토리·재실행용)
)

data class HttpResponseModel(
  val status: Int, val reason: String?, val version: HttpClient.Version,
  val headers: Map<String, List<String>>,
  val bodyFile: Path,                 // system dir에 저장된 본문
  val bodyPreview: ByteArray,         // 앞 1 MiB까지
  val contentType: String?, val elapsedMs: Long, val size: Long,
  val handlerResults: List<TestResult>, val log: List<String>,
)
```

변수 해석 순서(공개 문서 기준 **[A]**): 핸들러가 설정한 전역 변수(`client.global`) → 비공개 env의 선택 환경 → 공용 env의 선택 환경 → `$shared`(비공개 → 공용) → 파일 변수 → 동적 변수. 해석하지 못한 변수는 실행 전에 막고, 어떤 변수가 없는지 알림으로 알린다.

### 3.5 캐싱과 무효화

| 캐시 | 키 | 무효화 | 위치 |
|---|---|---|---|
| 파일 변수 맵 | `HttpRequestFile` | 그 파일 PSI 변경 (`CachedValueProvider.Result.create(map, file)`) | PSI `CachedValue` |
| env 파일 위치 | `.http` 파일의 부모 디렉터리 | `BulkFileListener`에서 이름이 `http-client*.env.json`인 파일의 생성·삭제·이동 이벤트일 때만 해당 디렉터리 하위 항목 제거 | `HttpEnvironmentFiles` project service (지연 생성) |
| env 내용(파싱된 JSON) | env `VirtualFile` + `modificationStamp` | stamp 비교 (읽을 때 검사) | 같은 서비스 |
| 선택된 환경 | `.http` 파일 경로 | 사용자가 콤보에서 바꿀 때 | `PropertiesComponent`(프로젝트) |
| 전역 핸들러 변수 | 이름 | 프로젝트 닫기, "Clear Global Variables" 액션 | 메모리 + `system dir/…/globals.json` |
| Spring 경로 존재 여부 | 프로젝트 | `ProjectRootModificationTracker` | `CachedValuesManager.getManager(project)` |
| 히스토리 | 프로젝트 | 51번째 항목이 들어오면 가장 오래된 것 삭제 | `system dir/phantom-http/<hash>/history.json` + 본문 파일 |

- `BulkFileListener`는 `plugin.xml`의 `projectListeners`로 등록한다. 이벤트 콜백에서는 파일 이름 비교만 하고, 서비스가 아직 생성되지 않았으면 아무것도 하지 않는다(`project.serviceIfCreated<…>()` **[A: API 이름]**).
- 어떤 캐시도 `PsiModificationTracker.MODIFICATION_COUNT`나 `ProjectRootModificationTracker` 외의 전역 트래커에 걸지 않는다. Spring 경로 존재 여부만 root 트래커에 건다. 의존성 변경 때만 바뀌는 값이기 때문이다.

### 3.6 스레딩

| 단계 | 스레드 | 수단 |
|---|---|---|
| 거터 아이콘 결정 | 하이라이팅 백그라운드 | `RunLineMarkerContributor.getInfo` — 요청 줄 PSI 확인만 |
| 실행 클릭 → 모델 생성 | BGT | `readAction { HttpRequestBuilder.build(block) }` (코루틴 `readAction`, 쓰기에 양보) |
| env JSON 읽기 | BGT | 위 read action 안. 파일 I/O는 VFS 캐시된 내용 사용 |
| 전송·응답 수신 | IO | `HttpClient.sendAsync` + `Dispatchers.IO`. 본문은 `BodyHandlers.ofFile(tempPath)`로 디스크에 스트리밍 |
| 핸들러 스크립트 | 전용 단일 스레드(Rhino Context는 스레드 귀속) | `Dispatchers.IO.limitedParallelism(1)` **[A]**, 실행 시간 제한 10 s (`Context.setInstructionObserverThreshold` + 관측자에서 중단 **[A]**) |
| 응답 표시 | EDT | `withContext(Dispatchers.EDT)` 안에서 컴포넌트 교체만. JSON pretty print와 하이라이트 준비는 그 전에 BGT에서 |
| 히스토리 저장 | IO | 응답 표시 후 비동기 |

- 실행 서비스는 `@Service(Service.Level.PROJECT) class HttpExecutionService(private val project: Project, private val cs: CoroutineScope)` 형태다. 플랫폼이 주입하는 scope라서 프로젝트를 닫으면 진행 중인 요청이 취소된다 **[A: 생성자 주입 규약은 플랫폼 문서 기준]**.
- 진행 표시는 `withBackgroundProgress` **[A]**로 하고, 툴 창 탭의 "Stop" 버튼이 같은 `Job`을 취소한다.
- `HttpClient`는 app 수준 서비스가 프록시·SSL 설정의 해시를 키로 1개만 들고 있다. 설정이 바뀌면 새로 만든다. 실행 executor는 `AppExecutorUtil.createBoundedApplicationPoolExecutor("phantom-http", 4)` **[A]**로 상주 스레드를 만들지 않는다.

### 3.7 전송 계층 결정

`java.net.http.HttpClient`(JBR 25 내장)를 쓴다.

- 장점: HTTP/1.1, HTTP/2(ALPN), 비동기, 파일로 바로 스트리밍, 추가 의존성 0.
- 커뮤니티 lib에는 OkHttp 5.0.0-alpha.11(`lib/MODULE.bazel:1397` **[V]**), Ktor client CIO(`libraries/ktor/client-cio` **[V]**), Apache HttpClient 4(`libraries/http-client` **[V]**)도 있다. OkHttp는 alpha이고 Kotlin stdlib 버전 결합 위험이 있다. Ktor는 코루틴 친화적이지만 플러그인 classloader에 모듈 몇 개를 더 올린다. 따라서 대안으로만 둔다.
- `HttpRequests`(`platform/ide-core/src/com/intellij/util/io/HttpRequests.java` **[V]**)는 IDE 내부 다운로드용 URLConnection 래퍼다. 임의 메서드(PATCH)와 HTTP/2를 다루기 불편해 쓰지 않는다 **[A]**.
- 제약 **[A]**: JDK 클라이언트는 `Host`, `Connection`, `Content-Length`, `Expect`, `Upgrade` 같은 제한 헤더를 사용자가 설정하지 못하게 한다(`jdk.httpclient.allowRestrictedHeaders` 시스템 속성으로 일부 허용). 사용자가 이런 헤더를 쓰면 경고를 띄운다. 리다이렉트는 `Redirect.NORMAL`을 기본으로 하고 `@no-redirect`면 `NEVER`다. 쿠키는 `CookieManager`에 `CookieStore`를 붙여 시스템 디렉터리에 저장한다.
- 프록시는 `IdeProxySelector`를 `ProxySelector`로 넘기고, 인증은 `ProxyAuthentication`으로 `Authenticator`를 만든다. TLS는 `CertificateManager`의 `SSLContext`를 써서 IDE가 신뢰하는 인증서를 그대로 쓴다 **[A: 정확한 메서드 이름]**.

### 3.8 응답 핸들러 (script content module)

- 엔진: Rhino(`intellij.libraries.rhino`, 아티팩트 `rhino-runtime` **[V]**). 이 모듈은 `> {%`가 있는 요청이 실행될 때 처음 로드된다. ES2015 지원은 부분적이다(`let`, `const`, 화살표 함수는 1.7.14+에서 대부분 된다 **[A]**). 템플릿 리터럴 같은 미지원 문법은 오류 메시지로 알린다.
- 노출 객체(공개 문서의 핸들러 API 중 부분집합):
  - `client.global.set(name, value)`, `get`, `isEmpty`, `clear`, `clearAll`
  - `client.test(name, fn)`, `client.assert(cond, message)`, `client.log(text)`
  - `response.status`, `response.body`(JSON이면 파싱된 객체, 아니면 문자열), `response.headers.valueOf(name)`, `response.headers.valuesOf(name)`, `response.contentType.mimeType`, `response.contentType.charset`
  - `request.variables.get(name)` **[A]**
- 샌드박스: `ClassShutter`로 Java 클래스 접근을 막는다. `initSafeStandardObjects()`만 쓴다. `java.*` 패키지 접근, 파일·네트워크 접근은 없다.
- `client.test` 결과는 응답 탭의 "Tests" 서브탭에 통과·실패로 보인다.
- script 모듈이 없을 때(사용자가 끈 경우) 핸들러가 있는 요청은 전송은 하고 "핸들러 실행 안 됨" 경고를 띄운다.

### 3.9 Spring 연동 (spring content module)

**계약**: phantom-http는 Spring 모델을 만들지 않는다. `phantom-spring-boot`가 `com.intellij.microservices.urlResolverFactory`로 Spring MVC `UrlTargetInfo`를 제공한다고 가정한다 **[A: phantom-spring-boot 설계와 맞춰야 함]**. phantom-http는 커뮤니티 microservices API만 보고 컴파일한다. 두 플러그인 사이에 컴파일 의존이 없다.

1. **컨트롤러 → 요청 생성**
   - `microservices.urlInlayAction`에 `OpenInHttpClientInlayAction`을 등록한다. 컨트롤러 URL 인레이(커뮤니티 `UrlPathInlayHintsProviderFactory`가 그린다 **[V]**)의 메뉴에 "Open in HTTP Client"가 생긴다.
   - `microservices.requestNavigator`에 `HttpClientRequestNavigator`를 등록한다. `DefaultRequestNavigator`가 `order="last"`이므로 우리 것을 그 앞에 둔다 **[V]**. phantom-spring-boot의 Endpoints 뷰가 `RequestNavigator.getRequestNavigators(request)`를 부르면 우리 생성기로 온다.
   - 인레이가 꺼진 사용자를 위해 같은 동작을 인텐션(`Alt+Enter` → "Generate HTTP request")으로도 둔다. UAST `UMethod`에서 `UrlResolverManager.getInstance(project).getVariants(...)`를 부르고, `resolveToPsiElement()`가 이 메서드인 `UrlTargetInfo`를 고른다.
2. **요청 텍스트 만들기**
   - 경로: `UrlPath.segments`를 순회한다. `Exact`는 그대로 쓰고, `Variable(name, regex)`는 정규식을 버리고 `{{name}}`으로 바꾼다. 사용자 코드 `"$PREFIX/{id:[0-9a-fA-F\\-]{36}}"`는 `/api/v1/holiday/{{id}}`가 된다. `Composite`는 이어 붙이고, `Undefined`는 `{{TODO}}`로 둔다.
   - 메서드: `UrlTargetInfo.methods`. 비어 있으면 GET.
   - 호스트: `{{host}}`. `http-client.env.json`이 없으면 만들자고 제안한다. 기본값은 `application.yaml`의 `server.port`(없으면 8080)와 `server.servlet.context-path`다 **[A: phantom-spring-boot의 설정 모델이 이 값을 줄 수 있어야 함]**.
   - 본문: 메서드 파라미터 중 `@RequestBody`가 있으면 그 타입의 JSON 뼈대를 만든다. Kotlin data class는 primary constructor 파라미터를 순서대로 쓴다(UAST `UClass` → `UMethod.isConstructor`). 타입별 기본값은 `String` → `""`, 숫자 → `0`, `Boolean` → `false`, `List`/`Set` → `[]`, 중첩 클래스 → 재귀(깊이 3, 순환 방지), enum → 첫 상수, `LocalDate`/`LocalDateTime` → ISO 예시다. `@RequestPart`가 있으면 `multipart/form-data` 파트를 만든다(`name="request"`, JSON 파트 + 파일 파트 `< ./file`).
   - 헤더: `consumes`가 있으면 `Content-Type`, `produces`가 있으면 `Accept`. `@RequestHeader("X")`는 `X: {{X}}`, `HttpHeaders` 통째 파라미터는 `Authorization: Bearer {{token}}` 자리만 남긴다.
   - 쿼리: `@RequestParam`은 `?name={{name}}`, `required=false`면 주석 처리한다.
   - 출력 위치: 프로젝트에 `http/` 디렉터리가 있으면 `http/<ControllerName>.http`에 덧붙인다. 없으면 스크래치 파일 `generated-requests.http`에 쓴다. 기존 같은 요청(같은 메서드 + 경로)이 있으면 새로 쓰지 않고 그 위치로 이동한다.
3. **`.http` → 컨트롤러 이동, 경로 완성**
   - `psi.referenceContributor`로 `request_target`의 경로 부분에 `UrlPathReferenceInjector`가 만드는 참조를 붙인다. Ctrl+B는 `UrlTargetInfo.resolveToPsiElement()`(핸들러 메서드)로 간다. 완성 목록은 `getVariants`에서 온다. `{{host}}` 부분은 authority 자리로 취급하고 경로만 비교한다.
   - 이 참조는 해석이 비싸므로 **lazy reference**로 둔다. 하이라이팅 중에 해석하지 않고 Ctrl+B·완성·Find Usages 때만 해석한다. 해석되지 않는 경로를 경고하는 검사는 기본으로 끈다.
4. **Kotlin K2**: 생성기와 참조는 모두 UAST로 작성한다. 상수·템플릿 경로 평가는 phantom-spring-boot 쪽 `UrlTargetInfo`가 이미 해 둔 값을 쓴다. phantom-http가 직접 평가해야 하는 곳(인텐션에서 resolver가 없을 때의 폴백 메시지)은 `UStringConcatenationsFacade` + `PartiallyKnownString`을 쓴다 **[V: 경로]**. K1 전용 API(`KotlinType`, `BindingContext`)는 쓰지 않는다.

### 3.10 UI

- **툴 창** `HTTP Client`(id `PhantomHttpClient`): `plugin.xml`에 `doNotActivateOnStart="true"`로 선언하고, `ToolWindowFactory.shouldBeAvailable`은 `false`를 반환한다. 첫 실행 때 `ToolWindowManager.getToolWindow(...).setAvailable(true)`로 띄운다 **[A: 지연 노출 방법]**. 시작 화면에는 스트라이프 버튼이 생기지 않는다.
- 탭 하나 = 실행 하나. 최대 10개를 유지하고 고정(pin)한 탭은 남긴다. 탭 안에는 상태 줄, `Body | Headers | Tests | Log` 서브탭이 있다. Body 뷰어는 `EditorFactory.createViewer` + 응답 MIME에 맞는 `FileType`으로 하이라이트한다. 1 MiB가 넘으면 앞부분만 보이고 "전체 파일 열기"를 누르면 system dir의 본문 파일을 읽기 전용 에디터로 연다.
- **에디터 툴바**: `.http` 파일 에디터 상단에 환경 콤보(`No environment`, `dev`, `local` …), "Run All", "Add Request", "History" 버튼을 둔다. `EditorNotificationProvider`가 아니라 에디터 floating toolbar 또는 `TextEditorWithPreview` 없이 `FileEditorManagerListener`로 붙이는 방식 중 가벼운 쪽을 고른다(§6 의문 Q3).
- **히스토리**: `LightVirtualFile("http-requests-log.http")`을 만들어 최근 요청을 `.http` 문법으로 늘어놓는다. 각 요청 위 주석에 응답 본문 파일 경로가 있어 클릭으로 연다. 같은 파일에서 바로 다시 실행할 수 있다.

---

## 4. 구현 단계

effort는 한 사람 기준 일(day)이다. 총 MVP ≈ **25일**.

| # | 작업 | 일 | IDE에서 보이는 수용 확인 |
|---|---|---|---|
| T0 | 플러그인 뼈대: `plugins/phantom-http` 디렉터리, `.iml` 4개, `plugin.xml`, `plugin-content.yaml`, 번들 목록 추가, `./installers.cmd` 빌드 통과 | 1 | 빌드한 IDE의 Settings → Plugins에 "Phantom HTTP Client"(bundled)가 보인다. Help → Diagnostic Tools → Startup Performance에서 이 플러그인의 activity가 0개다 |
| T1 | 렉서(JFlex)·파서(Grammar-Kit)·PSI, `fileType`, 하이라이트, commenter, brace matcher, folding(`###` 블록), 구조 뷰 | 4 | 새 `api.http` 파일에 위 §3.3 예제를 붙여 넣으면 메서드·URL·헤더·변수·핸들러가 다른 색으로 보이고, Structure 창에 `Login`, `GET …/restaurants` 두 항목이 나온다. 반쯤 쓴 블록이 있어도 다음 블록이 정상 하이라이트된다 |
| T2 | 변수: 파일 변수, 동적 변수, env 파일 탐색·병합, 환경 콤보, `{{` 완성, Ctrl+B로 env 정의 이동, 미해결 변수 경고, env JSON 스키마 | 3 | `{{` 입력 시 env의 키와 `$uuid` 등이 완성 목록에 뜬다. env에 없는 `{{foo}}`에 노란 밑줄이 생긴다. `http-client.env.json`에서 최상위 키 아래 값 완성이 동작한다 |
| T3 | 전송: `HttpRequestBuilder`, `HttpExecutor`(java.net.http), 프록시·SSL, 쿼리 연속 줄, `< file` 본문, multipart, 지시어(F3), 취소 | 3 | 실행 테스트(§5.1)가 통과한다. 수동으로 `GET https://httpbin.org/get`이 동작하고 `# @timeout 1`을 붙인 `/delay/3` 요청이 타임아웃 오류로 끝난다 |
| T4 | Run 거터, `Ctrl+Enter`, Run All, `HTTP Client` 툴 창(상태 줄·Body·Headers), JSON pretty print | 3 | 요청 줄 왼쪽 ▶를 누르면 툴 창이 처음으로 나타나고 `200 OK · NN ms · N B`와 접을 수 있는 JSON이 보인다. 요청 중 Stop을 누르면 취소된다. 그동안 에디터 입력이 끊기지 않는다 |
| T5 | 히스토리(system dir), 쿠키 저장, "Show History", "Copy as cURL" | 2 | 같은 요청을 세 번 실행한 뒤 History를 열면 세 항목이 보이고, 그 안에서 ▶로 재실행된다. 프로젝트 폴더 안에 새 파일이 생기지 않는다(`git status` 깨끗). Copy as cURL 결과를 터미널에 붙여 넣으면 같은 응답이 온다 |
| T6 | script 모듈: Rhino 바인딩, `client.global`, `client.test/assert/log`, `response.*`, 샌드박스, Tests 서브탭 | 3 | 로그인 요청 핸들러가 `client.global.set("token", …)`을 하고, 다음 요청의 `{{token}}`이 그 값으로 바뀐다. `client.test("status", () => client.assert(response.status === 200))`가 Tests 탭에 초록색으로 뜬다. 핸들러가 없는 요청만 실행한 세션에서는 `jcmd VM.classloader_stats`에 Rhino 클래스가 없다 |
| T7 | spring 모듈: `UrlPathInlayAction`, `RequestNavigator`, 인텐션, 경로·본문·헤더·multipart 생성, `.http` 경로 참조(이동·완성) | 4 | 사용자 프로젝트 `CreateHolidayController.createHoliday`에서 "Open in HTTP Client"를 고르면 `POST {{host}}/api/v1/holiday/{{id}}` + `CreateHolidayRequest` JSON 뼈대가 생긴다. `CreateRestaurantController`는 multipart 파트 `request`로 생긴다. `.http`의 `/api/v1/holiday/…`에서 Ctrl+B를 누르면 `createHoliday`로 이동한다 |
| T8 | 마무리: 미지원 구문 알림, 제한 헤더 경고, 오류 메시지, 성능 측정(§5.3) | 2 | §5.3 측정표가 목표 안에 든다 |

선행 관계: T0 → T1 → T2 → T3 → T4 → (T5, T6 병렬) → T7 → T8. T7은 phantom-spring-boot의 `UrlResolverFactory` 구현이 먼저 있어야 한다. 그 전에는 단위 테스트에서 가짜 `UrlResolverFactory`를 등록해 진행한다.

---

## 5. 테스트 방법

### 5.1 자동 테스트 (커뮤니티 테스트 프레임워크)

| 대상 | 베이스 클래스 | 경로 | 내용 |
|---|---|---|---|
| 파서 | `ParsingTestCase` | `platform/testFramework/src/com/intellij/testFramework/ParsingTestCase.java` **[V]** | `testData/parser/*.http` → `.txt` PSI 덤프. 요청 줄 변형, 메서드 생략, 쿼리 연속, multipart, 핸들러, 오류 복구(반쯤 쓴 블록) 25개 이상 |
| 렉서 | `LexerTestCase` **[A: 경로 미확인]** | — | 상태 전이, `{{` 중첩, `{% %}` 안의 `%}` 문자열 |
| 하이라이트·완성·이동·검사 | `BasePlatformTestCase` | `platform/testFramework/src/com/intellij/testFramework/fixtures/BasePlatformTestCase.java` **[V]** | `myFixture.configureByFiles("a.http", "http-client.env.json")` → `completeBasic`, `checkHighlighting`, `GotoDeclaration` |
| env 병합 순서 | 순수 단위 테스트 | — | 비공개가 공용을 이기는지, `$shared` 폴백 |
| 전송 | `BasePlatformTestCase` + JDK `com.sun.net.httpserver.HttpServer` **[A: JBR에 `jdk.httpserver` 모듈 포함]** | — | 임의 포트 서버로 메서드·헤더·본문·multipart 경계·리다이렉트·쿠키·타임아웃·취소 검증. MockWebServer는 lib에 없다 **[V: 검색 결과 없음]**. 커뮤니티의 `BuiltInServerTestCase`(`platform/built-in-server/testSrc/org/jetbrains/ide/BuiltInServerTestCase.kt` **[V]**)를 대안으로 쓸 수 있다 |
| 핸들러 | 순수 단위 테스트 | — | Rhino 바인딩, 샌드박스(`java.lang.System` 접근 시 실패), 무한 루프 10 s 중단 |
| Spring 생성 (Java) | `LightJavaCodeInsightFixtureTestCase` **[A: 경로 미확인]** | — | 가짜 `@PostMapping`, `@RequestBody`, `@PathVariable` 스텁 클래스 + 가짜 `UrlResolverFactory`로 생성 텍스트 비교 |
| Spring 생성 (Kotlin, K2) | `KotlinLightCodeInsightFixtureTestCase` | `plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/KotlinLightCodeInsightFixtureTestCase.kt` **[V]** | `object HolidayUrl { const val CREATE = "$PREFIX/{id:…}" }` 형태를 재현해 `{{id}}`가 나오는지. K2 모드로 실행하는 설정(시스템 속성)은 **[A]** |

### 5.2 사용자 프로젝트 수동 확인 (`~/orca/workspaces/prototype-reservation-system`)

1. `compose.yaml`로 MySQL·Redis를 띄우고 `./gradlew :adapter-module:bootRun`으로 서버를 띄운다 **[A: 실행 모듈 이름]**.
2. Phantom IDEA로 프로젝트를 연다. 첫 smart mode 이후 `idea.log`에서 `Invalidating all indexing flags`가 phantom-http 때문에 늘지 않았는지 본다(플러그인 끈 상태와 비교).
3. `GeneralUserSignUpController.signUp`에서 "Open in HTTP Client" → `http-client.env.json` 생성 제안을 수락 → `local` 환경 선택 → 실행 → 응답 `BooleanResponse` JSON 확인.
4. 로그인 요청(auth-server 쪽)에 핸들러로 토큰을 저장하고, `CreateRestaurantController`의 multipart 요청에 `Authorization: Bearer {{token}}`을 넣어 실행한다. 서버 로그에 multipart 파트 `request`가 들어왔는지 본다.
5. `.http`의 `/api/v1/holiday/{{id}}`에서 Ctrl+B → `CreateHolidayController.createHoliday`로 이동한다.
6. 워크트리 4개가 열려 있을 때 같은 경로가 여러 개로 해석되면 선택 팝업이 뜨는지 본다.

### 5.3 경량성 측정 (Ultimate 분석과 같은 방법)

| 지표 | 측정 | 목표 |
|---|---|---|
| 시작 activity | Startup Performance 덤프 | phantom-http 0개 |
| 시작 직후 로드 클래스 | `jcmd <pid> VM.classloader_stats`, 분석 저장소의 `loaded-classes-by-plugin.json`과 같은 집계 | < 60 (Ultimate restClient 309 **[M]**) |
| `.http` 열고 1회 실행 후 | 같은 방법 | < 400, Rhino 미로드 |
| 유휴 상주 스레드 | `jcmd Thread.print`에서 `phantom-http` 접두사 | 실행 60 s 후 0개 |
| 인덱스 | `Indexing diagnostics` | phantom-http 소유 인덱스 0개 |
| 배포 크기 | `plugins/phantom-http/lib` | < 2 MB (Ultimate 122 MB **[M]**) |
| EDT | 실행 중 `ActionUpdater … to grab EDT` 경고 수 | 플러그인 끈 상태 대비 증가 없음 |

---

## 6. 리스크와 남은 의문

### 리스크

- **R1 Spring 연동이 phantom-spring-boot에 매달린다.** `UrlTargetInfo`가 Kotlin 상수 템플릿 경로, 정규식 경로 변수, HTTP 메서드를 정확히 주지 못하면 T7의 생성·이동이 동작하지 않는다. 완화: T7 시작 전에 phantom-spring-boot 설계자와 `UrlTargetInfo` 필드 계약(특히 `methods`, `PathSegment.Variable.regex`, `resolveToPsiElement`)을 합의한다. 계약 테스트를 양쪽 저장소에 둔다.
- **R2 microservices API 안정성.** `UrlPathInlayActionService`는 `@ApiStatus.Internal`이다 **[V]**. 우리가 쓰는 `UrlPathInlayAction`, `RequestNavigator`, `UrlResolverFactory`는 Internal 표시가 없지만, 업스트림이 바뀔 수 있다. 포크라 버전이 고정돼 당장은 문제가 아니다. 업스트림 리베이스 때 깨질 수 있다.
- **R3 Rhino 호환성.** 사용자가 Ultimate에서 쓰던 핸들러 스크립트(ES2020 문법, `jsonPath()` 같은 전역 함수 **[A]**)가 Rhino에서 안 돌 수 있다. 완화: 미지원 문법은 정확한 위치와 함께 오류로 보이고, 필요해지면 v0.2에서 엔진을 플러그형으로 바꾼다(GraalJS 별도 다운로드).
- **R4 JDK HttpClient 제한 헤더와 동작 차이.** `Host` 덮어쓰기, `Content-Length` 수동 지정이 안 된다. HTTP/2 업그레이드 동작도 curl과 다르다. 완화: 경고 + 필요 시 Ktor CIO를 대체 전송으로 둘 수 있게 `HttpTransport` 인터페이스를 둔다.
- **R5 문법 호환성 범위.** 공개 문서에 없는 세부(예: 블록 사이 공백 처리, 쿼리 연속 줄의 정확한 들여쓰기 규칙)는 추측으로 구현한다. Ultimate가 만든 `.http`가 다르게 해석될 수 있다. 완화: 예제 파일 묶음을 테스트 데이터로 모으고, 불일치는 이슈로 기록한다.
- **R6 multipart 대용량 파일.** `< ./big.bin` 파트를 메모리에 올리면 힙이 커진다. 완화: `BodyPublishers.ofInputStream` 연결로 스트리밍한다.
- **R7 비공개 env 파일 유출.** `http-client.private.env.json`이 git에 올라갈 수 있다. 완화: 처음 만들 때 `.gitignore`에 추가할지 묻는 알림(쓰기는 사용자 승인 후).

### 남은 의문

- **Q1** 툴 창을 시작 시 숨겼다가 첫 실행 때 보이게 하는 가장 싼 방법은 무엇인가. `shouldBeAvailable=false` + `setAvailable(true)`로 충분한지, `toolWindow` 선언 자체가 시작 시 클래스 로드를 일으키는지 측정해야 한다.
- **Q2** JSON 본문 하이라이트를 `LayeredLexer`로 할지 언어 주입으로 할지. 주입은 완성까지 주지만 하이라이팅 패스가 무거워진다. T1에서 두 방식을 1,000줄짜리 `.http`로 비교한다.
- **Q3** 환경 콤보를 어디에 둘지. 에디터 floating toolbar, 에디터 상단 패널, 상태바 위젯 중 시작 비용이 없는 쪽. 상태바 위젯은 프로젝트가 열릴 때 생성되므로 피한다.
- **Q4** `phantom-spring-boot`가 `server.port`·`context-path` 설정 값을 API로 줄 수 있는가. 아니면 phantom-http가 `application.yaml`을 직접 읽어야 하는가(YAML PSI는 `plugins/yaml`에 있다).
- **Q5** Rhino `rhino-runtime` 아티팩트에 인터프리터 모드(`setOptimizationLevel(-1)` / `setInterpretedMode(true)`)가 포함되는가. 바이트코드 생성 모드면 Metaspace를 더 쓴다 **[A]**.
- **Q6** 워크트리 4개처럼 같은 컨트롤러가 여러 모듈에 있을 때 `UrlResolverManager`가 중복을 어떻게 돌려주는가(`UrlTargetInfoDistinctKey`가 `UrlResolverManager.kt:74`에 있다 **[V]**. 그 동등성 기준은 확인하지 않았다).
- **Q7** Ultimate가 응답 기록을 실제로 어디에 쓰는지(`.idea/httpRequests/`로 알려져 있으나 이 분석에서 관측하지 않음 **[A]**). 사용자가 기존 기록을 옮기고 싶어 하는지.
