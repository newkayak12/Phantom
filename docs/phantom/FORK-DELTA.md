# Phantom 포크 델타

- 목적: Phantom이 업스트림 intellij-community와 어디서 어떻게 다른지 적는다. 이 포크를 안전하게 고치고 새 업스트림 태그를 병합할 때 본다.
- 기준 커밋: 51a03a83f7 (업스트림 기준 d2faebf601 = idea/262.10968.63)
- 작성일: 2026-11-17

이 문서는 d2faebf601과 PR #5 브랜치의 표식 커밋(1절) 사이의 `git diff`에서 만들고, 51a03a83f7에서 그 앞에 PR 없이 들어온 일곱 커밋(2cff0f911e..588cd923ce)을 반영했다. 커밋 메시지는 이유를 찾을 때만 썼다. `path:line`은 파일을 열어 확인한 위치다. 51a03a83f7에서 다시 연 것은 일곱 커밋이 바꾼 파일(`IdeaCommunityProperties.kt`, `BaseIdeaProperties.kt`, `phantom-build.sh`, `phantom-release.sh`, `phantom-bench.sh`, `PlatformExtensions.xml`, `VmOptionsGenerator.kt`, `JetBrainsProductProperties.kt`, Kotlin `plugin.xml`)의 줄과 이번에 고친 줄이다. 그 뒤 main에 PR 없이 들어온 네 커밋(ce70b07310, d1a97edf7a, cd1fc57a5f, 5641b42107)은 브랜딩 행과 `phantom-install.sh`의 줄에만 반영했다(`phantom-install.sh`는 5641b42107에서 다시 열었다). 그 표식 커밋에서 연 것은 그 밖의 루트 스크립트(`phantom-sign.sh` 제외), `OpenSourceCommunityInstallersBuildTarget.kt`, `MarketplaceRequests.kt`의 Marketplace 필터 줄이다. 나머지는 ded15d8d28에서 연 값이고, PR #5(d421f71ac4)가 표식 주석을 더한 78개 파일(2절)에서는 몇 줄 밀렸을 수 있다. 직접 읽지 않고 미루어 짐작한 내용은 "(추정)"으로 표시한다.

## 1. 요약

d2faebf601은 업스트림 태그 소스를 한 번에 들여온 루트 커밋이다. 업스트림 히스토리는 이 리포에 없다. 그 뒤 포크 커밋은 136개다(병합 커밋 6개 포함). 이 절의 숫자(커밋 수, 파일 수, 줄 수)는 모두 d2faebf601부터 PR #5 브랜치의 표식 커밋(브랜치 `fix/known-problems-batch3`의 "Mark every unmarked fork patch in upstream files with a phantom-idea comment")까지 기준이다. PR #5는 스쿼시 머지되어 main에는 그 커밋 대신 스쿼시 커밋 d421f71ac4가 있다. 그 뒤의 문서 커밋은 `docs/`와 루트 문서의 줄 수만 바꾼다. 그 뒤 코드 커밋(일곱 커밋, PR #4·#5·#7·#3의 나머지, ce70b07310, d1a97edf7a, cd1fc57a5f, 5641b42107)으로 바뀐 숫자는 다시 세지 않았다.

### 1.1 상태별

| 구분 | 이름 변경 감지 끔 (`--no-renames`) | 기본 (이름 변경 감지) |
|---|---|---|
| 추가(A) | 100 | 98 |
| 수정(M) | 135 | 135 |
| 삭제(D) | 35 | 33 |
| 이름 변경(R) | - | 2 |
| 합계 | 270 파일, +16541 / -3080 | 268 파일, +16482 / -3021 |

이름 변경 2건(`java/java-promo/BUILD.bazel` → `plugins/phantom-sync/core/BUILD.bazel`, `plugins/kotlin/onboarding-promoter/intellij.kotlin.onboarding.promoter.iml` → `plugins/phantom-sync/core/phantom.sync.core.iml`)은 git이 내용 유사도로 짝지은 결과다. 실제로는 삭제와 추가다. 아래 표는 `--no-renames` 기준이다.

### 1.2 최상위 디렉터리별 (`--no-renames`)

| 디렉터리 | 추가 | 수정 | 삭제 | 줄 +/- |
|---|---|---|---|---|
| `.agents/` | 0 | 5 | 0 | +13 / -7 |
| `.ai/` | 0 | 2 | 0 | +2 / -2 |
| `.claude/` | 0 | 5 | 0 | +13 / -7 |
| `.github/` | 1 | 0 | 4 | +61 / -144 |
| `.idea/` | 0 | 1 | 0 | +4 / -3 |
| 루트 파일 | 9 | 4 | 0 | +1310 / -13 |
| `build/` | 0 | 16 | 0 | +104 / -33 (이미지 10개는 바이너리) |
| `community-resources/` | 10 | 1 | 0 | +156 / -6 |
| `docs/` | 31 | 0 | 0 | +7460 / -0 |
| `idea/` | 0 | 1 | 0 | +20 / -12 |
| `java/` | 0 | 1 | 10 | +1 / -677 |
| `platform/` | 23 | 71 | 7 | +4749 / -1252 |
| `plugins/` | 25 | 28 | 14 | +2611 / -924 |
| `tools/` | 1 | 0 | 0 | +37 / -0 |

루트 파일은 추가 9개(`README.md`, `README.ko.md`, `CHANGELOG.md`, `phantom-build.sh`, `phantom-install.sh`, `phantom-release.sh`, `phantom-sign.sh`, `phantom-bench.sh`, `phantom-bench-report.py`)와 수정 4개(`AGENTS.md`, `BUILD.bazel`, `build.txt`, `intellij.idea.community.main.iml`)다. 이 숫자 뒤에 ce70b07310가 업스트림 루트 파일 두 개를 고쳤다: `LICENSE.txt`는 JetBrains Open-Source Build Terms에서 Apache License 2.0 원문으로 바뀌었고, `NOTICE.txt`에는 JetBrains 고지 앞뒤로 포크 이름, 수정 파일의 `phantom-idea:` 표식, 서드파티 라이선스 위치, JetBrains와 무관하다는 문구가 더해졌다(d1a97edf7a이 그 이름을 Phantom으로 바꿨다). `.agents/`, `.ai/`, `.claude/`의 수정 12개는 PR #4(f74262e02d)의 안내 문구 수정이다. `docs/`는 전부 Phantom 문서이고 코드에 영향이 없다. 코드 델타는 `platform/`과 `plugins/`에 몰려 있다. ded15d8d28 뒤 늘어난 줄 대부분은 PR #5(d421f71ac4)의 표식 주석 137줄, Kotlin 게시(ac9ddb534c), 스크립트 수정이다.

### 1.3 숫자를 다시 만드는 명령

```bash
git rev-list --count d2faebf601..HEAD                  # 포크 커밋 수 (136, 병합 6, HEAD = PR #5 브랜치의 표식 커밋)
git diff --shortstat d2faebf601 HEAD                   # 268 files changed, 16482 insertions(+), 3021 deletions(-)
git diff --no-renames --shortstat d2faebf601 HEAD      # 270 files changed, 16541 insertions(+), 3080 deletions(-)
git diff --no-renames --name-status d2faebf601 HEAD | cut -c1 | sort | uniq -c
# 디렉터리별 A/M/D
git diff --no-renames --name-status d2faebf601 HEAD \
  | awk -F'\t' '{p=$2; split(p,a,"/"); d=(index(p,"/")?a[1]"/":"(root)"); c[d" "$1]++} END{for(k in c) print k, c[k]}' | sort
# 디렉터리별 줄 수
git diff --no-renames --numstat d2faebf601 HEAD \
  | awk '{split($3,a,"/"); d=(index($3,"/")?a[1]"/":"(root)"); i[d]+=$1; x[d]+=$2} END{for(k in i) print k, "+"i[k], "-"x[k]}' | sort
```

## 2. 변경 방식의 원칙

히스토리에서 읽히는 규칙이다.

| 규칙 | 내용 | 근거 |
|---|---|---|
| 번들은 설정으로 줄이고 소스는 남긴다 | 플러그인을 빼도 모듈 소스, `.idea/modules.xml` 항목, 루트 모듈 의존은 그대로 둔다. 번들 목록에서만 뺀다. 예: `intellij.keymap.eclipse`는 번들에서 빠졌지만 `intellij.idea.community.main.iml:143`에 남아 있다. | `IdeaCommunityProperties.kt:91-131`. e2a4ffa4ee 본문 "the sources stay for upstream merges". |
| JetBrains 서비스는 옵트인 플래그 뒤에 둔다 | 자동으로 JetBrains 서버를 부르는 코드는 지우지 않고 `MarketplaceRequests.jetBrainsServicesEnabled`(시스템 프로퍼티 `phantom.jetbrains.services`, 기본 false)로 막는다. | `MarketplaceRequests.kt:108-109`. d7c795f347, fdd9682290, 98673d9e9a, be60914099. |
| 진단 파일은 기본으로 끈다 | 업스트림의 진단 프로퍼티 기본값을 바꾸거나 새 프로퍼티로 막는다. 켜는 방법은 프로퍼티로 남긴다. | 53aa9e1ec7, 7568bf108c, 3718f68ff4. 4.2절. |
| 패치에 표식 주석을 단다 | 수정한 줄 옆에 `// phantom-idea:`(XML은 `<!-- phantom-idea: -->`)를 단다. PR #5 브랜치의 표식 커밋(1절) 뒤의 상태(집계: 그 커밋에서 `git diff --name-status d2faebf601 HEAD`의 상태 M 135개): 주석을 달 수 없는 34개(markdown, JSON, `.iml`, `.idea/modules.xml`, `BUILD.bazel`, API 덤프, 생성 파일 목록, `build.txt`, 이미지)를 뺀 수정 업스트림 파일 101개를 검사했고, 101개 모두 수정 덩어리(5줄 이내로 붙은 덩어리는 하나로 셈)마다 3줄 안에 표식이 있다. 옛 `// Phantom:` 형식은 PR #7(cf39628de4)에서 `phantom-idea:`로 통일했다(22개 파일의 표식 주석 24줄). 그 표식 커밋 전에는 24개만 표식이 다 있었고, 그 커밋이 업스트림 파일 77개와 포크가 더한 `FoojayJdkList.kt`, 모두 78개 파일에 주석 137줄을 더했다(지운 줄 없음, 동작 변화 없음). 이름 변경으로 짝지어진 2건(1.1절)은 세지 않았다. PR 없이 main에 바로 들어온 일곱 커밋의 덩어리는 51a03a83f7에서 따로 봤다: 표식이 없는 것은 588cd923ce의 `BaseIdeaProperties.kt:82-83`(줄 끝 주석 `// Phantom Groovy, ...`만 있음)과 8d27b3dfd8의 `plugins/kotlin/plugin/resources/META-INF/plugin.xml:17`(벤더 줄) 둘이다. 그 뒤 cd1fc57a5f의 `IdeaCommunityProperties.kt:198-199`(`baseFileName`)에도 표식이 없다(그 뒤 커밋은 이 덩어리만 봤다). 표식은 덩어리를 찾는 길잡이일 뿐이므로 델타는 `git diff`를 기준으로 삼는다. | `./tools/rg.cmd -n 'phantom-idea:'`로 확인한다. PR #5(d421f71ac4) |
| Phantom 코드는 자기 패키지에 둔다 | 새 코드는 `dev.phantom.*` 패키지, 새 모듈은 `phantom.*` 이름을 쓴다. 예외는 업스트림 패키지 안에 넣은 `FoojayJdkList.kt`, `GHDeviceFlow.kt`, `PhantomBuildNumber.kt`다. | c06e071a51(모듈 이름 `intellij.phantom.*` → `phantom.*`). |
| IntelliJ 플러그인을 Phantom-plugins 판으로 바꿔 번들할 수 있다 | 원본은 `.removing`으로 빼고, Phantom-plugins가 `dev.phantom.*` ID에 원본 ID를 별칭으로, 원본 콘텐츠 모듈 이름을 그대로 두고 빌드한 판을 `PHANTOM_PLUGINS_BUNDLED`로 넣는다. 원본과 함께 설치돼도 대체판만 로드된다(3.12절). 바꾼 뒤에는 이 리포의 원본 소스와 그 안의 포크 패치가 번들에 들어가지 않을 수 있다(3.2절 끝의 주의). | 9229667cc7, 04dba66c3a, 9235351694, 6019d518b2, 2cff0f911e, 0748db2720, 588cd923ce. 3.2절. |
| 플러그인은 트리 안에서 만들고 밖으로 옮긴다 | 트리 안에서 만들어 번들한 뒤, 같은 플러그인 ID로 `newkayak12/Phantom-plugins`에 옮기고 번들 목록, 루트 모듈, Bazel 파일 목록, 프로젝트 모듈에서 뺀다. 설계 문서는 남긴다. | 5.2절. 8983487a29, b68d97332e, 1570329708 본문. |

### 2.1 업스트림 코드를 실제로 지운 곳

설정이 아니라 소스를 지운 경우는 아래뿐이다.

| 커밋 | 지운 것 | 이유 (커밋 본문) |
|---|---|---|
| 1e0d4353f7 | `java/plugin/resources/META-INF/plugin.xml`의 `intellij.java.promo` 콘텐츠 모듈 등록, `BaseIdeaProperties.kt`의 `intellij.android.gradle.declarative.lang.ide`·`intellij.compose.ide.plugin`, `TechInsightsLabPromoter` 등록 | Ultimate 유도 화면과 Android 제거 |
| 3676457781 | 모듈 `intellij.java.promo`(`java/java-promo/`), `intellij.vcs.github.ultimatePromo`(`plugins/github/ultimate-promo/`), `intellij.kotlin.onboarding.promoter`(`plugins/kotlin/onboarding-promoter/`) 전체, `KotlinPluginUpdateCheckerStartActivity.kt`, YAML `OpenApiSuggestionProvider.kt`·`OpenRewriteSuggestionProvider.kt` | 1e0d4353f7에서 이미 번들에서 뺀 프로모 모듈이 고아로 남음. Kotlin이 .kt 편집 때 plugins.jetbrains.com을 부름 |
| 43b28af952 | Toolbox 통합 전부: `platform/built-in-server/src/org/jetbrains/ide/Toolbox*.kt` 4개, `ToolboxUpdates.http`, `ToolboxUpdateServiceTest.kt`, `CopyTBXReferenceAction.kt`, `CreateTBXReferenceTest.kt`, smart-update의 `IdeUpdateStep`, 업데이트 설정의 Toolbox 홍보, Try Ultimate의 Toolbox 설치 경로 | Toolbox 앱 연동을 쓰지 않음. 단 `ExternalUpdateManager.TOOLBOX` 상수는 Android 플러그인 컴파일 때문에 0a5ca64795에서 되살림 |
| d7c795f347 | `GHOAuthCallbackHandler.kt`, `GHOAuthCredentialsAcquier.kt`, `GHOAuthService.kt` | GitHub 로그인이 account.jetbrains.com을 거침. `GHDeviceFlow.kt`로 대체 |
| fdd9682290 | `TerminalAgent.kt`의 `JunieTerminalAgent`와 관련 테스트 | Junie는 junie.jetbrains.com에서 설치되는 JetBrains 에이전트 |
| ae2a766191 | `.github/workflows/IntelliJ_IDEA.yml`, `PyCharm.yml`, `code_quality.yml`, `ide_build_and_upload.yml` | 업스트림 CI 대신 `phantom-release.yml` |

