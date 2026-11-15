# Phantom IDEA 개발 가이드

- 목적: 이 포크를 안전하게 고치는 방법을 작업별로 적는다. 무엇이 어디서 바뀌었는지는 [FORK-DELTA.md](FORK-DELTA.md), 구조와 기동 흐름은 [ARCHITECTURE.md](ARCHITECTURE.md), 빌드·릴리스·벤치 명령은 [BUILD.md](BUILD.md)가 맡는다. 이 문서는 그 내용을 되풀이하지 않고 링크한다.
- 기준 커밋: 64dc82b3f2
- 작성일: 2026-11-12

표기: `경로:줄`은 기준 커밋에서 직접 연 위치다. "(추정)"은 읽은 사실에서 끌어낸 결론이다. 저장소 루트가 곧 community 루트다(`community/` 디렉터리 없음). 업스트림 문서와 스킬의 `community/...`는 `./...`로 읽는다.

## 1. 작업 전 확인

이 포크의 규칙이다. 근거는 [FORK-DELTA.md](FORK-DELTA.md) 2절의 히스토리 분석과 README 설계 규칙(`git show be60914099:README.md`의 56-60행, 94-98행)이다.

1. **번들은 설정으로 줄이고 소스는 남긴다.** 플러그인을 뺄 때 모듈 소스, `.idea/modules.xml` 항목, 루트 모듈 의존은 그대로 두고 `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:85-107`의 번들 목록에서만 뺀다. 업스트림 병합을 쉽게 하려는 것이다(e2a4ffa4ee 본문). IntelliJ 플러그인을 Phantom-plugins 판으로 바꿀 때도 원본은 이 목록에서만 뺀다(9229667cc7, 2절 (c)). 소스를 실제로 지운 경우는 FORK-DELTA 2.1절의 6개 커밋뿐이다.
2. **JetBrains 서버 호출은 지우지 말고 게이트 뒤에 둔다.** 게이트는 `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:104-109`의 `jetBrainsServicesEnabled`(시스템 프로퍼티 `phantom.jetbrains.services`, 기본 false) 하나다.
3. **패치에 표식 주석을 단다.** 수정한 줄 옆에 `// phantom-idea:` 또는 `// Phantom:`, XML은 `<!-- phantom-idea: -->`. 단 FORK-DELTA 2절에 따르면 바이너리를 뺀 수정 파일(상태 M, 바이너리 제외) 96개 중 47개에 두 표식(`phantom-idea:`, `Phantom:`)이 모두 없고, 그중 38개는 'phantom'이라는 글자 자체가 없다(대부분 줄 삭제만 한 파일). 델타를 찾을 때는 표식 검색이 아니라 `git diff d2faebf601 HEAD`를 기준으로 삼는다.
4. **새 코드는 `dev.phantom.*` 패키지, 새 모듈은 `phantom.*` 이름**을 쓴다(c06e071a51). 예외는 업스트림 패키지 안의 `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/FoojayJdkList.kt`, `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/GHDeviceFlow.kt`, `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt`다.
5. **시작 경로를 무겁게 하지 않는다.** README 설계 규칙: 프로젝트가 그 기술을 쓸 때만 동작, 증분 갱신, EDT 작업·불필요한 시작 활동·시작 경로의 네트워크 금지. 로드맵 공통 원칙(`docs/phantom/design/2026-10-16-roadmap.md:50-51`)도 같다. 현재 코어에는 Phantom `postStartupActivity`가 2개 있다(`platform/platform-resources/src/META-INF/PlatformExtensions.xml:841`, `:843`). 정적 게이트(3절)는 시작 활동 등록 수가 하나라도 늘면 실패한다.
6. **클린룸.** intellij-community API와 공개 스펙만 쓴다. 디컴파일한 JetBrains 코드를 옮기지 않고, Ultimate 플러그인을 번들하지 않으며, 라이선스 검사를 건드리지 않는다(README 94-98행, 로드맵 `:53`).
7. **업스트림 저장소 규칙은 그대로다.** `*.iml`이 원본이고 정규 형식을 지킨다(주석·재정렬·끝 줄바꿈 추가 금지), 사용자에게 보이는 문자열은 `*.properties`에 둔다([AGENTS.md](../../AGENTS.md)). 기존 Phantom 코드의 하드코딩 문구 39개는 a1313a347c가 리소스 번들로 옮겼다(빌드 전, 5절 4번). 새 Phantom 문구는 `platform/platform-impl/resources/messages/PhantomIdeBundle.properties`(코드는 `dev.phantom.ide.PhantomIdeBundle`, platform-impl)나 `platform/lang-impl/resources/messages/PhantomLangBundle.properties`(`dev.phantom.ide.PhantomLangBundle`, lang-impl)에 키 `phantom.<기능>.<무엇>`으로 넣는다. 번들 이름이 모듈마다 다른 것은 platform-impl과 lang-impl이 코어 클래스 로더를 같이 쓰기 때문이다. 업스트림 번들에 넣어야 하는 것(도구 창 제목은 `IdeBundle`, GitHub 플러그인은 `GithubBundle`)은 파일 끝 `# phantom-idea` 아래에 둔다.
8. **변경마다 [CHANGELOG.md](../../CHANGELOG.md) `미배포` 아래에 항목을 단다**(4절).

## 2. 작업별 방법

### (a) 번들에서 플러그인 빼기

