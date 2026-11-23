<!-- synced-with: README.md @ 0a09032770+docs -->
<p align="center">
  <img src="https://raw.githubusercontent.com/newkayak12/phantom-libs/main/brand/phantom-black.png" alt="Phantom" width="180">
</p>

<h1 align="center">Phantom</h1>

<p align="center">Kotlin, Spring Boot, JPA 백엔드 개발에 집중한 IDE.</p>

<p align="center">
  <a href="README.md">English</a> | 한국어<br>
  <a href="#다운로드와-설치">다운로드</a> · <a href="#무엇이-다른가">달라진 점</a> · <a href="#플러그인">플러그인</a> · <a href="#소스에서-빌드">빌드</a> · <a href="#문서">문서</a>
</p>

<p align="center">
  <a href="LICENSE.txt"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-blue"></a>
  <a href="https://github.com/newkayak12/Phantom/actions/workflows/phantom-quality.yml"><img alt="Quality CI" src="https://github.com/newkayak12/Phantom/actions/workflows/phantom-quality.yml/badge.svg?branch=main"></a>
  <a href="https://github.com/newkayak12/Phantom/actions/workflows/phantom-analysis.yml"><img alt="Analysis workflow" src="https://github.com/newkayak12/Phantom/actions/workflows/phantom-analysis.yml/badge.svg?branch=main"></a>
  <a href="https://app.codecov.io/gh/newkayak12/Phantom"><img alt="Codecov: quality tool coverage" src="https://codecov.io/gh/newkayak12/Phantom/branch/main/graph/badge.svg?flag=phantom-quality"></a>
  <a href="https://sonarcloud.io/summary/new_code?id=newkayak12_Phantom"><img alt="SonarQube quality gate" src="https://sonarcloud.io/api/project_badges/measure?project=newkayak12_Phantom&amp;metric=alert_status&amp;token=6a41d7c304b7c2d65aa9bf426fb78d1bb9ce415f"></a>
  <a href="#다운로드와-설치"><img alt="Platforms: macOS arm64, Windows x64" src="https://img.shields.io/badge/platforms-macOS%20arm64%20%7C%20Windows%20x64-lightgrey"></a>
</p>

> 이 문서는 [README.md](README.md)(영어)를 옮긴 것이다. 두 문서의 내용이 다르면 영어판이 기준이다.

커버리지 배지는 저장소 품질 도구만 측정하며, IDE 전체 커버리지는 포함하지 않는다.

## 소개

