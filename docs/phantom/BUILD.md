# 빌드·릴리스·벤치마크 가이드

- 목적: Phantom IDEA를 빌드, 서명, 설치, 릴리스, 측정하는 방법과 그 스크립트의 실제 동작을 적는다.
- 기준 커밋: 64dc82b3f2
- 작성일: 2026-11-12

이 문서의 줄 번호는 위 커밋의 파일을 직접 열어 확인한 값이다. 읽지 않고 짐작한 내용은 "(추정)"으로 표시했다. 이 문서는 설명만 하며 개선 제안은 하지 않는다.

## 1. 한눈에 보기

파이프라인은 다음 사슬이다.

1. `./phantom-build.sh` -> Phantom-plugins 체크아웃에서 플러그인 8개를 Gradle로 빌드해 `out/phantom-plugins/`에 풂 -> `installers.cmd` -> `build/run_build_target.sh` -> `bazel.cmd run //build:i_build_target` -> `OpenSourceCommunityInstallersBuildTarget.kt`의 `main` -> `out/phantom-idea/artifacts/`에 DMG(macOS arm64), exe, win.zip.
2. `./phantom-install.sh` -> (기본은 1번을 먼저 실행, `--release`는 GitHub 릴리스에서 DMG 다운로드) -> DMG 마운트 -> `/Applications/Phantom IDEA.app` 교체 -> `./phantom-sign.sh` -> 설치된 앱.
3. `./phantom-release.sh` -> 1번 실행 -> DMG/exe/zip 검증 -> 태그 push -> `gh release create --draft` -> `gh release edit --draft=false` -> GitHub 릴리스.
4. `.github/workflows/phantom-release.yml` -> 셀프호스트 macOS ARM64 러너에서 Phantom-plugins 체크아웃을 맞춘 뒤 `./phantom-release.sh` 실행 (러너 등록은 `tools/phantom-runner-setup.sh`).
5. `./phantom-bench.sh` + `phantom-bench-report.py` -> 설치된 앱을 실행해 런타임 지표 측정, 또는 앱 번들을 읽어 정적 지표 계산 -> `compare`로 baseline과 비교.

| 스크립트 | 목적 | 주요 옵션과 환경변수 | 출력 |
|---|---|---|---|
| `phantom-build.sh` | Phantom-plugins의 번들 플러그인 8개를 빌드한 뒤 날짜 기반 버전으로 macOS DMG와 Windows 설치 파일 빌드 | 인자는 모두 `installers.cmd`에 `-D` JVM 옵션으로 전달. `PHANTOM_WINDOWS`(기본 1, `0`이면 DMG만), `PHANTOM_BUILD_SEQ`(N 고정), `PHANTOM_PLUGINS_REPO`(Phantom-plugins 체크아웃, 기본 `../Phantom-plugins`) | `out/phantom-idea/artifacts/*`, `out/phantom-plugins/<dir>`, `out/phantom-build-seq`, `out/phantom-build-number` |
| `phantom-install.sh` | 최신 빌드를 `/Applications`에 설치하고 재시작 | 인자 없음(빌드 후 설치), `--no-build`, `--release [tag]`. `GH_TOKEN`(없으면 `gh auth token`) | `/Applications/Phantom IDEA.app`, `out/phantom-release-download/` |
| `phantom-sign.sh` | 설치된 앱을 "Phantom Local" 인증서로 재서명. `--setup`은 그 인증서를 이 머신에 만든다 | 선택 인자: 앱 경로, 또는 `--setup`. `PHANTOM_APP`, `PHANTOM_SIGN_SETUP`(`1`이면 인증서가 없을 때 먼저 `--setup`), `PHANTOM_SIGN_OPTIONAL` | 재서명된 앱 (인증서가 없으면 종료 코드 1, `PHANTOM_SIGN_OPTIONAL=1`이면 경고 후 ad-hoc 서명 유지). `--setup`은 전용 키체인, 비밀번호 파일, 인증서 |
| `phantom-release.sh` | 빌드 후 GitHub 릴리스 게시 | `--no-build`. `NOTES`, `PHANTOM_BUILD_SEQ`, `GITHUB_ACTIONS`, `GH_TOKEN` | 태그 `v<YY>.<MM>.<DD>.<N>`와 GitHub 릴리스 |
| `phantom-bench.sh` | 설치된 앱을 고정 프로젝트로 N회 격리 측정 | `--runs 5`, `--label base`, `--settle 180`, `--vm-extra FILE`, `--fixture DIR` | `out/phantom-bench/<short-sha>/<label>/` |
| `phantom-bench-report.py` | 측정 결과 가공, 정적 게이트, 비교 | 서브커맨드 `run`, `show`, `summary`, `static`, `compare` | JSON(stdout), `summary.json`, 종료 코드 |
| `tools/phantom-runner-setup.sh` | 셀프호스트 GitHub Actions 러너 등록 | `--remove` | `~/actions-runner/phantom-idea`, launchd 에이전트 |

## 2. 사전 준비

- OS와 아키텍처: macOS Apple Silicon. README는 "Needs macOS on Apple Silicon"이라고 쓴다 (`README.md:105`). 이전에 macOS 전용이던 근거(`sed -i ''`, BSD sed 문법)는 048852ca23이 그 줄을 지우면서 사라졌다. `phantom-build.sh`에 남은 `sed`는 `:40`의 `sed -nE`(플러그인 버전 읽기) 하나이고, macOS 전용 설정은 `-Dintellij.build.target.arch=aarch64` 고정(`:45`)이다. `phantom-release.yml:29`는 `[self-hosted, macOS, ARM64]` 러너를 요구한다.
- 디스크와 메모리: 16 GB 이상 RAM(작성자는 48 GB 사용), 첫 빌드에 약 120 GB 여유 디스크 (`README.md:105`).
- `android` 클론: 저장소 옆이 아니라 저장소 루트 안의 `android` 디렉터리에 `idea/262.10968.63` 태그를 받아 둔다 (`README.md:109-110`).
  `git clone --depth 1 --branch idea/262.10968.63 https://github.com/JetBrains/android.git android`
  README는 "Android modules are still referenced by the build graph"라고 이유를 적는다. 이 클론이 없으면 빌드가 실패하는지는 실행하지 않아 확인하지 못했다 (추정).
- Phantom-plugins 체크아웃(9229667cc7부터): 기본 위치는 이 저장소 옆 `../Phantom-plugins`이고 `PHANTOM_PLUGINS_REPO`로 바꾼다 (`phantom-build.sh:32`). 그 디렉터리에 `settings.gradle.kts`가 없으면 빌드가 시작하지 않는다 (`:34`). 릴리스 워크플로 주석은 이 저장소가 비공개라고 적는다 (`phantom-release.yml:40-42`). 따라서 클론하려면 읽기 권한이 필요하다.
  `git clone git@github.com:newkayak12/Phantom-plugins.git ../Phantom-plugins` (주소는 `phantom-release.yml:50`의 저장소 이름에서 옮김)
  그 저장소의 Gradle 래퍼(`./gradlew`)가 실행된다 (`:37`). Gradle에 필요한 JDK 등은 그 저장소의 설정에 달렸고 이 리포에서는 확인할 수 없다. 스크립트는 `git`(`:35`), `unzip`(`:43`)도 부른다.