- 언제: 업스트림 플러그인을 Phantom 기본 설치에서 빼고 싶을 때.
- 파일: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:85-107`(`.removing(...)` 사슬), 이유 주석 `:77-84`. 빼려는 ID가 업스트림 목록 어디에 있는지는 `platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:14-58`(`IDEA_BUNDLED_PLUGINS`)과 그것이 포함하는 `DEFAULT_BUNDLED_PLUGINS`(`platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/ProductModulesLayout.kt:23-39`)에서 찾는다.
- 순서: (1) `.removing("<모듈>")` 한 줄을 더한다. (2) `:77-84` 주석에 이유를 한 줄 쓴다. (3) 다른 번들 플러그인이 그 모듈에 의존하는지 확인한다(커밋 본문들이 "nothing bundled depends on it"으로 적은 확인). (4) 제품 콘텐츠 모듈이면 `.removing`이 아니라 `intellijCommunityBaseFragment()`(`:243-281`)의 `module(...)` 줄을 지운다(770810693c, `:266` 주석). 소스, `.idea/modules.xml`, `intellij.idea.community.main.iml`은 건드리지 않는다. 예: 7c43a36532는 이 파일 한 개만 바꿨다.
- 검증: 빌드 후 `Phantom IDEA.app/Contents/plugins/`에 그 플러그인이 없는지 본다. 정적 게이트(3절)는 감소를 실패로 보지 않는다.

### (b) 뺀 플러그인 되돌리기

- 업스트림 플러그인(소스가 남아 있는 21개, FORK-DELTA 3.2절): `.removing` 줄과 주석을 지운다. 제품 콘텐츠 모듈이면 `module(...)` 줄을 되살린다. 예: a3e497f72d가 `intellij.ide.startup.importSettings`를 `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:267`로 되살렸다(2줄 변경). Phantom-plugins 판이 대신 들어간 7개(`:101-107`)와 Lombok은 그 판을 `PHANTOM_PLUGINS_BUNDLED`(`:31-33`)와 `phantom-build.sh:33`에서도 빼야 한다. 둘을 같이 넣으면 같은 ID와 모듈을 두 번 차지한다(9229667cc7 본문). 04dba66c3a 뒤로는 그 경우 별칭 대체로 Phantom-plugins 판만 로드될 것으로 보인다(추정, FORK-DELTA 3.12절).
- 소스까지 지운 모듈(FORK-DELTA 2.1절: 프로모 3개, Toolbox, JetBrains OAuth 등): 지운 커밋을 되돌리고 플러그인 기술자(`META-INF/plugin.xml`) 등록과 `.idea/modules.xml` 항목까지 함께 되살려야 한다(추정, 해 본 적 없음).
- Phantom-plugins로 옮긴 플러그인(FORK-DELTA 5.2절 8개, Bytecode Viewer·WebP·Modal Commit 대체판): 이 리포에 되돌리지 않는다. IDE의 Plugins 화면에서 공식 저장소 `newkayak12/Phantom-plugins`로 설치한다.
- 검증: 번들 플러그인 수가 늘면 정적 게이트의 `bundled_plugins`(허용 +0)가 실패한다. 의도한 증가면 같은 커밋에서 `docs/phantom/bench/static-baseline.json`을 새 측정값으로 바꾸고 커밋 본문에 이유를 쓴다. 단 현재 baseline(26.10.27, `bundled_plugins` 56)은 7c43a36532가 `.removing` 5건을 더하기 전 값이라, 되돌리는 플러그인이 5개 이하면 게이트가 실패하지 않는다(추정, 실행하지 않음). 되돌리기 전에 baseline을 새로 측정해 둔다.

### (c) Phantom 모듈·번들 플러그인 추가 (이 리포 vs Phantom-plugins)

- 먼저 어디에 둘지 정한다. 기본은 별도 리포 `newkayak12/Phantom-plugins`다. 거기서 만든 플러그인은 필요할 때 설치하거나, 아래 "Phantom-plugins 판을 번들에 넣기"처럼 이 리포의 빌드가 번들에 넣는다. 이 리포에서 만든 플러그인 10개 중 8개가 결국 그리로 옮겨졌고 `phantom-sync`, `phantom-theme` 2개만 남았다(FORK-DELTA 2절 "트리 안에서 만들고 밖으로 옮긴다", 5.2절). 이 리포에 두는 것은 플랫폼 패치가 필요한 것(GitHub 플러그인 저장소, `PhantomMemorySizer`, Endpoints 창 — 플랫폼 모듈 안에 있음)과 제품 기본값인 것(`phantom-theme`, JetBrains 서비스를 대체하는 `phantom-sync`)뿐이다.
- 이 리포에 번들 플러그인을 더할 때 함께 고치는 파일(5f1819d799가 한 번에 바꾼 목록):

| 파일:줄 | 내용 |
|---|---|
| `plugins/phantom-<name>/phantom.<name>.iml`, `plugins/phantom-<name>/resources/META-INF/plugin.xml` | 모듈과 플러그인 ID `dev.phantom.<name>`. 콘텐츠 모듈은 `<content>`에 나열(`plugins/phantom-sync/resources/META-INF/plugin.xml:15-17`) |
| `.idea/modules.xml:359-362` | 프로젝트 모듈 등록. 기존 Phantom 항목 옆에 넣는다 |
| `intellij.idea.community.main.iml:138-140` | 루트 모듈 RUNTIME 의존(소스 실행·테스트용) |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:20-24` | `PHANTOM_BUNDLED_PLUGINS`에 한 줄 |
| `plugins/phantom-<name>/BUILD.bazel`, `BUILD.bazel:284-286`, `:473-475`, `build/bazel-generated-file-list.txt:1475-1478` | Bazel 파일. `.iml`에서 생성한다 |

