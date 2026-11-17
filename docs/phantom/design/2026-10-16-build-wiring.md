# Phantom 번들 플러그인 빌드 배선 (build wiring)

- 날짜: 2026-10-16
- 대상 트리: `/Users/sanghyeonkim/Downloads/port/phantom-idea` (intellij-community `idea/262.10968.63`, Bazel)
- 범위: 새 번들 플러그인 `plugins/phantom-<name>`을 정의하고, Phantom에 번들로 등록하고, 소스에서 바로 띄워 보고, 테스트를 돌리는 방법
- 표기: **[V]**는 이 트리에서 경로, 클래스, 설정을 직접 확인한 것이다. **[A]**는 가정이며 첫 빌드나 첫 실행에서 확인해야 한다.
- 클린룸: 이 문서는 community 소스, 공개 문서, 분석 노트의 동작 설명만 근거로 쓴다. `reversed/source/`는 열지 않았다.

---

## 1. 목표와 범위

### 1.1 MVP (v0.1): "플러그인 하나를 끝까지 배선한다"

| # | 항목 | 사용자가 IDE에서 확인하는 결과 |
|---|---|---|
| M1 | 플러그인 골격 하나 (`plugins/phantom-hello`, 설명자 전용 메인 모듈 + 콘텐츠 모듈 `core` + `kotlin` + `tests`) | Settings > Plugins > Installed에 "Phantom Hello"가 **Bundled**로 보인다 |
| M2 | `.iml` → `BUILD.bazel` 생성 (`./build/jpsModelToBazelCommunityOnly.cmd`) | `./bazel.cmd build //plugins/phantom-hello/...`가 성공한다 |
| M3 | 번들 등록 (`IdeaCommunityProperties.kt`의 `bundledPluginModules`) | 개발 실행과 DMG 양쪽에 플러그인이 포함된다 |
| M4 | 빠른 개발 루프 두 가지: `bazel run //build:idea_community`(dev server)와 `bazel run //:main_run`(클래스패스 모드) | DMG를 만들지 않고 1–3분 안에 수정 사항을 IDE에서 본다 |
| M5 | 단일 모듈 테스트 (`./tests.cmd --module phantom.hello.tests --test ...`) | light 테스트 하나가 초록으로 끝난다 |
| M6 | 같은 레시피를 첫 실기능 플러그인(`phantom-spring-boot`)에 적용 | 사용자 프로젝트에서 그 플러그인의 기능 하나가 동작한다 |

MVP는 "기능"이 아니라 **배선**이다. Spring Boot, HTTP Client, DB, Docker 같은 기능 설계는 같은 디렉터리의 다른 문서(`2026-10-16-spring-boot.md`, `-http-client.md`, `-database.md`, `-docker-k8s.md`)가 맡는다. 이 문서는 그 플러그인들이 공통으로 따를 규칙을 정한다.

### 1.2 v0.2 이후

- `phantom-*` 플러그인만 zip으로 빌드하는 전용 Bazel 타깃 (설치된 Phantom에 "Install from disk"로 넣는 용도). community의 `KotlinPluginBuildTarget`처럼 `buildNonBundledPlugins(listOf(...))`를 부르면 된다 [V: `build/src/KotlinPluginBuildTarget.kt`, `KotlinPluginBuilder.kt:324`] [A: 비번들 빌드 경로가 phantom 모듈에도 그대로 동작하는지].
- 여러 phantom 플러그인이 공유하는 `phantom-core`(프로젝트 기술 감지, 공용 캐시 키). 공유가 실제로 두 곳 이상에서 필요해질 때만 만든다.
- `plugin-content.yaml` 스냅샷 (배포 내용 회귀 검사용, 아래 3.7).
- 무거운 community 번들 플러그인 정리 (`IDEA_BUNDLED_PLUGINS`에서 빼기). 이 문서와 같은 파일을 건드리지만 별도 결정이다.

### 1.3 비목표

- Gradle IntelliJ Platform Plugin(`org.jetbrains.intellij.platform`)로 바깥에서 빌드하는 방식. 이 포크는 모노레포 Bazel이 진실 공급원이다.
- Marketplace 배포, 서명, 플러그인 저장소 생성 (`buildAllCompatiblePlugins = false`로 이미 꺼져 있다 [V]).
- 모듈 기반 로더(`rootModuleForModularLoader`, `product-modules.xml`). Community IDEA는 이 값을 설정하지 않으므로 `bundledPluginModules` 경로를 쓴다 [V: `BuildContextImpl.kt:311-320`, `BuildTasksImpl.kt:573-582`].
- 라이선스, 인증, 활성화 코드. 손대지 않는다.
- Ultimate 플러그인과 같은 플러그인 ID를 쓰는 것. 이름이 겹치면 사용자가 Marketplace에서 받은 플러그인과 충돌한다.

---

## 2. Ultimate 대비 경량화 원칙 (빌드 배선 수준)

분석 원장(`Intellij-rea/reversed/README.md`)의 4번 결론은 이렇다. Ultimate의 고유 비용 대부분은 **"콘텐츠 모듈이 프로젝트가 그 기술을 쓰는지와 상관없이 의존성만 맞으면 로드된다"**는 구조에서 나온다 (observed). 예를 들어 Kotlin 백엔드 프로젝트에서도 JS 스택이 클래스 5k개를 올리고, Database Tools는 모듈 78개가 전부 `required`로 로드된다. 시작 JFR의 약 81%는 community 코드라서 플랫폼 자체는 우리가 바꾸지 않는다. 우리가 통제할 수 있는 것은 **우리 플러그인이 무엇을, 언제 올리느냐**다.

빌드 배선에서 지키는 규칙은 다음과 같다.