- Bazel: 별도 설치가 필요 없다. `installers.cmd:18`이 `build/run_build_target.sh`를 부르고, 이 스크립트는 `bazel.cmd run //build:i_build_target`을 실행한다 (`build/run_build_target.sh:22,24`). `bazel.cmd`는 `.bazeliskversion`을 읽어 bazelisk를 캐시 디렉터리(`~/Library/Caches/JetBrains/monorepo-bazelisk`)에 내려받는다 (`bazel.cmd:10,22`). JDK 설정은 이 스크립트들에 없다. Bazel 쪽 툴체인이 제공한다 (추정).
- `gh` CLI: `phantom-release.sh`(`gh release create`, `gh release edit`), `phantom-install.sh --release`(토큰 없을 때 `gh auth token`), `tools/phantom-runner-setup.sh`(`gh api`)에 필요하다. 빌드만 할 때는 필요 없다.
- `git`에 `origin` 원격: `phantom-release.sh`와 `phantom-install.sh --release`가 `origin` URL에서 저장소 이름을 뽑는다 (`phantom-release.sh:67`, `phantom-install.sh:20`).
- 서명 인증서: 손으로 준비할 것은 없다. `phantom-install.sh`가 없으면 만든다. 자세한 내용은 5장.
- 벤치마크 전용 준비물: 설치된 앱, `jcmd`(JDK), `footprint`(macOS), `python3`, `rsync`, 고정 프로젝트 (`phantom-bench.sh:80,83,43,67`). 코드에서 선언된 요구사항이 아니라 호출되는 명령에서 읽은 것이다.
- 자격 증명 변수: `GH_TOKEN`. 로컬에서는 `gh`가 로그인되어 있으면 대체된다. CI에서는 `${{ github.token }}`이 주입된다 (`phantom-release.yml:59`). 서명용 시크릿은 GitHub에 저장하지 않는다 (`phantom-release.yml:9-10`).

## 3. 빌드

### phantom-build.sh 단계

1. `d=$(date +%y%m%d)`로 날짜를 구하고 `yy`, `mm`, `dd`로 나눈다 (`phantom-build.sh:15`). `set -euo pipefail` 상태이다 (`:13`).
2. 표시 버전은 파일에 쓰지 않는다. 048852ca23 전에는 추적 파일 `community-resources/resources/idea/IdeaApplicationInfo.xml`의 `<version>`을 `sed -i ''`로 고쳐 작업 트리가 더러워졌다. 지금은 YY와 `MM.DD`를 6, 9단계의 `installers.cmd` 인자 `-Dphantom.version.major=<YY>`, `-Dphantom.version.minor=<MM>.<DD>`로만 넘긴다 (`:46`, `:63`). 추적 파일에는 자리표시자 `major="0" minor="0.0"`이 남는다. 코드를 읽은 결론이고 빌드는 아직 하지 않았다.
3. 일련번호 N을 정한다 (`:17-25`). `PHANTOM_BUILD_SEQ`가 있으면 그 값이다. 없으면 `out/phantom-build-seq`의 `<d>.<마지막 N>`을 읽어 같은 날이면 +1, 날짜가 다르면 1이다. 결정한 `d.N`을 같은 파일에 다시 쓴다.
4. `phantom=PH-$d.$n`을 만들고 한 줄로 출력한다 (`:26-27`).
5. 번들에 넣을 Phantom-plugins 플러그인을 빌드한다 (`:29-44`, 9229667cc7). 주석(`:29-31`)은 IntelliJ 원본 대신 번들하는 플러그인(`IdeaCommunityProperties.kt`의 `PHANTOM_PLUGINS_BUNDLED`)이고, Phantom이 포크한 IntelliJ Community 빌드를 대상으로 빌드하므로 설치된 Phantom이 필요 없다고 적는다.
   - 체크아웃 위치는 `PHANTOM_PLUGINS_REPO`, 없으면 `$root/../Phantom-plugins`다 (`:32`). 그곳에 `settings.gradle.kts`가 없으면 `no Phantom-plugins checkout at ...`을 출력하고 `exit 1` 한다 (`:34`).
   - 목록은 `toml yaml sh lombok decompiler coverage textmate markdown`이다 (`:33`). `IdeaCommunityProperties.kt:32`의 목록과 같아야 하지만 코드로 묶여 있지 않다.
   - 체크아웃의 마지막 커밋(해시와 제목 앞 80자)을 출력한다 (`:35`).
   - 목록마다 `:<p>:buildPlugin` 태스크를 모아(`:36`) 체크아웃 안에서 `./gradlew --no-configuration-cache -q -PplatformFromRepository=true <태스크들>`을 한 번 실행한다 (`:37`). `platformFromRepository`의 뜻은 Phantom-plugins의 빌드 설정에 있어 이 리포에서는 확인할 수 없다(주석에 따르면 IntelliJ Community 빌드를 대상으로 한다는 뜻으로 보인다, 추정).
   - `out/phantom-plugins`를 지우고 다시 만든다 (`:38`).
   - 플러그인마다 `<p>/build.gradle.kts`의 `version = "..."` 줄에서 버전을 읽고(`:40`), `<p>/build/distributions/<p>-<version>.zip`이 없으면 `no <zip> after the Phantom-plugins build`를 출력하고 `exit 1` 한다 (`:41-42`). 있으면 `unzip -q`로 `out/phantom-plugins`에 푼다 (`:43`). 빌드는 푼 결과가 `out/phantom-plugins/<p>/lib`에 있다고 보고, 없으면 `IdeaCommunityProperties.kt:191-192`의 `check`로 실패한다. zip 안의 최상위 디렉터리가 `<p>`라는 점은 이 두 코드가 전제할 뿐 이 리포에서 확인할 수 없다(추정).
6. macOS 빌드를 실행한다 (`:45-46`).
   `nice -n 10 ./installers.cmd -Dintellij.build.target.os=current -Dintellij.build.target.arch=aarch64 -Dbuild.number=262.<d> -Dphantom.build.number=PH-<d>.<N> -Dphantom.version.major=<YY> -Dphantom.version.minor=<MM>.<DD> "$@"`
7. `phantom-idea-<d>.<N>-aarch64.dmg`가 없으면 `exit 1` 한다 (`:49-51`).
8. 정리한다 (`:52-56`). `.sit` 파일, 방금 만든 것이 아닌 이전 DMG, `out/phantom-idea/temp`, `artifacts/maven-artifacts`, `artifacts/jps`, `out/phantom-idea/dist.*`를 지운다.
9. `PHANTOM_WINDOWS`가 `0`이 아니면 Windows를 이어서 빌드한다 (`:58-68`). Windows 빌드가 `out/phantom-idea`를 비우므로 DMG를 `out/`으로 옮겨 두고(`:61`), `target.os=windows`, `target.arch=x64`로 `installers.cmd`를 같은 `-D` 인자와 함께 다시 실행한 뒤(`:62-63`) DMG를 되돌린다(`:64`). 그다음 `.exe`와 `.win.zip`이 있는지 `ls`로 확인하고(없으면 `exit 1`, `:65-66`) 같은 정리를 한 번 더 한다(`:67`).
10. 빌드 번호 `PH-<d>.<N>`을 `out/phantom-build-number`에 쓰고 `built <dmg>`를 출력한다 (`:69-70`).

### 버전 도장