- Bazel 파일 생성기: 이 리포에 있는 것은 `build/jpsModelToBazelCommunityOnly.cmd`뿐이다(업스트림 문서의 `./build/jpsModelToBazel.cmd`는 없다). 확인한 것: 스크립트가 `RUN_WITHOUT_ULTIMATE_ROOT=true`를 켜고(`:8`) `platform/build-scripts/bazel`에서 `bazel.cmd run //:jps-to-bazel`을 실행한다(`:11-12`). 대상은 `platform/build-scripts/bazel/BUILD.bazel:115`이고, 문서상 각 `BUILD.bazel`과 `build/bazel-generated-file-list.txt`를 쓴다(`platform/build-scripts/bazel/README.md:248-252`). 확인하지 못한 것: 이 리포에서 실제로 끝까지 도는지(실행해 보지 않음), Phantom 커밋이 이 명령으로 Bazel 파일을 만들었는지(FORK-DELTA 6.1절). 같은 README `:250`이 만든다는 `build/bazel-targets.json`이 트리에 없어 판단할 수 없다. 돌지 않으면 5f1819d799처럼 같은 형식(`### auto-generated section` 표식, 예 `plugins/phantom-theme/BUILD.bazel:3`)을 손으로 맞춘다.
- 검증: `./tests.cmd --module <새 테스트 모듈> --test 'dev.phantom.<name>.*'`(컴파일 포함), 빌드한 앱의 `Contents/plugins/`에 새 플러그인 존재. 정적 게이트는 `bundled_plugins`와 시작 등록 증가를 실패로 보므로 (b)처럼 기준선을 의도적으로 갱신한다.
- Phantom-plugins 판을 번들에 넣기(IntelliJ 플러그인 대체 포함, 9229667cc7이 한 방법):
  1. Phantom-plugins 쪽: 디렉터리 `<dir>`의 `:<dir>:buildPlugin`이 `<dir>/build/distributions/<dir>-<version>.zip`을 만들고(`<dir>/build.gradle.kts`의 `version = "..."` 줄에서 버전을 읽음), 그 zip이 `<dir>/lib`로 풀려야 한다(`phantom-build.sh:36-43`, `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:191-192`). IntelliJ 원본을 대체하는 판은 `dev.phantom.*` ID에 원본 ID를 별칭으로, 원본 콘텐츠 모듈 이름을 그대로 둔다(`IdeaCommunityProperties.kt:26-30` 주석). 그래야 원본에 의존하는 번들 플러그인이 대체판으로 해석된다.
  2. 이 리포: `PHANTOM_PLUGINS_BUNDLED`(`IdeaCommunityProperties.kt:31-33`)와 `phantom-build.sh:33`의 `bundled_from_plugins`에 같은 `<dir>`를 더한다. 두 목록은 주석으로만 서로를 가리킨다.
  3. 원본이 번들에 있으면 `.removing("<원본 모듈>")`을 `:100` 주석 아래에 더한다(`:101-107`). `PHANTOM_BUNDLED_PLUGINS`에 있던 것이면 그 줄을 지운다(`intellij.lombok` 사례).
  4. 빌드 설정이 원본 플러그인 디렉터리 이름을 쓰는 곳을 찾아 고친다. 예: `CE_CLASS_VERSIONS`의 커버리지 런타임 경로 `plugins/java-coverage/...` → `plugins/coverage/...`(`platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:68`, a199380d7d). `./tools/rg.cmd -n 'plugins/<원본 디렉터리>/' platform/build-scripts build`로 찾는다.
  5. 릴리스 러너는 Phantom-plugins의 `origin/main`을 빌드하므로(`.github/workflows/phantom-release.yml:52-53`) 그쪽 변경을 먼저 main에 올린다.
  - 검증: `./phantom-build.sh`가 `out/phantom-plugins/<dir>/lib`를 만들고 빌드한 앱의 `Contents/plugins/<dir>`가 있는지 본다. 원본과 함께 설치됐을 때의 동작은 FORK-DELTA 3.12절의 테스트로 본다.

### (d) JetBrains 서버 호출 막기·게이트 걸기

- 언제: 사용자 조작 없이 JetBrains 서버로 나가는 호출을 찾았을 때. 후보 목록은 ARCHITECTURE 6절 "게이트 밖에 남은 세 곳"과 5절 표.
- 실제 예 (98673d9e9a): `plugins/stats-collector/src/com/intellij/stats/completion/sender/SenderPreloadingActivity.kt:33-36`은 확장 생성자에서 `if (!MarketplaceRequests.jetBrainsServicesEnabled) throw ExtensionNotApplicableException.create()`로 등록 자체를 막는다. 함수 단위 예는 `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:403`(`getBrokenPlugins`가 null 반환)와 `platform/feedback/src/com/intellij/platform/feedback/impl/GeneralFeedbackSubmit.kt:123-126`(`submitFeedback`이 요청도 `RegionUrlMapper` 조회도 없이 바로 돌아옴, 6e71ed80c4, 빌드 전). 호출 결과를 기다리는 호출자에게는 콜백을 부르지 않는 반환이 "아무 일도 안 일어남"으로 보이므로, 성공 알림을 띄우는 호출자(예 `LocalizationFeedbackDialog.kt:132`)는 같은 조건으로 알림도 건너뛰게 한다.
- 순서: (1) 호출 지점을 찾는다(`./tools/rg.cmd -n 'jetbrains\.com'` 등). (2) 게이트로 막을지, 플래그 없이 끊을지 정한다. 사용자 가치가 없는 홍보·링크는 플래그 없이 지웠다(FORK-DELTA 3.3.2절). (3) 가장 바깥에서 중립값(null, 빈 목록, 미등록)을 돌려준다. 바로 위에 `// phantom-idea:` 주석으로 어디로 가는 호출인지와 켜는 법을 적는다. (4) 앱이 뜨기 전 코드는 게이트 클래스를 쓸 수 없으므로 `Boolean.getBoolean("phantom.jetbrains.services")`를 직접 읽는다(`platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java:196-197`). (5) 그 서비스가 필요한 기존 테스트는 프로퍼티를 켜고 끈다(`plugins/grazie/src/tests.pro/testSrc/com/intellij/grazie/pro/BaseTestCase.kt:34`, `:55`). (6) FORK-DELTA 3.3.1 표와 CHANGELOG "JetBrains 서버 통신"에 적는다.
- 검증: `./tools/rg.cmd -n -e jetBrainsServicesEnabled -e phantom.jetbrains.services -g '*.kt' -g '*.java'`로 새 지점이 나오는지 본다. 동작은 `.vmoptions`에 `-Dphantom.jetbrains.services=true`를 넣었을 때와 비교한다.