| # | 규칙 | 피하는 Ultimate 비용 | 구현 수단 |
|---|---|---|---|
| P1 | 콘텐츠 모듈을 **기능이 아니라 외부 의존성 단위로** 나눈다 (`core`, `kotlin`, `yaml`, `properties`, `gradle`) | 의존 플러그인이 없으면 그 모듈의 클래스가 아예 안 올라간다 | 콘텐츠 모듈 `<dependencies><plugin id=…/>` [V: `plugins/env-files-support/kotlin/resources/intellij.dotenv.kotlin.xml`] |
| P2 | `loading="required"`와 `loading="embedded"`는 기본으로 쓰지 않는다. 기본값(optional)으로 두고, `embedded`는 다른 모듈이 반드시 필요로 하는 아주 작은 `core`에만 쓴다 | DB Tools의 "78개 모듈 전부 required" 패턴 | `<content>` 속성 [V: `plugins/toml/resources/META-INF/plugin.xml`] |
| P3 | 설명자에 `postStartupActivity`, `backgroundPostStartupActivity`, `projectActivity`를 등록하지 않는다. 꼭 필요하면 리뷰에서 근거를 남긴다 | Ultimate 플러그인들의 시작 활동 수십 개 | 체크리스트 (4장 T7의 grep 검사) |
| P4 | 서비스는 `@Service` light service로 둔다 (첫 `service<T>()` 호출 때 생성). XML `applicationService`/`projectService`는 인터페이스와 구현을 분리해야 할 때만 쓴다 | 시작 시 서비스 초기화 | 플랫폼 기본 동작 [A: 이 브랜치에서도 light service가 지연 생성인지는 플랫폼 문서 기준] |
| P5 | "이 프로젝트가 Spring/JPA를 쓰는가"는 `core`가 `CachedValuesManager` + `ProjectRootModificationTracker` 같은 루트 변경 추적기로 캐시한다. 모든 확장(EP 구현)은 첫 줄에서 이 값을 보고 바로 빠져나간다 | Spring 모델이 파일 하나만 바뀌어도 프로젝트 전체가 무효화되는 문제 (원장 3번, inferred) | 플러그인 코드 규약. 세부 모델 설계는 각 기능 문서가 맡는다 |
| P6 | 플러그인 하나는 `bundledPluginModules`의 **한 줄**로 넣고 뺀다. 그래서 A/B 측정(켬/끔 시작 시간)이 쉽다 | 끄고 싶어도 연쇄 의존 때문에 못 끄는 구조 | 3.4 |
| P7 | 새 서드파티 라이브러리는 MVP에서 들이지 않는다. 필요하면 plugin-local 라이브러리로만 넣는다 | 플랫폼 `lib/`에 라이브러리가 쌓이는 문제 | 3.6 |

P1이 막지 못하는 것이 있다. 사용자 머신에는 Kotlin, YAML 플러그인이 항상 켜져 있으므로 `kotlin`, `yaml` 콘텐츠 모듈은 Spring을 안 쓰는 프로젝트에서도 로드된다. 그래서 P5가 꼭 필요하다. 로드는 되지만 일은 하지 않게 만든다. 클래스 로딩 비용은 EP 구현이 실제로 불릴 때 생기므로(확장은 지연 인스턴스화), 등록 개수를 줄이는 것도 효과가 있다 [A: EP별 지연 여부는 EP마다 다르다].

`loading="on-demand"` 값은 파서와 테스트 데이터에 존재한다 [V: `platform/runtime/repository/tests/testSrc/serialization/RuntimeModuleRepositorySerializationTest.kt:177`]. 하지만 의미가 공개 문서로 정리되어 있지 않아 MVP에서는 쓰지 않는다 (unknown).

---

## 3. 아키텍처

### 3.1 빌드 모델 한눈에 보기

```
.iml (진실 공급원)  ──jpsModelToBazelCommunityOnly──▶  BUILD.bazel (auto-generated section)
.idea/modules.xml  (모듈 목록)                            build/bazel-generated-file-list.txt
.idea/libraries/*.xml (프로젝트 라이브러리)  ─────────▶  @lib//:<name>

IdeaCommunityProperties.bundledPluginModules  ─▶  installers.cmd (DMG)      : PluginLayout.pluginAuto
                                              └▶  //build:idea_community   : dev server (DevMainKt)
intellij.idea.community.main.iml (RUNTIME)    ─▶  //:main_run / "IDEA" 실행 구성 : 클래스패스 모드
```

- `.iml`이 진실 공급원이고 `BUILD.bazel`의 auto-generated 구역은 생성물이다 [V: `AGENTS.md` "Project Invariants"; `BUILD.bazel`의 `### auto-generated section ... start/end` 주석].
- 생성기는 `./build/jpsModelToBazelCommunityOnly.cmd`다. 이 스크립트는 `RUN_WITHOUT_ULTIMATE_ROOT=true`를 설정하고 `platform/build-scripts/bazel`에서 `bazel run //:jps-to-bazel`을 실행한다 [V]. `AGENTS.md`에 적힌 `./build/jpsModelToBazel.cmd`는 이 community 트리에 **없다** [V]. Ultimate 모노레포용 안내다.
- 생성기는 모듈 목록을 `.idea/modules.xml`에서 읽는다 [V: `platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/ModulesXml.kt:13`, 해석 실패 시 `BazelBuildFileGenerator.kt:195`의 "Cannot resolve module" 오류].
- Bazel 쪽 동적 타깃 목록(`ALL_PRODUCTION_COMMUNITY_TARGETS`)도 `.iml`과 `.idea/modules.xml`, `.idea/libraries/*.xml`에서 Starlark로 계산된다 [V: `build/jps_dynamic_deps_community.bzl`, 루트 `BUILD.bazel`의 `jps_to_bazel_community_model_root_files`]. 그래서 dev server 타깃은 새 모듈을 따로 등록하지 않아도 데이터로 포함한다 [A: 저장소 규칙이 `modules.xml` 변경을 감지해 다시 평가하는지].

### 3.2 디렉터리와 이름 규칙

```
plugins/phantom-<name>/
├── phantom.<name>.iml              # 메인 모듈: 설명자 전용 (소스 없음)
├── BUILD.bazel                              # 생성물
├── resources/META-INF/plugin.xml
├── resources/META-INF/pluginIcon.svg        # 선택
├── core/                                    # 콘텐츠 모듈 phantom.<name>.core
│   ├── phantom.<name>.core.iml
│   ├── BUILD.bazel
│   ├── resources/phantom.<name>.core.xml
│   ├── resources/messages/Phantom<Name>Bundle.properties
│   └── src/dev/phantom/<name>/core/...
├── kotlin/   → phantom.<name>.kotlin   (Kotlin PSI/K2 Analysis API가 필요한 부분)
├── yaml/     → phantom.<name>.yaml     (필요할 때만)
├── properties/ → phantom.<name>.properties
└── tests/    → phantom.<name>.tests    (testSrc/, testData/)
```

이름 규칙:

| 대상 | 규칙 | 예 | 근거 |
|---|---|---|---|
| 디렉터리 | `plugins/phantom-<kebab>` | `plugins/phantom-spring-boot` | 작업 지시 |
| JPS 모듈 | `phantom.<camel>[.<sub>]` | `phantom.springBoot.core` | 형제 문서들과 일치 |
| 플러그인 ID | `dev.phantom.<kebab-or-dot>` | `dev.phantom.spring.boot` | 맥 번들 ID `dev.phantom.idea`와 맞춘다 [V: `IdeaCommunityProperties.kt` `bundleIdentifier`] |
| 패키지 | `dev.phantom.<name>.<sub>` | `dev.phantom.springboot.core` | 콘텐츠 모듈 `package` 속성과 일치시킨다 |
| 콘텐츠 모듈 설명자 | 모듈 리소스 루트의 `<모듈이름>.xml` | `core/resources/phantom.springBoot.core.xml` | [V: `plugins/toml/backend/resources/intellij.toml.backend.xml`] |

형제 문서들은 플러그인 ID를 `phantom.spring.boot`, `phantom.http`, `com.phantom.db`로 제각각 적었다. 구현 전에 `dev.phantom.*` 하나로 통일하자고 제안한다 (6장 R8).

**Bazel 타깃 이름은 생성기가 정한다.** 규칙은 `build/jps_target_derivation.bzl`의 `module_name_to_target`에 있다 [V].
- 모듈 이름이 `.<디렉터리 이름>`으로 끝나면 디렉터리 이름이 타깃 이름이 된다. 그래서 `plugins/phantom-hello/core`의 `phantom.hello.core`는 `//plugins/phantom-hello/core:core`이다.
- 그렇지 않으면 `intellij.` 접두어를 떼고 `.`을 `-`로 바꾼다. 그래서 `plugins/phantom-hello`의 `phantom.hello`는 `//plugins/phantom-hello:phantom-hello`가 된다 [A: 생성 결과로 확인. camelCase 이름(`phantom-springBoot`)은 대소문자가 그대로 남는다].
- 테스트 모듈 `phantom.hello.tests`는 `:tests`(라이브러리)와 `:tests_test`(`jps_test`)를 만든다 [V: `plugins/toml/tests/BUILD.bazel`].

### 3.3 `.iml` 작성 규칙

- **IDE가 직렬화한 형태를 그대로 지킨다.** 주석을 넣지 않고, 정렬이나 포맷을 바꾸지 않고, 파일 끝에 줄바꿈을 넣지 않는다 [V: `AGENTS.md` "Repository-wide rules"]. 손으로 쓸 때는 기존 `.iml`을 복사해서 고친다.
- 소스 폴더 관례는 두 가지가 섞여 있다. 신규 모듈은 `src` + `resources`(toml, dotenv.kotlin 방식)를 쓴다 [V: `plugins/toml/backend/intellij.toml.backend.iml`].
- Kotlin 표준 라이브러리는 `<orderEntry type="library" name="kotlin-stdlib" level="project" />`로 넣는다. 생성 결과는 `@lib//:kotlin-stdlib`이다 [V: `plugins/toml/backend/BUILD.bazel:25`].
- Kotlin 컴파일러 PSI와 Analysis API는 **PROVIDED** 범위 라이브러리로 넣는다. `kotlinc.kotlin-compiler-common`과 `kotlinc.analysis-api`가 각각 `@lib//:kotlinc-kotlin-compiler-common-provided`, `@lib//:kotlinc-analysis-api-provided`로 생성된다 [V: `plugins/devkit/intellij.kotlin.devkit/k2/intellij.kotlin.devkit.k2.iml`, 같은 폴더 `BUILD.bazel:36-37`]. 런타임에는 Kotlin 플러그인의 클래스로더가 이 클래스들을 제공하므로 우리 jar에 들어가면 안 된다.
- Analysis API의 opt-in 같은 Kotlin 컴파일 옵션은 `.iml`의 `FacetManager` → `kotlin-language` facet `additionalArguments`에 적는다. 생성기가 이를 `create_kotlinc_options(name = "custom_k2", opt_in = [...])`로 옮긴다 [V: 같은 k2 devkit 모듈].

### 3.4 번들 등록: 손대는 파일

| 파일 | 변경 | 효과 | 상태 |
|---|---|---|---|
| `.idea/modules.xml` | 새 `.iml`마다 `<module fileurl=… filepath=… />` 한 줄씩. **경로 알파벳 순서** 위치에 넣는다 | JPS와 Bazel 생성기가 모듈을 인식한다 | [V: 목록이 경로순 정렬, 예: 1930–1939행 textmate/toml] |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt` | `productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf("intellij.javaFX.community", "phantom.hello")` | DMG와 dev server에 번들된다. `pluginLayouts`에 없으면 `PluginLayout.pluginAuto(listOf(moduleName))`가 자동으로 쓰인다 | [V: `DistributionJARsBuilder.kt:601`, `PluginsCollector.kt:181`, dev: `dev/PluginBuilder.kt:184-189`] |
| `intellij.idea.community.main.iml` | 메인 모듈과 **모든 콘텐츠 모듈**을 `scope="RUNTIME"`로 추가 | `//:main_run`과 "IDEA" 실행 구성(클래스패스 모드)에서 플러그인이 보인다 | [V: 기존 플러그인들이 이렇게 들어가 있다. 예: `intellij.toml`(137행), `intellij.yaml.backend`(99행)] |
| `platform/build-scripts/src/.../BaseIdeaProperties.kt` | **건드리지 않는다** | `IDEA_BUNDLED_PLUGINS`는 Android Studio 프로퍼티 등과 공유하는 기본 목록이다. phantom 전용 추가는 `IdeaCommunityProperties`에 둔다 | [V] |
| `productLayout.pluginLayouts` | MVP에서는 건드리지 않는다. jar 이름이나 디렉터리 이름을 바꾸거나 plugin-local 라이브러리를 넣을 때만 `PluginLayout.pluginAutoWithCustomDirName(...)`을 추가한다 | | [V: `PluginLayout.kt:225-258`] |

`bundledPluginModules`는 phantom 전용 상수로 묶어 두면 P6(한 줄로 넣고 빼기)이 쉬워진다.

```kotlin
// IdeaCommunityProperties.kt (제안)
private val PHANTOM_BUNDLED_PLUGINS = persistentListOf(
  "phantom.hello",
  // "phantom.springBoot",
)
productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf("intellij.javaFX.community") + PHANTOM_BUNDLED_PLUGINS
```

`IdeaCommunityProperties`는 `productLayout.skipUnresolvedContentModules = true`이다 [V]. 그래서 콘텐츠 모듈 이름을 잘못 적어도 빌드가 실패하지 않고 **조용히 빠질 수 있다** [A: 동작 의미는 이름에서 추론]. 4장 T4의 수락 기준은 그래서 "배포본 jar 목록에 모듈이 있는가"를 직접 본다.