| 이름 | 값 | 찍히는 방식 |
|---|---|---|
| Phantom 빌드 번호 | `PH-<YYMMDD>.<N>` | `-Dphantom.build.number`. `PhantomBuildNumber.kt:11`이 속성 이름을 정의하고, `ApplicationInfoPropertiesImpl.kt:171-174`가 `number="IC-262.x"`를 `number="PH-.." apiVersion="IC-262.x"`로 바꾼다. `MacDistributionBuilder.kt:571,597`이 Info.plist의 build 값으로 쓴다 |
| 표시 버전 | `<YY>.<MM>.<DD>` | `-Dphantom.version.major=<YY>`, `-Dphantom.version.minor=<MM>.<DD>`(`phantom-build.sh:46`, `:63`). 이름은 `PhantomBuildNumber.kt:21-22`, `ApplicationInfoPropertiesImpl.kt:72-75`가 XML보다 먼저 읽어 `product-info.json`, Info.plist 등에 쓰고, `:177-181`(`withPhantomVersion`, `:268-280`)이 패키지 안 `<version>`에도 같은 값을 쓴다. 추적 파일 `IdeaApplicationInfo.xml:2`는 자리표시자 `0` / `0.0`이라 속성 없이 실행하면 0.0이 보인다(048852ca23, 빌드 전). 표시 버전과 플랫폼 빌드를 분리하는 코드 주석은 `ApplicationInfoPropertiesImpl.kt:145` |
| 플랫폼 빌드 | `262.<YYMMDD>` | `-Dbuild.number`. 번들 플러그인의 since/until-build와 IDE의 `apiVersion`에 쓰이며 사용자에게 버전으로 보이지 않는다 (`phantom-build.sh:9-10`) |
| 아티팩트 이름 | `phantom-idea-<d>.<N>` | `IdeaCommunityProperties.kt:207-208`이 `phantom.build.number`에서 `PH-`를 뗀 값을 쓴다. 속성이 없으면 플랫폼 빌드의 `262.` 뒤를 쓴다 |

### 대상 OS와 아키텍처

- macOS는 `target.os=current`, `target.arch=aarch64` 고정이다 (`phantom-build.sh:45`). Windows는 `PHANTOM_WINDOWS`(기본 1)가 `0`이 아닐 때 `target.os=windows`, `target.arch=x64`로 빌드한다 (`:58,62`).
- Linux 빌드를 부르는 스크립트는 없다. `ideaCommunityLinuxCustomizer`는 코드에 있다 (`IdeaCommunityProperties.kt:314-326`).
- Windows 설치 파일을 macOS에서 만든다는 주석은 `OpenSourceCommunityInstallersBuildTarget.kt:32`와 `platform/build-scripts/src/org/jetbrains/intellij/build/impl/WinExeInstallerBuilder.kt:41-42`에 있다.

### 빌드 옵션 (`OpenSourceCommunityInstallersBuildTarget.kt`)

- `incrementalCompilation = true`, `useCompiledClassesFromProjectOutput = false` (`:28-29`).
- `MAC_SIGN_STEP`, `WIN_SIGN_STEP`을 건너뛴다 (`:30-31`). 그래서 산출물은 서명되지 않거나 ad-hoc 서명이다.
- `CROSS_PLATFORM_DISTRIBUTION_STEP`, `SOURCES_ARCHIVE_STEP`, `ARCHIVE_PLUGINS`도 `main`에서 항상 건너뛴다 (`:18-23`, `:38`). 9장 참조.
- 흐름은 `createCommunityBuildContext` -> `compileModules` -> `buildDistributions` -> 독립 실행형 JPS 빌더 생성이다 (`:39-44`). Gradle이 아니다.

### 산출물 위치와 정리 대상

- `out/phantom-idea/artifacts/phantom-idea-<d>.<N>-aarch64.dmg`, `phantom-idea-<d>.<N>.exe`, `phantom-idea-<d>.<N>.win.zip`. 출력 디렉터리 이름 `phantom-idea`는 `IdeaCommunityProperties.kt:210`이다.
- 지워지는 것: `.sit`, 이전 DMG, `out/phantom-idea/temp`, `maven-artifacts`, `jps`, `dist.*` (위 8, 9단계). 이전 `.exe`와 `.zip`을 지우는 줄은 없다. Windows 빌드가 `out/phantom-idea` 전체를 비운다고 `phantom-build.sh:59`가 설명한다.
- 남는 것: `out/phantom-build-seq`, `out/phantom-build-number`, `out/phantom-plugins/`(다음 빌드가 지우고 다시 만든다, `phantom-build.sh:38`).

### 소요 시간

README는 첫 빌드가 약 2시간, 이후 빌드는 "minutes"라고 쓴다 (`README.md:116`). 워크플로 타임아웃은 240분이다 (`phantom-release.yml:30`). 9229667cc7부터 앞에 붙은 Phantom-plugins Gradle 빌드의 시간은 재지 않았다.

## 4. 제품 정의가 있는 곳

- 제품 속성 클래스: `IdeaCommunityProperties` (`build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt:60`). `createCommunityBuildContext`가 이 클래스를 만든다 (`:48-54`). `platformPrefix = "Idea"` (`:63`), `applicationInfoModule = "intellij.idea.community.customization"` (`:64`).
- 번들 플러그인 조립: `productLayout.bundledPluginModules`는 `IDEA_BUNDLED_PLUGINS`에서 21개를 `.removing(...)`으로 빼고 `PHANTOM_BUNDLED_PLUGINS`를 더한다 (`:85-107`, 설명 주석 `:77-84`, `:100`).
  - 제거 14개: `intellij.featuresTrainer`, `intellij.mcpserver`, `intellij.performanceTesting`, `intellij.platform.acp.plugin`, `intellij.dev`, `intellij.eclipse`, `intellij.java.byteCodeViewer`, `intellij.webp`, `intellij.vcs.git.commit.modal`, `intellij.vcs.gitlab`, `intellij.keymap.eclipse`, `intellij.keymap.visualStudio`, `intellij.keymap.netbeans`, `intellij.configurationScript` (`:86-99`).
  - Phantom-plugins 판으로 바꾸는 7개: `intellij.toml`, `intellij.yaml`, `intellij.sh.plugin`, `intellij.java.decompiler`, `intellij.java.coverage`, `intellij.textmate.plugin`, `intellij.markdown` (`:101-107`, 9229667cc7).
  - 추가: `phantom.sync`, `phantom.theme` (`PHANTOM_BUNDLED_PLUGINS`, `:20-24`). 9229667cc7 전에는 `intellij.lombok`도 여기 있었다.
  - `IDEA_BUNDLED_PLUGINS`의 정의 위치는 `platform/build-scripts/src/org/jetbrains/intellij/build/BaseIdeaProperties.kt:14-58`이다.