### (e) 레지스트리 기본값 변경, 시스템 프로퍼티 추가

- 레지스트리 기본값: 키가 정의된 곳을 고친다. XML `<registryKey ... defaultValue=...>`(예 `platform/platform-resources/src/META-INF/PlatformExtensions.xml:1384`, `platform/feedback/resources/intellij.platform.feedback.xml:83`, 기본값을 바꾼 `platform/tips-of-the-day/resources/intellij.platform.tips.xml:41-44`) 또는 `platform/util/resources/misc/registry.properties`(예 `:2241`). 규칙은 `.agents/skills/registry/SKILL.md`. Phantom 고유 레지스트리 키는 아직 없고 바꾼 것은 업스트림 키의 기본값이다(FORK-DELTA 4.3절). 레지스트리 값에 따라 UI가 달라지면 값을 읽는 쪽도 같이 본다: `tips.of.the.day.feedback`을 nowhere로 둔 6e71ed80c4는 `TipsFeedback.isFeedbackSendingEnabled`(`TipsFeedback.kt:76`)로 `TipPanel.java:140`이 좋아요/싫어요 줄을 숨기게 했다(빌드 전).
- 업스트림 시스템 프로퍼티의 기본값 변경: 읽는 줄의 기본 인자만 바꾼다. 예 `platform/diagnostic/telemetry/src/com/intellij/platform/diagnostic/telemetry/OpenTelemetryUtils.kt:62-67`, `platform/platform-impl/src/com/intellij/openapi/application/impl/NonBlockingReadActionImpl.java:126-127`.
- 새 프로퍼티: 이름은 `phantom.<무엇>`, 기본값은 "JetBrains로 안 보냄·파일 안 씀" 쪽. 예 `platform/lang-impl/src/com/intellij/util/indexing/diagnostic/IndexDiagnosticDumper.kt:70-72`(`SystemProperties.getBooleanProperty(name, false)`). 사용자는 Help | Edit Custom VM Options에 `-D<이름>=<값>`으로 켠다.
- 기록: FORK-DELTA 4.1(신규 `phantom.*`), 4.2(기본값을 바꾼 업스트림 프로퍼티), 4.3(레지스트리) 표와 CHANGELOG. 커밋 본문에 끄고 켜는 플래그를 쓴다(4절).

### (f) 브랜딩·버전 문자열 변경

| 바꿀 것 | 파일:줄 |
|---|---|
| 제품 이름, 회사, 로고·아이콘, 기본 테마 | `community-resources/resources/idea/IdeaApplicationInfo.xml:3`, `:5-8`, `:10` |
| 표시 버전 | 값은 빌드 날짜다. `phantom-build.sh:15`가 YY, MM, DD를 구해 `:46`, `:63`에서 `-Dphantom.version.major=<YY>`, `-Dphantom.version.minor=<MM>.<DD>`로 넘긴다. `ApplicationInfoPropertiesImpl.kt:72-75`가 XML보다 먼저 읽고 `:177-181`이 패키지 안의 `<version>`에 쓴다. 추적 파일 `community-resources/resources/idea/IdeaApplicationInfo.xml:2`는 자리표시자 `0` / `0.0`이므로 고치지 않는다(048852ca23, 빌드 전). 소스에서 실행하면 0.0으로 보인다 |
| 설정 폴더, 산출물·출력 폴더 이름 | `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:202-204`, `:207-208`, `:210` |
| Windows 이름, macOS URL 스킴·번들 ID·앱 이름, Linux 폴더 | `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:289-290`, `:299`, `:302`, `:304`, `:319` |
| Phantom 빌드 번호 `PH-<YYMMDD>.<N>` | `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt`, `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:170-175`, `phantom-build.sh:26-27`, `:46`, `:63` |
| 아이콘 원본 | `community-resources/phantom-brand-src/compose.swift`가 `phantom-hood.png`에서 만든다(빌드가 부르지 않음, BUILD.md 4절) |

