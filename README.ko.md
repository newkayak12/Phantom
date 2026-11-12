<!-- synced-with: README.md @ be60914099+docs -->
<p align="center">
  <img src="community-resources/phantom-brand-src/phantom-black.png" alt="Phantom IDEA" width="180">
</p>

<h1 align="center">Phantom IDEA</h1>

<p align="center">Kotlin, Spring Boot, JPA 백엔드 개발에 맞춰 덜어 낸 intellij-community 포크.</p>

<p align="center">
  <a href="README.md">English</a> | 한국어<br>
  <a href="#다운로드와-설치">다운로드</a> · <a href="#무엇이-다른가">달라진 점</a> · <a href="#성능">성능</a> · <a href="#플러그인">플러그인</a> · <a href="#소스에서-빌드">빌드</a> · <a href="#문서">문서</a>
</p>

<p align="center">
  <a href="LICENSE.txt"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-blue"></a>
  <a href="#업스트림-상표-클린룸-정책"><img alt="Upstream: idea/262.10968.63" src="https://img.shields.io/badge/upstream-idea%2F262.10968.63-lightgrey"></a>
  <a href="#다운로드와-설치"><img alt="Platforms: macOS arm64, Windows x64" src="https://img.shields.io/badge/platforms-macOS%20arm64%20%7C%20Windows%20x64-lightgrey"></a>
</p>

> 이 문서는 [README.md](README.md)(영어)를 옮긴 것이다. 두 문서의 내용이 다르면 영어판이 기준이다.

## 소개