- Phantom-plugins에서 빌드한 플러그인: `PHANTOM_PLUGINS_BUNDLED`(`:26-33`: `toml`, `yaml`, `sh`, `lombok`, `decompiler`, `coverage`, `textmate`, `markdown`)는 모듈 이름이 아니라 `out/phantom-plugins/<dir>`의 디렉터리 이름이다. `getAdditionalPluginPaths()` 재정의(`:187-194`)가 그 디렉터리들을 돌려주고 `lib/`가 없는 것이 있으면 빌드를 멈춘다(`:191-192`). 빌드 스크립트가 그 디렉터리를 번들의 `plugins/<dir>`로 복사한다(`platform/build-scripts/src/org/jetbrains/intellij/build/impl/DistributionJARsBuilder.kt:551`, `:560`). 개발 모드 레이아웃(`platform/build-scripts/src/org/jetbrains/intellij/build/dev/PluginBuilder.kt:74`)도 같은 함수를 부르므로 소스에서 IDE를 실행할 때도 `out/phantom-plugins`가 필요할 것으로 보인다(추정, 실행하지 않음).
- 클래스 버전 검사: `versionCheckerConfig = CE_CLASS_VERSIONS` (`:155`, 정의 `BaseIdeaProperties.kt:60`). 커버리지 런타임 jar 항목은 Phantom-plugins 판이 들어가는 경로 `plugins/coverage/lib/java-coverage-rt.jar`다 (`BaseIdeaProperties.kt:68`, a199380d7d. 이전 경로 `plugins/java-coverage/...`).
- 플러그인 레이아웃: Android 플러그인은 기본 번들에서 제거되어 있다 (`:111-115`). `buildAllCompatiblePlugins = false`, `prepareCustomPluginRepositoryForPublishedPlugins = false` (`:109-110`).
- 런타임 의존성: `intellij.idea.community.main.iml:138-140`이 `phantom.sync`, `phantom.sync.core`, `phantom.theme`을 RUNTIME 스코프로 참조한다.
- 제품 콘텐츠 기술자: `intellijCommunityBaseFragment` (`:243-281`). 온보딩 투어 제거 주석은 `:266`이다.
- 브랜딩 상수 (`IdeaCommunityProperties.kt`): 시스템 셀렉터 `PhantomIDEA` 고정 (`:202-204`), Mac `bundleIdentifier = "dev.phantom.idea"` (`:302`), 루트 디렉터리 `Phantom IDEA.app` (`:304`), URL 스킴 `phantom` (`:299`), Windows `fullName`과 설치 폴더 `Phantom IDEA` (`:289-290`), Linux 루트 `phantom-idea` (`:319`).
- 브랜딩 파일 `community-resources/resources/idea/IdeaApplicationInfo.xml`: 버전 `:2`(자리표시자 `0` / `0.0`. 표시 버전은 빌드 스크립트가 `-Dphantom.version.*`로 넘긴다), 회사 `:3`, 빌드 번호 자리표시자 `IC-__BUILD__` `:4`, 로고 `/phantom_logo.png` `:5`, 아이콘 `/phantom.svg`와 `/phantom_16.svg` `:6`, EAP 아이콘 `:7`, 이름 `product="Phantom" fullname="Phantom IDEA" script="idea"` `:8`, 기본 LAF `Phantom Light`와 `Phantom Dark` `:10`, essential 플러그인 `:12-14`.
- 아이콘과 로고 원본: `community-resources/resources/phantom*.svg`, `phantom_logo.png`. 생성 소스는 `community-resources/phantom-brand-src/compose.swift`이다. 이 스크립트를 부르는 빌드 스크립트는 읽은 범위에서 찾지 못했다.
- Phantom 전용 플랫폼 패치: `VmOptionsGenerator.kt`의 Phantom 주석(`:26` 미사용 힙 반환, `:33` `-ea` 제거). 위치는 `platform/build-scripts/src/org/jetbrains/intellij/build/impl/VmOptionsGenerator.kt`. Windows 설치 파일을 macOS에서 만드는 패치는 `platform/build-scripts/src/org/jetbrains/intellij/build/impl/WinExeInstallerBuilder.kt:41-42`의 주석이다.
- Phantom 플러그인 소스: `plugins/phantom-sync`, `plugins/phantom-theme`. 번들에 넣는 나머지 8개의 소스는 Phantom-plugins 저장소에 있다.

## 5. 서명·설치

### phantom-sign.sh

- 목적은 설치된 앱을 로컬 자체 서명 인증서 "Phantom Local"과 식별자 `dev.phantom.idea`로 다시 서명하는 것이다. ad-hoc 서명은 빌드마다 바뀌어 macOS가 개인정보 권한을 다시 묻기 때문이다 (`phantom-sign.sh:2-4`).
- 앱 경로는 첫 인자, 없으면 `PHANTOM_APP`, 둘 다 없으면 `/Applications/Phantom IDEA.app`이다 (`:95`).
- 인증서는 전용 키체인 `$HOME/Library/Keychains/phantom-signing.keychain-db`(`:30`)에서만 찾는다. 키체인과 비밀번호 파일 `$HOME/.config/phantom/signing-keychain.pass`(`:31`)가 둘 다 있을 때만 `security unlock-keychain`으로 연 뒤 찾는다 (`:34-38`). 잠긴 키체인을 조회하면 macOS가 비밀번호 대화상자를 띄우기 때문이다. 그래서 비밀번호 파일 없이 키체인만 있으면 인증서가 없는 것으로 본다. 비밀번호가 틀리면 `PHANTOM_SIGN_OPTIONAL`과 상관없이 종료 코드 1이다. 비밀번호 파일의 내용은 이 문서에 적지 않는다.
- 인증서가 없으면 이유를 stderr에 쓰고 종료 코드 1로 실패한다. `PHANTOM_SIGN_SETUP=1`이면 먼저 `--setup`을 실행하고(실패하면 stderr 경고), 그래도 없으면 같은 처리를 한다. `PHANTOM_SIGN_OPTIONAL=1`이면 stderr 경고 후 종료 코드 0이다 (`:96-107`). `phantom-install.sh`는 두 값을 켜고 부른다 (9.4).
- 인증서가 있으면 `codesign --force --sign "Phantom Local" --keychain <전용 키체인> --identifier dev.phantom.idea "$APP"` 후 designated requirement를 출력한다 (`:108-109`). `--keychain` 덕분에 전용 키체인을 사용자 키체인 검색 목록에 넣지 않아도 된다. 같은 인증서면 designated requirement(`identifier "dev.phantom.idea" and certificate leaf = H"<인증서 SHA-1>"`)는 빌드가 바뀌어도 같다.
- 공증(notarization)과 Developer ID 서명은 어디에도 없다. Windows 파일은 서명하지 않는다 (`phantom-release.sh:7-9`).

### 로컬 서명 인증서 (`phantom-sign.sh --setup`)

- 무엇인가: 이 머신에서 만든 자체 서명 코드 서명 인증서 "Phantom Local"이다. Apple 인증서가 아니고 Apple 계정이나 Developer Program이 필요 없다. 공증이나 Gatekeeper 통과와는 관계없고, macOS가 빌드가 바뀌어도 같은 앱으로 알아보게 하는 용도만 있다. 인증서를 신뢰(trust) 설정하지 않으므로 `security find-identity`에는 `CSSMERR_TP_NOT_TRUSTED`로 보이지만 `codesign` 서명과 `codesign --verify`는 통과한다.
- 언제 만들어지나: `phantom-install.sh`가 서명 전에 인증서가 없으면 자동으로 만들고 한 줄로 알린다. 끄려면 `PHANTOM_SIGN_SETUP=0 ./phantom-install.sh ...`. 직접 만들려면 `./phantom-sign.sh --setup`.
- 만드는 것 (`phantom-sign.sh:40-93`): 무작위 비밀번호의 전용 키체인(자동 잠금 없음), 비밀번호 파일(권한 600, 폴더 700), RSA 2048 인증서(CN "Phantom Local", 확장 키 용도 codeSigning(critical), 키 용도 digitalSignature, 유효 10년). macOS 기본 도구 `/usr/bin/openssl`(LibreSSL)과 `security`만 쓰고 대화상자가 뜨지 않는다. 개인 키는 `mktemp -d` 임시 폴더에서만 파일로 존재하고 종료 때 지운다. 가져온 뒤 `security set-key-partition-list -S apple-tool:,apple:`로 `codesign`이 비밀번호를 묻지 않게 한다. 사용자 키체인 검색 목록, 기본 키체인, login 키체인, 신뢰 설정은 바꾸지 않는다.
- 다시 실행해도 안전하다: 인증서가 이미 있으면 그렇다고 출력하고 종료 코드 0이다. 기존 키체인·인증서·비밀번호 파일을 덮어쓰거나 지우지 않는다. 비밀번호 파일이 이미 있으면 그 비밀번호를 쓰고, 키체인은 있는데 비밀번호 파일이 없으면 아무것도 바꾸지 않고 종료 코드 1이다.
- 파일 위치: `~/Library/Keychains/phantom-signing.keychain-db`, `~/.config/phantom/signing-keychain.pass`.
- 지우기: `security delete-keychain ~/Library/Keychains/phantom-signing.keychain-db` 후 `rm ~/.config/phantom/signing-keychain.pass`. 다음 설치 때 새 인증서를 만드는데, 인증서가 바뀌면 designated requirement도 바뀌므로 macOS가 개인정보 권한을 한 번 다시 묻는다.
- 인증서가 없으면(`PHANTOM_SIGN_SETUP=0`이거나 만들기에 실패하면): 설치는 끝나고 앱은 ad-hoc 서명으로 남으며, 새 빌드를 설치할 때마다 macOS가 개인정보 권한을 다시 묻는다.
- 확인한 것: 임시 `HOME`과 더미 앱 번들로 만들기(두 번), 서명, 새로 복사한 앱의 재서명에서 같은 designated requirement, `codesign --verify` 통과, 키체인 검색 목록 불변을 실행했다. 실제 Phantom 앱에서 개인정보 권한이 재서명 뒤에도 남는지는 이 절차로 확인하지 않았다.