- 주의: 번들 ID `dev.phantom.idea`는 `phantom-install.sh:45`, `phantom-sign.sh:18`에도 있다. 설정 폴더 `PhantomIDEA`를 바꾸면 기존 사용자 설정을 못 찾고(추정), `phantom-bench.sh:16`의 경로도 틀어진다. 표시 버전 속성 이름 `phantom.version.major`, `phantom.version.minor`는 `PhantomBuildNumber.kt:21-22`, 플랫폼 브랜치 `262`는 `phantom-build.sh:27`, `:46`, `:63`과 `phantom-release.sh:51-52`에 박혀 있다.
- 검증: FORK-DELTA 3.1절 끝(빌드한 `Info.plist`의 `CFBundleIdentifier`, `CFBundleVersion`, `product-info.json`의 build).

### (g) 업스트림 코드에 플랫폼 패치 넣기

- 최소 diff: 업스트림 줄을 고치기보다 앞에 한 줄을 더한다(조기 반환, 게이트). 함수 본문을 다시 쓰면 병합 때 덩어리 충돌이 난다(FORK-DELTA 6절 "깊게 고친 파일"). 큰 로직은 `dev.phantom.*` 새 파일에 두고 업스트림 파일에서는 호출 한 줄만 둔다(예 `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:679`의 `PhantomMarketplace` 호출, ARCHITECTURE 7절).
- 표식: 패치 줄 바로 위에 `// phantom-idea: <왜, 켜는 법>`. 줄을 지우기만 할 때도 그 자리에 표식 주석을 남긴다(지금 38개 파일에 없는 이유가 이것이다).
- 업스트림 파일을 통째로 지우지 않는다. 지우면 업스트림의 다른 코드가 그 심볼을 계속 쓸 수 있다(0a5ca64795가 `ExternalUpdateManager.TOOLBOX`를 되살린 사례).
- 마무리: 테스트가 있으면 같은 커밋에 넣고, FORK-DELTA의 해당 주제 표와 CHANGELOG에 한 줄을 더한다. 6절 충돌 표에 오를 만한 파일이면 거기도 적는다.

### (h) 새 업스트림 태그로 올리기 (미검증 절차)

이 리포 히스토리에서 한 번도 해 본 적이 없다. 아래는 FORK-DELTA 6절과 저장소 구조에서 세운 절차이고 전부 (추정)이다.