## 3. 주제별 변경 지점

### 3.1 브랜딩과 버전

제품 이름, 아이콘, 번들 ID, 설정 폴더, 버전 체계를 바꾼다. 표시 버전은 빌드 날짜(YY.MM.DD), 내부 플랫폼 빌드는 `262.<YYMMDD>`, Phantom 빌드 번호는 `PH-<YYMMDD>.<N>`이다. 표시 버전은 추적 파일 `IdeaApplicationInfo.xml`에 적혀 있지 않다. 추적 파일은 자리표시자 `0` / `0.0`이고, `phantom-build.sh`가 `-Dphantom.version.major=<YY>`, `-Dphantom.version.minor=<MM>.<DD>`를 `installers.cmd`에 넘긴다(c844a9f18e, 코드만 바꿨고 빌드 전). 그래서 속성 없이 실행한 빌드와 소스에서 실행한 IDE는 0.0으로 보인다.

| 파일:행 (HEAD) | 패치 | 커밋 |
|---|---|---|
| `community-resources/resources/idea/IdeaApplicationInfo.xml:2` | `<version eap="false" major="0" minor="0.0"/>` 자리표시자와 `phantom-idea` 주석. 이전에는 `<version major="26" minor="10.27"/>`를 `phantom-build.sh`가 sed로 빌드마다 다시 썼다(fc05eddc04, bf2b90f700). 지금은 추적 파일을 고치지 않는다 | fc05eddc04, bf2b90f700, c844a9f18e |
| `community-resources/resources/idea/IdeaApplicationInfo.xml:3,5-8` | 회사 Phantom, 로고·아이콘 `phantom*.svg`, 이름 Phantom, 실행 파일 이름 `script="phantom"`과 소개 문구(`:8`) | 1e0d4353f7, d1a97edf7a, cd1fc57a5f |
| `community-resources/resources/idea/IdeaApplicationInfo.xml:10` | `<default-laf light="Phantom Light" dark="Phantom Dark"/>` | a91f9ef7e8 |
| `build.txt:1` | `262.10968.SNAPSHOT` → `262.SNAPSHOT` | e8ef2fd569 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:198-199` | 실행 파일 이름 `baseFileName`을 `idea`에서 `phantom`으로(`Contents/MacOS/phantom`, `phantom.vmoptions`, `PHANTOM_VM_OPTIONS` 등. 표식 주석 없음) | cd1fc57a5f |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:237` | 설정 폴더 선택자를 `PhantomIDEA`로 고정 | fc05eddc04 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:240-242` | 산출물 이름 `phantom-<…>`. `phantom.build.number`가 있으면 그 값을 쓴다 | ae2a766191 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:244` | 출력 폴더 `phantom-idea` | 1e0d4353f7 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:323-325` | Windows 이름 Phantom. 제거 설문 URL(`uninstallFeedbackUrl`) 삭제(표식 `:323`) | 1e0d4353f7, d0eab60d1b, d1a97edf7a |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:335,338,340,356` | URL 스킴 `phantom`, 번들 ID `dev.phantom.idea`, `Phantom.app`, Linux 폴더 `phantom`(표식 `:334`, `:355`) | 1e0d4353f7, d1a97edf7a |
| `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:145-147` | 버전 major/minor가 빌드 브랜치와 같아야 한다는 검사를 "major가 비어 있지 않음"으로 완화 | e8ef2fd569 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:170-175` | `phantom.build.number`가 있으면 `<build number>`를 Phantom 번호로 바꾸고 플랫폼 빌드를 `apiVersion`에 둔다 | ae2a766191 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:71-75` | `majorVersion`, `minorVersion`을 속성 `phantom.version.major/minor`에서 먼저 읽고 없으면 `applicationInfoOverride`, XML 순 | c844a9f18e |
| `platform/build-scripts/src/org/jetbrains/intellij/build/ApplicationInfoPropertiesImpl.kt:177-181`, `:268-280` (`withPhantomVersion`) | 패키지에 들어가는 XML 복사본의 `<version>`에 `major`, `minor`를 같은 값으로 씀(About 대화상자, 제목 등이 읽는 쪽). 속성이 하나라도 있을 때만 | c844a9f18e |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt:11,13-14` (신규) | `PHANTOM_BUILD_NUMBER_PROPERTY`, `phantomBuildNumber(context)` | ae2a766191 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt:16-24` | `PHANTOM_VERSION_MAJOR_PROPERTY`, `PHANTOM_VERSION_MINOR_PROPERTY`, `phantomVersionProperty(name)`(빈 문자열은 없는 것으로 봄) | c844a9f18e |
| `platform/build-scripts/tests/testSrc/org/jetbrains/intellij/build/ApplicationInfoPropertiesTest.java:28-44` | `phantomVersion`, `phantomVersionWithoutVersionElement` 테스트(실행 전) | c844a9f18e |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/MacDistributionBuilder.kt:571,597` | Info.plist의 `build`에 Phantom 번호 | ae2a766191 |
| `platform/build-scripts/resources/mac/Contents/Info.plist:40` | 저작권 문구 "Based on the IntelliJ Platform" | ae2a766191 |
| `plugins/ide-startup/importSettings/src/com/intellij/ide/startup/importSettings/data/SettingsService.kt:304` | 가져오기 마법사에 IC 아이콘 대신 제품 아이콘 | 5a12a1a666 |
| `plugins/ide-startup/importSettings/src/com/intellij/ide/startup/importSettings/jb/JbImportServiceImpl.kt:310-312` | 설정이 `.../Phantom` 아래에 있으므로 옆의 `JetBrains` 폴더도 가져오기 대상으로 본다 | 048d85af98 |
| `build/idea-community-images/**` (바이너리 10개), `community-resources/resources/phantom*.svg`, `phantom_logo*.png`, `community-resources/phantom-brand-src/` | 앱 아이콘, DMG 배경, Windows 설치 이미지. 원본 그림과 생성 스크립트 `compose.swift` | 1e0d4353f7, 5a12a1a666, 9ad40b0e37, c8cb99fb6f, d0eab60d1b, d1a97edf7a |

검증: 빌드한 `Phantom.app/Contents/Info.plist`의 `CFBundleIdentifier`가 `dev.phantom.idea`, `CFBundleVersion`이 `PH-…`인지 본다. `product-info.json`의 `version`이 `YY.MM.DD`이고 About 대화상자도 같은 값인지, 그리고 빌드 뒤 `git status`에 `IdeaApplicationInfo.xml`이 없는지 본다(c844a9f18e, 빌드 전이라 확인하지 못했다). 앱 안 `product-info.json`의 build가 `262.<YYMMDD>`인지 본다(빌드가 필요하다).

### 3.2 번들 구성

번들 플러그인 목록은 `IdeaCommunityProperties.kt:91-131` 한 식으로 만든다. 업스트림 `IDEA_BUNDLED_PLUGINS`(`platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:14-60`, 이 목록은 `DEFAULT_BUNDLED_PLUGINS` = `platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/ProductModulesLayout.kt:23-39`를 포함한다)에서 `.removing(...)`으로 빼고 `PHANTOM_BUNDLED_PLUGINS`를 더한다. `.removing`의 정의는 이 리포에서 찾지 못했다. 외부 라이브러리 확장 함수로 보인다(추정). 이와 별도로 Phantom-plugins에서 빌드한 플러그인 25개가 모듈 목록이 아니라 빌드된 플러그인 디렉터리로 들어간다(`PHANTOM_PLUGINS_BUNDLED`, 아래 표).

빼는 모듈 39개 (`IdeaCommunityProperties.kt`, 51a03a83f7 행). 앞의 14개는 그냥 빼고, 다음 24개(행 106의 주석 아래)는 Phantom-plugins 판이 대신 들어가며, 마지막 Kotlin은 번들 밖 zip으로 게시한다. 셈: `./tools/rg.cmd -c '\.removing\(' build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`는 41인데, 그중 2개(`:256-257`)는 `AndroidStudioProperties`의 것이다:

| 행 | 모듈 | 업스트림 정의 위치 | 커밋 | 이유 (행 83-90, 106, 132-134 주석·커밋 본문) |
|---|---|---|---|---|
| 92 | `intellij.featuresTrainer` | `BaseIdeaProperties.kt:52` | b561ac8fd5 (처음 f736b95fd0) | Learn 과정과 Learn 창 |
| 93 | `intellij.mcpserver` | `BaseIdeaProperties.kt:49` | b561ac8fd5 | 외부 에이전트용 MCP 서버. 의존하는 번들 없음, -11 MB |
| 94 | `intellij.performanceTesting` | `BaseIdeaProperties.kt:58` | 3718f68ff4 | JetBrains 성능 테스트 러너 |
| 95 | `intellij.platform.acp.plugin` | `ProductModulesLayout.kt:37` | 770810693c (처음 b561ac8fd5) | Agent Client Protocol |
| 96 | `intellij.dev` | `ProductModulesLayout.kt:24` | 770810693c | DevKit 런타임(내부 PSI 뷰어, 통계 도구) |
| 97 | `intellij.eclipse` | `BaseIdeaProperties.kt:45` | e2a4ffa4ee (처음 770810693c) | Eclipse 프로젝트 가져오기 |
| 98 | `intellij.java.byteCodeViewer` | `BaseIdeaProperties.kt:42` | e2a4ffa4ee | Phantom-plugins의 `bytecode`로 대체(필요할 때 설치) |
| 99 | `intellij.webp` | `BaseIdeaProperties.kt:50` | e2a4ffa4ee | Phantom-plugins의 `webp`로 대체(필요할 때 설치) |
| 100 | `intellij.vcs.git.commit.modal` | `BaseIdeaProperties.kt:33` | 7c43a36532 (처음 e2a4ffa4ee) | Phantom-plugins의 `modal-commit`으로 대체(필요할 때 설치) |
| 101 | `intellij.vcs.gitlab` | `BaseIdeaProperties.kt:35` | 7c43a36532 | GitHub만 쓴다 |
| 102 | `intellij.keymap.eclipse` | `BaseIdeaProperties.kt:55` | 7c43a36532 | Marketplace에서 설치 가능 |
| 103 | `intellij.keymap.visualStudio` | `BaseIdeaProperties.kt:56` | 7c43a36532 | 같음 |
| 104 | `intellij.keymap.netbeans` | `BaseIdeaProperties.kt:57` | 7c43a36532 | 같음 |
| 105 | `intellij.configurationScript` | `BaseIdeaProperties.kt:22` | 7c43a36532 | 의존하는 번들 없음 |
| 107 | `intellij.toml` | `BaseIdeaProperties.kt:53` | 9229667cc7 | Phantom-plugins `toml`을 번들 |
| 108 | `intellij.yaml` | `BaseIdeaProperties.kt:24` | 9229667cc7 | Phantom-plugins `yaml`을 번들 |
| 109 | `intellij.sh.plugin` | `BaseIdeaProperties.kt:46` | 9229667cc7 | Phantom-plugins `sh`를 번들 |
| 110 | `intellij.java.decompiler` | `BaseIdeaProperties.kt:44` | 9229667cc7 | Phantom-plugins `decompiler`를 번들 |
| 111 | `intellij.java.coverage` | `BaseIdeaProperties.kt:43` | 9229667cc7 | Phantom-plugins `coverage`를 번들 |
| 112 | `intellij.textmate.plugin` | `BaseIdeaProperties.kt:20` | 9229667cc7 | Phantom-plugins `textmate`를 번들 |
| 113 | `intellij.markdown` | `BaseIdeaProperties.kt:47` | 9229667cc7 | Phantom-plugins `markdown`을 번들 |
| 114 | `intellij.mermaid` | `BaseIdeaProperties.kt:48` | 9235351694 | Phantom-plugins `mermaid`(Phantom 자체 구현 `com.phantom.mermaid`, 별칭 없음)를 번들 |
| 115 | `intellij.copyright` | `BaseIdeaProperties.kt:17` | 6019d518b2 | Phantom-plugins `copyright`를 번들 |
| 116 | `intellij.editorconfig.plugin` | `BaseIdeaProperties.kt:21` | 6019d518b2 | Phantom-plugins `editorconfig`를 번들 |
| 117 | `intellij.html.tools` | `BaseIdeaProperties.kt:25` | 6019d518b2 | Phantom-plugins `htmltools`를 번들 |
| 118 | `intellij.testng` | `BaseIdeaProperties.kt:40` | 6019d518b2 | Phantom-plugins `testng`을 번들 |
| 119 | `intellij.java.i18n` | `BaseIdeaProperties.kt:41` | 6019d518b2 | Phantom-plugins `java-i18n`을 번들 |
| 120 | `intellij.grazie` | `BaseIdeaProperties.kt:51` | 6019d518b2 | Phantom-plugins `grazie`를 번들 |
| 121 | `intellij.terminal` | `BaseIdeaProperties.kt:19` | 6019d518b2 | Phantom-plugins `terminal`을 번들 |
| 122 | `intellij.vcs.github` | `BaseIdeaProperties.kt:34` | 6019d518b2 | Phantom-plugins `github`를 번들 |
| 123 | `intellij.junit` | `BaseIdeaProperties.kt:39` | 2cff0f911e | Phantom-plugins `junit`을 번들 |
| 124 | `intellij.properties` | `BaseIdeaProperties.kt:18` | 2cff0f911e | Phantom-plugins `properties`를 번들 |
| 125 | `intellij.repository.search` | `BaseIdeaProperties.kt:26` | 0748db2720 | Phantom-plugins `repository-search`를 번들 |
| 126 | `intellij.json` | `BaseIdeaProperties.kt:23` | 0748db2720 | Phantom-plugins `json`을 번들 |
| 127 | `intellij.android.gradle.dsl` | `BaseIdeaProperties.kt:30` | 0748db2720 | Phantom-plugins `gradle-dsl`을 번들. 7c43a36532는 Gradle 때문에 원본을 남겼었다(아래 표의 `BaseIdeaProperties.kt:29` 행) |
| 128 | `intellij.groovy.scripting` | `BaseIdeaProperties.kt:36` | 588cd923ce | Phantom-plugins `groovy-scripting`을 번들 |
| 129 | `intellij.groovy` | `BaseIdeaProperties.kt:37` | 588cd923ce | Phantom-plugins `groovy`를 번들 |
| 130 | `intellij.groovy.live.templates` | `BaseIdeaProperties.kt:38` | 588cd923ce | Phantom-plugins `groovy-live-templates`를 번들 |
| 131 | `KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE`(`kotlin.plugin`) | `BaseIdeaProperties.kt:54` | ac9ddb534c | 381 MB이고 Java만 쓰는 프로젝트는 쓰지 않음. `pluginModulesToPublish`(`:135`)로 zip을 만들고 릴리스가 Phantom plugins 저장소에 게시한다(아래) |

행 107-130과 Phantom-plugins 디렉터리의 짝은 이름과 커밋 제목(9229667cc7 "Toml, YAML, Shell Script, Lombok, Decompiler, Coverage, TextMate and Markdown", 9235351694 "Phantom Mermaid bundled in place of JetBrains Mermaid", 6019d518b2 "Copyright, EditorConfig, HTML Tools, TestNG, Java I18n, Grazie, Terminal and GitHub", 2cff0f911e "JUnit and Properties", 0748db2720 "Repository Search, JSON and Gradle DSL", 588cd923ce "Groovy, Groovy Scripting and Groovy Live Templates")으로 맞춘 것이다. 코드에 짝을 적은 곳은 없다. 2cff0f911e과 0748db2720 본문은 대체판이 `dev.phantom.*` ID에 원본 ID(`JUnit`, `com.intellij.properties`, `org.jetbrains.idea.reposearch`, `com.intellij.modules.json`, `org.jetbrains.idea.gradle.dsl`)를 별칭으로 둔다고 적고, 588cd923ce 본문은 Groovy 판의 ID를 적지 않는다(Phantom-plugins는 읽지 못해 확인하지 못함).

`.removing`이 아닌 방식으로 뺀 것:

| 위치 | 뺀 것 | 커밋 |
|---|---|---|
| `IdeaCommunityProperties.kt:91` | 업스트림이 더하던 `+ sequenceOf("intellij.javaFX.community")` 자체를 지움(JavaFX Scene Builder가 JetBrains에서 키트를 받음) | 770810693c |
| `IdeaCommunityProperties.kt:148` | `pluginLayouts`에서 `CommunityRepositoryModules.androidPlugin(allPlatforms = true)` 삭제 | 1e0d4353f7 |
| `IdeaCommunityProperties.kt:144` | `buildAllCompatiblePlugins = false`(번들 밖 플러그인 저장소 빌드 안 함) | 1e0d4353f7 |
| `IdeaCommunityProperties.kt:300` | 제품 콘텐츠에서 `intellij.platform.ide.newUiOnboarding`, `intellij.platform.ide.newUsersOnboarding` 삭제. 같은 커밋에서 뺀 `intellij.ide.startup.importSettings`는 a3e497f72d가 되살림(`:301`) | 770810693c, a3e497f72d |
| `IdeaCommunityProperties.kt:24-27` (`PHANTOM_BUNDLED_PLUGINS`) | f736b95fd0이 더한 `intellij.lombok` 줄 삭제. Phantom-plugins `lombok`이 대신 들어간다 | 9229667cc7 |
| `BaseIdeaProperties.kt:29`, `:59` (표식 줄) | `intellij.android.gradle.declarative.lang.ide`, `intellij.compose.ide.plugin` 줄 삭제. `intellij.android.gradle.dsl`(`:30`)은 Gradle 분석 때문에 남김. 단 0748db2720부터는 그것도 `.removing`으로 빼고 Phantom-plugins `gradle-dsl`을 넣는다(위 표 행 127). `:29`의 표식 주석은 아직 "android.gradle.dsl stays for Gradle"이라고 적는다 | 1e0d4353f7 |
| `java/plugin/resources/META-INF/plugin.xml:111` 뒤, `plugins/github/github-core/resources/META-INF/plugin.xml:20` 뒤, `plugins/ide-features-trainer/res/META-INF/plugin.xml:24` 뒤 | 프로모 콘텐츠 모듈 등록 삭제(`intellij.java.promo`, `intellij.vcs.github.ultimatePromo`, `intellij.kotlin.onboarding.promoter`) | 1e0d4353f7 |

더하는 모듈 (`PHANTOM_BUNDLED_PLUGINS`, `IdeaCommunityProperties.kt:23-27`):

| 행 | 모듈 | 플러그인 ID | 커밋 |
|---|---|---|---|
| 25 | `phantom.sync` | `dev.phantom.sync` | 5f1819d799 |
| 26 | `phantom.theme` | `dev.phantom.theme` | eb50867a56, 이름 변경 c06e071a51 |

f736b95fd0부터 같은 목록에 있던 `intellij.lombok`(업스트림 커뮤니티 플러그인, Ultimate만 번들하던 것)은 9229667cc7이 지웠다.

Phantom-plugins에서 빌드해 넣는 플러그인 25개 (`PHANTOM_PLUGINS_BUNDLED`, `IdeaCommunityProperties.kt:29-39`, 9229667cc7, 9235351694, 6019d518b2, 2cff0f911e, 0748db2720, 588cd923ce). `phantom-build.sh:33`의 `bundled_from_plugins`도 같은 25개를 같은 순서로 적는다(51a03a83f7에서 두 목록을 비교함):

| 디렉터리 (`:36-38`) | 대체하는 것 |
|---|---|
| `toml`, `yaml`, `sh`, `decompiler`, `coverage`, `textmate`, `markdown` | 위 표 행 107-113의 업스트림 모듈 |
| `mermaid` | 행 114 `intellij.mermaid`(Phantom 자체 구현, 별칭 없음) |
| `copyright`, `editorconfig`, `htmltools`, `testng`, `java-i18n`, `grazie`, `terminal`, `github` | 행 115-122의 업스트림 모듈 |
| `junit`, `properties` | 행 123-124 (2cff0f911e) |
| `repository-search`, `json`, `gradle-dsl` | 행 125-127 (0748db2720) |
| `groovy-scripting`, `groovy`, `groovy-live-templates` | 행 128-130 (588cd923ce) |
| `lombok` | 지운 `intellij.lombok` |

| 파일:행 | 내용 | 커밋 |
|---|---|---|
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:29-34` | 주석: Phantom-plugins 판은 `dev.phantom.*` ID를 쓰고 IntelliJ ID를 별칭으로, IntelliJ 콘텐츠 모듈 이름을 그대로 가져, 원본에 의존하는 플러그인과 모듈이 이것들로 해석된다. Mermaid는 Phantom 자체 구현(`com.phantom.mermaid`, 별칭 없음). 값은 모듈 이름이 아니라 `out/phantom-plugins/<dir>`의 디렉터리 이름 | 9229667cc7, 9235351694 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:221-228` | `getAdditionalPluginPaths()` 재정의(업스트림 기본은 빈 목록, `platform/build-scripts/src/org/jetbrains/intellij/build/ProductProperties.kt:405`). `out/phantom-plugins/<dir>`를 돌려주고, `lib/`가 없는 것이 하나라도 있으면 `check`로 실패(:225-226). 빌드는 이 디렉터리를 `plugins/<dir>`로 복사한다(`platform/build-scripts/src/org/jetbrains/intellij/build/impl/DistributionJARsBuilder.kt:551`, :560) | 9229667cc7 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:20` | `import java.nio.file.Files` | 9229667cc7 |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:6-7`, `:15`, `:131-135` | Kotlin을 번들에서 빼고(`:131`) `productLayout.pluginModulesToPublish = persistentSetOf(KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE)`(`:135`)로 zip을 만든다. 주석 `:132-134`: 381 MB이고 Java만 쓰는 프로젝트는 쓰지 않으며, 빌드가 `artifacts/<제품 코드>-plugins`에 zip을 만들고 `phantom-release.sh`가 릴리스마다 Phantom plugins 저장소에 게시하며, 그 저장소가 Kotlin이나 Gradle Kotlin DSL 스크립트를 쓰는 프로젝트에 제안한다. import `persistentSetOf`(`:7`), `KotlinPluginBuilder`(`:15`) | ac9ddb534c |
| `build/src/OpenSourceCommunityInstallersBuildTarget.kt:23` | 건너뛰는 단계 목록에서 `ARCHIVE_PLUGINS`를 빼 플러그인 zip을 만든다(Kotlin zip이 나오게) | b238d55e24 |
| `phantom-build.sh:29-44` | 같은 목록(`:33`, 25개)을 Phantom-plugins 체크아웃에서 빌드해 `out/phantom-plugins`에 푼다(3.9절, BUILD.md 3절) | 9229667cc7, 9235351694, 6019d518b2, 2cff0f911e, 0748db2720, 588cd923ce |
| `phantom-release.sh:59-63`, `:81-123` | Kotlin zip 확인과 게시: Phantom-plugins 체크아웃의 릴리스 `kotlin-262.<YYMMDD>`, `.phantom/marketplace.json`의 `org.jetbrains.kotlin` 항목(BUILD.md 6장). 항목의 이름은 `Phantom Kotlin`, 벤더는 `Phantom`이고 `source` 키가 없다(`:106-112`) | ac9ddb534c, bedeb19cae, f6adc2ce77, 8d27b3dfd8 |
| `plugins/kotlin/plugin/resources/META-INF/plugin.xml:3-4`, `:17` | Kotlin 플러그인 이름 `Kotlin` → `Phantom Kotlin`, 벤더 `JetBrains` → `Phantom`(URL은 이 저장소). ID `org.jetbrains.kotlin`(`:2`)은 의존하는 플러그인 때문에 그대로(표식 `:3`). `:17`에는 표식이 없다 | 8d27b3dfd8 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/JetBrainsProductProperties.kt:59-60` | 빌드의 플러그인 검증 `validatePlugin`(`:80-96`)이 허용하는 벤더 `ALLOWED_PLUGIN_VENDORS`(`:55-61`)에 `Phantom` 추가. 벤더를 Phantom으로 바꾼 Kotlin이 이 검증에서 실패했다(커밋 제목) | 71ff75bf3d |
| `platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:71` | `CE_CLASS_VERSIONS`(:62)의 커버리지 런타임 jar 경로 `plugins/java-coverage/lib/java-coverage-rt.jar` → `plugins/coverage/lib/java-coverage-rt.jar`(Phantom Code Coverage가 들어가는 경로). 클래스 버전 1.8 규칙은 그대로 | a199380d7d |
| `platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:82-83` | 같은 맵의 Groovy rt jar 경로 `plugins/Groovy/lib/` → `plugins/groovy/lib/`(Phantom-plugins 빌드가 쓰는 폴더). `phantom-idea:` 표식은 없고 줄 끝 주석만 있다. JUnit rt jar(`:72-73`, `plugins/junit/lib/`)는 커밋 본문에 따르면 대체판이 jar 이름을 그대로 두어 고치지 않았다 | 588cd923ce |

새 모듈을 번들하려면 네 곳을 함께 고친다. 이 순서는 phantom 플러그인 추가 커밋(5f1819d799 등)이 매번 같이 바꾼 파일에서 읽은 것이다.

| 파일:행 | 내용 |
|---|---|
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:23-27` | 번들 목록 |
| `intellij.idea.community.main.iml:138-140` | 루트 모듈의 RUNTIME 의존 |
| `.idea/modules.xml:359-362` | 프로젝트 모듈 등록 |
| `BUILD.bazel:284-286, 473-475`, `build/bazel-generated-file-list.txt:1475-1478` | Bazel 타깃 (생성 파일, 6절) |

Phantom-plugins에서 빌드한 판을 번들에 넣을 때는 이 네 곳이 아니라 `PHANTOM_PLUGINS_BUNDLED`(`IdeaCommunityProperties.kt:35-39`)와 `phantom-build.sh:33`의 `bundled_from_plugins`를 같이 고친다. 두 목록은 서로를 주석으로 가리킬 뿐 코드로 묶여 있지 않다. 원본이 `IDEA_BUNDLED_PLUGINS`에 있으면 `.removing` 한 줄도 더한다(9229667cc7이 바꾼 파일).

주의(소스와 배포 앱): Phantom-plugins 판으로 바꾼 24개의 원본 소스는 이 리포에 남아 있고, 그중 여러 곳에 포크 패치가 있다(`plugins/github`의 `gh`·디바이스 플로 로그인과 `GithubBundle` 문구, `plugins/grazie`의 Grazie Cloud 게이트, `plugins/terminal`의 Junie 삭제, `plugins/yaml`의 "Try Ultimate"·OpenAPI/OpenRewrite 제안 삭제, `plugins/sh`의 ShellCheck 다운로드 경로, 0748db2720부터 `plugins/repository-search`의 Package Search 차단. 3.3절. 새로 바꾼 나머지 원본 소스 `plugins/junit`, `plugins/properties`, `json`, `plugins/groovy`, `plugins/groovy-scripting`, `plugins/groovy-live-templates`에는 `git diff d2faebf601 51a03a83f7` 기준 포크 패치가 없다. Android Gradle DSL 모듈 `intellij.android.gradle.dsl`은 이 리포에 추적되지 않아(`android` 클론 쪽으로 보임, 추정) 보지 않았다). 번들은 이제 이 소스가 아니라 Phantom-plugins가 빌드한 `out/phantom-plugins/<dir>`를 쓴다. 따라서 이 리포의 `plugins/<이름>`을 고쳐도 Phantom-plugins가 이 리포의 소스로 빌드하지 않는 한 배포 앱은 바뀌지 않고, 위 패치들이 배포 앱에 들어 있는지도 그 빌드에 달렸다. 이 문서를 쓸 때 Phantom-plugins 저장소를 볼 수 없어서 어느 쪽인지 확인하지 못했다. Kotlin은 다르다: 이 리포의 빌드가 `plugins/kotlin` 소스로 zip을 만든다.

검증: 빌드한 앱의 `Contents/plugins/` 목록에 위 39개 모듈의 플러그인이 없고 `phantom-sync`, `phantom-theme`과 `PHANTOM_PLUGINS_BUNDLED`의 25개 디렉터리가 있는지 본다. `out/phantom-idea/artifacts/*-plugins/`에 `Kotlin-*.zip`이 있는지 본다. `python3 phantom-bench-report.py static "/Applications/Phantom.app"`가 번들 플러그인 수와 크기를 `docs/phantom/bench/static-baseline.json`과 비교한다(4b3ffd9498). 9229667cc7 이후 빌드한 앱은 이 문서를 쓰며 열어 보지 않았다(v26.11.13.1 릴리스 앱 포함).

### 3.3 JetBrains 네트워크 호출과 텔레메트리

#### 3.3.1 `phantom.jetbrains.services`로 막은 호출

게이트는 `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:108-109`의 `jetBrainsServicesEnabled`(`Boolean.getBoolean("phantom.jetbrains.services")`) 하나다. 같은 프로퍼티를 직접 읽는 곳은 둘이다: `StartupErrorReporter`는 IDE가 뜨기 전이라서, `LibrariesDownloadAssistant`는 모듈(`intellij.platform.ide.core`)이 `MarketplaceRequests`를 볼 수 없어서다.

| 파일:행 | 막는 호출 | 꺼졌을 때 | 커밋 |
|---|---|---|---|
| `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:403` | `getBrokenPlugins` (brokenPlugins.json) | null 반환 | d7c795f347 |
| `…/MarketplaceRequests.kt:572` | `getFeatures` (기능별 플러그인 제안) | 빈 목록 | d7c795f347 |
| `…/MarketplaceRequests.kt:613` | `getMarketplacePlugins` (전체 플러그인 ID 목록) | 빈 집합 | d7c795f347 |
| `…/MarketplaceRequests.kt:935` | `schedulePluginIdsUpdate` (30초 뒤 ID 목록 갱신) | 예약 안 함 | d7c795f347 |
| `platform/platform-impl/update-checker/src/com/intellij/openapi/updateSettings/impl/UpdateChecker.kt:343` | 플러그인 업데이트 확인에서 JetBrains Marketplace 백엔드 | Marketplace 백엔드를 빼고 사용자 저장소만 본다 | d7c795f347 |
| `platform/built-in-server/src/org/jetbrains/ide/UploadLogsService.kt:38,87` | 내장 서버 `/api/logs`(지원 페이지가 IDE 로그 업로드를 요청) | FORBID | fdd9682290 |
| `platform/built-in-server/src/org/jetbrains/ide/StartUpMeasurementService.kt:19-21` | 내장 서버 `/api/startUpMeasurement`를 JetBrains ij-perf 페이지(`ij-perf.jetbrains.com`, `ij-perf.labs.jb.gg`)가 읽는 것(외부 전송이 아니라 수신) | 기본 origin 검사(로컬 origin은 통과, 그 밖의 origin은 사용자에게 호스트를 신뢰할지 묻는 대화상자). 코드만 바꿨고 빌드 전 | PR #7(cf39628de4) |
| `platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java:196-197` | 시작 오류 대화상자의 Report 버튼(로그와 오류를 JetBrains로 전송) | 버튼 숨김. `ij.startup.error.handler.class`가 있으면 표시 | fdd9682290 |
| `platform/feedback/src/com/intellij/platform/feedback/ActionBasedFeedbackConfig.kt:65` | 액션 기반 피드백 설문 | 표시 안 함 | 3676457781 |
| `platform/feedback/src/com/intellij/platform/feedback/impl/FeedbackSurveyUtils.kt:74` | 알림 기반 설문 | 표시 안 함 | 3676457781 |
| `platform/feedback/src/com/intellij/platform/feedback/impl/GeneralFeedbackSubmit.kt:123-126` (`submitFeedback`) | 모든 피드백 양식의 전송(forms-service.jetbrains.com 또는 스테이징)과 그 앞의 `RegionUrlMapper` 지역 매핑 조회 | 요청 없이 반환하고 `onDone`/`onError`도 부르지 않음(`NO_REQUEST`와 같은 계약). 코드만 바꿨고 빌드 전 | 4ca69e1a95 |
| `platform/feedback/src/com/intellij/platform/feedback/localization/LocalizationFeedbackDialog.kt:132` | 지역화 피드백 대화상자의 감사 알림 | 알림을 띄우지 않음(보내지 않았으므로) | 4ca69e1a95 |
| `plugins/ide-features-trainer/src/training/ui/OnboardingFeedbackForm.kt:279` | 온보딩 피드백 양식의 감사 알림(번들 밖 `featuresTrainer` 소속) | 알림을 띄우지 않음. 코드만 바꿨고 빌드 전 | 9c32c01f49 |
| `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/JdkList.kt:536` | JDK 목록 피드(download.jetbrains.com) | foojay Disco API(`FoojayJdkList.FEED_URL`, `FoojayJdkList.kt:48`). 레지스트리 `jdk.downloader.url`이 비어 있지 않으면 그 값이 먼저다(`JdkList.kt:530-533`) | be60914099 |
| `platform/ide-core/src/com/intellij/facet/frameworks/LibrariesDownloadAssistant.java:47-50` (`getDownloadServiceVersions`, 프로퍼티 직접 읽음) | 프레임워크 라이브러리 버전 목록(`www.jetbrains.com/idea/download-assistant.xml`, `frameworks.jetbrains.com`, 주소는 `LibrariesDownloadConnectionService.java:6-7`). New Project 마법사의 Groovy SDK 콤보, `LibraryOptionsPanel`이 화면을 열 때 받던 것 | null 반환, 호출자(`:35-44`)가 번들 목록(`localUrls`, 예 `groovy.sdk.xml`)을 씀. 코드만 바꿨고 빌드 전 | PR #7(cf39628de4) |
| `plugins/git4idea/backend/src/config/GitVersionUpdateInSettings.kt:89-91` (`GitNewVersionCheckerStarter`) | Windows의 Git 새 버전 유휴 감시가 받던 Git 설치 목록 피드(`download.jetbrains.com/jdk/feed/v1/gits.json.xz`, `GitDownloadAndInstall.kt:20`) | 감시를 켜지 않음. 업데이트 진입점 액션도 나타나지 않음. Git이 없을 때의 "Download and Install"은 그대로 피드를 받음(사용자 조작). 코드만 바꿨고 빌드 전 | PR #7(cf39628de4) |
| `plugins/grazie/src/main/kotlin/com/intellij/grazie/cloud/DefaultGrazieCloudConnector.kt:22,44,46` | Grazie Cloud 연결, 약관 동의, API 클라이언트(api.jetbrains.ai) | false / null | 98673d9e9a |
| `plugins/grazie/src/main/kotlin/com/intellij/grazie/cloud/license/GrazieLoginManager.kt:80,159,260` | 라이선스·계정·클라우드 조회, JetBrains Account 로그인 | 초기화만 표시하고 조회 안 함 | 98673d9e9a |
| `plugins/grazie/src/main/kotlin/com/intellij/grazie/utils/Utils.kt:79` | Grazie Cloud 홍보 | false | 98673d9e9a |
| `plugins/stats-collector/src/com/intellij/stats/completion/sender/SenderPreloadingActivity.kt:34` | 완성 로그 전송(resources.jetbrains.com) | 활동 등록 안 함 | 98673d9e9a |

#### 3.3.2 플래그 없이 끊거나 바꾼 호출

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/platform-impl/src/com/intellij/platform/ide/impl/customization/BaseJetBrainsExternalProductResourceUrls.kt:112-114` | IDE 업데이트 피드 기본값(`https://www.jetbrains.com/updates/updates.xml`) 삭제. `-Didea.updates.url`이 있을 때만 확인 | d7c795f347 |
| `platform/statistics/src/com/intellij/internal/statistic/utils/PluginInfoDetector.kt:213-214` | 통계용 Marketplace ID 목록을 내려받지 않고 캐시만 씀 | d7c795f347 |
| `platform/ml-impl/src/com/intellij/platform/ml/impl/logs/MLEventLoggerProvider.kt:43-44` | `isLoggingAlwaysActive()` → false (동의 없이 FUS 설정을 받던 경로) | d7c795f347 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:24` | `ITNReporter`(Report to JetBrains) 등록 삭제 | d7c795f347 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:1387` | 레지스트리 `jdk.updater` 기본 false | d7c795f347 |
| `platform/tips-of-the-day/resources/intellij.platform.tips.xml:40-44`, `platform/tips-of-the-day/src/com/intellij/ide/util/TipPanel.java:139-140`, `platform/tips-of-the-day/src/com/intellij/ide/util/TipsFeedback.kt:75-76` | Tips of the Day 좋아요/싫어요: 레지스트리 `tips.of.the.day.feedback` 기본값 staging → nowhere, 그 값이면 `TipPanel`이 좋아요/싫어요 줄을 숨김(`isFeedbackSendingEnabled`). 기능 코드는 남김. 코드만 바꿨고 빌드 전 | 4ca69e1a95 |
| `platform/util/resources/misc/registry.properties:2241` | `ide.show.plugin.suggestions.on.open=false` | d7c795f347 |
| `plugins/gradle/plugin-resources/intellij.gradle.xml:219-221` | `gradle.compatibility.update.interval` 기본 0(호환성 피드 갱신 안 함) | d7c795f347 |
| `idea/customization/base/src/IntelliJIdeaExternalResourceUrls.kt:22-42` | Help 메뉴의 버그 신고, 지원, 피드백, YouTube, 키맵 PDF, What's New, 다운로드 페이지 URL을 null로. 온라인 도움말은 남김 | 3676457781, fdd9682290 |
| `platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java:177-191` | 지원 링크 기본값 `https://jb.gg/ide/critical-startup-errors` 삭제. `ij.startup.error.support.url`이 있을 때만 표시 | fdd9682290 |
| `plugins/terminal/src/org/jetbrains/plugins/terminal/agent/TerminalAgent.kt:108-109` | 터미널 AI 에이전트에서 Junie 삭제(Claude Code, Codex 유지) | fdd9682290 |
| `platform/platform-impl/codeinsight-inline/src/com/intellij/codeInsight/inline/completion/options/InlineCompletionConfigurable.kt:56-57` | JetBrains AI Assistant 링크 삭제 | fdd9682290 |
| `plugins/repository-search/common/src/main/kotlin/org/jetbrains/idea/packagesearch/api/PackageSearchApiClientService.kt:87-93` | package-search.services.jetbrains.com 클라이언트의 모든 요청을 전송 전에 IOException으로 실패시킴. 0748db2720부터 번들의 Repository Search는 Phantom-plugins 빌드라 이 패치가 배포 앱에 있는지는 미확인(3.2절 끝의 주의) | 98673d9e9a |
| `plugins/sh/core/src/com/intellij/sh/shellcheck/ShShellcheckUtil.java:69-73, 121, 311-320, 329-346` | ShellCheck 다운로드를 cache-redirector.jetbrains.com 대신 GitHub 릴리스(koalaman/shellcheck)에서. 압축 형식 tar.xz/zip | 98673d9e9a |
| `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/ui/GHOAuthCredentialsUi.kt:55` (`acquireToken`) | 브라우저 로그인: `gh auth token` 재사용, 없으면 GitHub OAuth 디바이스 플로(`GHDeviceFlow.kt`). account.jetbrains.com 안 거침 | d7c795f347 |
| `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/ui/GHTokenCredentialsUi.kt:53,59` | 토큰 로그인에 "From gh CLI" 버튼 | d7c795f347 |
| `plugins/github/github-core/resources/intellij.vcs.github.xml:46` 직전 | `GHOAuthCallbackHandler` 등록 삭제 | d7c795f347 |
| `plugins/kotlin/plugin/common/resources/META-INF/kotlin-core.xml:173` 직전, `plugins/kotlin/plugin/k2/resources/kotlin.plugin.k2.xml:156` 직후 | `KotlinPluginUpdateCheckerStartActivity` 등록 삭제(파일도 삭제) | 3676457781 |
| `platform/feedback/resources/intellij.platform.feedback.xml:83` | `csat.survey.enabled` 기본 false | 3676457781 |

#### 3.3.3 진단 파일과 텔레메트리 기본값

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/diagnostic/telemetry/src/com/intellij/platform/diagnostic/telemetry/OpenTelemetryUtils.kt:62-67` | OTel 메트릭 CSV/JSON 파일 경로 기본값을 빈 값으로(파일 안 씀) | 53aa9e1ec7 |
| `platform/lang-impl/src/com/intellij/util/indexing/diagnostic/IndexDiagnosticDumper.kt:70-72, 251, 282` | 인덱싱 진단 리포트를 `intellij.indexes.diagnostics.dump=true`일 때만 씀(단위 테스트는 예외) | 53aa9e1ec7 |
| `platform/lang-impl/src/com/intellij/util/indexing/diagnostic/StorageDiagnosticData.kt:67-68, 84, 99` | 스토리지 진단 JSON과 OTel 게이지를 `intellij.storage.diagnostics.dump=true`일 때만 | 7568bf108c |
| `platform/platform-impl/src/com/intellij/openapi/application/impl/NonBlockingReadActionImpl.java:126-127` | `idea.non-blocking-action.enable-monitoring` 기본 false | 3718f68ff4 |

검증: `./tools/rg.cmd -n -e jetBrainsServicesEnabled -e phantom.jetbrains.services -g '*.kt' -g '*.java'`로 위 표의 호출 지점이 모두 나오는지 본다. 동작은 `-Dphantom.jetbrains.services=true`를 `.vmoptions`에 넣고 껐을 때와 비교한다. 관련 테스트: `./tests.cmd --module intellij.featuresTrainer.tests --test training.ui.FeedbackSubmissionGateTest`(4ca69e1a95, 9c32c01f49. 피드백 전송이 로컬 대역 서버에 닿지 않는지 본다. 요청 대기는 15초라 그보다 느린 지역 매핑 조회는 고장 난 게이트를 통과시킬 수 있다. 실행 전), `./tests.cmd --module intellij.platform.lang.tests --test com.intellij.openapi.projectRoots.impl.jdkDownloader.FoojayJdkListTest`, `./tests.cmd --module intellij.terminal.tests --test 'com.intellij.terminal.tests.agent.*'`. Grazie 테스트는 `plugins/grazie/src/tests.pro/testSrc/com/intellij/grazie/pro/BaseTestCase.kt:34,55`에서 플래그를 켜고 끈다. 그 테스트 모듈은 `intellij.grazie.tests.pro`다(`plugins/grazie/src/tests.pro/intellij.grazie.tests.pro.iml:6`에 `testSrc` 소스 폴더). 실행은 하지 않았다.

### 3.4 플러그인 저장소: GitHub 마켓플레이스

GitHub 리포의 `.phantom/marketplace.json`을 플러그인 저장소로 읽는다. 공식 저장소 `newkayak12/Phantom-plugins`(`GitHubPluginRepository.kt:40`)는 항상 붙는다.

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/platform-impl/src/com/intellij/ide/plugins/RepositoryHelper.java:76-79` | 저장소 목록에 `GitHubPluginRepository.OFFICIAL` 추가. `-Dphantom.plugins.official=false`면 뺀다 | c8f33b8a61 |
| `platform/platform-impl/src/com/intellij/ide/plugins/RepositoryHelper.java:150-162` | `owner/repo` 형식 저장소를 `GitHubPluginRepository.load`로 읽음 | c8f33b8a61, 79d2dc6b96 |
| `platform/platform-impl/src/com/intellij/ide/plugins/PluginManagerMain.java:303-305` | 공식 Phantom 저장소 플러그인은 서드파티 경고 없이 설치 | c8f33b8a61 |
| `platform/platform-impl/src/com/intellij/ide/plugins/PluginHostsConfigurable.java:300-302` | 저장소 입력을 `owner/repo`로 정규화 | c8f33b8a61 |
| `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:686, 707` | 운영 빌드에서 설치할 수 없는 Marketplace 플러그인(상용 IDE 전용, 라이선스 플러그인 없이 유료)을 검색 결과에서 숨김 | c83c67511e |
| `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:708`, `platform/platform-impl/src/dev/phantom/ide/plugins/PhantomMarketplace.kt:29` (`isJetBrainsPlugin`) | JetBrains가 게시한 플러그인(조직 이름이 `JetBrains`로 시작)도 목록에서 숨김. Phantom이 유지하는 것은 번들이나 Phantom 플러그인 저장소에 자체 판이 있다. 위와 같은 조건(소스 실행, internal 모드, `-Dphantom.marketplace.showAll=true`이면 모두 보임) | 9235351694 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:840-841, 847-848` | `PhantomPluginAdvisor` 시작 후 활동, 알림 그룹 `Phantom Plugins`(이름은 `bundle="messages.PhantomIdeBundle"`, `key="notification.group.phantom.plugins"`) | de02add11d, d06d0012af |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:846` | New Project에 Spring Boot 항목(`SpringBootPluginNewProjectWizard`) | 53c0592476 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:2087-2088` | 비공개 GitHub 저장소용 `GitHubPluginRepositoryAuthProvider` | 79d2dc6b96 |
| `platform/platform-resources/src/idea/PlatformActions.xml:1975-1978` | 액션 `Phantom.SetGitHubPluginToken`. 파일 끝의 자체 `<actions resource-bundle="messages.PhantomIdeBundle">` 블록(이전에는 :1806-1807의 일반 블록 안). 이름은 `PhantomIdeBundle.properties`의 `action.Phantom.SetGitHubPluginToken.text` | 79d2dc6b96, d06d0012af |

새 코드는 `platform/platform-impl/src/dev/phantom/ide/plugins/`에 있다(5.1절). 검증: `./tests.cmd --module intellij.platform.tests --test com.intellij.ide.plugins.GitHubPluginRepositoryTest`.

### 3.5 성능, 인덱싱, 메모리

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/lang-impl/src/com/intellij/util/indexing/FileBasedIndexFileTypeListener.java:20-49` | 추가·삭제 없는 "맨" 파일 타입 변경 이벤트는 파일 타입, 연결, 무시 목록 스냅숏이 같으면 전체 재스캔을 건너뜀. `beforeFileTypesChanged`로 알린 변경은 항상 재스캔 | 46482b0bf1 |
| `platform/indexing-impl/src/com/intellij/find/ngrams/TrigramIndexFilter.kt:57-58, 66-72` | 라이브러리 안 텍스트 파일을 모두 트라이그램 인덱스에서 뺌 | 0a5ca64795 |
| `platform/indexing-impl/src/com/intellij/find/ngrams/TrigramIndex.java:93-94` | `shardlessVersion` 4 → 5 | 0a5ca64795 |
| `platform/platform-impl/src/com/intellij/platform/ide/IdeFingerprint.kt:76-78, 121, 125` | 인덱스 지문에 인덱싱에 영향을 주는 확장점을 등록한 플러그인만 넣음 | 6613a4ed82 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/VmOptionsGenerator.kt:26-32` | 기본 VM 옵션에 `-XX:G1PeriodicGCInterval=60000`, `-XX:G1PeriodicGCSystemLoadThreshold=0`, `-XX:MinHeapFreeRatio=20`, `-XX:MaxHeapFreeRatio=40`. 힙 여유 비율은 c7ea71c59e가 10, 25로 넣었고 1d49734b5c이 20, 40으로 올렸다(10-25%에서는 쉬고 난 뒤 작업이 힙을 다시 키우는 수집을 연달아 일으켰다, 표식 주석 `:26-28`) | c7ea71c59e, 1d49734b5c |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/VmOptionsGenerator.kt:34` | 기본 VM 옵션에서 `-ea` 삭제 | a91f9ef7e8 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:842-843` | `PhantomMemorySizer`: 물리 메모리에 맞춰 `-Xmx`와 파일 페이지 캐시를 사용자 `.vmoptions`에 씀 | a91f9ef7e8 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:2065-2068` | 레지스트리 `ide.idle.memory.cleaner.delay`(IDE가 유휴 상태가 된 뒤 메모리 정리까지의 밀리초) 기본값 5000 → 300000. 표식 주석에 따르면 정리는 IDE를 최대 1초 멈추는 full GC이고, 5초일 때는 다른 앱으로 전환할 때마다 돌아 돌아온 사용자 앞에서 멈췄다 | 1d49734b5c |
| `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:137-141` | `enableCds = true`(업스트림 기본 false, `platform/build-scripts/src/org/jetbrains/intellij/build/ProductProperties.kt:145`). 빌드가 런처 JVM 인자에 `-XX:SharedArchiveFile=$IDE_CACHE_DIR/<baseFileName><빌드 번호>.jsa`와 `-XX:+AutoCreateSharedArchive`를 넣고 `-Djava.system.class.loader`는 넣지 않는다(`platform/build-scripts/src/org/jetbrains/intellij/build/impl/BuildContextImpl.kt:464-473`). 주석에 따르면 설치·업데이트 뒤 첫 종료 때 아카이브를 쓰고(약 25초, 약 370 MB) 다음 시작부터 매핑해, `phantom-bench.sh` Kotlin 픽스처에서 "app initialization" 2.3-3.9 s → 0.8-2.9 s, 첫 smart mode 14-19 s → 5-9 s였다 | 71ff75bf3d |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:844-845`, `platform/platform-impl/src/dev/phantom/ide/memory/PhantomCdsArchiveCleaner.kt` (신규) | 시작 후 활동 `PhantomCdsArchiveCleaner`: 아카이브 이름에 빌드 번호가 들어가 업데이트마다 남는 이전 빌드의 `.jsa`를 캐시 폴더에서 지운다(5.1절) | 17cce24354 |

검증: `./tests.cmd --module intellij.platform.lang.tests --test com.intellij.util.indexing.FileTypeChangeRescanTest`, `./tests.cmd --module intellij.platform.tests --test dev.phantom.ide.memory.MemoryPlanTest`, `./tests.cmd --module intellij.platform.tests --test dev.phantom.ide.memory.CdsArchivesTest`(17cce24354, 실행하지 않음). 실행 지표는 `./phantom-bench.sh`와 `phantom-bench-report.py compare`(`docs/phantom/bench/README.md`).

### 3.6 UI 기본값

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/platform-impl/src/com/intellij/toolWindow/defaultToolWindowlayoutProvider.kt:263, 270, 282` | 기본 레이아웃에서 Structure, AIAssistant, Services 스트라이프 버튼 삭제 | f736b95fd0 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:707` | 환영 화면 Learn 탭 삭제 | f736b95fd0 |
| `platform/platform-resources/src/META-INF/PlatformExtensions.xml:711` 직전 | `TechInsightsLabPromoter` 삭제 | 1e0d4353f7 |
| `platform/editor-ui-api/src/com/intellij/ide/ui/UISettingsState.kt:147-148` | `differentiateProjects` 기본 false | f2e7052a14 |
| `platform/platform-resources/src/META-INF/LangExtensions.xml:1556-1557` | `Endpoints` 툴 윈도우 등록(`dev.phantom.ide.endpoints.EndpointsToolWindowFactory`). 업스트림 확장점 `com.intellij.microservices.endpointsProvider`(`platform/lang-api/src/com/intellij/microservices/endpoints/EndpointsProvider.kt:102-103`) 제공자가 있을 때만 보인다. 도구 창 제목은 코어가 `IdeBundle`에서만 읽으므로 키 `toolwindow.stripe.Endpoints`가 `platform/platform-api/resources/messages/IdeBundle.properties:2215`(`# phantom-idea` 아래)에 있고, 나머지 문구는 `PhantomLangBundle` | 60881b3dfb, 53c0592476, ccc29997cb, d06d0012af |

### 3.7 버그 수정

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/platform-impl/src/com/intellij/ui/AppUIUtil.kt:379-380` | 동의 목록을 복사한 뒤 `removeIf`. 비 JetBrains 벤더에서 불변 목록 예외로 설정 페이지가 깨지던 문제 | 1e0d4353f7 |
| `platform/platform-impl/src/com/intellij/openapi/editor/colors/impl/EditorColorsManagerImpl.kt:91-95, 212, 422, 613, 627` | 색 구성표를 다시 읽는 동안 다른 스레드가 이전 구성표를 찾을 수 있게 함. 테스트 `EditorColorSchemeTest.kt:84-134`, 리소스 `bundledSchemeForReloadTest.xml` | d86195e55c |
| `platform/ui.jcef/jcef/JBCefApp.java:382-388` | `Holder` 클래스 초기화 전에 `HttpConfigurable`을 만들어 clinit 안 서비스 요청 경고를 없앰 | e1204fc098 |
| `plugins/gradle/src/org/jetbrains/plugins/gradle/service/execution/LocalGradleExecutionAware.kt:124, 136, 140-172` | 프로젝트 SDK가 Gradle 버전과 안 맞거나 등록되지 않았으면 등록된 JDK 중 지원되는 최신으로 Gradle 실행 | 9e9ab0c964 |
| `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:523-528, 625, 649-652, 1107` | Marketplace 캐시를 `.part`로 받은 뒤 옮김. 읽을 수 없는 캐시와 ETag 삭제 | a61aa7bce3 |

### 3.8 Toolbox 제거 (수정 파일 쪽)

2.1절의 삭제와 함께 아래 업스트림 파일에서 Toolbox 분기를 지웠다. 모두 43b28af952다. `ExternalUpdateManager.java:14-15`의 `TOOLBOX` 상수와 `UpdateManagerUsagesCollector.kt:20`은 0a5ca64795가 Android 플러그인 컴파일을 위해 다시 다뤘다.

| 파일:행 | 패치 |
|---|---|
| `platform/platform-impl/src/com/intellij/openapi/updateSettings/impl/ExternalUpdateManager.java:14-15, 38` | `TOOLBOX`는 감지하지 않음(상수만 유지) |
| `platform/platform-impl/internal/src/com/intellij/internal/statistic/collectors/fus/UpdateManagerUsagesCollector.kt:20` | `TOOLBOX`를 "Other"로 |
| `platform/platform-impl/src/com/intellij/ide/actions/CreateLauncherScriptAction.java:45` | Toolbox 안내 분기 삭제 |
| `platform/platform-impl/src/com/intellij/openapi/updateSettings/impl/DefaultUpdateRequestParametersProvider.java:50` | Toolbox 이름 치환 삭제 |
| `platform/platform-impl/src/com/intellij/openapi/updateSettings/impl/upgradeToUltimate/installation/install/UltimateInstallationService.kt:90` | Toolbox 설치 경로 삭제 |
| `platform/platform-impl/src/com/intellij/openapi/updateSettings/impl/pluginsAdvertisement/FUSEventSource.kt` | `TRY_ULTIMATE_TOOLBOX_EVENT`, `logTryUltimateToolboxUsed` 삭제 |
| `platform/platform-impl/update-checker/src/com/intellij/openapi/updateSettings/impl/NoUpdatesDialog.java`, `UpdateSettingsConfigurable.kt` | Toolbox 메시지와 홍보 블록 삭제 |
| `platform/built-in-server/resources/intellij.platform.builtInServer.impl.xml:59-61` 부근 | Toolbox 확장점과 핸들러 등록 삭제 |
| `platform/smart-update/` (BUILD.bazel, iml, plugin.xml:18 직전, 번들, `IdeUpdateStep.kt`, `SmartUpdate.kt`) | Toolbox 기반 IDE 업데이트 단계 삭제, built-in-server 의존 삭제 |
| `platform/lang-impl/src/com/intellij/ide/actions/CopyPathProvider.kt:151` 직전, `platform/platform-resources/src/idea/PlatformActions.xml:1779`, `platform/platform-resources-en/src/messages/ActionsBundle.properties` | `CopyTBXReference` 액션 삭제(`CopyExternalReferenceGroup`은 빈 그룹으로 남김) |
| `platform/util/resources/misc/registry.properties` | 키 `copy.tbx.reference.enabled`, `ide.hide.toolbox.promo`, `ide.try.ultimate.automatic.installation.use.toolbox` 삭제 |
| `platform/ide-core/resources/messages/ApplicationBundle.properties`, `platform/platform-api/resources/messages/IdeBundle.properties`, `platform/smart-update/resources/messages/SmartUpdateBundle.properties` | Toolbox 문자열 삭제 |
| `platform/built-in-server/api-dump-unreviewed.txt` | 삭제한 Toolbox 클래스의 API 덤프 30줄 삭제 |

### 3.9 빌드, 패키징, 릴리스

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `build/src/OpenSourceCommunityInstallersBuildTarget.kt:20, 33` | Windows zip 단계와 macOS에서의 Windows 설치 프로그램 빌드를 건너뛰지 않음 | 1191317a96 |
| `build/src/OpenSourceCommunityInstallersBuildTarget.kt:23` | 플러그인 zip 단계(`ARCHIVE_PLUGINS`)를 건너뛰지 않음(Kotlin zip) | b238d55e24 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/WinExeInstallerBuilder.kt:41-42` | "운영 모드에서 macOS면 NSIS 건너뜀" 삭제 | d0eab60d1b |
| `phantom-build.sh` (신규) | 날짜로 구한 표시 버전·빌드 번호를 `installers.cmd`에 `-D`로 넘김(`:15`, `:45-46`, aarch64. 표시 버전은 추적 파일을 고치지 않고 `-Dphantom.version.major/minor`, c844a9f18e, 빌드 전. v26.11.13.1의 태그 커밋에는 68ae9c8c86이 손으로 쓴 `26` / `11.13`이 추적 파일에 있었다), Phantom-plugins 체크아웃(`$root/../Phantom-plugins` 또는 `PHANTOM_PLUGINS_REPO`, `:32`)에서 플러그인 25개(`:33`)를 `./gradlew --no-configuration-cache -q -PplatformFromRepository=true :<dir>:buildPlugin`으로 빌드해 `out/phantom-plugins`에 풂(`:29-44`, 체크아웃이나 zip이 없으면 `exit 1`, `:34`, `:42`, 9229667cc7), Windows x64 교차 빌드(`:58-68`), 중간 산출물 삭제 | fc05eddc04 외 4개, c844a9f18e, 9229667cc7, 2cff0f911e, 0748db2720, 588cd923ce |
| `phantom-release.sh` (신규) | 빌드, DMG 검사, 태그 push, GitHub 릴리스(초안 후 공개). Kotlin zip 확인(`:59-63`)과 Phantom-plugins 저장소에 Kotlin 릴리스·`marketplace.json` 게시(`:81-123`, 항목 이름 `Phantom Kotlin`, 벤더 `Phantom`). `origin`이 SSH 호스트 별칭이어도 저장소 이름을 뽑음(`:76`) | ae2a766191 외 5개, ac9ddb534c, bedeb19cae, f6adc2ce77, 8d27b3dfd8 |
| `phantom-install.sh` (신규) | `PHANTOM_APP`(기본 `/Applications/Phantom.app`, `.app`으로 끝나는 절대 경로만, `:22-23`) 교체와 재시작, `--release`로 릴리스 DMG 설치. 그 경로에서 실행 중인 Phantom만 프로세스 ID로 종료 요청(`:58-90`, 실행 파일 `MacOS/phantom`과 이름 변경 전 `MacOS/idea` 모두). 기본 경로에 `Phantom.app`이 아직 없으면 옛 `/Applications/Phantom IDEA.app`을 종료하고 새 앱을 둔 뒤 지움(`:24-29`, `:99`). 설정 폴더의 `idea.vmoptions`·`idea.jdk`를 새 이름이 없을 때 `phantom.*`으로 한 번 복사(`:100-105`) | a0c4e0d04a 외 5개, PR #4(f74262e02d), PR #5(d421f71ac4), d1a97edf7a, cd1fc57a5f |
| `phantom-sign.sh` (신규) | 로컬 인증서 "Phantom Local"로 재서명 | e71672a6ab |
| `.github/workflows/phantom-release.yml` (신규) | 셀프 호스티드 러너에서 수동 실행 릴리스. `notes` 입력 설명을 따옴표로 감싸 올바른 YAML로 고침(`:17`). 빌드 전에 Phantom-plugins를 `$GITHUB_WORKSPACE/../Phantom-plugins`에 클론하거나 `origin/main`으로 맞추는 단계(`:40-55`, 러너의 SSH 키 사용) | ae2a766191, 25be81d8e6, d0eab60d1b, 60ea243e02 |
| `tools/phantom-runner-setup.sh` (신규) | 셀프 호스티드 러너 등록 | ae2a766191 |

빌드 절차 자체는 `docs/phantom/BUILD.md`가 다룬다(별도 문서).

### 3.10 벤치마크 도구

| 파일 | 내용 | 커밋 |
|---|---|---|
| `phantom-bench.sh` | 설치된 앱을 격리된 설정으로 N회 실행해 지표 수집. 픽스처 복사본은 `out/phantom-bench/work/fixture.source`에 원본을 적어 같은 원본이면 다시 쓰고 다르면 바꾸며(예열한 `work/system-*`도 지움), 상대 경로 픽스처는 부른 폴더 기준(`:18`, `:41-42`, `:55-75`). 설치되지 않은 앱 복사본을 `PHANTOM_BENCH_APP`으로 재고, `PHANTOM_BENCH_PLUGINS`의 플러그인 zip을 실행마다 설정 복사본에 푼다(`:21-23`, `:89`) | 38efaf66bf, 6767ffc6e1, PR #4(f74262e02d), PR #5(d421f71ac4), 1d49734b5c |
| `phantom-bench-report.py` | `run`/`summary`/`compare`/`static`. compare는 시간 10%, 메모리·클래스 5%, 프리즈 1건 악화를 실패로 본다(6767ffc6e1 본문). static baseline이 번들 목록보다 낡았으면 종료 코드 2(`stale_static_baseline`, `:192-220`). git 기록이 없을 때, baseline 빌드 번호에 날짜가 없을 때, 얕은 클론일 때는 안내만 쓰고 건너뜀 | 38efaf66bf, 6767ffc6e1, 4b3ffd9498, PR #4(f74262e02d), PR #5(d421f71ac4) |
| `docs/phantom/bench/README.md`, `docs/phantom/bench/static-baseline.json` | 사용법과 정적 기준선 | 4b3ffd9498 |

`docs/phantom/bench/README.md:10`은 `docs/phantom/bench/baseline.json`을 쓰지만 이 파일은 HEAD에 없다. 런타임 기준선은 아직 만들지 않았다(4b3ffd9498 본문).

### 3.11 문서

`docs/` 아래 31개는 모두 추가 파일이다. 설계 문서 15개(`docs/phantom/design/`, 로드맵 포함), 벤치 결과 3개, 기능 표 1개, 벤치 도구 2개, 계획·명세 4개(`docs/plans/`, `docs/superpowers/`), 개발 문서 5개(`docs/phantom/README.md`, `ARCHITECTURE.md`, `FORK-DELTA.md`, `BUILD.md`, `DEVELOPMENT.md`, af12411f8d), 인덱싱 기록 1개(`docs/phantom/2026-10-29-indexing-changes.md`, af12411f8d). 코드 병합과 무관하다.

### 3.12 플러그인 로딩: 다른 플러그인의 ID를 별칭으로 가진 플러그인이 그 플러그인을 대체

Phantom-plugins 판 IntelliJ 플러그인(예 `dev.phantom.toml`, 별칭 `org.toml.lang`)과 IntelliJ 원본이 함께 설치되면 ID 충돌로 둘 다 빠지던 것을, 대체하는 쪽만 로드하도록 바꿨다(04dba66c3a 본문). 흐름은 ARCHITECTURE.md 4절 "플러그인 기술자 로딩".

| 파일:행 | 패치 | 커밋 |
|---|---|---|
| `platform/core-impl/src/com/intellij/ide/plugins/pluginInitContextIdConflictResolver.kt:28-31` | `resolveIdConflicts`의 충돌 경로에서 업스트림 `resolveConflicts`(:32-35) 전에 `excludeReplacedPlugins`를 부르고, 그것이 뺀 플러그인을 빼고 `resolveConflicts`에 넘김 | 04dba66c3a |
| `platform/core-impl/src/com/intellij/ide/plugins/pluginInitContextIdConflictResolver.kt:41-59` | `excludeReplacedPlugins`(신규, `Phantom:` 주석 :41-45): 각 플러그인의 `pluginAliases` 중 다른 플러그인의 ID가 있으면 그 플러그인을 뺀다. essential이면 빼지 않는다(:54) | 04dba66c3a |
| `platform/core-impl/src/com/intellij/ide/plugins/PluginNonLoadReason.kt:38-50` | 새 비적재 사유 `PluginIsReplacedByAnotherPlugin(plugin, replacement)`. `shouldNotifyUser = false`(:49). 표식 주석 없음 | 04dba66c3a |
| `platform/core-api/resources/messages/CoreBundle.properties:73-74` | `plugin.loading.error.short.replaced=Replaced by {0}`, `plugin.loading.error.long.replaced` | 04dba66c3a |
| `platform/platform-tests/testSrc/com/intellij/ide/plugins/PluginInitializationSelectPluginsToLoadTest.kt:444-510` | 업스트림 테스트 `plugin main ID conflicts with another plugin alias`(둘 다 제외를 기대)를 `plugin keeping another plugin's ID as an alias replaces it`(:446)로 바꾸고, 대체판이 원본 콘텐츠 모듈을 가진 경우(:465), 대체판을 끄면 원본이 로드되는 경우(:484), essential 원본은 대체되지 않는 경우(:498) 3개를 더함 | 04dba66c3a |

동작(코드와 테스트 기대값에서 읽음, 테스트는 이 문서를 쓰며 실행하지 않았다): 원본은 사유 "Replaced by <대체판 이름>"으로 빠지고, 원본 ID로 찾으면 대체판이 나온다(`resolvePluginId`, 테스트 :446). 비활성 플러그인은 충돌 해석 전에 걸러지므로 대체판을 끄면 원본이 로드된다. 원본이 essential이면 업스트림처럼 ID 충돌로 처리되어 대체판이 `PluginDeclaresConflictingId`로 빠진다. 번들 자체에는 대체된 원본 24개가 없으므로(3.2절) 이 경로는 사용자가 원본을 따로 설치했을 때 쓰인다(추정).

검증: `./tests.cmd --module intellij.platform.tests --test com.intellij.ide.plugins.PluginInitializationSelectPluginsToLoadTest`(모듈은 `platform/platform-tests/intellij.platform.tests.iml`의 `testSrc`).

## 4. 시스템 프로퍼티, 레지스트리 키, 환경 변수

### 4.1 `phantom.*` 시스템 프로퍼티

| 이름 | 읽는 곳 | 기본값 | 효과 |
|---|---|---|---|
| `phantom.jetbrains.services` | `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt:109`, `platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java:197` | false | true면 3.3.1절의 JetBrains 호출을 모두 되살린다 |
| `phantom.plugins.official` | `platform/platform-impl/src/com/intellij/ide/plugins/RepositoryHelper.java:77` | 미설정(켜짐) | `false`면 `newkayak12/Phantom-plugins` 저장소를 붙이지 않는다 |
| `phantom.marketplace.showAll` | `platform/platform-impl/src/dev/phantom/ide/plugins/PhantomMarketplace.kt:20` | false | true면 설치할 수 없는 Marketplace 플러그인과 JetBrains가 게시한 플러그인(9235351694)도 보인다. 소스 실행과 internal 모드도 같은 효과(`:21-23`) |
| `phantom.github.oauth.client.id` | `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/GHDeviceFlow.kt:32` | 미설정. 내장 값 `PHANTOM_CLIENT_ID`(`:30`)가 빈 문자열이다 | GitHub 디바이스 플로 로그인에 쓸 OAuth 앱 client id. 없으면 `gh` CLI 로그인만 된다(`GHOAuthCredentialsUi.kt:55` 이하) |
| `phantom.build.number` | 빌드 스크립트: `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt:11,14`, `ApplicationInfoPropertiesImpl.kt:171`, `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:242` | 미설정 | 빌드 시 `PH-<YYMMDD>.<N>`. `phantom-build.sh:46,63`이 넘긴다. IDE 런타임이 읽는 값이 아니다 |
| `phantom.version.major` | 이름 `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt:21`, 읽는 곳 `ApplicationInfoPropertiesImpl.kt:72`, `:179` | 미설정 | 빌드 시 표시 버전의 major(빌드 날짜의 YY). 있으면 XML의 `major`보다 먼저 쓰고 패키지 안 `<version>`에도 쓴다. `phantom-build.sh:46,63`이 넘긴다. 없으면 XML 값(자리표시자 `0`). IDE 런타임이 읽는 값이 아니다(c844a9f18e, 빌드 전) |
| `phantom.version.minor` | 이름 `PhantomBuildNumber.kt:22`, 읽는 곳 `ApplicationInfoPropertiesImpl.kt:74`, `:179` | 미설정 | 표시 버전의 minor(`<MM>.<DD>`). 위와 같다. 없으면 XML 값(자리표시자 `0.0`) |

같은 접두어지만 시스템 프로퍼티가 아닌 것: `phantom.memory.auto.heap`, `phantom.memory.auto.page.cache`는 `PropertiesComponent` 키다(`platform/platform-impl/src/dev/phantom/ide/memory/PhantomMemorySizer.kt:64-65`). `PhantomMemorySizer`가 마지막으로 쓴 값을 기억해, 사용자가 직접 바꾼 값은 건드리지 않는다.

### 4.2 Phantom이 기본값이나 의미를 바꾼 업스트림 프로퍼티

| 이름 | 읽는 곳 | 업스트림 기본 → Phantom | 효과 |
|---|---|---|---|
| `idea.updates.url` | `BaseJetBrainsExternalProductResourceUrls.kt:114` | jetbrains.com updates.xml → 없음 | 값이 있을 때만 IDE 업데이트 확인 |
| `ij.startup.error.support.url` | `StartupErrorReporter.java:78, 178` | jb.gg 링크 → 없음 | 값이 있을 때만 "?" 지원 링크 |
| `ij.startup.error.handler.class` | `StartupErrorReporter.java:79, 197` | 의미 추가 | 값이 있으면 플래그 없이도 Report 버튼 |
| `idea.diagnostic.opentelemetry.metrics.file` | `OpenTelemetryUtils.kt:64` | `telemetry/open-telemetry-metrics.csv` → 빈 값 | 경로를 줄 때만 CSV |
| `idea.diagnostic.opentelemetry.meters.file.json` | `OpenTelemetryUtils.kt:67` | `telemetry/open-telemetry-meters.json` → 빈 값 | 경로를 줄 때만 JSON |
| `intellij.indexes.diagnostics.dump` (신규) | `IndexDiagnosticDumper.kt:72` | - → false | true면 인덱싱 진단 리포트 |
| `intellij.storage.diagnostics.dump` (신규) | `StorageDiagnosticData.kt:68` | - → false | true면 스토리지 진단 |
| `idea.non-blocking-action.enable-monitoring` | `NonBlockingReadActionImpl.java:127` | true → false | NBRA OTel 미터 |

### 4.3 레지스트리 키

Phantom 고유 레지스트리 키는 없다. 바꾼 업스트림 키는 아래와 같다.

| 키 | 정의 위치 | 업스트림 → Phantom | 커밋 |
|---|---|---|---|
| `csat.survey.enabled` | `platform/feedback/resources/intellij.platform.feedback.xml:83` | true → false | 3676457781 |
| `tips.of.the.day.feedback` | `platform/tips-of-the-day/resources/intellij.platform.tips.xml:41-44` | staging → nowhere (값: nowhere, staging, production). nowhere이면 `TipPanel`이 좋아요/싫어요 줄을 숨긴다 | 4ca69e1a95 |
| `jdk.updater` | `platform/platform-resources/src/META-INF/PlatformExtensions.xml:1387` | true → false | d7c795f347 |
| `jdk.downloader.url` | `PlatformExtensions.xml:1389`, 읽는 곳 `JdkList.kt:530` | 변경 없음(빈 값). 값이 있으면 foojay보다 먼저 쓴다 | - |
| `ide.idle.memory.cleaner.delay` | `PlatformExtensions.xml:2067-2068` | 5000 → 300000(밀리초, 유휴 상태가 된 뒤 메모리 정리까지) | 1d49734b5c |
| `gradle.compatibility.update.interval` | `plugins/gradle/plugin-resources/intellij.gradle.xml:219-221` | 86400 → 0 | d7c795f347 |
| `ide.show.plugin.suggestions.on.open` | `platform/util/resources/misc/registry.properties:2241` | true → false | d7c795f347 |
| `copy.tbx.reference.enabled`, `ide.hide.toolbox.promo`, `ide.try.ultimate.automatic.installation.use.toolbox` | `registry.properties`에서 삭제 | 삭제 | 43b28af952 |

### 4.4 환경 변수

| 이름 | 읽는 곳 | 기본값 | 효과 |
|---|---|---|---|
| `PHANTOM_WINDOWS` | `phantom-build.sh:58` | 1 | 0이면 DMG만 빌드하고 Windows 교차 빌드를 건너뛴다 |
| `PHANTOM_BUILD_SEQ` | `phantom-build.sh:19-20`, `phantom-release.sh:19-22` | 미설정 | 그날 빌드 순번 N을 고정한다. 없으면 `out/phantom-build-seq`에서 계산 |
| `PHANTOM_PLUGINS_REPO` | `phantom-build.sh:32`, `phantom-release.sh:62` | `$root/../Phantom-plugins`(이 체크아웃 옆) | 번들할 플러그인 25개를 빌드할 Phantom-plugins 체크아웃. `settings.gradle.kts`가 없으면 빌드가 멈춘다(`phantom-build.sh:34`). 릴리스는 같은 체크아웃에 Kotlin을 게시하며 `.phantom/marketplace.json`이 없으면 멈춘다(`phantom-release.sh:63`). 9229667cc7, ac9ddb534c |
| `PHANTOM_APP` | `phantom-install.sh:22`, `phantom-bench.sh:23`, `phantom-sign.sh`(인자가 먼저) | `/Applications/Phantom.app` | 설치·측정·서명할 앱. 설치는 `.app`으로 끝나는 절대 경로만 받고(`phantom-install.sh:23`) 그 경로에서 실행 중인 Phantom만 끈다. 벤치에서는 `PHANTOM_BENCH_APP`이 먼저다. PR #4(f74262e02d), PR #5(d421f71ac4) |
| `PHANTOM_BENCH_APP`, `PHANTOM_BENCH_PLUGINS` | `phantom-bench.sh:21-23`, `:89` | 미설정 | 측정할 다른 앱 복사본(예: 풀어 둔 DMG, `PHANTOM_APP`보다 먼저), 실행마다 설정 복사본의 `plugins/`에 풀 플러그인 zip 목록(공백 구분. 릴리스가 번들하지 않는 Kotlin 등). 1d49734b5c |
| `PHANTOM_CONFIG_DIR`, `PHANTOM_BENCH_FIXTURE` | `phantom-bench.sh:24`, `:30` | `$HOME/Library/Application Support/Phantom/PhantomIDEA`, `$HOME/projects/private/prototype-reservation-system` | 벤치에 복사할 사용자 설정, 열 프로젝트(`--fixture`가 먼저, 상대 경로는 부른 폴더 기준). PR #4(f74262e02d), PR #5(d421f71ac4) |
| `PHANTOM_SIGN_SETUP`, `PHANTOM_SIGN_OPTIONAL` | `phantom-sign.sh`, `phantom-install.sh:108` | 설치에서는 `1`, `1` | 인증서가 없을 때 먼저 만들지, 서명 실패를 경고로 끝낼지(BUILD.md 5장). PR #4(f74262e02d) |
| `PHANTOM_FOOJAY_E2E` | `platform/lang-impl/testSources/com/intellij/openapi/projectRoots/impl/jdkDownloader/FoojayJdkListTest.kt:98` | 미설정 | `true`면 실제 JDK를 내려받는 E2E 테스트를 실행 |
| `PHANTOM_FOOJAY_E2E_SDK` | 같은 파일 `:101` | 문자열 `temurin-21` | E2E 테스트가 받을 SDK 이름 |
| `GH_TOKEN`, `GITHUB_TOKEN` | `platform/platform-impl/src/dev/phantom/ide/plugins/GitHubTokens.kt:40-41` | 미설정 | 비공개 GitHub 플러그인 저장소 토큰. 순서는 비밀번호 저장소 → 이 변수 → `gh auth token` |
| `GH_TOKEN` | `phantom-install.sh:35` | `gh auth token` | `--release` 다운로드 토큰 |
| `NOTES` | `phantom-release.sh:65-68` | 이전 태그 이후 커밋 | 릴리스 노트 |

## 5. 이 리포에 남아 있는 Phantom 고유 코드

### 5.1 HEAD에 있는 것

`.idea/modules.xml`의 Phantom 모듈은 `:359-362`의 4개(`phantom.theme`, `phantom.sync.core`, `phantom.sync`, `phantom.sync.tests`)뿐이다. `plugins/` 아래 phantom 디렉터리도 `phantom-sync`, `phantom-theme` 둘뿐이다.

| 위치 | 무엇 | 번들 | 테스트 |
|---|---|---|---|
| `plugins/phantom-sync/` (모듈 `phantom.sync`, `phantom.sync.core`, `phantom.sync.tests`, ID `dev.phantom.sync`) | Backup and Sync를 사용자 git 저장소로. 업스트림 `intellij.settingsSync.core`의 `settingsSync.communicatorProvider` 확장 | 예 (`IdeaCommunityProperties.kt:25`) | `plugins/phantom-sync/tests/testSrc/dev/phantom/sync/GitRepoCommunicatorTest.kt` |
| `plugins/phantom-theme/` (모듈 `phantom.theme`, ID `dev.phantom.theme`) | Phantom Dark/Light 테마와 편집기 색. 테마 JSON은 `tools/generate-themes.py`가 업스트림 Islands 테마에서 만든다 | 예 (`:26`), 기본 LAF(`IdeaApplicationInfo.xml:10`) | 없음 |
| `platform/platform-impl/src/dev/phantom/ide/plugins/` (`GitHubPluginRepository.kt`, `GitHubTokens.kt`, `PhantomMarketplace.kt`, `PhantomPluginAdvisor.kt`, `PhantomPluginNewProjectWizard.kt`) | GitHub 플러그인 저장소, 토큰, Marketplace 필터, 라이브러리 기반 플러그인 제안, New Project의 Spring Boot 설치 안내 | 플랫폼 모듈 `intellij.platform.ide.impl` 안(항상 포함) | `platform/platform-tests/testSrc/com/intellij/ide/plugins/GitHubPluginRepositoryTest.kt`. `PhantomMarketplace`, `PhantomPluginAdvisor`, 위저드 전용 테스트는 없다 |
| `platform/platform-impl/src/dev/phantom/ide/PhantomIdeBundle.kt`, `platform/platform-impl/resources/messages/PhantomIdeBundle.properties`, `platform/lang-impl/src/dev/phantom/ide/PhantomLangBundle.kt`, `platform/lang-impl/resources/messages/PhantomLangBundle.properties` (d06d0012af, 신규) | Phantom 플랫폼 코드의 문구 번들. `PhantomIdeBundle` 17개 키(토큰 액션·대화상자, 플러그인 추천 알림, Spring Boot 새 프로젝트 항목, 알림 그룹 이름), `PhantomLangBundle` 11개 키(Endpoints). 업스트림 `.properties`를 덜 건드리려고 따로 두었고 이름이 모듈마다 다른 것은 platform-impl과 lang-impl이 코어 클래스 로더를 같이 쓰기 때문이다. 같은 커밋이 `IdeBundle.properties:2214-2215`와 `GithubBundle.properties:47` 이하(`# phantom-idea`)에 키 1개와 10개를 더했다 | 플랫폼(항상) | 없음 |
| `platform/platform-impl/src/dev/phantom/ide/memory/PhantomMemorySizer.kt` | 기계별 힙·페이지 캐시 크기 | 플랫폼(항상) | `platform/platform-tests/testSrc/dev/phantom/ide/memory/MemoryPlanTest.kt` |
| `platform/platform-impl/src/dev/phantom/ide/memory/PhantomCdsArchiveCleaner.kt` (17cce24354, 신규) | 시작 후 활동(`PlatformExtensions.xml:845`). JVM 인자의 `-XX:SharedArchiveFile=` 경로를 읽어, 같은 폴더에서 이름의 앞 글자(숫자 앞까지)가 같고 바로 뒤가 숫자이며 `.jsa`로 끝나는 다른 파일(이전 빌드의 CDS 아카이브)을 지운다(`CdsArchives.stale`). 단위 테스트·headless에서는 돌지 않고 앱 실행당 한 번, IO 스레드 | 플랫폼(항상) | `platform/platform-tests/testSrc/dev/phantom/ide/memory/CdsArchivesTest.kt`(`CdsArchives.stale`만, 실행하지 않음) |
| `platform/lang-impl/src/dev/phantom/ide/endpoints/` (`EndpointsToolWindow.kt`, `EndpointRequests.kt`) | Endpoints 툴 윈도우와 .http 요청 생성 | 플랫폼 `intellij.platform.lang.impl`(제공자 플러그인이 있을 때만 창이 보임) | `platform/lang-impl/testSources/dev/phantom/ide/endpoints/EndpointRequestsTest.kt` |
| `platform/platform-resources/src/icons/phantom/springBoot.svg`, `springBoot_dark.svg` | New Project Spring Boot 아이콘 | 플랫폼 | - |
| `platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/FoojayJdkList.kt` | foojay JDK 목록 변환과 다운로드 해석. 업스트림 패키지에 있고 파일 머리에 JetBrains 저작권 문구가 있어 업스트림 파일로 오해하기 쉽다. PR #5(d421f71ac4)가 그 아래 `:2`에 포크가 더한 파일이라는 `// phantom-idea:` 줄을 넣었다 | 플랫폼 | `FoojayJdkListTest.kt`, `testData/jdkDownload/foojay-packages.json` |
| `plugins/github/github-core/src/org/jetbrains/plugins/github/authentication/GHDeviceFlow.kt` | GitHub 디바이스 플로와 `gh` 토큰(로그인 문구는 `GithubBundle`의 `phantom.*` 키 10개, d06d0012af) | 업스트림 GitHub 플러그인 소스 안. 6019d518b2부터 번들의 GitHub 플러그인은 Phantom-plugins 빌드라 이 코드가 배포 앱에 있는지는 미확인(3.2절 끝) | 없음 |
| `platform/build-scripts/src/org/jetbrains/intellij/build/impl/PhantomBuildNumber.kt` | 빌드 번호와 표시 버전 속성 이름(`:16-24`, c844a9f18e) | 빌드 스크립트 | 표시 버전 쪽은 `ApplicationInfoPropertiesTest.java:28-44`(실행 전). 파일 자체의 전용 테스트는 없다 |
| `plugins/ide-features-trainer/testSrc/training/ui/FeedbackSubmissionGateTest.kt` | 3.3.1절 `submitFeedback` 게이트 테스트(4ca69e1a95, 실행 전) | - | 자신(모듈 `intellij.featuresTrainer.tests`) |
| `platform/lang-impl/testSources/com/intellij/util/indexing/FileTypeChangeRescanTest.kt` | 3.5절 재스캔 패치의 테스트 | - | 자신 |
| 루트 스크립트 6개, `tools/phantom-runner-setup.sh`, `.github/workflows/phantom-release.yml` | 3.9, 3.10절 | - | 없음 |

### 5.2 Phantom-plugins 리포로 옮긴 것

아래는 이 리포에 없다. `GitHubPluginRepository.kt:40`의 공식 저장소 `newkayak12/Phantom-plugins`에서 같은 플러그인 ID로 설치한다. 추가·삭제 커밋은 `plugin.xml` 기준으로 확인했다.

| 이전 경로 | 플러그인 ID (삭제 직전 plugin.xml) | 추가 | 이 리포에서 삭제 |
|---|---|---|---|
| `plugins/phantom-spring/` | `dev.phantom.spring` | e8ef2fd569 | c83c67511e |
| `plugins/phantom-spring-boot/` | `dev.phantom.spring.boot` | 9ae24548c2 | c83c67511e |
| `plugins/phantom-jpa/` | `dev.phantom.jpa` | e1712f214c | 53c0592476 |
| `plugins/phantom-db/` | `dev.phantom.db` | 87438dbfb4 | de02add11d |
| `plugins/phantom-http/` | `dev.phantom.http` | 83d8ab6e82 | cab0fa60d7 (디렉터리), ac3007c625 (번들·모듈 목록) |
| `plugins/phantom-workspaces/` | `dev.phantom.workspaces` | 2a4c13ce66 | 8983487a29 |
| `plugins/phantom-tabs/` | `dev.phantom.tabs` | 49b1b9e145 | b68d97332e (플랫폼 훅 f5778de337, b02cc88cfe도 되돌림) |
| `plugins/phantom-icons/` | `dev.phantom.icons`. 같은 날 Phantom-plugins로 간 Icon Pack은 다른 ID `dev.phantom.iconpack`이다(1570329708 본문에만 근거하고 Phantom-plugins는 읽지 못해 확인 못 함). 즉 ID가 바뀐 이동이다 | d7310f9556 | 1570329708 |

업스트림 플러그인 3개는 소스를 이 리포에 남긴 채 번들에서 빼고 Phantom-plugins 판으로 대체했다(e2a4ffa4ee 본문 기준, 이 리포에서 확인할 수 없음): `intellij.java.byteCodeViewer` → `bytecode`(`dev.phantom.bytecode`), `intellij.webp` → `webp`(`dev.phantom.webp`), `intellij.vcs.git.commit.modal` → `modal-commit`(`dev.phantom.modalcommit`). 이 셋은 필요할 때 설치한다.

9229667cc7부터 업스트림 플러그인 7개(TOML, YAML, Shell Script, Decompiler, Coverage, TextMate, Markdown)와 Lombok은 Phantom-plugins에서 빌드한 판(`toml`, `yaml`, `sh`, `decompiler`, `coverage`, `textmate`, `markdown`, `lombok`)을 번들에 넣는다(3.2절). 9235351694부터 JetBrains Mermaid 대신 Phantom Mermaid(`mermaid`), 6019d518b2부터 Copyright, EditorConfig, HTML Tools, TestNG, Java I18n, Grazie, Terminal, GitHub도 Phantom-plugins 판(`copyright`, `editorconfig`, `htmltools`, `testng`, `java-i18n`, `grazie`, `terminal`, `github`)을 넣는다. 그 뒤 PR 없이 들어온 2cff0f911e이 JUnit, Properties(`junit`, `properties`), 0748db2720가 Repository Search, JSON, Gradle DSL(`repository-search`, `json`, `gradle-dsl`), 588cd923ce가 Groovy, Groovy Scripting, Groovy Live Templates(`groovy`, `groovy-scripting`, `groovy-live-templates`)를 더해 모두 25개다. 원본 소스는 이 리포에 그대로 있다(`plugins/toml/`, `plugins/yaml/`, `plugins/sh/`, `plugins/java-decompiler/`, `plugins/coverage/`, `plugins/textmate/`, `plugins/markdown/`, `plugins/lombok/`, `plugins/github/`, `plugins/grazie/`, `plugins/terminal/`, `plugins/junit/`, `plugins/properties/`, `plugins/repository-search/`, `json/`, `plugins/groovy/` 등). 그 소스를 고쳐도 배포 앱이 바뀌는지는 Phantom-plugins가 무엇으로 빌드하는지에 달렸고 확인하지 못했다(3.2절 끝). Phantom-plugins 판의 ID는 `dev.phantom.*`이고 원본 ID를 별칭으로 둔다는 것은 9229667cc7, 6019d518b2, 2cff0f911e, 0748db2720 본문과 `IdeaCommunityProperties.kt:29-34` 주석의 설명이고, 이 리포에서 확인한 ID는 04dba66c3a 본문의 `dev.phantom.toml`뿐이다.

Kotlin(`kotlin.plugin`)은 ac9ddb534c부터 번들하지 않는다. 이 리포의 빌드가 `plugins/kotlin` 소스로 zip을 만들고 `phantom-release.sh`가 Phantom-plugins 저장소의 릴리스 `kotlin-262.<YYMMDD>`와 `.phantom/marketplace.json` 항목(`org.jetbrains.kotlin`, `since` 그 플랫폼 빌드)으로 게시한다. 8d27b3dfd8부터 플러그인 이름은 Phantom Kotlin, 벤더는 Phantom이고(3.2절 표) ID는 그대로다. 그 저장소가 Kotlin이나 Gradle Kotlin DSL 스크립트를 쓰는 프로젝트에 설치를 제안한다(커밋 본문, 이 리포에서 확인할 수 없음).

옮긴 플러그인의 설계 문서는 `docs/phantom/design/`에 남아 있다.

## 6. 충돌 위험이 큰 업스트림 파일

수정 빈도는 `git log --format= --name-only d2faebf601..HEAD | sort | uniq -c | sort -rn`(HEAD = PR #5 브랜치의 표식 커밋, 1절)으로 셌다. 아래는 업스트림에 원래 있던 파일만 골랐다. 표식 주석만 더한 그 커밋도 한 번으로 센다. 그 뒤 커밋(스쿼시 머지 포함)으로 바뀐 횟수는 다시 세지 않았다.

| 커밋 수 | 파일 | 예상 충돌 |
|---|---|---|
| 33 | `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt` | 업스트림이 `bundledPluginModules` 식(`:91-131`)과 커스터마이저를 바꾸면 같은 블록에서 충돌한다. Phantom 쪽 식을 유지하고, 새 `IDEA_BUNDLED_PLUGINS` 항목마다 뺄지 정한다. 빠진 ID가 업스트림에서 이름이 바뀌면 `.removing`이 조용히 효과를 잃을 수 있다(추정). Phantom-plugins 판으로 바꾼 24개는 원본이 다시 들어오면 같은 ID와 모듈을 두 번 차지한다(9229667cc7 본문). 업스트림이 Kotlin 레이아웃이나 `pluginModulesToPublish`를 바꾸면 Kotlin zip 게시(`:131-135`)도 다시 본다. `enableCds`(`:141`)를 업스트림이 건드리면 Phantom 값을 유지한다 |
| 22 | `.idea/modules.xml` (`:359-362` 추가, 프로모 3개 삭제) | 업스트림 모듈 추가 줄과 인접 충돌. IDE가 직렬화하는 파일이라 정렬·공백을 바꾸지 않고 손으로 합친다 |
| 21 | `intellij.idea.community.main.iml` (`:138-140` 추가, 프로모 1줄 삭제) | 같은 RUNTIME 의존 블록에서 충돌. iml은 원형을 지켜야 한다 |
| 21 | `build/bazel-generated-file-list.txt` (`:1475-1478`) | 생성 파일. 아래 참고 |
| 20 | `BUILD.bazel` (`:284-286`, `:473-475`) | 생성 파일. 아래 참고 |
| 9 | `platform/platform-resources/src/META-INF/PlatformExtensions.xml` (ded15d8d28 기준 `:24, 707, 711 직전, 840-846, 1384, 2082-2083`) | 2000줄이 넘는 활발한 파일이라 여러 덩어리에서 문맥 충돌. 삭제한 등록(`ITNReporter`, Learn 탭, `TechInsightsLabPromoter`)을 업스트림이 고치면 되살아나지 않게 본다 |
| 8 | `community-resources/resources/idea/IdeaApplicationInfo.xml` | 업스트림이 태그마다 `<version>`을 올리므로 `:2`에서 항상 충돌. 이 줄은 이제 자리표시자 `major="0" minor="0.0"`이고 표시 버전은 빌드 속성(`phantom.version.*`)이 정하므로, Phantom의 자리표시자 줄을 택한다(c844a9f18e). 업스트림이 새 요소를 더하면 받는다 |
| 5 | `platform/platform-impl/src/com/intellij/ide/plugins/marketplace/MarketplaceRequests.kt` | 함수 안 게이트(ded15d8d28 기준 `:403, 572, 613, 935`)는 텍스트 충돌이 없어도 의미가 깨질 수 있다. 병합 뒤 새 JetBrains 호출 경로가 생겼는지 다시 찾는다. Marketplace 목록 필터(`:707-708`)는 9235351694가 한 줄 더했다 |
| 4 | `ApplicationInfoPropertiesImpl.kt`, `PlatformActions.xml` | 작은 패치. 문맥 충돌 정도 |
| 3 | `RepositoryHelper.java`, `ExternalUpdateManager.java`, `UpdateManagerUsagesCollector.kt`, `IntelliJIdeaExternalResourceUrls.kt`, `registry.properties`, `BaseIdeaProperties.kt` | 작은 패치. 문맥 충돌 정도. `BaseIdeaProperties.kt`는 업스트림이 `CE_CLASS_VERSIONS`의 커버리지 줄(`:71`)이나 Groovy 줄(`:82-83`, 588cd923ce)을 바꾸면 Phantom 경로(`plugins/coverage/...`, `plugins/groovy/...`)를 유지한다. 그 뒤 71ff75bf3d이 `JetBrainsProductProperties.kt`의 벤더 허용 목록(`:59-60`)도 고쳤다 |
| 2 | `VmOptionsGenerator.kt` | 작은 패치. 문맥 충돌 정도 |
| 1 | `build.txt` | 업스트림 태그마다 바뀐다. `262.SNAPSHOT`을 유지한다 |

한 번만 고쳤지만 깊게 고친 파일:

| 파일 | 예상 충돌 |
|---|---|
| `platform/lang-impl/src/com/intellij/util/indexing/FileBasedIndexFileTypeListener.java` (+59) | 파일 대부분을 다시 썼다. 업스트림 변경은 손으로 옮긴다 |
| `platform/core-impl/src/com/intellij/ide/plugins/pluginInitContextIdConflictResolver.kt` (`:28-31`, `:41-59`), `platform/platform-tests/testSrc/com/intellij/ide/plugins/PluginInitializationSelectPluginsToLoadTest.kt` | 업스트림 충돌 해석 함수 안에 단계를 끼웠고 업스트림 테스트 하나의 기대값을 뒤집었다(3.12절). 업스트림이 충돌 해석이나 그 테스트를 바꾸면 덩어리 충돌이 나고, 병합 뒤 별칭 대체 테스트를 다시 돌린다 |
| `platform/indexing-impl/src/com/intellij/find/ngrams/TrigramIndex.java:94` | `shardlessVersion` 5. 업스트림이 같은 번호를 올리면 Phantom 값을 그보다 크게 다시 정해야 인덱스가 재구성된다(추정) |
| `platform/platform-impl/src/com/intellij/platform/ide/bootstrap/StartupErrorReporter.java`, `GHOAuthCredentialsUi.kt`, `TerminalAgent.kt`, `ShShellcheckUtil.java`, `JdkInstaller.kt`, `JdkList.kt`, `EditorColorsManagerImpl.kt`, `IdeFingerprint.kt`, `LocalGradleExecutionAware.kt` | 함수 본문을 바꿨다. 업스트림이 같은 함수를 고치면 덩어리 충돌. ShellCheck는 업스트림이 버전을 올리면 URL 형식(`:69-73`)을 다시 맞춘다 |
| 삭제한 업스트림 파일 35개 (2.1절) | 업스트림이 고치면 modify/delete 충돌. 삭제를 유지하되, 업스트림의 다른 코드가 지운 심볼을 새로 참조하는지 컴파일로 확인한다(0a5ca64795의 `ExternalUpdateManager.TOOLBOX` 사례) |
| `plugins/phantom-theme/resources/themes/*.theme.json` | Phantom 파일이지만 업스트림 Islands 테마에서 생성한다. 업스트림 테마가 바뀌면 `python3 -I plugins/phantom-theme/tools/generate-themes.py`(`generate-themes.py:6`)로 다시 만든다 |

### 6.1 병합하지 말고 다시 만들 파일

| 파일 | 생성 방법 | 확인 근거 |
|---|---|---|
| `BUILD.bazel`, 각 모듈 `BUILD.bazel`, `build/bazel-generated-file-list.txt` | `build/jpsModelToBazelCommunityOnly.cmd` (`bazel.cmd run //:jps-to-bazel`, `build/jpsModelToBazelCommunityOnly.cmd:12`) | `BUILD.bazel:148`의 `### auto-generated section` 표식, `platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/JpsModuleToBazel.kt:509`는 `deleteOldFiles` 안에서 이 목록을 읽어 옛 생성 파일을 지움, `platform/build-scripts/bazel/README.md:248-252` |
| `.idea/modules.xml`, `*.iml` | 생성 파일이 아니다. 원본이다(`AGENTS.md`). 손으로 합친 뒤 위 명령으로 Bazel 파일을 다시 만든다 | - |
| `platform/built-in-server/api-dump-unreviewed.txt` | 다시 만드는 명령은 확인하지 못했다(`tools/apiDump/` 디렉터리는 있음) | - |

`AGENTS.md`는 2026-11-12에 `build/jpsModelToBazelCommunityOnly.cmd`로 고쳤다. 옛 경로 `./build/jpsModelToBazel.cmd`는 이 트리에 없는데도 `.ai/guide.md`, `.ai/tool-permissions.json`, `.claude/skills`와 `.agents/skills`의 module-dependencies, pseudo-kmp, testing-internals, module-set-pluginization, extract-module 등을 가리키고 있었다. PR #4(f74262e02d)가 이들을 `build/jpsModelToBazelCommunityOnly.cmd`로 고쳤다(생성기가 이 포크에서 도는지는 여전히 미확인). Phantom 커밋이 이 명령으로 Bazel 파일을 만들었는지는 히스토리에서 확인할 수 없다.

## 7. README와 어긋나는 점

> 2026-11-12 메모: 아래 README 표는 개편 전 `README.md`(커밋 be60914099, `git show be60914099:README.md`)의 줄 번호와 내용을 가리킨다. 같은 날 README 개편(`README.md`, `README.ko.md`)에서 이 다섯 항목을 모두 반영했다. 로드맵은 본문을 고치지 않고 머리에 상태 메모 2줄을 넣었으며, 아래 로드맵 줄 번호는 그 메모를 넣은 뒤의 번호다.

개편 전 README.md:

| 위치 | README | HEAD |
|---|---|---|
| `README.md:113` | 산출물이 "Apple silicon only" | `phantom-build.sh:58-68`이 기본으로 Windows x64 `.exe`와 `.win.zip`도 만든다(`PHANTOM_WINDOWS=0`일 때만 빠짐). 릴리스 워크플로도 Windows 파일을 올린다(`phantom-release.yml:1-2`) |
| `README.md:40-54` | "Planned" 아래 표에 완료된 `phantom-sync`, 옮긴 플러그인, 존재하지 않는 `phantom-spring-runtime`(`:47`), `phantom-containers`(`:54`)가 상태 표시 없이 섞여 있다 | 이 리포의 Phantom 플러그인은 `phantom-sync`, `phantom-theme`뿐이다. `phantom-theme`은 표에 없다 |
| `README.md:45` | Endpoints가 Phantom-plugins `spring`으로 옮겨졌다 | Endpoints 툴 윈도우는 코어 `platform/lang-impl/src/dev/phantom/ide/endpoints/`에 있다. 플러그인은 제공자만 등록한다(53c0592476) |
| `README.md:31-38` | "Done" 목록 | 번들에서 뺀 14개 모듈(be60914099 기준. 지금은 39개, 3.2절), `phantom.jetbrains.services` 게이트(3.3절), Toolbox 제거, GitHub 플러그인 저장소, foojay JDK 목록, `PhantomMemorySizer`, VM 옵션 변경, Lombok 번들, Phantom 테마 기본값이 빠져 있다 |
| `README.md:136` | `phantom-build.sh`가 날짜를 찍는다 | 맞다. 다만 그 방법이 추적 파일 `IdeaApplicationInfo.xml`을 sed로 고치는 것이라(be60914099의 `phantom-build.sh:16`) 빌드 뒤 작업 트리가 더러워진다는 말이 없었다. c844a9f18e 이후 빌드는 추적 파일을 고치지 않는다(빌드 전, 코드를 읽은 결론) |

`docs/phantom/design/2026-10-16-roadmap.md`:

| 위치 | 로드맵 | HEAD |
|---|---|---|
| `:38-42` | S1-S5(phantom-spring, -spring-boot, -jpa, -http, -db)를 "완료 (DMG 번들 확인)"으로 적는다 | 다섯 플러그인 모두 이 리포에서 삭제되어 Phantom-plugins로 옮겨졌고 번들에 없다(5.2절). 로드맵에는 이동이 없다 |
| `:17` | Endpoints를 phantom-spring-boot 범위로 둔다 | Endpoints 창은 코어에 있다(3.6절) |
| `:62` (U1) | 새 툴 윈도우보다 기존 창을 쓴다 | 코어에 새 `Endpoints` 툴 윈도우를 더했다(`LangExtensions.xml:1556-1557`) |
| `:51` (공통 원칙 2) | 시작 활동을 만들지 않는다 | 플러그인이 아닌 코어에 `postStartupActivity` 3개를 더했다(`PlatformExtensions.xml:841, 843, 845`. 셋째는 17cce24354의 CDS 아카이브 정리). 원칙은 플러그인 대상이라 엄밀한 위반은 아니다 |