Phantom는 Kotlin, Spring Boot, JPA 백엔드 개발을 위한 IDE다. 플러그인 번들을 줄이고 고유 테마와 기본 설정을 제공하며, Spring, JPA, HTTP 요청, 데이터베이스 기능은 선택 플러그인으로 설치한다. 플랫폼에서는 시작 시 재스캔, 인덱싱, 메모리 설정을 수정했고, JetBrains 서비스로 가는 자동 요청은 [아래에 설명한 범위](#무엇이-다른가)에서 제한한다.

## 무엇이 다른가

모든 항목은 커밋으로 확인할 수 있다. 파일 단위 내용은 [CHANGELOG.md](CHANGELOG.md)에 있다.

**뺀 것**
- Ultimate 홍보: Java·GitHub·Kotlin 프로모 모듈, 환영 화면의 TechInsights 배너, YAML의 "Try Ultimate".
- Toolbox App 연동, 터미널의 Junie 에이전트, Help 메뉴의 JetBrains 링크.
- "Report to JetBrains" 오류 전송과 기본 IDE 업데이트 피드(`-Didea.updates.url`을 주면 되살아난다).

**줄인 것**
- TextMate와 Images 등은 Phantom-plugins 빌드로 교체한다. Phantom for Kotlin과 Phantom for Apache Maven은 릴리스마다 Phantom 플러그인 저장소에 별도 ZIP으로 게시한다. 업스트림 병합을 위해 원본 소스는 보존한다.
- 이 페이지에 적은 변경 일부는 번들이 이제 Phantom-plugins 빌드로 가져오는 플러그인의 이 리포지토리 소스에 있다(GitHub 로그인, Grazie Cloud, 터미널 Junie 에이전트, YAML "Try Ultimate", ShellCheck 다운로드). 그 빌드에 이 변경이 들어 있는지는 확인하지 않았다.
- Marketplace 목록은 JetBrains가 게시한 플러그인을 보여 주지 않는다(Phantom이 유지하는 것은 자체 판을 둔다). 소스 실행, internal 모드, `-Dphantom.marketplace.showAll=true`이면 보인다.
- JetBrains 자동 호출(깨진 플러그인 목록, 플러그인 추천, Grazie Cloud, 완성 로그, 설문, 피드백 양식, 로그 업로드)은 `-Dphantom.jetbrains.services=true`를 주지 않으면 꺼져 있다.
- 진단 파일은 기본으로 쓰지 않는다: OpenTelemetry 지표 CSV(4일에 약 116 MB), 인덱싱·스토리지 보고서.
- 라이브러리 안 텍스트 파일을 Find in Files 인덱스에서 뺐다. 작성자 프로젝트에서 trigram 인덱스 369 MB → 84 MB, 전체 인덱스 1.2 GB → 916 MB.

**고친 것**
- 아무것도 바꾸지 않는 파일 타입 변경 이벤트가 더는 프로젝트 전체를 다시 스캔하지 않는다(수정 커밋 `46482b0bf1`은 Atom Material Icons가 시작할 때마다 일으킨 것으로 적고, 벤치마크 기록은 이 귀속을 추정으로 표시한다).
- 플러그인을 업데이트해도 다음에 열 때 모든 파일을 다시 검사하지 않는다(이전에는 345k 파일, 약 2.3 s, CPU 20 s).
- 비 JetBrains 벤더에서 동의 설정 페이지가 깨지던 문제를 고쳤다. Gradle은 프로젝트 SDK로 돌 수 없으면 지원되는 등록 JDK로 실행한다.
- 다른 플러그인의 ID를 별칭으로 가진 플러그인이 그 플러그인을 대체한다. Phantom-plugins 판과 업스트림 원본이 함께 설치되면 Phantom 판만 로드된다. 전에는 ID 충돌로 둘 다 빠졌다(커밋 `04dba66c3a`).

**더한 것**
- 고유 정체성: `Phantom.app`, 번들 ID `dev.phantom.idea`, URL 스킴 `phantom://`, 설정 폴더 `PhantomIDEA`, Phantom Dark/Light 테마.
- GitHub 리포지토리(`owner/repo`)를 플러그인 저장소로 쓴다. [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins)는 항상 등록되어 있다.
- Backup and Sync를 내 git 저장소로 한다. GitHub 로그인은 account.jetbrains.com 대신 `gh auth token`을 쓴다.
- Download JDK 목록은 foojay Disco API에서 받는다. 힙과 파일 페이지 캐시 크기는 기기 RAM에 맞춰 정한다.

**여전히 네트워크에 접속하는 것.** Marketplace 탭 탐색(plugins.jetbrains.com)은 일부러 남겼고, Phantom 자체 플러그인 저장소는 GitHub에서 읽는다(`-Dphantom.plugins.official=false`로 뺄 수 있다). Grazie 언어 파일, JBR 런타임 목록, Git 설치 목록은 아직 JetBrains에서 받지만, 사용자가 그 다운로드를 고를 때만이다(Grazie 언어 추가, Choose Boot Java Runtime 열기, Windows에서 Git이 없을 때 Download and Install. macOS에서는 Git 설치 목록을 받는 경로가 없다). 사용 통계는 켤 수 없다. Phantom에는 사용 통계 동의 항목이 없어 동의할 것이 없고 업로더는 보내지 않는다. 밖으로 나가는 연결 전체는 [PRIVACY.ko.md](PRIVACY.ko.md)에 있다. 프레임워크 라이브러리 버전 목록과 Windows의 Git 업데이트 확인은 이제 플래그를 주지 않으면 JetBrains로 나가지 않는다. 이 변경은 소스에만 들어갔고 아직 빌드하거나 테스트하지 않았다. 피드백 양식과 Tips of the Day의 좋아요/싫어요는 더는 빈틈으로 치지 않는다. 양식은 플래그를 주지 않으면 아무것도 보내지 않고, 좋아요/싫어요 줄은 기본으로 숨겨진다. 이 변경은 소스에 들어갔지만(커밋 `6e71ed80c4`) 아직 빌드하거나 테스트하지 않았다.

## 다운로드와 설치

빌드는 [GitHub Releases](https://github.com/newkayak12/Phantom/releases)에 `v<YY>.<MM>.<DD>.<N>` 태그로 올라간다. Intel Mac과 Linux 빌드는 없다.

| 플랫폼 | 파일 |
|---|---|
| macOS, Apple Silicon | `phantom-<YYMMDD>.<N>-aarch64.dmg` |
| Windows x64 | `phantom-<YYMMDD>.<N>.exe`(설치 프로그램) 또는 `.win.zip` |

> [!WARNING]
> 공인된 기관의 서명이 없다. macOS 앱은 ad-hoc 서명만 되어 있고 공증(notarization)을 받지 않아서, 브라우저로 받은 DMG는 Gatekeeper가 막는다. Windows 파일은 서명하지 않았으므로 Windows가 알 수 없는 게시자라고 경고할 수 있다.

macOS에서는 클론한 리포지토리에서 `./phantom-install.sh --release [tag]`를 실행하면 DMG를 curl로 받고(격리 플래그가 붙지 않는다) `/Applications/Phantom.app`을 교체한 뒤 다시 띄운다. `gh auth login` 또는 `GH_TOKEN`이 필요하다. ad-hoc 서명은 빌드마다 바뀌어 설치할 때마다 macOS가 개인정보 권한을 다시 물을 것이므로, 이 스크립트는 앱을 로컬 자체 서명 인증서(Apple 인증서가 아니고 Apple 계정도 필요 없다)로 다시 서명하고, 처음에는 그 인증서를 전용 키체인에 만든다(`./phantom-sign.sh --setup`으로 직접 만들 수 있고, `PHANTOM_SIGN_SETUP=0`이면 건너뛴다).

IDE는 스스로 업데이트를 확인하지 않으니 새 릴리스도 같은 방법으로 설치한다. 첫 실행 때 설치된 JetBrains IDE의 설정을 가져올 수 있다.

## 플러그인

백엔드 기능은 별도 리포지토리 [newkayak12/Phantom-plugins](https://github.com/newkayak12/Phantom-plugins)에 있고, 모든 Phantom 빌드가 이곳을 플러그인 저장소로 등록한다. 프로젝트가 해당 라이브러리를 쓰면 IDE가 플러그인 설치를 한 번 제안한다. Settings | Plugins에서 직접 설치해도 된다. 번들 플러그인 버전은 `build/phantom-plugins-bundle.txt`에 기록한다.

| 번들, 이 리포지토리에서 빌드 | 번들, Phantom-plugins에서 빌드 | Phantom-plugins에서 필요할 때 설치 |
|---|---|---|
| Endpoints 도구 창(코어, 내용은 플러그인이 채움) | `toml`, `yaml`, `sh`(Shell Script), `markdown`, `textmate`, `mermaid` | `spring`: 빈, 주입 위치 이동, 자동 주입 검사 |
| GitHub 플러그인 저장소 지원(코어) | `decompiler`, `coverage`, `testng`, `java-i18n` | `spring-boot`: `application.yml`/`.properties` 완성과 문서 |
|  | `lombok`(전에 번들하던 커뮤니티 Lombok 플러그인을 대체) | `jpa`: `@Query` 안의 JPQL, 파생 쿼리 검사 |
|  | `copyright`, `editorconfig`, `htmltools` | `db`: JDBC 데이터 소스, SQL 콘솔, 스키마 트리 |
| | `grammar`, `terminal`, `github` | `http-client`: `.http` 요청 파일 |
| | `junit`, `properties`, `repository-search`, `json`, `gradle-dsl` | |
| | `groovy-scripting`, `groovy`, `groovy-live-templates`, `images`, `theme`, `sync` | |
| | | `kotlin`: Kotlin 언어 플러그인. 이 리포지토리의 릴리스마다 빌드 |
| | | `workspaces`, `tabs`, Phantom Icon Pack |
| | | `bytecode`, `webp`, `modal-commit`(번들에서 뺀 업스트림 플러그인을 대체) |

Docker, Kubernetes와 데이터베이스 마이그레이션은 [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins)에 플러그인 프로젝트와 마켓플레이스 항목이 있다. 현재 기능 범위는 그 저장소에서 확인한다.

## 소스에서 빌드

Apple Silicon macOS, 16 GB 이상 RAM, 첫 빌드용 여유 디스크 약 120 GB, 그리고 Phantom-plugins 읽기 권한(릴리스 워크플로에 따르면 비공개 리포지토리)이 필요하다.

```bash
git clone git@github.com:newkayak12/Phantom.git phantom-idea && cd phantom-idea
# Android modules are still referenced by the build graph; fetch the matching tag
git clone --depth 1 --branch idea/262.10968.63 https://github.com/JetBrains/android.git android
# Bundled plugins are downloaded from the phantom-libs release in build/phantom-plugins-bundle.txt
# (PHANTOM_PLUGINS_REPO=<Phantom-plugins checkout> builds them from source instead)
./phantom-build.sh   # PHANTOM_WINDOWS=0 skips the Windows build
```

`phantom-build.sh`는 phantom-libs에서 지정된 버전의 플러그인 ZIP을 받는다. `PHANTOM_PLUGINS_REPO`를 지정하면 해당 체크아웃에서 빌드한다. 첫 빌드는 약 2시간, 이후 빌드는 몇 분 걸린다. 결과물은 `out/phantom-idea/artifacts/`에 생긴다. 번들하지 않는 Kotlin·Maven 플러그인은 `PHANTOM_PUBLISH_PLUGINS=1`(phantom-release.sh가 켬)일 때만 그 안의 `*-plugins/` 폴더에 zip으로 생기고, 그 밖의 빌드는 두 플러그인의 타깃 약 190개를 건너뛴다. 빌드는 더는 추적 파일을 고치지 않는다. `phantom-build.sh`가 표시 버전을 `-Dphantom.version.major`, `-Dphantom.version.minor`로 넘기고, 추적 중인 `IdeaApplicationInfo.xml`에는 자리표시자(`0` / `0.0`)가 남는다. 그래서 소스에서 실행한 IDE나 `installers.cmd`를 직접 실행한 결과는 버전 0.0으로 보인다. 이 변경(커밋 `048852ca23`)은 아직 빌드하거나 테스트하지 않았다.

버전 체계: 표시 버전 `YY.MM.DD`, 빌드 번호 `PH-<YYMMDD>.<N>`, 그리고 플랫폼 262용 플러그인이 계속 로드되도록 남긴 내부 플랫폼 빌드 `262.<YYMMDD>`.

플랫폼을 수정하거나 디버그하려면 체크아웃 루트를 기존 프로젝트로 열고 공유 실행 구성 **IDEA**를 쓴다. 프로젝트 SDK는 `jbr-25`(JDK 25)다. 소스 실행과 배포 빌드가 서로 다른 플러그인을 로드할 수 있으므로, 대체 플러그인의 변경을 검증하기 전에 어느 쪽을 실행 중인지 확인한다.

## 문서

- [CHANGELOG.md](CHANGELOG.md): 릴리스별 변경
- [PRIVACY.ko.md](PRIVACY.ko.md): Phantom이 수집하는 것(없음)과 접속하는 곳
- [PERFORMANCE.ko.md](PERFORMANCE.ko.md): IntelliJ IDEA Ultimate 대비 시작 시간과 메모리

## 업스트림과 상표

> Phantom는 JetBrains와 제휴하지 않았으며 JetBrains의 보증을 받지 않았다. IntelliJ와 IntelliJ IDEA는 JetBrains s.r.o.의 상표다.

[JetBrains/intellij-community](https://github.com/JetBrains/intellij-community) 태그 `idea/262.10968.63`을 기준으로 한다. 첫 커밋은 업스트림 트리(업스트림 README와 히스토리는 빠짐)이고, Phantom 변경은 그 뒤부터다.

## 기여

한 개발자의 Kotlin·Spring 백엔드 작업에 맞춘 개인 포크다. 문제는 이 리포지토리의 GitHub Issues에 남긴다. [Phantom quality](.github/workflows/phantom-quality.yml)는 CI에서 저장소 스크립트와 워크플로를 검사한다. 관련 IDE 테스트는 로컬에서 `./tests.cmd`로 실행한다.

## 라이선스

intellij-community와 같은 Apache License 2.0이다. [LICENSE.txt](LICENSE.txt)와 [NOTICE.txt](NOTICE.txt)를 본다.