Phantom IDEA는 [JetBrains/intellij-community](https://github.com/JetBrains/intellij-community)를 개인이 포크한 IDE다. 기준은 태그 `idea/262.10968.63`이고, 업스트림과 같은 Apache 2.0 라이선스를 따른다. 백엔드 개발자가 쓰지 않는 것을 빼고, JetBrains 서비스로 가는 자동 호출을 끄고, 프로파일링으로 찾은 플랫폼 문제를 고친다. Ultimate에만 있던 백엔드 기능(Spring, JPA, HTTP 클라이언트, 데이터베이스)은 필요할 때 설치하는 클린룸 플러그인으로 옮겼다. JetBrains와 제휴하거나 보증받은 프로젝트가 아니다([자세히](#업스트림-상표-클린룸-정책)).

번들만이 아니라 플랫폼까지 고치는 이유: Spring Boot 프로젝트에서 IntelliJ IDEA Ultimate 2026.2.3을 프로파일링하니 기동 비용의 약 81%가 오픈소스 플랫폼 자체에 있었고(작성자가 직접 한 프로파일링이며 이 리포지토리 밖에 기록돼 있다), 프로젝트 전체를 다시 스캔하게 만드는 파일 타입 변경 이벤트도 그중 하나였다.

## 무엇이 다른가

모든 항목은 커밋으로 확인할 수 있다. 파일 단위 내용은 [FORK-DELTA.md](docs/phantom/FORK-DELTA.md)와 [CHANGELOG.md](CHANGELOG.md)에 있다.

**뺀 것** ([FORK-DELTA §2.1, §3.3](docs/phantom/FORK-DELTA.md))
- Ultimate 홍보: Java·GitHub·Kotlin 프로모 모듈, 환영 화면의 TechInsights 배너, YAML의 "Try Ultimate".
- Toolbox App 연동, 터미널의 Junie 에이전트, Help 메뉴의 JetBrains 링크.
- "Report to JetBrains" 오류 전송과 기본 IDE 업데이트 피드(`-Didea.updates.url`을 주면 되살아난다).

**줄인 것** ([FORK-DELTA §3.2, §3.3, §3.5](docs/phantom/FORK-DELTA.md))
- 번들 모듈 14개(Learn, MCP 서버, ACP, 내부 개발자 도구, Eclipse 가져오기, GitLab, 추가 키맵 등)와 Android, Compose를 뺐다. 소스는 업스트림 병합을 위해 남긴다.
- JetBrains 자동 호출(깨진 플러그인 목록, 플러그인 추천, Grazie Cloud, 완성 로그, 설문, 로그 업로드)은 `-Dphantom.jetbrains.services=true`를 주지 않으면 꺼져 있다.
- 진단 파일은 기본으로 쓰지 않는다: OpenTelemetry 지표 CSV(4일에 약 116 MB), 인덱싱·스토리지 보고서.
- 라이브러리 안 텍스트 파일을 Find in Files 인덱스에서 뺐다. 작성자 프로젝트에서 trigram 인덱스 369 MB → 84 MB, 전체 인덱스 1.2 GB → 916 MB.

**고친 것** ([FORK-DELTA §3.5, §3.7](docs/phantom/FORK-DELTA.md))
- 아무것도 바꾸지 않는 파일 타입 변경 이벤트가 더는 프로젝트 전체를 다시 스캔하지 않는다(수정 커밋 `46482b0bf1`은 Atom Material Icons가 시작할 때마다 일으킨 것으로 적고, 벤치마크 기록은 이 귀속을 추정으로 표시한다).
- 플러그인을 업데이트해도 다음에 열 때 모든 파일을 다시 검사하지 않는다(이전에는 345k 파일, 약 2.3 s, CPU 20 s).
- 비 JetBrains 벤더에서 동의 설정 페이지가 깨지던 문제를 고쳤다. Gradle은 프로젝트 SDK로 돌 수 없으면 지원되는 등록 JDK로 실행한다.

**더한 것** ([FORK-DELTA §3.1, §3.4, §5.1](docs/phantom/FORK-DELTA.md))
- 고유 정체성: `Phantom IDEA.app`, 번들 ID `dev.phantom.idea`, URL 스킴 `phantom://`, 설정 폴더 `PhantomIDEA`, Phantom Dark/Light 테마.
- GitHub 리포지토리(`owner/repo`)를 플러그인 저장소로 쓴다. [Phantom-plugins](https://github.com/newkayak12/Phantom-plugins)는 항상 등록되어 있다.
- Backup and Sync를 내 git 저장소로 한다. GitHub 로그인은 account.jetbrains.com 대신 `gh auth token`을 쓴다.
- Download JDK 목록은 foojay Disco API에서 받는다. 힙과 파일 페이지 캐시 크기는 기기 RAM에 맞춰 정한다.

**여전히 네트워크에 접속하는 것.** Marketplace 탭 탐색(plugins.jetbrains.com)은 일부러 남겼고, Phantom 자체 플러그인 저장소는 GitHub에서 읽는다(`-Dphantom.plugins.official=false`로 뺄 수 있다). Grazie 언어 파일과 JBR·Git 설치 피드는 아직 JetBrains에서 받는다. 알려진 빈틈은 [ARCHITECTURE.md §6](docs/phantom/ARCHITECTURE.md)에 있다. 사용 통계 업로드는 플래그가 아니라 사용자 동의 설정이 결정하고, Tips of the Day의 좋아요/싫어요 피드백은 플래그와 무관하게 JetBrains로 가며, 피드백 양식과 프레임워크 라이브러리 다운로드 도우미도 플래그 밖에 있다.

## 성능

2026-10-17에 IntelliJ IDEA Ultimate 2026.2와 번갈아 5회씩 실행한 중앙값이다. 조건: Apple Silicon Mac, Kotlin 멀티 모듈 Gradle 프로젝트 하나를 예열한 복사본, 실행 180초 뒤 측정, Phantom 빌드 `f2e7052`.

| 중앙값 | Phantom | Ultimate | Phantom / Ultimate |
|---|---|---|---|
| 첫 smart mode | 3.4 s | 3.8 s | 0.89× |
| 인덱싱 안정화 | 4.5 s | 9.0 s | 0.50× |
| IDE 프로세스 메모리(footprint) | 1,005 MB | 2,211 MB | 0.45× |
| 전체 GC 뒤 힙 | 215 MB | 428 MB | 0.50× |
| 로드된 클래스 | 41.6k | 68.6k | 0.61× |

한계: 기기 하나, 프로젝트 하나로 쟀고, Ultimate는 작성자가 쓰던 플러그인(SonarLint 포함)과 함께 돌렸다. 첫 smart mode는 두 IDE의 범위가 겹친다(3.1–3.7 s). 차이는 코어가 빨라서가 아니라 덜 로드하고 다시 스캔하지 않아서 생긴다. 플러그인 업데이트 뒤 파일 검사를 건너뛰면 CPU는 줄지만 프로젝트를 여는 시간은 거의 같다(이전 9.8 s, 이후 10.1 s, 10회, 커밋 `6613a4ed82`). 현재 빌드로는 아직 다시 재지 않았다.

방법: [벤치 도구](docs/phantom/bench/README.md).

## 다운로드와 설치

빌드는 [GitHub Releases](https://github.com/newkayak12/Phantom-IDEA/releases)에 `v<YY>.<MM>.<DD>.<N>` 태그로 올라간다. Intel Mac과 Linux 빌드는 없다.

| 플랫폼 | 파일 |
|---|---|
| macOS, Apple Silicon | `phantom-idea-<YYMMDD>.<N>-aarch64.dmg` |
| Windows x64 | `phantom-idea-<YYMMDD>.<N>.exe`(설치 프로그램) 또는 `.win.zip` |

> [!WARNING]
> 공인된 기관의 서명이 없다. macOS 앱은 ad-hoc 서명만 되어 있고 공증(notarization)을 받지 않아서, 브라우저로 받은 DMG는 Gatekeeper가 막는다. Windows 파일은 서명하지 않았으므로 Windows가 알 수 없는 게시자라고 경고할 수 있다.

macOS에서는 클론한 리포지토리에서 `./phantom-install.sh --release [tag]`를 실행하면 DMG를 curl로 받고(격리 플래그가 붙지 않는다) `/Applications/Phantom IDEA.app`을 교체한 뒤 다시 띄운다. `gh auth login` 또는 `GH_TOKEN`이 필요하다. ad-hoc 서명은 빌드마다 바뀌므로, 로컬 인증서로 다시 서명하지 않으면(`phantom-sign.sh`, [BUILD.md §5](docs/phantom/BUILD.md)) macOS가 개인정보 권한을 다시 묻는다.

IDE는 스스로 업데이트를 확인하지 않으니 새 릴리스도 같은 방법으로 설치한다. 첫 실행 때 설치된 JetBrains IDE의 설정을 가져올 수 있다.

## 플러그인

백엔드 기능은 별도 리포지토리 [newkayak12/Phantom-plugins](https://github.com/newkayak12/Phantom-plugins)에 있고, 모든 Phantom IDEA 빌드가 이곳을 플러그인 저장소로 등록한다. 프로젝트가 해당 라이브러리를 쓰면 IDE가 플러그인 설치를 한 번 제안한다. Settings | Plugins에서 직접 설치해도 된다.

| 이 리포지토리에 번들 | Phantom-plugins에서 필요할 때 설치 |
|---|---|
| `phantom-sync`: 내 git 저장소로 Backup and Sync | `spring`: 빈, 주입 위치 이동, 자동 주입 검사 |
| `phantom-theme`: Phantom Dark와 Light | `spring-boot`: `application.yml`/`.properties` 완성과 문서 |
| Lombok(업스트림 커뮤니티 플러그인, 새로 번들) | `jpa`: `@Query` 안의 JPQL, 파생 쿼리 검사 |
| Endpoints 도구 창(코어, 내용은 플러그인이 채움) | `db`: JDBC 데이터 소스, SQL 콘솔, 스키마 트리 |
| GitHub 플러그인 저장소 지원(코어) | `http-client`: `.http` 요청 파일 |
| | `workspaces`, `tabs`, Phantom Icon Pack |
| | `bytecode`, `webp`, `modal-commit`(번들에서 뺀 업스트림 플러그인을 대체) |

계획만 있고 아직 만들지 않은 것: Spring 런타임(Actuator), Docker와 Kubernetes, 데이터베이스 마이그레이션 등. 설계는 [docs/phantom/design/](docs/phantom/design/)에 있다. 옮긴 플러그인이 이 리포지토리 어디에 있었는지는 [FORK-DELTA §5](docs/phantom/FORK-DELTA.md)에 있다.

## 소스에서 빌드

Apple Silicon macOS, 16 GB 이상 RAM, 첫 빌드용 여유 디스크 약 120 GB가 필요하다.

```bash
git clone git@github.com:newkayak12/Phantom-IDEA.git phantom-idea && cd phantom-idea
# Android modules are still referenced by the build graph; fetch the matching tag
git clone --depth 1 --branch idea/262.10968.63 https://github.com/JetBrains/android.git android
./phantom-build.sh   # PHANTOM_WINDOWS=0 skips the Windows build
```

첫 빌드는 약 2시간, 이후 빌드는 몇 분 걸린다. 결과물은 `out/phantom-idea/artifacts/`에 생긴다. 빌드가 추적 중인 `IdeaApplicationInfo.xml`의 버전을 고쳐 쓰므로 커밋 전에 작업 트리를 확인한다.

버전 체계: 표시 버전 `YY.MM.DD`, 빌드 번호 `PH-<YYMMDD>.<N>`, 그리고 IntelliJ Platform 262용 플러그인이 계속 로드되도록 남긴 내부 플랫폼 빌드 `262.<YYMMDD>`. 서명, 릴리스, 벤치마크는 [BUILD.md](docs/phantom/BUILD.md)에 있다.

## 문서

프로젝트 문서는 대부분 한국어로 쓰여 있다.

- [문서 색인](docs/phantom/README.md): 어디서부터 읽을지
- [ARCHITECTURE.md](docs/phantom/ARCHITECTURE.md): 플랫폼이 뜨는 과정과 Phantom 코드가 붙는 자리
- [FORK-DELTA.md](docs/phantom/FORK-DELTA.md): 업스트림과 다른 점 전부(파일 단위)와 병합 위험
- [BUILD.md](docs/phantom/BUILD.md): 빌드, 서명, 설치, 릴리스, 벤치마크
- [DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md): 코드 작업과 테스트 실행
- [CHANGELOG.md](CHANGELOG.md): 릴리스별 변경

## 업스트림, 상표, 클린룸 정책

> Phantom IDEA는 JetBrains와 제휴하지 않았으며 JetBrains의 보증을 받지 않았다. IntelliJ와 IntelliJ IDEA는 JetBrains s.r.o.의 상표다.

intellij-community 태그 `idea/262.10968.63`을 기준으로 한다. 첫 커밋은 업스트림 트리(업스트림 README와 히스토리는 빠짐)이고, Phantom 변경은 그 뒤부터다. 새 업스트림 태그를 병합할 때 충돌하기 쉬운 파일은 [FORK-DELTA §6](docs/phantom/FORK-DELTA.md)에 정리되어 있다.

Phantom 코드는 intellij-community API와 공개 명세를 바탕으로 작성한다. Ultimate 플러그인은 번들하지 않으며, 라이선스 검사는 절대 건드리지 않는다.

## 기여

한 개발자의 Kotlin·Spring 백엔드 작업에 맞춘 개인 포크다. 문제는 이 리포지토리의 GitHub Issues에 남긴다. 테스트를 돌리는 CI는 없으니 관련 테스트는 로컬에서 `./tests.cmd`로 실행한다([DEVELOPMENT.md](docs/phantom/DEVELOPMENT.md) 참고).

## 라이선스

intellij-community와 같은 Apache License 2.0이다. [LICENSE.txt](LICENSE.txt)와 [NOTICE.txt](NOTICE.txt)를 본다.