### 3.5 `plugin.xml`과 콘텐츠 모듈 규약

**메인 설명자** (`plugins/phantom-hello/resources/META-INF/plugin.xml`). 소스가 없는 설명자 전용 모듈이다 (toml 방식 [V: `plugins/toml/intellij.toml.iml`은 `resources`만 있다]).

```xml
<idea-plugin>
  <id>dev.phantom.hello</id>
  <name>Phantom Hello</name>
  <vendor>Phantom</vendor>
  <description><![CDATA[Build-wiring smoke plugin for Phantom.]]></description>

  <dependencies>
    <plugin id="com.intellij.modules.lang"/>
  </dependencies>

  <content namespace="jetbrains">
    <module name="phantom.hello.core"/>
    <module name="phantom.hello.kotlin"/>
  </content>
</idea-plugin>
```

- `<idea-version>`과 `<version>`은 번들 빌드가 채운다 [A: community 번들 플러그인들의 `plugin.xml`에 이 태그가 없다는 점에서 추론, 예: toml].
- `vendor`가 JetBrains가 아니어도 번들이면 "외부 비번들 플러그인" 취급을 받지 않는다. 조건이 `!plugin.isBundled && !isVendorJetBrains(...)`이기 때문이다 [V: `platform/core-impl/src/com/intellij/ide/plugins/ProductPluginInitContext.kt:400-403`].
- **`namespace="jetbrains"`를 쓰는 이유.** 콘텐츠 모듈의 기본 visibility는 `PRIVATE`이다 [V: `platform/pluginSystem/parser/impl/src/.../XmlReader.kt:1074`]. `INTERNAL` 모듈은 **같은 namespace의 모듈만** 의존할 수 있다 [V: `PluginSetBuilder.kt:277-312`, `ModuleVisibility.kt`]. 기본값은 위반하면 모듈을 로드하지 않는 `REPORT_ERROR`이다 [V: `PluginSetBuilder.kt:357-364`, 시스템 프로퍼티 `intellij.platform.plugin.modules.check.visibility`]. Kotlin과 Gradle 쪽에는 `visibility="internal"`인 모듈이 있다 (예: `intellij.kotlin.base.analysis.platform`, `intellij.gradle.settings`, `intellij.kotlin.base.externalSystem`) [V]. 포크 안의 번들 플러그인이므로 같은 namespace를 써서 이런 모듈에도 의존할 수 있게 둔다. namespace를 생략하면 `<플러그인ID>_$implicit`가 된다 [V: `PluginMainDescriptor.kt:84-89`].
- 반대로 **phantom 플러그인끼리 콘텐츠 모듈을 공유하려면** 제공하는 쪽 모듈 설명자에 `visibility="public"`(또는 같은 namespace에서 `internal`)을 명시해야 한다. 기본값 `PRIVATE`이면 다른 플러그인이 의존할 때 로드되지 않는다. 예: HTTP 문서의 `phantom.http.spring`이 Spring Boot 플러그인의 `core`에 의존하는 경우.

**콘텐츠 모듈 설명자** (`core/resources/phantom.hello.core.xml`):

```xml
<idea-plugin package="dev.phantom.hello.core">
  <dependencies>
    <plugin id="com.intellij.java"/>
    <module name="intellij.java.psi"/>
  </dependencies>
  <resource-bundle>messages.PhantomHelloBundle</resource-bundle>
  <extensions defaultExtensionNs="com.intellij">
    <!-- 예: <localInspection language="JAVA" ... implementationClass="dev.phantom.hello.core.HelloInspection"/> -->
  </extensions>
</idea-plugin>
```

**Kotlin 콘텐츠 모듈** (`kotlin/resources/phantom.hello.kotlin.xml`):

```xml
<idea-plugin package="dev.phantom.hello.kotlin">
  <dependencies>
    <plugin id="org.jetbrains.kotlin"/>
    <module name="phantom.hello.core"/>
  </dependencies>
  <extensions defaultExtensionNs="com.intellij">
    <!-- language="kotlin" 확장 -->
  </extensions>
</idea-plugin>
```

- K2 전용 기능이면 `<plugin id="com.intellij.modules.kotlin.k2"/>`에 의존한다 [V: `plugins/kotlin/plugin/k2/resources/kotlin.plugin.k2.xml`에서 정의, `intellij.kotlin.devkit.k2.xml`에서 사용].
- `<supportsKotlinPluginMode supportsK2="true"/>`는 **더 이상 필요 없다**. `SupportsKotlinPluginMode`는 `@Deprecated("K1 plugin doesn't exist anymore, K2 mode is used by default")`이다 [V: `plugins/kotlin/base/plugin/src/org/jetbrains/kotlin/idea/base/plugin/SupportsKotlinPluginMode.kt`].
- Java와 Kotlin을 한 코드로 다룰 수 있는 기능(애너테이션 탐색, 호출 그래프)은 UAST(`intellij.platform.uast`, `intellij.java.uast`, 둘 다 `visibility="public"` [V])로 `core`에 두고, Kotlin 전용 PSI나 Analysis API가 필요한 것만 `kotlin` 모듈로 보낸다. UAST만 써도 Kotlin 파일을 처리하려면 Kotlin 플러그인이 런타임에 있어야 하지만, 그 의존을 `core`에 걸지 않으므로 `core`는 Kotlin 없이도 로드된다.

**의존 대상 표** (모두 [V]: 해당 `plugin.xml` 또는 모듈 설명자 확인)

| 쓰는 기술 | `<plugin id>` | 자주 쓰는 콘텐츠 모듈 (`<module name>`) | `.iml` orderEntry |
|---|---|---|---|
| Java PSI | `com.intellij.java` (`java/plugin/resources/META-INF/plugin.xml`) | `intellij.java.psi`, `intellij.java.impl`, `intellij.java.backend`, `intellij.java.execution.impl` (전부 public) | 같은 이름의 모듈 |
| Kotlin | `org.jetbrains.kotlin` (`plugins/kotlin/plugin/resources/META-INF/plugin.xml`) | 대부분 Kotlin 메인 jar에 embedded되어 따로 적지 않는다. `intellij.kotlin.base.*`는 별도 설명자가 없는 모듈이다 | `intellij.kotlin.base.plugin` 등 + PROVIDED `kotlinc.*` |
| YAML | `org.jetbrains.plugins.yaml` (`plugins/yaml/resources/META-INF/plugin.xml`) | `intellij.yaml`(embedded, public), `intellij.yaml.backend`(public) | `intellij.yaml`, `intellij.yaml.backend` |
| Properties | `com.intellij.properties` (`plugins/properties/properties-common/resources/META-INF/plugin.xml`) | `intellij.properties.psi`, `intellij.properties.backend.psi`, `intellij.properties.backend` (public) | 같은 이름 |
| Gradle | `com.intellij.gradle` (`plugins/gradle/plugin/resources/META-INF/plugin.xml`), Java 쪽 `org.jetbrains.plugins.gradle` | `intellij.gradle`(public), `intellij.gradle.java`(public) | `intellij.gradle`, `intellij.gradle.common`, `intellij.platform.externalSystem` |
| UAST | (플랫폼) | `intellij.platform.uast`, `intellij.java.uast` | 같은 이름 |