### phantom-install.sh

- 앱 경로는 `PHANTOM_APP`, 없으면 `/Applications/Phantom IDEA.app`이다 (`phantom-install.sh:17`). 같은 경로를 `phantom-sign.sh`에 넘긴다.
- 모드 (`:16-37`): 인자 없음은 `./phantom-build.sh`를 실행한다. `--no-build`는 이미 빌드된 DMG를 쓴다. `--release [tag]`는 `origin`의 GitHub 릴리스(`latest` 또는 `tags/<tag>`)에서 `-aarch64.dmg`로 끝나는 에셋을 `curl`로 받아 `out/phantom-release-download/`에 둔다. 토큰은 `GH_TOKEN`, 없으면 `gh auth token`이다 (`:19`). `curl`을 쓰는 이유는 격리(quarantine) 플래그가 붙지 않아 공증되지 않은 앱을 Gatekeeper가 막지 않기 때문이다 (`:6-7`).
- DMG 선택: 직접 받은 DMG가 없으면 `out/phantom-idea/artifacts/phantom-idea-*-aarch64.dmg` 중 `ls -t` 최신이다 (`:39`).
- 실행 중이면: `osascript`로 `dev.phantom.idea`에 종료를 요청하고 최대 180초 기다린다. 그래도 떠 있으면 메시지를 출력하고 `exit 1` 한다 (`:43-51`).
- 설치: DMG를 읽기 전용으로 마운트하고(`:54`), `$APP.new`로 `ditto` 한 뒤 기존 앱을 지우고 교체한다 (`:56-59`). `EXIT` trap이 마운트를 해제한다 (`:55`). 그다음 `PHANTOM_SIGN_SETUP`(기본 `1`)과 `PHANTOM_SIGN_OPTIONAL=1`로 `./phantom-sign.sh "$APP"`를 실행하고(인증서가 없으면 먼저 만든다) `CFBundleVersion`을 출력한다 (`:69-72`).
- 재시작: 설치 전에 실행 중이었다면 `/`로 이동해 최대 5회 `open "$APP"`을 시도한다 (`:63-74`).

## 6. 릴리스

### phantom-release.sh 단계

1. `--no-build`가 아니면 빌드한다 (`phantom-release.sh:14-22`). `PHANTOM_BUILD_SEQ`가 없으면 `git ls-remote --tags origin "refs/tags/v<오늘>.*"`의 가장 큰 번호에 1을 더해 N으로 `export` 한 뒤 `./phantom-build.sh`를 실행한다.
2. `out/phantom-build-number`를 읽어 버전과 태그를 만든다 (`:23-26`). 예: 빌드 `PH-261018.2` -> 버전 `26.10.18`, 태그 `v26.10.18.2`. DMG가 없으면 `exit 1`.
3. DMG를 마운트해 검증한다 (`:31-41`). `codesign -d`로 런처에 서명이 있는지, `file`로 arm64인지, `CFBundleVersion`이 빌드 번호와 같은지 본다. 엄격한 `codesign --verify`는 통과할 수 없다고 주석에 적혀 있다 (`:35-36`).
4. `.exe` 또는 `.win.zip`이 하나라도 있으면 (`:48-54`) `.exe`가 "Nullsoft Installer"인지, zip의 `product-info.json`이 `version`=`YY.MM.DD`, `buildNumber`=`262.<day>`인지 확인하고 에셋에 더한다.
5. 릴리스 노트 기본값은 `NOTES` 환경변수이고, 비어 있으면 직전 `v[0-9]*` 태그 이후의 `git log --no-merges` 제목을 `- 제목` 형식으로 최대 40줄 모은다 (`:56-59`).
6. 로컬 실행이면 `git push -q origin HEAD` 한다. `GITHUB_ACTIONS`가 설정되어 있으면 이 push를 건너뛴다 (`:61`).
7. `git tag -f $tag HEAD`와 `git push -q -f origin refs/tags/$tag`로 태그를 강제 push 한다 (`:64-65`). 태그를 API 대신 git으로 올리는 이유는 API가 workflow 스코프를 요구하기 때문이라고 주석에 있다 (`:62-63`).
8. 저장소 이름을 `origin` URL에서 뽑아 `gh release create "$tag" ... -R "$repo" --verify-tag --draft --title "Phantom IDEA <version> (<build>)" --notes "$NOTES"` 후 `gh release edit "$tag" -R "$repo" --draft=false` 한다 (`:67-70`). 먼저 draft로 올리는 이유는 DMG(약 800 MB) 업로드가 끝나야 릴리스가 보이게 하려는 것이다 (`:68`).

### GitHub 워크플로 (`.github/workflows/phantom-release.yml`)

- 트리거는 `workflow_dispatch`만이고 입력은 `notes`(선택)이다 (`:13-18`). 그 설명 문자열은 따옴표로 감싼다 (`:17`). 60ea243e02 전에는 따옴표 없는 `: `가 들어 있어 GitHub가 워크플로 파일을 거부했다(커밋 본문). 태그 push 트리거를 쓰지 않는 이유는 릴리스가 두 번 빌드되기 때문이라고 주석에 있다 (`:3-4`).
- `permissions: contents: write` (`:20-21`), 동시 실행 그룹 `phantom-release`와 취소 안 함 (`:23-25`).
- `runs-on: [self-hosted, macOS, ARM64]`, `timeout-minutes: 240` (`:29-30`).
- 체크아웃은 `actions/checkout@v4`, `fetch-depth: 200`, `fetch-tags: true`, `clean: false`(캐시 유지) (`:32-38`). `android` 클론 단계는 없다.
- Phantom-plugins 체크아웃 단계 (`:40-55`, 60ea243e02). 주석(`:40-42`): `phantom-build.sh`가 옆의 Phantom-plugins 체크아웃에서 플러그인을 빌드하는데, 그 저장소는 비공개이고 작업 토큰은 이 저장소만 읽으므로 러너 자신의 git 자격 증명(소유자의 SSH 키)으로 클론한다. 체크아웃은 이 저장소처럼 실행 사이에 남고 매번 main으로 맞춘다.
  - 위치는 `$GITHUB_WORKSPACE/../Phantom-plugins`다 (`:45`). `phantom-build.sh`의 기본값 `$root/../Phantom-plugins`와 같은 곳이다(체크아웃 경로를 따로 주지 않았으므로, 추정).
  - 호스트는 `ssh -G github-newkayak`의 hostname이 `github.com`이면 SSH 호스트 별칭 `github-newkayak`, 아니면 `github.com`이다 (`:46-48`).
  - `.git`이 없으면 `git clone "git@<host>:newkayak12/Phantom-plugins.git"` 한다 (`:49-51`). 그다음 `git fetch origin main`, `git reset --hard origin/main`, `git clean -fdq`로 맞추고 마지막 커밋을 출력한다 (`:52-55`).