1. 전제: `d2faebf601`은 업스트림 태그 트리를 README 없이 한 번에 넣은 루트 커밋이다(`git rev-list --max-parents=0 HEAD`). 업스트림 히스토리가 없으므로 업스트림 태그를 그대로 `git merge`하면 공통 조상이 없다.
2. 별도 워크트리에서 `d2faebf601`을 부모로 하는 가져오기 브랜치를 만든다. 업스트림 새 태그를 `--depth 1`로 받아 그 트리로 작업 트리를 바꾸고(README.md 제외) "Import ... idea/<새 태그>"로 커밋한다. 그러면 Phantom 브랜치와의 병합 기준이 `d2faebf601`이 된다.
3. 그 브랜치를 Phantom 작업 브랜치에 병합한다. 충돌은 FORK-DELTA 6절 표대로 푼다: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`는 Phantom 식을 유지하고 새 `IDEA_BUNDLED_PLUGINS` 항목마다 뺄지 정한다. `community-resources/resources/idea/IdeaApplicationInfo.xml:2`(업스트림이 올린 `<version>`과 충돌한다)는 Phantom의 자리표시자 줄(`major="0" minor="0.0"`)을 택한다. 표시 버전은 빌드 속성이 정하므로 이 줄의 값은 빌드에 쓰이지 않는다. `build.txt`(`262.SNAPSHOT`)도 Phantom 값을 택한다. `.idea/modules.xml`과 `*.iml`은 원형을 지키며 손으로 합친다.
4. 병합하지 말고 다시 만들 파일(FORK-DELTA 6.1절): 루트·모듈 `BUILD.bazel`, `build/bazel-generated-file-list.txt`는 `build/jpsModelToBazelCommunityOnly.cmd`로 다시 만든다. `platform/built-in-server/api-dump-unreviewed.txt`의 재생성 명령은 확인되지 않았다.
5. 의미 충돌 점검: 지운 업스트림 파일 35개를 새 코드가 참조하는지 컴파일로 본다. Phantom-plugins 판으로 바꾼 7개(FORK-DELTA 3.2절)는 업스트림이 그 플러그인의 ID나 콘텐츠 모듈 이름을 바꾸면 Phantom-plugins 판도 맞춰야 한다(추정). `pluginInitContextIdConflictResolver.kt`가 충돌하면 FORK-DELTA 3.12절의 동작을 다시 맞추고 그 테스트를 돌린다. `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt` 등에서 새 JetBrains 호출 경로가 생겼는지 `rg`로 다시 찾는다. 빠진 플러그인 ID가 이름을 바꿨으면 `.removing`이 조용히 효과를 잃는다. `platform/indexing-impl/src/com/intellij/find/ngrams/TrigramIndex.java:94`의 `shardlessVersion`은 업스트림 값보다 크게 다시 정한다. Phantom 테마는 `python3 -I plugins/phantom-theme/tools/generate-themes.py`로 다시 만든다.
6. 플랫폼 브랜치가 바뀌면(262 → 다음) `phantom-build.sh:27`, `:46`, `:63`, `phantom-release.sh:51-52`의 `262`와 `build.txt`를 고친다. Phantom-plugins 플러그인도 새 플랫폼으로 다시 빌드해야 할 수 있다. 저장소 안의 `android` 클론도 같은 태그로 바꾼다(BUILD.md 2절).
7. 3절의 테스트, 빌드, 정적 게이트를 모두 돌리고 CHANGELOG에 "기준 소스" 항목을 더한다. 이 절차를 실제로 한 뒤 이 절의 "(미검증)"을 고친다.

## 3. 검증

| 확인 | 명령 | 근거 |
|---|---|---|
| Phantom 테스트 4개 | `./tests.cmd --module phantom.sync.tests --test dev.phantom.sync.GitRepoCommunicatorTest`<br>`./tests.cmd --module intellij.platform.tests --test dev.phantom.ide.memory.MemoryPlanTest`<br>`./tests.cmd --module intellij.platform.lang.tests --test dev.phantom.ide.endpoints.EndpointRequestsTest`<br>`./tests.cmd --module intellij.platform.tests --test com.intellij.ide.plugins.GitHubPluginRepositoryTest` | BUILD.md 8절. 모듈 이름은 `.iml`에서 읽었고 실행해 보지 않았다 |
| 패치별 테스트 | `./tests.cmd --module intellij.platform.lang.tests --test com.intellij.openapi.projectRoots.impl.jdkDownloader.FoojayJdkListTest`<br>`./tests.cmd --module intellij.platform.lang.tests --test com.intellij.util.indexing.FileTypeChangeRescanTest`<br>`./tests.cmd --module intellij.terminal.tests --test 'com.intellij.terminal.tests.agent.*'` | FORK-DELTA 3.3, 3.5절 |
| 플러그인 별칭 대체(04dba66c3a) | `./tests.cmd --module intellij.platform.tests --test com.intellij.ide.plugins.PluginInitializationSelectPluginsToLoadTest` | FORK-DELTA 3.12절. 모듈 이름은 `platform/platform-tests/intellij.platform.tests.iml`(`testSrc` 소스 폴더, `:7`)에서 읽었다. 업스트림 테스트 클래스에 04dba66c3a가 1개를 바꾸고 3개를 더했다(`PluginInitializationSelectPluginsToLoadTest.kt:444-510`). 이 문서를 쓰며 실행하지 않았다 |
| 이 브랜치가 더한 테스트 2개(6e71ed80c4와 5185f60295의 보정, 048852ca23) | `./tests.cmd --module intellij.featuresTrainer.tests --test training.ui.FeedbackSubmissionGateTest`<br>`./tests.cmd --module intellij.platform.buildScripts.tests --test org.jetbrains.intellij.build.ApplicationInfoPropertiesTest` | BUILD.md 8절. 모듈 이름은 `plugins/ide-features-trainer/intellij.featuresTrainer.tests.iml`, `platform/build-scripts/tests/intellij.platform.buildScripts.tests.iml`에서 읽었다. 둘 다 실행하지 않았다(프로젝트를 아직 빌드하지 않음). 뒤쪽은 기존 테스트에 `phantomVersion`, `phantomVersionWithoutVersionElement`를 더한 것이다(`ApplicationInfoPropertiesTest.java:28-44`) |
| 정적 게이트 | `python3 phantom-bench-report.py static "/Applications/Phantom IDEA.app" > /tmp/static.json`<br>`python3 phantom-bench-report.py compare docs/phantom/bench/static-baseline.json /tmp/static.json` | BUILD.md 7절. 설치된 앱이 필요. 회귀면 종료 코드 1 |
| 런타임 벤치 | `./phantom-bench.sh --runs 5 --label base` 후 `compare` | 런타임 기준선 `docs/phantom/bench/baseline.json`이 없어 지금은 비교 불가(5절 표) |
| 빌드 | `./phantom-build.sh`(`PHANTOM_WINDOWS=0`이면 DMG만) | BUILD.md 3절. Phantom-plugins 체크아웃이 이 리포 옆(`../Phantom-plugins`)이나 `PHANTOM_PLUGINS_REPO`에 있어야 한다(9229667cc7, BUILD.md 2절). 048852ca23 이후 추적 파일을 고치지 않으므로 빌드 뒤 `git status`가 깨끗해야 한다(빌드 전이라 확인하지 못했다). 빌드는 `out/phantom-plugins/`를 만든다(무시되는 `out/` 아래) |
| 컴파일만 | `bazel build <대상>`(AGENTS.md). 문서·JSON만 바꿨으면 생략 | AGENTS.md |

- `./tests.cmd`는 Bazel 컴파일을 포함한다(`tests.cmd:15-28`). `--test`에는 FQN이나 와일드카드가 필요하다.
- 소스에서 IDE를 실행할 때: 개발 모드 레이아웃도 `getAdditionalPluginPaths()`를 부르므로(`platform/build-scripts/src/org/jetbrains/intellij/build/dev/PluginBuilder.kt:74` → `IdeaCommunityProperties.kt:187-194`) `out/phantom-plugins/<dir>/lib`가 없으면 실패할 것으로 보인다(추정, 실행하지 않음). 그 디렉터리를 만드는 것은 `phantom-build.sh:29-44`뿐이다.
- 자동화되지 않은 것: CI는 테스트도 게이트도 돌리지 않는다. 워크플로는 `.github/workflows/phantom-release.yml` 하나이고 수동 릴리스만 한다. `phantom-build.sh`, `phantom-release.sh`도 벤치·게이트·테스트를 부르지 않는다(BUILD.md 9절 6번). 위 확인은 사람이 커밋 전에 직접 돌린다.

## 4. 커밋·변경 이력 규칙

- 제목: 평범한 영어 명령문 한 줄. 무엇이 달라지는지를 쓴다. 예: `Download JDK lists JDKs from foojay, not JetBrains' feed`(be60914099), `Drop Toolbox App integration`(43b28af952).
- 접두어: 한 플러그인·도구에 한정되면 `phantom-spring:`, `phantom-install:`, `Phantom bench:`, `HTTP client:`, `Workspaces:`처럼 대상 이름을 붙인다(히스토리에서 읽은 관례). 플랫폼 전반 변경은 접두어 없이 쓴다.
- 본문: 메커니즘(무엇을 어디서 막거나 바꿨는지), 이유, 되살리는 플래그(`-Dphantom.jetbrains.services=true` 등), 측정값이 있으면 숫자. be60914099 본문이 전형이다.
- 업스트림 커밋 규칙은 쓰지 않는다: `.agents/skills/commits/SKILL.md`는 JetBrains 형식(YouTrack 티켓, SafePush 검증)을 요구하지만 이 포크 히스토리는 그 형식을 쓰지 않는다.
- CHANGELOG: `CHANGELOG.md:7-13`의 규칙대로 같은 커밋에서 `미배포` 아래 고정 카테고리에 한 줄을 더하고, 해시는 다음 커밋에서 채운다. 릴리스 태그가 생기면 `미배포` 항목을 그 버전 아래로 옮긴다.
- 문서: 패치 지점이 바뀌면 FORK-DELTA의 해당 표를, 빌드 스크립트가 바뀌면 BUILD.md를 고친다. 유지 규칙은 [README.md](README.md) "문서 유지 규칙".