번들 빌드에 쓰는 메인 모듈 이름은 다음과 같다 [V]. Java는 `intellij.java.plugin`(`JavaPluginLayout.MAIN_MODULE_NAME`), Kotlin은 `kotlin.plugin`(`KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE`), YAML은 `intellij.yaml`, Gradle은 `intellij.gradle.plugin`과 `intellij.gradle.java.plugin`.

**Generated dependencies 구역.** community 설명자에는 `<!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->` 구역이 있다 [V]. 이 구역을 채우는 도구 `bazel run //platform/buildScripts:plugin-model-tool`은 Ultimate 경로이고, 이 트리에는 `platform/buildScripts`가 없다 [V]. 그래서 phantom 설명자는 **`<dependencies>`를 손으로 쓰고 이 구역을 만들지 않는다.** 규칙은 하나다. `.iml`에서 컴파일 의존하는 다른 플러그인의 콘텐츠 모듈은 설명자 `<dependencies>`에도 적는다. 이것을 빠뜨리면 dev server나 배포본의 플러그인 클래스로더에서 `NoClassDefFoundError`가 난다. 클래스패스 모드에서는 드러나지 않으므로 dev server로 검증한다 (3.8).

### 3.6 라이브러리

- 플랫폼에 이미 있는 라이브러리(kotlinx.coroutines, Jackson, OkHttp 등)는 `intellij.libraries.*` 모듈에 의존한다 (예: `libraries/kotlinx/coroutines/core/intellij.libraries.kotlinx.coroutines.core.iml` [V]).
- 새 Maven 라이브러리는 `.idea/libraries/<name>.xml`을 추가하면 생성기가 `@lib//:<name>`을 만든다 [V: 루트 `BUILD.bazel`의 `jps_to_bazel_community_model_root_files`에 `.idea/libraries/*.xml` 포함] [A: 해시 고정과 다운로드 설정(`build/dependencies`, `MODULE.bazel`)까지 손대야 하는지]. MVP에서는 쓰지 않는다 (P7). 필요해지면 `pluginAutoWithCustomDirName { spec -> spec.withProjectLibrary(...) }`로 플러그인 `lib/`에만 넣는다 [V: `PluginLayout.kt` 주석 "Project-level library is included in the plugin by default, if not yet included in the platform"].

### 3.7 `plugin-content.yaml`

community 플러그인 중 일부에는 `plugin-content.yaml`이 있다 (예: `plugins/toml/plugin-content.yaml`, `plugins/env-files-support/plugin-content.yaml` [V]). 배포 내용 스냅샷이며 테스트 프레임워크의 내용 검사기가 비교한다 [V: `platform/build-scripts/testFramework/src/.../distributionContent/contentChecker.kt:120`]. product-dsl 문서는 새 래퍼 플러그인에 `plugin.xml`과 `plugin-content.yaml`을 같이 만들라고 권한다 [V: `platform/build-scripts/product-dsl/docs/module-sets.md:131`]. 빌드가 이 파일을 요구하지는 않는다 [A]. 그래서 v0.2에서 첫 배포본의 내용 보고서를 보고 만든다.

### 3.8 실행 모드 세 가지와 차이

| 모드 | 명령 | 플러그인을 찾는 방식 | 장점 | 단점 |
|---|---|---|---|---|
| A. dev server (권장) | `./bazel.cmd run //build:idea_community` | `IdeaCommunityProperties`로 실제 플러그인 레이아웃을 `out/dev-run/` 아래에 조립한다. 플러그인마다 클래스로더가 따로 생긴다 | 배포본과 같은 클래스로더 경계라 설명자 의존 누락이 드러난다. 증분 빌드다 | 첫 실행이 느리다 |
| B. 클래스패스 | `./bazel.cmd run //:main_run` 또는 IDE에서 "IDEA" 실행 구성 | `intellij.idea.community.main`의 런타임 클래스패스에서 `META-INF/plugin.xml`과 `<모듈>.xml`을 찾는다 | 조립 단계가 없어 가장 빠르다. IDE 디버거와 HotSwap을 쓸 수 있다 | 클래스로더 격리가 약해 의존 누락을 놓칠 수 있다 |
| C. 배포본 | `./phantom-build.sh` (`installers.cmd` 래퍼) 또는 `./installers.cmd -Dintellij.build.target.os=current` | 정식 빌드 | 최종 확인용이다 | 가장 느리다 |

근거:
- A: `build/BUILD.bazel`의 `intellij_dev_binary_community(name = "idea_community", platform_prefix = "community")`이 `org.jetbrains.intellij.build.devServer.DevMainKt`를 실행한다 [V: `build/intellij_dev.bzl:100`]. 설정과 시스템 경로는 `out/dev-data/idea_community/{config,system}`이다 [V: `intellij_dev.bzl:77-78`]. 제품 클래스는 `build/dev-build.json`의 `"community"` → `IdeaCommunityProperties`로 정해진다 [V]. 번들 목록에 `additionalModules`를 더한다 [V: `dev/PluginBuilder.kt:184-189`]. 그래서 번들 등록 전에도 `-Dadditional.modules=phantom.hello`로 시험할 수 있다 [A: java_binary 런처에 `--jvm_flag=-Dadditional.modules=...`로 넘기는 방식이 동작하는지. 매크로에 `additional_modules` 속성이 있다 V]. `out/dev-run/jar-cache`가 이미 있어 이 모드를 한 번 돌린 것으로 보인다 [V: 디렉터리 존재].
- B: 루트 `BUILD.bazel`의 `java_binary(name = "main_run", main_class = "com.intellij.idea.Main", runtime_deps = [":main"])`이고, 설정은 `config/idea`, `system/idea`다 [V]. "IDEA" 실행 구성은 같은 모듈을 JPS로 빌드해서 실행한다 [V: `.idea/runConfigurations/IDEA.xml`]. 소스 실행 여부는 `PathManager`가 홈에 `.idea`가 있는지로 판단한다 [V: `platform/util/src/com/intellij/openapi/application/PathManager.java:966`].
- C: `installers.cmd`는 `//build:i_build_target`(`OpenSourceCommunityInstallersBuildTarget`)을 실행한다 [V]. 증분 대안으로 `build/updateFromSources.cmd`(`OpenSourceCommunityUpdateFromSourcesBuildTarget`, jps-bootstrap 기반, `buildUnpackedDistribution`)가 있다 [V] [A: Bazel 전환 이후에도 쓸 만한 속도인지].