- 실행 단계는 `./phantom-release.sh`이고 환경변수는 `GH_TOKEN`(`github.token`)과 `NOTES`(`inputs.notes`)이다 (`:57-61`). 서명 시크릿은 없다.

### 셀프호스트 러너 (`tools/phantom-runner-setup.sh`)

- 대화형 스크립트다. 단계마다 y/N을 묻는다 (`:14`).
- 설치는 GitHub 러너(osx-arm64)를 `~/actions-runner/phantom-idea`에 받아 `newkayak12/phantom-idea`에 레이블 `self-hosted,macOS,ARM64`로 등록하고 launchd 에이전트(`svc.sh install`, `svc.sh start`)로 띄운다 (`:12-13,24-37`). 등록 토큰은 `gh api`로 받는다.
- `--remove`는 서비스를 멈추고 제거한 뒤 GitHub에서 등록을 해제한다 (`:16-22`).
- 작업은 사용자 계정 권한으로 실행되므로 파일과 키체인에 접근할 수 있다고 스크립트 주석이 밝힌다 (`:8-10`).

## 7. 벤치마크와 회귀 게이트

문서: `docs/phantom/bench/README.md`. 계획 문서는 `docs/plans/2026-10-30-phantom-perf-measure-first.md`이다.

### 런타임 벤치

실행:

```
./phantom-bench.sh --runs 5 --label base
python3 phantom-bench-report.py compare docs/phantom/bench/baseline.json out/phantom-bench/<sha>/base/summary.json
```

- 옵션 기본값: `--runs 5`, `--label base`, `--settle 180`, `--vm-extra` 없음, `--fixture`는 `PHANTOM_BENCH_FIXTURE`, 없으면 `$HOME/projects/private/prototype-reservation-system` (`phantom-bench.sh:22-26`). 모르는 인자는 `exit 2` (`:30`).
- 경로: 앱은 `PHANTOM_APP`, 없으면 `/Applications/Phantom IDEA.app` (`:19`). 사용자 설정은 `PHANTOM_CONFIG_DIR`, 없으면 `$HOME/Library/Application Support/Phantom/PhantomIDEA` (`:20`).
- 출력 배치: `out/phantom-bench/<short-sha>/<label>/{warmup, run-N/, run-N.json, summary.json}`, 작업 디렉터리 `out/phantom-bench/work/{fixture, system-<label>}` (`:34-38`).
- 픽스처는 한 번만 `rsync`로 복사하며 `mysql_data`와 `build/`는 제외한다 (`:41-44`).
- 격리: 실행마다 사용자 설정을 임시 디렉터리로 복사하고(`.lock` 제외), `idea.properties`로 config, system, log 경로를 따로 지정한다 (`:55-59`). 사용자 IDE가 떠 있어도 건드리지 않지만 CPU를 두고 경쟁하므로 다른 Phantom이 떠 있으면 경고만 출력한다 (`:46-48`).
- VM 옵션: 사용자 `idea.vmoptions` + `-Didea.log.perf.stats.file` + `-Didea.trust.all.projects=true` + `--vm-extra` 파일 (`:60-65`).
- 워밍업: 레이블별 system 디렉터리에 `.bench-warm`이 없으면 버려지는 실행을 한 번 한다 (`:92-96`).
- settle: 실행마다 `sleep $settle` 후 `jcmd GC.run`, `GC.heap_info`, `PerfCounter.print`, `footprint -p`를 저장하고 TERM, 30초 대기, KILL 한다 (`:73-89`). 프로세스가 죽었거나 `idea.log`가 없으면 `return 1`이고 `set -e` 때문에 스크립트가 중단된다 (`:74-79`).
- 마지막에 `show`와 `summary`를 실행한다 (`:103-104`). 회귀 비교는 스크립트가 하지 않는다. 수동으로 `compare`를 실행해야 한다.

지표 (`phantom-bench-report.py:100-101`, `run` 정의 `:82-97`):

| 지표 | 의미 |
|---|---|
| `first_smart_mode_ms` | 로그에서 첫 "exit dumb mode" 시각 |
| `indexing_settled_ms` | 마지막 "exit dumb mode" 시각 |
| `footprint_mb` | `footprint` 출력의 Footprint 값 |
| `live_heap_mb` | `GC.run` 후 G1 힙 used |
| `loaded_classes` | `java.cls.*` 카운터로 계산한 로드된 클래스 수 |
| `freezes`, `freeze_total_ms` | "UI was frozen for Nms" 로그의 횟수와 합계 |

추가로 `startup_top`(시작 보고서 `perf.json`에서 이름이 있는 항목 상위 20개)이 `run`과 `summary`에 들어간다 (`:37-57`, `:127-130`). `summary`는 지표별 median, min, max를 `summary.json`에 쓴다 (`:115-132`).

허용 오차 `TOLERANCE` (`:136-144`): `first_smart_mode_ms` +10%, `indexing_settled_ms` +10%, `footprint_mb` +5%, `live_heap_mb` +5%, `loaded_classes` +5%, `freezes` +1(절대), `freeze_total_ms` +500 ms(절대).

`compare` (`:184-199`): baseline 문서의 `kind`가 `static`이면 `STATIC_TOLERANCE`, 아니면 `TOLERANCE`를 쓴다. 각 지표의 한도는 상대면 `before * (1 + tol)`, 절대면 `before + tol`이고 `after > limit`이면 `REGRESSION`이다. 한쪽 파일에 없는 지표는 조용히 건너뛴다. 회귀가 하나라도 있으면 종료 코드 1, 없으면 0이다 (`:199`, `:213`). 값이 낮아지는 것은 실패로 치지 않는다.

`docs/phantom/bench/README.md`의 조건: 조용한 머신에서 실행한다. 첫 스모크 실행은 로드 평균 약 400에서 smart mode까지 52초가 걸렸고 2026-10-17 벤치마크는 3.4초였다 (`docs/phantom/bench/README.md:18-22`).

### 정적 게이트

실행:

```
python3 phantom-bench-report.py static "/Applications/Phantom IDEA.app" > /tmp/static.json
python3 phantom-bench-report.py compare docs/phantom/bench/static-baseline.json /tmp/static.json
```