## 5. 알려진 문제 모음

여러 문서에 흩어진 열린 문제를 한 표로 모았다. 처음 모을 때는 고치지 않았고, 이후 고친 항목은 문제 칸을 "고침:"으로 시작한다. 새로 찾은 항목은 넣지 않았다. 상세는 "문서" 열을 본다.

| # | 문제 | 위치 | 문서 |
|---|---|---|---|
| 1 | GitHub 브라우저 로그인 client id가 비어 있어 `gh` 로그인이나 `-Dphantom.github.oauth.client.id` 없이는 예외 | `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/GHDeviceFlow.kt:29`, `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/ui/GHOAuthCredentialsUi.kt:61-62` | ARCHITECTURE 문제 1 |
| 2 | Tips of the Day 좋아요/싫어요가 게이트 없이 JetBrains 스테이징으로 전송 | `platform/tips-of-the-day/resources/intellij.platform.tips.xml:40-42`, `platform/tips-of-the-day/src/com/intellij/ide/util/TipsFeedback.kt:50`, `platform/feedback/src/com/intellij/platform/feedback/impl/GeneralFeedbackSubmit.kt:24` | ARCHITECTURE 문제 2 |
| 3 | 고침: 안내가 가리키던 없는 `./build/jpsModelToBazel.cmd`를 `./build/jpsModelToBazelCommunityOnly.cmd`로 바꿈(`.ai/guide.md`, `.ai/tool-permissions.json`, 두 skills 폴더의 5개 스킬, `plugins/maven/.ai/index.md`, `build/libraries-dashboard`). `projectModelUpdater.kt:55`는 ultimate 루트가 있을 때만 실행되어 그대로 둠. 이 리포에서 생성기가 끝까지 도는지는 여전히 실행하지 않아 미확인. ARCHITECTURE 문제 3과 FORK-DELTA 6.1의 "남은 파일" 목록은 고치지 않음 | `.ai/guide.md:30`, `.agents/skills/module-dependencies/SKILL.md:24`, `:31` | ARCHITECTURE 문제 3, FORK-DELTA 6.1 |
| 4 | 사용자에게 보이는 문자열이 코드에 박혀 있음(`*.properties` 규칙 위반) | `platform/platform-impl/src/dev/phantom/ide/plugins/GitHubTokens.kt:84`, `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/ui/GHOAuthCredentialsUi.kt:56-66` | ARCHITECTURE 문제 4 |
| 5 | 빌드가 추적 파일 버전을 고치는데 릴리스는 커밋 없이 `HEAD`를 태그. 태그 커밋의 버전과 릴리스 버전이 다를 수 있음 | `phantom-build.sh:16`, `phantom-release.sh:61`, `:64` | BUILD 9.1, ARCHITECTURE 문제 5 |
| 6 | 런타임 벤치 기준선 파일이 없어 `compare` 불가, 허용 오차 미보정 | `docs/phantom/bench/README.md:10`, `:32`, `:39-40` | BUILD 9.2, FORK-DELTA 3.10 |
| 7 | 고침: 앱·설정·픽스처 경로를 `PHANTOM_APP`, `PHANTOM_CONFIG_DIR`, `PHANTOM_BENCH_FIXTURE`로 바꿀 수 있음(기본값은 전과 같음). 기본값·재정의 해석만 확인했고 벤치와 설치 전체는 실행하지 않음 | `phantom-install.sh:17`, `phantom-bench.sh:19-20`, `:26`, `phantom-sign.sh:13` | BUILD 9.3 |
| 8 | 고침: 인증서가 없으면 `phantom-sign.sh`가 stderr 메시지와 종료 코드 1로 실패. `PHANTOM_SIGN_OPTIONAL=1`이면 경고 후 0이고 `phantom-install.sh`가 이 값을 켬. 헤더 주석의 키체인도 바로잡음. 실제 인증서로 서명하는 경로는 실행하지 않음 | `phantom-sign.sh:7-11`, `:19-27`, `phantom-install.sh` 서명 호출 | BUILD 9.4 |
| 9 | GitHub Actions용 건너뛰기 목록이 로컬 빌드에도 항상 적용(upstream 동작) | `build/src/OpenSourceCommunityInstallersBuildTarget.kt:18-23`, `:38`, `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:58-59` | BUILD 9.5 |
| 10 | 테스트·벤치·정적 게이트가 CI와 빌드·릴리스 스크립트에 연결되지 않음 | `.github/workflows/phantom-release.yml` | BUILD 9.6 |
| 11 | FUS 업로드 주소는 외부 jar 안에 있어 확인 못 함. 전송은 게이트가 아니라 사용자 동의로 결정 | `platform/statistics/uploader/src/com/intellij/internal/statistic/eventLog/connection/EventLogSettingsClient.kt:96-98` | ARCHITECTURE 6절 열린 질문 (a) |
| 12 | **변경됨, 빌드 대기 (6e71ed80c4, 5185f60295)**: 게이트 밖 피드백 전송(대화상자 경로)과 지역 URL 매핑을 `submitFeedback` 맨 앞 게이트로 막음. 5185f60295가 `OnboardingFeedbackForm.kt:279`의 감사 알림도 같은 조건으로 건너뛰게 함. 테스트의 요청 대기 시간은 15초라 그보다 느린 지역 매핑 조회는 고장 난 게이트를 통과시킬 수 있음 | `platform/feedback/src/com/intellij/platform/feedback/impl/GeneralFeedbackSubmit.kt:116-126`, `:135`, `platform/platform-impl/src/com/intellij/ide/RegionUrlMapper.java:42` | ARCHITECTURE 6절 (b) |
| 13 | 프레임워크 라이브러리 다운로드가 jetbrains.com 사용(UI를 열 때만으로 보임, 추정) | `platform/ide-core/src/com/intellij/facet/frameworks/LibrariesDownloadConnectionService.java:6-7` | ARCHITECTURE 6절 (b) |
| 14 | remoteDev 다운로드 주소가 남아 있음(Code With Me·Gateway 설치 시에만 쓰임, 추정) | `platform/remoteDev-util/src/com/intellij/remoteDev/RemoteDevSystemSettings.kt:17-19` | ARCHITECTURE 6절 (b) |
| 15 | 내장 서버가 JetBrains ij-perf origin에 로컬 기동 보고서 읽기를 허용(외부 전송은 아님) | `platform/built-in-server/src/org/jetbrains/ide/StartUpMeasurementService.kt:16-21` | ARCHITECTURE 6절 (b) |
| 16 | 수정 파일 96개 중 47개에 두 표식이 모두 없음(38개는 'phantom' 글자 자체가 없음) | - (`git diff d2faebf601 HEAD`) | FORK-DELTA 2절 |
| 17 | `.removing` 정의를 리포에서 못 찾음. 업스트림이 ID를 바꾸면 조용히 효과를 잃을 수 있음(추정) | `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:86-107` | FORK-DELTA 3.2, 6절 |
| 18 | API 덤프 파일 재생성 명령 미확인 | `platform/built-in-server/api-dump-unreviewed.txt` | FORK-DELTA 6.1 |
| 19 | 게이트를 켜고 끄는 Grazie 테스트의 모듈은 `intellij.grazie.tests.pro`(`plugins/grazie/src/tests.pro/intellij.grazie.tests.pro.iml`의 `testSrc`). 실행은 하지 않음 | `plugins/grazie/src/tests.pro/testSrc/com/intellij/grazie/pro/BaseTestCase.kt:34`, `:55` | FORK-DELTA 3.3 |
| 20 | Phantom 파일인 FoojayJdkList 머리에 JetBrains 저작권 문구가 있어 업스트림 파일로 오해하기 쉬움 | `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/FoojayJdkList.kt:1` | FORK-DELTA 5.1 |
| 21 | phantom-icons의 ID가 삭제 직전 플러그인 기술자는 `dev.phantom.icons`, 1570329708 본문과 CHANGELOG는 `dev.phantom.iconpack` | `CHANGELOG.md:25` | FORK-DELTA 5.2 |
| 22 | 기준 커밋 README의 "Done/Planned" 표, "Apple silicon only" 등이 HEAD와 어긋남 | `git show be60914099:README.md` 31-54, 113, 136행 | FORK-DELTA 7절 |
| 23 | 로드맵 S1–S5 "완료"는 이동 전 상태. 본문은 그대로 두고 머리에 상태 메모만 더함 | `docs/phantom/design/2026-10-16-roadmap.md:38-42` | FORK-DELTA 7절 |
| 24 | Grazie 언어 파일(LanguageTool, Hunspell), JBR 런타임 선택 피드, Git 설치 목록 피드가 게이트 없이 JetBrains에서 받음. 호출 시점(UI 조작 때만인지)은 확인하지 못함 | `plugins/grazie/src/main/kotlin/com/intellij/grazie/GraziePlugin.kt:20`, `:25`, `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/RuntimeChooserJbr.kt:43`, `plugins/git4idea/backend/src/config/GitDownloadAndInstall.kt:20` | ARCHITECTURE 6절 |
| 25 | 일부 고침: baseline은 그대로 낡았지만(26.10.27 빌드, 그 뒤 번들 목록이 바뀜) 이제 `compare`가 `STALE BASELINE`을 stderr에 쓰고 종료 코드 2로 끝나 놓칠 수 없음. 다시 만들려면 빌드된 앱이 필요해 재생성은 하지 않았고 명령은 `docs/phantom/bench/README.md` Baselines에 적음 | `docs/phantom/bench/static-baseline.json`(4b3ffd9498), `phantom-bench-report.py`의 `stale_static_baseline`, `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt` | BUILD 7절 |