세 모드는 설정 디렉터리가 서로 다르다. 그래서 개발 중 실험이 사용자의 실제 Phantom 설정(`~/Library/Application Support/JetBrains/PhantomIDEA`)을 더럽히지 않는다 [A: 시스템 셀렉터가 `"PhantomIDEA"`로 고정된 것은 V, 실제 경로는 A].

---

## 4. 구현 단계

각 단계는 이전 단계가 끝나야 시작한다. 공수는 하루 4–6시간 집중 기준이다.

| # | 작업 | 공수 | 수락 기준 (사용자가 보는 것) |
|---|---|---|---|
| T1 | **골격 생성.** 5장의 파일 목록대로 `plugins/phantom-hello/`를 만든다. `.iml`은 `plugins/toml/backend/intellij.toml.backend.iml`과 `plugins/env-files-support/kotlin/intellij.dotenv.kotlin.iml`을 복사해서 고친다. 액션 하나(`Tools > Phantom: Hello`)와 Java/Kotlin 공용 inspection 하나(UAST로 `@Deprecated` 없는 `TODO()` 호출 표시 같은 장난감)를 넣는다 | 0.5 | 파일이 생기고 `git status`에 `.iml` 4개와 설명자 3개가 보인다 |
| T2 | **모듈 등록.** `.idea/modules.xml`에 4줄을 경로순으로 넣는다. 이어서 `./build/jpsModelToBazelCommunityOnly.cmd`를 실행한다 | 0.25 | 각 폴더에 `BUILD.bazel`이 생기고 `build/bazel-generated-file-list.txt`에 경로가 추가된다. `./bazel.cmd build //plugins/phantom-hello/...`가 성공한다 |
| T3 | **클래스패스 모드 배선.** `intellij.idea.community.main.iml`에 메인 모듈과 콘텐츠 모듈을 `RUNTIME`으로 추가하고 T2의 생성기를 다시 실행한다 (루트 `BUILD.bazel`의 `:main` deps가 갱신된다) | 0.25 | `./bazel.cmd run //:main_run`으로 뜬 IDE의 Plugins 목록에 "Phantom Hello"가 있다. 메뉴에 액션이 보인다 |
| T4 | **번들 등록.** `IdeaCommunityProperties.kt`에 `PHANTOM_BUNDLED_PLUGINS`를 추가한다 (3.4) | 0.25 | `./bazel.cmd run //build:idea_community`로 뜬 IDE에서 플러그인이 **Bundled**로 보인다. `out/dev-run/` 아래 플러그인 디렉터리에 `lib/modules/phantom.hello.core.jar`(또는 embedded면 메인 jar)가 있다 [A: 정확한 디렉터리 이름] |
| T5 | **테스트 모듈.** `tests/`에 JUnit5 `@TestApplication` 기반 light 테스트 1개(Java)와 `KotlinLightCodeInsightFixtureTestCase` 기반 테스트 1개(Kotlin)를 쓴다 | 0.5 | `./tests.cmd --module phantom.hello.tests --test 'dev.phantom.hello.*Test'`가 초록이다 |
| T6 | **클래스로더 경계 검증.** 일부러 `kotlin` 모듈 설명자에서 `<plugin id="org.jetbrains.kotlin"/>`을 빼고 dev server로 실행해 본다. 오류가 로그에 남는지 확인하고 되돌린다 | 0.25 | `out/dev-data/idea_community/system/log/idea.log`에 해당 모듈의 로드 실패 메시지가 있다. 되돌리면 사라진다 |
| T7 | **경량화 검사 스크립트.** `plugins/phantom-*/**/*.xml`에서 `postStartupActivity|backgroundPostStartupActivity|projectActivity|loading="required"`를 찾는 `tools/rg.cmd` 한 줄을 `docs/phantom/design`의 체크리스트로 남긴다 (P2, P3) | 0.1 | 명령 결과가 비어 있다 |
| T8 | **배포본 확인.** `./phantom-build.sh` | 0.5 (대기 시간 위주) | DMG로 설치한 Phantom의 Plugins에 "Phantom Hello"가 Bundled로 보인다. `Phantom.app/Contents/plugins/` 아래에 플러그인 디렉터리가 있다 |
| T9 | **첫 실기능 플러그인에 적용.** `phantom-spring-boot`를 T1–T8 순서로 배선한다. 기능 구현은 `2026-10-16-spring-boot.md`를 따른다 | 0.5 (배선만) | `~/orca/workspaces/prototype-reservation-system`에서 `application.yml`의 `server.port` 완성이 뜬다 (기능 문서 수락 기준) |
| T10 | **hello 정리.** 골격 검증이 끝나면 `phantom-hello`를 번들 목록에서 빼거나 지운다 | 0.1 | Plugins 목록에서 사라진다 |

**MVP 합계: 약 3.2일** (T1–T10. 배포본 빌드 대기 시간은 머신 성능에 따라 늘어난다).

모듈을 추가하거나 `.iml`을 고칠 때마다 `./build/jpsModelToBazelCommunityOnly.cmd`를 다시 실행한다. `AGENTS.md`의 "After modifying `*.iml`, `BUILD.bazel`, or `.idea/` files" 규칙에 해당한다 [V].

---

## 5. 템플릿 골격: `plugins/phantom-<name>`

### 5.1 파일 목록