- 앱을 실행하지 않는다 (`phantom-bench-report.py:150-176`). 세는 것: `Contents/lib`와 `Contents/plugins` 아래의 jar 수(`jars`), `Contents/plugins` 바로 아래 디렉터리 수(`bundled_plugins`), jar 안의 `META-INF/*.xml`과 루트 XML에서 `<applicationActivity`, `<postStartupActivity`, `<backgroundPostStartupActivity`, `<startupActivity` 태그 수와 `preload="true"` 또는 `preload="await"` 수(`preload_services`), 번들 크기 `bundle_mb`(일반 파일만, 심볼릭 링크 제외). 버전과 빌드는 `Contents/Resources/product-info.json`에서 읽는다. 출력은 `kind: "static"`이고 각 지표의 median, min, max가 같다.
- 허용 오차 `STATIC_TOLERANCE` (`:180-181`): `bundle_mb` +2%(상대), 나머지(`jars`, `bundled_plugins`, `preload_services`, 시작 EP 4종)는 +0, 즉 증가 불가이다.
- baseline `docs/phantom/bench/static-baseline.json`의 현재 값: 버전 `26.10.27`, 빌드 `262.261027`, `bundle_mb` 2393, `jars` 891, `bundled_plugins` 56, `applicationActivity` 36, `postStartupActivity` 193, `backgroundPostStartupActivity` 104, `startupActivity` 0, `preload_services` 63.
- 이 baseline은 7c43a36532(2026-11-02, 번들에서 `.removing` 5건 추가: GitLab, 키맵 3종, configurationScript)보다 앞선 4b3ffd9498(2026-10-30)에서 측정한 값이다. 따라서 그 뒤로 빠진 플러그인(최대 5개) 수만큼 `bundled_plugins` 56이 실제 번들보다 크고, 같은 수만큼 뺐던 플러그인을 다시 넣어도 정적 게이트를 통과한다(추정, 실행하지 않음).
- 낡은 baseline 표시(커밋 C): `compare`는 static baseline이면 빌드 번호 `262.<YYMMDD>`에서 읽은 baseline 앱의 빌드 날짜와 `IdeaCommunityProperties.kt`의 마지막 커밋 날짜(`git log -1 --format=%cs`)를 비교한다. 커밋이 더 늦으면 stderr에 `STALE BASELINE`을 쓰고, 회귀가 없으면 종료 코드 2(회귀가 있으면 여전히 1)로 끝난다. 날짜 단위라 빌드 당일의 변경은 보지 못하고, git 체크아웃 밖에서는 안내만 쓰고 건너뛴다. 지금 baseline(빌드 2026-10-27, 마지막 번들 변경 2026-11-13 ed67704350)은 이 검사에 걸려 종료 코드 2다(임시 디렉터리의 JSON으로 실행해 확인). baseline 값은 빌드된 앱의 jar·크기에서 나오므로 소스만으로 다시 만들 수 없어 고치지 않았다. 재생성 명령은 `docs/phantom/bench/README.md`의 Baselines 절에 있다(빌드와 Phantom-plugins 체크아웃이 필요, 실행하지 않음).
- 런타임 baseline `docs/phantom/bench/baseline.json`은 존재하지 않는다 (`ls docs/phantom/bench` 결과는 `README.md`와 `static-baseline.json`뿐). README는 "not yet: needs a quiet machine; then calibrate the tolerances below"라고 적는다 (`docs/phantom/bench/README.md:32`). 허용 오차 보정은 아직 하지 않았다 (`docs/phantom/bench/README.md:39-40`).

## 8. 테스트

- 아래 표의 새 테스트 모듈 이름은 `.iml` 파일명에서 읽었고 실행하지 않았다(프로젝트를 아직 빌드하지 않음). 예시 명령: `./tests.cmd --module intellij.featuresTrainer.tests --test training.ui.FeedbackSubmissionGateTest`, `./tests.cmd --module intellij.platform.buildScripts.tests --test org.jetbrains.intellij.build.ApplicationInfoPropertiesTest`, `./tests.cmd --module intellij.platform.tests --test com.intellij.ide.plugins.PluginInitializationSelectPluginsToLoadTest`.
- 실행 방식: `./tests.cmd --module <JPS 모듈> --test <FQN | 와일드카드 | FQN#메서드> [--debug]` (`tests.cmd:15-28`). 모듈과 패턴은 필수이고 추가 인자는 `TestingOptions` JVM 플래그로 전달된다. 저장소 지침(`AGENTS.md`)에 따라 단순 클래스 이름은 매칭되지 않으므로 FQN이 필요하다. `*.test.mjs`는 `node --test <file>`로 실행한다.
- Phantom 관련 테스트 (각 파일의 `package` 줄과 클래스 선언을 열어 확인):

| 테스트 클래스 FQN | 파일 | 모듈 |
|---|---|---|
| `dev.phantom.sync.GitRepoCommunicatorTest` | `plugins/phantom-sync/tests/testSrc/dev/phantom/sync/GitRepoCommunicatorTest.kt` | `phantom.sync.tests` (`phantom.sync.tests.iml`) |
| `dev.phantom.ide.memory.MemoryPlanTest` | `platform/platform-tests/testSrc/dev/phantom/ide/memory/MemoryPlanTest.kt` | `intellij.platform.tests` |
| `dev.phantom.ide.endpoints.EndpointRequestsTest` | `platform/lang-impl/testSources/dev/phantom/ide/endpoints/EndpointRequestsTest.kt` | `intellij.platform.lang.tests` |
| `com.intellij.ide.plugins.GitHubPluginRepositoryTest` | `platform/platform-tests/testSrc/com/intellij/ide/plugins/GitHubPluginRepositoryTest.kt` | `intellij.platform.tests` |
| `training.ui.FeedbackSubmissionGateTest` (6e71ed80c4, 5185f60295가 요청 대기를 15초로 늘림, 실행 전) | `plugins/ide-features-trainer/testSrc/training/ui/FeedbackSubmissionGateTest.kt` | `intellij.featuresTrainer.tests` (`plugins/ide-features-trainer/intellij.featuresTrainer.tests.iml`. `intellij.platform.feedback`에 직접 의존하는 유일한 테스트 모듈) |
| `org.jetbrains.intellij.build.ApplicationInfoPropertiesTest` (048852ca23이 `phantomVersion`, `phantomVersionWithoutVersionElement`를 더함, 실행 전) | `platform/build-scripts/tests/testSrc/org/jetbrains/intellij/build/ApplicationInfoPropertiesTest.java` | `intellij.platform.buildScripts.tests` (`platform/build-scripts/tests/intellij.platform.buildScripts.tests.iml`) |
| `com.intellij.ide.plugins.PluginInitializationSelectPluginsToLoadTest` (업스트림 테스트. 04dba66c3a가 별칭 충돌 테스트 1개를 바꾸고 3개를 더함, :444-510. 이 문서를 쓰며 실행하지 않음) | `platform/platform-tests/testSrc/com/intellij/ide/plugins/PluginInitializationSelectPluginsToLoadTest.kt` | `intellij.platform.tests` (`platform/platform-tests/intellij.platform.tests.iml`) |

  `GitHubPluginRepositoryTest`는 패키지가 `com.intellij`이지만 커밋 이력(`53c0592476`, `79d2dc6b96`, `e67f21c7b5`)이 Phantom 플러그인 저장소 읽기 기능을 다룬다. 모듈 이름은 `.iml` 파일명과 `sourceFolder` 선언에서 읽은 것이고 실행해 보지는 않았다. 예시 명령:
  `./tests.cmd --module phantom.sync.tests --test dev.phantom.sync.GitRepoCommunicatorTest`
- 빌드 스크립트(`phantom-*.sh`), `PhantomBuildNumber.kt`, `phantom-bench-report.py`, `plugins/phantom-theme`에 대한 테스트 파일은 찾지 못했다. 표시 버전을 `<version>`에 쓰는 `withPhantomVersion`만 048852ca23의 `ApplicationInfoPropertiesTest`가 다룬다. 이름에 "phantom"과 "Test"가 들어간 파일과 `dev.phantom`을 포함한 테스트 소스를 검색한 결과이며 전수 조사는 아니다.
- CI에서 테스트를 돌리는 워크플로는 없다. `.github/workflows`에서 "phantom"을 포함하는 파일은 `phantom-release.yml` 하나이고 이 파일은 테스트를 호출하지 않는다.

## 9. 알려진 문제

직접 코드를 열어 확인한 항목만 적는다. 설명만 하고 고치지 않는다.