```
plugins/phantom-<name>/
  phantom.<name>.iml
  resources/META-INF/plugin.xml
  resources/META-INF/pluginIcon.svg                       (선택)
  core/phantom.<name>.core.iml
  core/resources/phantom.<name>.core.xml
  core/resources/messages/Phantom<Name>Bundle.properties
  core/src/dev/phantom/<name>/core/Phantom<Name>Bundle.kt
  core/src/dev/phantom/<name>/core/<Name>ProjectDetector.kt   (P5: 기술 사용 여부 캐시)
  kotlin/phantom.<name>.kotlin.iml
  kotlin/resources/phantom.<name>.kotlin.xml
  kotlin/src/dev/phantom/<name>/kotlin/...
  tests/phantom.<name>.tests.iml
  tests/testSrc/dev/phantom/<name>/...Test.kt
  tests/testData/...
  BUILD.bazel, core/BUILD.bazel, kotlin/BUILD.bazel, tests/BUILD.bazel   ← 손으로 쓰지 않는다 (생성물)
```

### 5.2 내용 개요

**`phantom.<name>.iml`** (설명자 전용, `plugins/toml/intellij.toml.iml` 형태 [V])

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$">
      <sourceFolder url="file://$MODULE_DIR$/resources" type="java-resource" />
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>
```
(실제 파일은 끝에 줄바꿈이 없어야 한다.)

**`core/phantom.<name>.core.iml`** (orderEntry 개요)
- `<sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />`, `<sourceFolder url="file://$MODULE_DIR$/resources" type="java-resource" />`
- `library kotlin-stdlib (project)`
- 모듈: `intellij.platform.core`, `intellij.platform.core.impl`, `intellij.platform.util`, `intellij.platform.analysis`, `intellij.platform.lang`, `intellij.platform.lang.impl`, `intellij.platform.projectModel`, `intellij.platform.indexing`, `intellij.platform.ide.core`, `intellij.platform.uast`, `intellij.java.psi`, `intellij.java.uast`
- 필요할 때만: `intellij.yaml`, `intellij.properties.psi`, `intellij.gradle`, `intellij.platform.externalSystem`

**`kotlin/phantom.<name>.kotlin.iml`**
- 위 `core`의 기본 항목 + `phantom.<name>.core`
- `library kotlinc.kotlin-compiler-common (project, PROVIDED)`, `library kotlinc.analysis-api (project, PROVIDED)`
- 모듈: `intellij.kotlin.base.plugin`, 필요하면 `intellij.kotlin.base.psi`, `intellij.kotlin.base.analysis` [V: 모듈 존재. 어떤 API가 어느 모듈에 있는지는 구현 시 확인]
- Analysis API를 쓰면 `FacetManager`의 `kotlin-language` facet에 opt-in 인자를 넣는다 (`intellij.kotlin.devkit.k2.iml` 1–17행 형태 [V])

**`tests/phantom.<name>.tests.iml`**
- `<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" />`, testData는 소스 폴더로 만들지 않는다
- 모듈(`scope="TEST"`): `phantom.<name>`, `.core`, `.kotlin`, `intellij.platform.testFramework`, `intellij.platform.testFramework.junit5`, `intellij.java.testFramework`, `intellij.kotlin.testFramework`(Kotlin fixture), `intellij.libraries.junit5` [V: 모두 `.idea/modules.xml`에 존재]
- Kotlin K2 테스트가 필요하면 `kotlin.plugin.k2`를 `RUNTIME`으로 넣는다 [V: `jvm/jvm-analysis-kotlin-tests-k2/intellij.jvm.analysis.kotlin.tests.k2.iml:13`]

**`core/src/.../Phantom<Name>Bundle.kt`**: `DynamicBundle("messages.Phantom<Name>Bundle")` 패턴. 사용자에게 보이는 문자열은 `.properties`에 둔다 [V: `AGENTS.md` "User-visible strings belong in `*.properties`"].

**`core/src/.../<Name>ProjectDetector.kt`** (P5의 계약만 정의)
- `@Service(Service.Level.PROJECT) class <Name>ProjectDetector(project)`
- `fun isActive(): Boolean` = `CachedValuesManager.getManager(project).getCachedValue(project) { Result(compute(), ProjectRootModificationTracker.getInstance(project)) }`
- `compute()`는 `JavaPsiFacade.findClass("org.springframework.boot.SpringApplication", allScope)`처럼 **라이브러리 클래스 존재 여부 하나**만 본다. 인덱스가 필요하므로 dumb mode에서는 `false`를 돌려주고 캐시하지 않는다.
- EDT에서 부르지 않는다. 확장 구현(완성, 검사, 라인 마커)은 이미 백그라운드 읽기 액션에서 불리므로 첫 줄에서 `if (!detector.isActive()) return`만 한다.

**`plugin.xml`, 콘텐츠 모듈 XML**: 3.5의 예시 그대로.

---

## 6. 테스트 방법

### 6.1 자동

| 대상 | 명령 | 비고 |
|---|---|---|
| 컴파일만 | `./bazel.cmd build //plugins/phantom-<name>/...` | `AGENTS.md`의 "Bazel compilation without tests" [V] |
| 한 테스트 클래스 | `./tests.cmd --module phantom.<name>.tests --test dev.phantom.<name>.FooTest` | FQN이 필요하다. 단순 클래스 이름은 매치되지 않는다 [V: `tests.cmd`, `.agents/skills/testing/SKILL.md`]. 내부적으로 `//build:run_tests_build_target`(`CommunityRunTestsBuildTarget`)을 실행한다 [V] |
| 와일드카드 | `--test 'dev.phantom.<name>.*Test'` | `*` → `.*` 정규식 전체 일치 [V] |
| 메서드 하나 | `--test dev.phantom.<name>.FooTest#testBar` | `#`이 있으면 `intellij.build.test.simple.patterns`를 쓴다 [V] |
| Bazel 직접 | `./bazel.cmd test //plugins/phantom-<name>/tests:tests_test --test_output=errors` | `jps_test` 매크로가 `JUnit5BazelRunner`를 쓴다 [V: `build/tests-options.bzl:107-160`] [A: `--test_filter`로 클래스를 거르는 방식이 `tests.cmd`와 같은지] |
| 패키징 스모크 (선택, 무거움) | `./tests.cmd --module intellij.idea.community.build.tests --test org.jetbrains.intellij.build.IdeaCommunityBuildTest` | 전체 테스트 빌드를 돈다 [V: `build/tests/testSrc/.../IdeaCommunityBuildTest.kt`]. 릴리스 전에만 실행한다 |

테스트 작성 규약은 다음과 같다.
- 새 테스트는 JUnit5 `@TestApplication` + `projectFixture()`를 쓴다 [V: `.agents/skills/writing-tests/SKILL.md`, 예제는 `platform/testFramework/junit5/test/showcase/`].
- 코드 인사이트 fixture가 필요한 경우(완성, 하이라이팅)에는 JUnit4 계열의 `LightJavaCodeInsightFixtureTestCase`(`java/testFramework/src/com/intellij/testFramework/fixtures/LightJavaCodeInsightFixtureTestCase.java` [V])와 `KotlinLightCodeInsightFixtureTestCase`(`plugins/kotlin/test-framework/test/org/jetbrains/kotlin/idea/test/KotlinLightCodeInsightFixtureTestCase.kt` [V])를 쓴다. 이때는 `intellij.libraries.junit4`도 넣는다.
- Spring/JPA 라이브러리는 실제 jar를 내려받지 않는다. testData에 `org/springframework/boot/SpringApplication.java` 같은 **스텁 소스**를 두고 fixture에 추가한다. 테스트가 네트워크와 로컬 Gradle 캐시에 의존하지 않게 하기 위해서다.

### 6.2 수동 (사용자 프로젝트)

1. `./bazel.cmd run //build:idea_community`로 IDE를 띄운다.
2. `~/orca/workspaces/prototype-reservation-system`을 연다. 첫 Gradle import는 오래 걸린다 (원장 L: 약 38 s).
3. 확인 항목:
   - Settings > Plugins > Installed에서 phantom 플러그인이 Bundled로 보이는가.
   - `Help > Diagnostic Tools > Activity Monitor`, 또는 로그 `out/dev-data/idea_community/system/log/idea.log`에서 phantom 모듈 관련 오류가 없는가.
   - Spring을 안 쓰는 프로젝트(예: 빈 Kotlin 프로젝트)를 열었을 때 phantom 확장이 일을 하지 않는가. 확인 방법: `jcmd <pid> VM.class_hierarchy`나 `-verbose:class`로 `dev.phantom.*` 클래스가 detector 하나만 로드되는지 본다 [A: 측정 도구 선택은 런타임 분석 노트(`reversed/02-runtime`)의 방식을 따른다].
4. 켬/끔 A/B: `PHANTOM_BUNDLED_PLUGINS`에서 한 줄을 빼고 dev server를 다시 띄워 시작 시간(`idea.log`의 "Startup" 계열 측정 줄)을 비교한다 (P6).

---

## 7. 리스크와 남은 의문

| # | 리스크 / 의문 | 영향 | 대응 |
|---|---|---|---|
| R1 | `skipUnresolvedContentModules = true` 때문에 콘텐츠 모듈 이름 오타가 빌드에서 조용히 무시될 수 있다 [V: 설정값, A: 의미] | 기능이 배포본에서만 사라진다 | T4 수락 기준대로 jar 목록을 직접 본다. 이름은 디렉터리와 `.iml`에서 복사한다 |
| R2 | 클래스패스 모드(B)는 설명자 의존 누락을 숨긴다 | 개발 중엔 되고 DMG에서 `NoClassDefFoundError` | 머지 전에 dev server(A)로 한 번 띄운다. T6로 동작을 확인해 둔다 |
| R3 | `namespace="jetbrains"`를 쓰면 JetBrains 내부 모듈에 마음대로 의존할 수 있다. 업스트림을 다시 가져올 때 내부 API가 바뀌면 깨질 수 있다 | 리베이스 비용 | 가능하면 `public` 모듈만 쓴다. internal 의존은 설명자에 주석 없이 목록으로 관리하고 리베이스 때 이 목록부터 점검한다 |
| R4 | Generated dependencies 도구(`plugin-model-tool`)가 community 트리에 없다 [V] | 설명자 `<dependencies>`와 `.iml`이 어긋날 수 있다 | 3.5의 수기 규칙을 따른다. v0.2에서 `.iml`과 설명자를 비교하는 작은 검사 스크립트를 고려한다 |
| R5 | `bazel run //build:idea_community`의 `platform_prefix = "community"`가 실제 IDE 플랫폼 프리픽스(`Idea`)와 어떻게 매핑되는지 확인하지 않았다 (unknown) | dev server의 제품 식별이 다를 수 있다 | 첫 실행에서 About 창과 로그로 확인한다 |
| R6 | `.idea/modules.xml` 변경 후 Bazel 동적 타깃 저장소(`jps_dynamic_deps_community`)가 다시 평가되는지 [A] | dev server에 새 모듈이 빠진다 | 안 되면 `./bazel.cmd sync --only=jps_dynamic_deps_community` 또는 `bazel shutdown` 후 재실행 [A] |
| R7 | 업스트림 `AGENTS.md`의 생성기 경로(`./build/jpsModelToBazel.cmd`)는 이 트리에 없다 [V] | 다른 에이전트가 잘못된 명령을 실행할 수 있다 | 이 문서의 레시피를 기준으로 삼는다. 필요하면 `.ai/local.md`에 한 줄로 적는다 (사용자 승인 후) |
| R8 | 형제 설계 문서들의 플러그인 ID가 제각각이다 (`phantom.spring.boot`, `phantom.http`, `com.phantom.db`) | 플러그인 간 의존 선언이 헷갈린다 | `dev.phantom.<name>`으로 통일하자고 제안한다. ID는 배포 후 바꾸기 어려우므로 첫 DMG 전에 결정한다 |
| R9 | 형제 문서 예시는 `<content>`에 namespace가 없다. internal 모듈(Kotlin, Gradle 일부)에 의존하면 `REPORT_ERROR`로 모듈이 로드되지 않는다 [V: 검사 로직] | 기능이 조용히 꺼진다 | 3.5 규칙(`namespace="jetbrains"`)을 모든 phantom 플러그인에 적용한다 |
| R10 | 콘텐츠 모듈 로드는 "의존 플러그인 존재" 기준이지 "프로젝트가 그 기술을 쓰는지" 기준이 아니다. 플랫폼 한계다 | Spring을 안 쓰는 프로젝트에서도 `kotlin`, `yaml` 모듈 클래스가 일부 로드된다 | P5(detector 조기 반환)와 EP 등록 최소화. 플랫폼 수정(프로젝트 단위 모듈 활성화)은 범위 밖이다 |
| R11 | 배포본 플러그인 디렉터리 이름과 jar 배치(`lib/modules/*.jar`)를 직접 확인하지 않았다 [A] | 문서와 실제가 다를 수 있다 | T4/T8에서 확인하고 이 문서를 고친다 |
| R12 | `-Dadditional.modules`를 `bazel run` 인자로 넘기는 방식 [A] | 번들 등록 없이 시험하는 지름길이 안 될 수 있다 | 안 되면 바로 T4(번들 등록)로 간다. 비용은 한 줄 수정이다 |