1. **변경됨, 빌드 대기 (048852ca23).** 빌드가 추적 파일을 수정하는데 릴리스는 그 변경을 커밋하지 않고 `HEAD`를 태그하던 문제다. 048852ca23 전의 `phantom-build.sh:16`이 추적 파일 `IdeaApplicationInfo.xml`을 고쳤고 `phantom-release.sh:61,64`는 변경을 커밋하지 않은 채 `HEAD`를 push하고 태그해서, 태그 커밋의 XML 버전(당시 `26.10.27`)이 릴리스 표시 버전과 다를 수 있었다. 지금은 스크립트가 파일을 고치지 않고 `-Dphantom.version.major/minor`로 넘기며(`phantom-build.sh:46`, `:63`), 추적 파일은 자리표시자 `0` / `0.0`이다(`IdeaApplicationInfo.xml:2`). 코드를 읽은 결론이고 빌드와 테스트는 하지 않았다. 부작용: `installers.cmd`를 직접 실행하거나 IDE를 소스에서 실행하면 표시 버전이 0.0이다. 릴리스가 `HEAD`를 태그하는 방식(`phantom-release.sh:61,64`)과 CI에서 push(`:61`)만 생략되는 점은 그대로다.
2. 런타임 baseline이 없다. `docs/phantom/bench/baseline.json`을 `docs/phantom/bench/README.md:10,32`가 참조하지만 파일이 없다. 결과: 런타임 `compare` 명령을 지금은 실행할 수 없고 `TOLERANCE`는 보정되지 않았다 (`docs/phantom/bench/README.md:39-40`).
3. 고침(45d41fe813). 전에는 앱 `/Applications/Phantom IDEA.app`(`phantom-install.sh`, `phantom-bench.sh`, `phantom-sign.sh` 기본값), 사용자 설정 경로(`phantom-bench.sh`), 기본 픽스처 `$HOME/projects/private/prototype-reservation-system`(`phantom-bench.sh`)이 코드에 고정되어 다른 위치에 설치한 머신에서는 바꿀 방법이 없었다. 지금은 환경변수로 바꾼다. 값이 없으면 기본값은 전과 같다: `PHANTOM_APP`(세 스크립트. `phantom-install.sh:17`, `phantom-bench.sh:19`, `phantom-sign.sh:13`에서 인자가 먼저), `PHANTOM_CONFIG_DIR`(`phantom-bench.sh:20`), `PHANTOM_BENCH_FIXTURE`(`phantom-bench.sh:26`, `--fixture`가 먼저). 각 스크립트 머리 주석에 적었다. 기본값은 이미 `$HOME`에서 만들어 사용자별 절대 경로는 없었다. 확인: 세 스크립트의 대입 줄을 변수 없이/있이 해석해 기본값과 재정의 값을 출력했다. 벤치 실행과 설치 전체는 실행하지 않았다. 픽스처 프로젝트가 없는 머신에서는 여전히 `--fixture`나 `PHANTOM_BENCH_FIXTURE`가 필요하다.
4. 고침(bf98d32b40). 전에는 `phantom-sign.sh`가 인증서가 없어도 메시지만 내고 `exit 0` 해서 호출자가 서명 실패를 알 수 없었고, 헤더 주석은 "login keychain"이라고 잘못 적었다. 지금은 인증서가 없으면 이유를 stderr에 쓰고 종료 코드 1로 실패한다 (`phantom-sign.sh:19-27`). `PHANTOM_SIGN_OPTIONAL=1`이면 같은 상황을 stderr 경고와 종료 코드 0으로 끝낸다. 의도적으로 건너뛰기에 기대는 호출자는 `phantom-install.sh` 하나뿐이고(릴리스 DMG는 ad-hoc 서명이며 설치하는 머신에서 재서명한다는 `phantom-release.sh:10-11`, `phantom-release.yml:9-10` 주석), 이 스크립트가 서명 호출에 이 값을 명시적으로 켠다. 헤더 주석은 전용 키체인 `phantom-signing.keychain-db`만 본다고 고쳤다 (`phantom-sign.sh:7-11`). 확인: 인증서가 없는 임시 `HOME`에서 두 경로(종료 코드 1, `PHANTOM_SIGN_OPTIONAL=1`에서 0)를 실행했다. 실제 인증서로 서명하는 경로와 `phantom-install.sh` 전체는 실행하지 않았다. 이어서 인증서를 만드는 절차가 리포에 없어 다른 사람의 설치는 늘 ad-hoc 서명으로 끝나던 것을 고쳤다: `phantom-sign.sh --setup`이 macOS 기본 도구만으로 전용 키체인·비밀번호 파일·자체 서명 인증서를 만들고, `phantom-install.sh`가 인증서가 없으면 서명 전에 이를 부른다(`PHANTOM_SIGN_SETUP=0`이면 건너뛴다. 만들기에 실패하면 전처럼 경고 후 ad-hoc 서명). 서명은 `codesign --keychain`으로 전용 키체인을 가리켜 검색 목록과 신뢰 설정을 바꾸지 않는다(5장 "로컬 서명 인증서"). 기존 키체인·비밀번호 파일·인증서 이름·식별자는 그대로라 기존 설정의 designated requirement도 같다. 확인: 임시 `HOME`과 더미 앱으로 만들기 두 번, 서명, 재복사 앱 재서명(같은 designated requirement), `codesign --verify`, 자동 만들기·`PHANTOM_SIGN_SETUP=0`·만들기 실패·틀린 비밀번호 경로를 실행했고 키체인 검색 목록이 전후 같았다. 실제 설치와 실제 앱의 개인정보 권한 유지는 실행하지 않았다.
5. 건너뛰기 목록이 이름과 달리 항상 적용된다. 이는 upstream 동작이다(`git diff d2faebf601 HEAD -- build/src/OpenSourceCommunityInstallersBuildTarget.kt`에서 `:38`의 `OPTIONS.buildStepsToSkip + BUILD_STEPS_DISABLED_FOR_GITHUB_ACTIONS`는 바뀌지 않았다). 포크가 이 파일에서 바꾼 것은 목록에서 `WINDOWS_ZIP_STEP`을 뺀 것과 macOS에서 `WINDOWS_EXE_INSTALLER_STEP`을 건너뛰던 조건을 지운 것(Windows 설치 파일과 zip을 만들기 위해)뿐이다. `OpenSourceCommunityInstallersBuildTarget.kt:18-23`의 `BUILD_STEPS_DISABLED_FOR_GITHUB_ACTIONS`가 `:38`에서 조건 없이 `buildStepsToSkip`에 더해진다. 결과: 로컬 빌드에서도 `CROSS_PLATFORM_DISTRIBUTION_STEP`, `SOURCES_ARCHIVE_STEP`, `ARCHIVE_PLUGINS`가 항상 건너뛰어진다. 그 사이 `IdeaCommunityProperties.kt:71-72`은 `buildCrossPlatformDistribution = true`, `buildSourcesArchive = true`로 남아 있다.
6. 벤치, 정적 게이트, 테스트가 CI와 빌드·릴리스 스크립트에 연결되어 있지 않다. `phantom-build.sh`, `phantom-release.sh`, `phantom-install.sh`, `phantom-release.yml`에서 `bench`, `tests.cmd`, `static`을 검색한 결과가 없고, `phantom-bench*`를 참조하는 파일은 `phantom-bench.sh`와 `phantom-bench-report.py` 자신뿐이다. 결과: 번들 크기나 시작 등록 수가 늘어도 릴리스 경로에서 자동으로 걸러지지 않는다.

### 확인하지 못해 뺀 항목

- 이번 조사 시작 자료의 "`./android`가 이 체크아웃에 없다"는 점은 이 워크트리에서 `ls android`가 실패한 사실일 뿐이고 문서화할 결함이 아니라서 뺐다. 다만 `phantom-release.yml`에 `android` 클론 단계가 없다는 점(6장)은 읽어서 확인했다. 새 러너에서 첫 실행이 실패하는지는 확인하지 못했다.
- "기본 벤치 픽스처 경로가 이 머신에 없다"는 머신 의존 사실이라 뺐다. 코드에 하드코딩되어 있다는 점만 3번에 남겼다.
- "`phantom-release.sh`에서 exe 또는 zip 중 하나만 있으면 `file`이나 `unzip`이 거친 오류로 중단된다"는 `set -e`와 `pipefail`에서 오는 추정이고 실행으로 확인하지 못해 뺐다.
