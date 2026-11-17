# Phantom 변경 이력

이 파일은 JetBrains intellij-community를 포크한 개인 프로젝트 Phantom의 변경 이력이다. 기준(base)은 upstream 태그 `idea/262.10968.63`이며, 루트 커밋 `d2faebf601`이 이 태그의 소스를 한 번에 가져온 것이다. 그 이후의 모든 커밋이 Phantom 변경이다.

버전 표기는 두 가지다. 릴리스 태그는 `v<YY>.<MM>.<DD>.<N>` 형식이고, 빌드 번호는 `PH-<YYMMDD>.<N>` 형식이다. 플랫폼 빌드 `262.<YYMMDD>`는 내부 apiVersion과 번들 플러그인의 since/until-build로만 쓴다.

## 이 파일을 유지하는 방법

- 변경을 커밋할 때 같은 커밋에서 `미배포` 아래에 항목을 추가한다.
- `phantom-release.sh`가 릴리스 태그를 만들면 `미배포` 항목을 그 버전 제목(태그와 날짜) 아래로 옮긴다.
- 사용자나 개발자에게 보이는 변경마다 한 줄을 쓰고, 끝에 짧은 커밋 해시와 PR 번호를 괄호로 붙인다: `(f74262e02d, PR #4)`. PR 없이 main에 바로 넣은 커밋은 해시만 쓴다: `(f6adc2ce77)`.
- 항목은 고정 카테고리(브랜딩·버전 / 번들 구성 / JetBrains 서버 통신 / 성능·인덱싱 / 기능(Phantom 플러그인) / 빌드·배포·서명 / 벤치마크 / 버그 수정 / 문서)로 나누고 빈 카테고리는 생략한다.
- 해시는 커밋 후에야, PR 번호는 PR을 연 뒤에야 알 수 있으므로, 같은 커밋에 쓸 수 없으면 다음 커밋에서 채운다.
- PR을 스쿼시 머지하거나 브랜치를 리베이스하면 브랜치 커밋의 해시는 main에 남지 않는다. 그 경우에도 PR 번호로 변경을 찾을 수 있다. 해시는 main에 있는 것(스쿼시 커밋)으로 고치고, 고칠 수 없으면 PR 번호만 남긴다.

## 미배포 (v26.11.13.1 이후)

### 브랜딩·버전
- 제품 이름을 Phantom IDEA에서 Phantom으로 바꿨다(IntelliJ IDEA 상표와 혼동을 피하려고). 앱은 `Phantom.app`, Windows 이름과 설치 폴더는 `Phantom`, 스플래시(`phantom_logo*.png`, `compose.swift splash phantom-white.png`로 다시 만듦)와 DMG 배경의 글자도 Phantom이다. 설정이 이어지도록 번들 ID `dev.phantom.idea`, 설정 폴더 이름 `PhantomIDEA`, 산출물 이름 `phantom-idea-*`는 그대로 둔다. `phantom-install.sh`는 기본 경로에서 `Phantom.app`이 아직 없고 `/Applications/Phantom IDEA.app`이 있으면 그 앱을 종료하고 새 앱으로 바꾼 뒤 지운다. 이 변경 이력의 이전 항목은 당시 이름 그대로 둔다 (d1a97edf7a).
- 실행 파일 이름을 `idea`에서 `phantom`으로 바꿨다(`baseFileName`, `IdeaApplicationInfo.xml`의 `script`). 그래서 `Contents/MacOS/phantom`, 번들 `bin/phantom.vmoptions`, 사용자 설정의 `phantom.vmoptions`·`phantom.jdk`, 환경 변수 `PHANTOM_VM_OPTIONS`·`PHANTOM_PROPERTIES`·`PHANTOM_JDK`가 된다. `phantom-install.sh`는 설정 폴더의 `idea.vmoptions`·`idea.jdk`를 새 이름 파일이 없을 때 한 번 복사하고, 교체할 앱이 아직 `MacOS/idea`로 떠 있어도 찾아 종료한다. DMG를 직접 설치하면 이 복사가 없어 기존 `idea.vmoptions`가 적용되지 않는다. 소개 문구(motto)는 "Built on the open-source IntelliJ Platform"으로 바꾸고 스플래시를 다시 만들었다 (cd1fc57a5f).
- `README.md`, `README.ko.md`의 GitHub Releases 링크와 `git clone` 주소, 번들하지 않는 Kotlin 플러그인 `plugin.xml`의 벤더 URL이 저장소의 새 이름 `newkayak12/Phantom`을 가리킨다 (5641b42107).
- 산출물 이름을 `phantom-idea-<YYMMDD>.<N>`에서 `phantom-<YYMMDD>.<N>`으로 바꿨다(DMG, exe, win.zip; `getBaseArtifactName`). Linux 루트 폴더도 `phantom`이다. 출력 폴더 `out/phantom-idea`와 설정 폴더 `PhantomIDEA`는 그대로 둔다. `phantom-install.sh`와 `phantom-build.sh`의 정리 단계는 `phantom-*` 패턴이라 예전 이름의 DMG도 찾고 지운다 (2f607127fd).
- `NOTICE.txt`에 GPL 2.0 + Classpath Exception 구성 요소(JetBrains Runtime, jb-jdi, sa-jdwp, java-compatibility)의 소스 위치와, 요청하면 3년 동안 소스 사본을 제공한다는 서면 제안을 넣었다. 바이너리 배포에 GPLv2 3조가 요구하는 것이다. JBR 소스 태그는 `jbr-release-<runtimeBuild>`이다 (2f607127fd).
- 수정한 원본 파일 중 표시가 없던 두 파일(`.idea/modules.xml`, `GithubBundle.properties`)에 `phantom-idea:` 표시를 달았다 (2f607127fd).
- `LICENSE.txt`를 JetBrains Open-Source Build Terms에서 Apache License 2.0 원문으로 바꾸고, `NOTICE.txt`에 포크 사실과 JetBrains와 무관하다는 문구를 넣었다 (ce70b07310).
- 번들하지 않는 Kotlin 플러그인을 Phantom Kotlin이라는 이름으로, 벤더 Phantom으로 낸다. `plugins/kotlin/plugin/resources/META-INF/plugin.xml`의 `<name>`이 `Phantom Kotlin`, `<vendor>`가 `Phantom`(URL은 이 저장소)이고, `<id>`는 Kotlin에 의존하는 플러그인이 계속 동작하도록 `org.jetbrains.kotlin` 그대로다. `phantom-release.sh`가 Phantom-plugins의 `.phantom/marketplace.json`에 쓰는 항목도 이름 `Phantom Kotlin`, 벤더 `Phantom`, 새 설명으로 바뀌었고 `source` 키가 빠져서, 플러그인 화면의 링크는 저장소 하위 폴더가 아니라 저장소 첫 화면을 가리킨다(`GitHubPluginRepository.kt:173`). 이 판이 빌드의 플러그인 검증을 통과하도록 아래 71ff75bf3d이 벤더 허용 목록에 Phantom을 더했다 (8d27b3dfd8).
- 번들하지 않는 Kotlin과 Maven 플러그인의 이름을 Kotlin Support, Maven Support로 바꿨다(전에는 Phantom Kotlin, Phantom Maven). `plugins/kotlin/plugin/resources/META-INF/plugin.xml`, `plugins/maven/plugin/resources/META-INF/plugin.xml`의 `<name>`과 `phantom-release.sh`가 `.phantom/marketplace.json`에 쓰는 항목 이름이 바뀌었고 ID는 그대로다. 커밋 본문에 따르면 Phantom-plugins의 플러그인처럼, "Phantom Kotlin"이 Phantom이 따로 만든 Kotlin으로 읽히지 않게 하려는 것이다 (0a09032770).

### 번들 구성
- JUnit과 Properties도 IntelliJ 원본(`intellij.junit`, `intellij.properties`) 대신 Phantom-plugins가 만든 판(디렉터리 `junit`, `properties`)으로 번들한다. 커밋 본문에 따르면 이름은 Phantom JUnit, Phantom Properties이고 `dev.phantom.*` ID에 원본 ID `JUnit`, `com.intellij.properties`를 별칭으로 두며 rt jar 이름과 콘텐츠 모듈 이름은 그대로다(Phantom-plugins 저장소는 읽지 못해 확인하지 못함). 그래서 클래스 버전 검사의 `plugins/junit/lib/junit-rt.jar`, `junit5-rt.jar` 항목(`BaseIdeaProperties.kt:72-73`)은 고치지 않았다. `PHANTOM_PLUGINS_BUNDLED`와 `phantom-build.sh`의 `bundled_from_plugins`가 19개가 됐다 (2cff0f911e).
- Repository Search, JSON, Gradle DSL도 원본(`intellij.repository.search`, `intellij.json`, `intellij.android.gradle.dsl`) 대신 Phantom-plugins 판(디렉터리 `repository-search`, `json`, `gradle-dsl`)으로 번들한다. 커밋 본문에 따르면 Phantom Repository Search, Phantom JSON, Phantom Gradle DSL이고 원본 ID `org.jetbrains.idea.reposearch`, `com.intellij.modules.json`, `org.jetbrains.idea.gradle.dsl`을 별칭으로, 콘텐츠 모듈 이름을 그대로 둔다. 7c43a36532가 Gradle 때문에 남겼던 Android Gradle DSL 모델도 이제 원본이 아니라 이 판이다. 이 리포의 `plugins/repository-search`에 있는 Package Search 차단 패치(98673d9e9a)가 그 판에 들어 있는지는 확인할 수 없다. 목록은 22개가 됐다 (0748db2720).
- Groovy, Groovy Scripting, Groovy Live Templates도 원본(`intellij.groovy`, `intellij.groovy.scripting`, `intellij.groovy.live.templates`) 대신 Phantom-plugins 판(디렉터리 `groovy`, `groovy-scripting`, `groovy-live-templates`)으로 번들한다. 클래스 버전 검사(`CE_CLASS_VERSIONS`)가 Groovy rt jar를 `plugins/Groovy/lib/` 대신 Phantom-plugins 빌드가 쓰는 폴더 `plugins/groovy/lib/`에서 찾는다(`BaseIdeaProperties.kt:82-83`). 목록은 25개가 됐다. 이 판의 ID와 별칭은 커밋에 적혀 있지 않다 (588cd923ce).
- Maven(`intellij.maven.plugin`)을 번들하지 않고 Kotlin처럼 릴리스마다 zip으로 Phantom 플러그인 저장소에 올린다(`pluginModulesToPublish`, 릴리스 `maven-262.<YYMMDD>`, `.phantom/marketplace.json`의 `org.jetbrains.idea.maven` 항목, `pom.xml`이 있는 프로젝트에 제안). 코드 주석에 따르면 51 MB이고, Gradle의 Maven 가져오기, Kotlin의 Maven 지원, Repository Search의 공통 부분 같은 다른 플러그인의 선택 모듈은 Maven이 없으면 꺼진다. 클래스 버전 검사는 `plugins/maven-plugin/` 항목을 거른다. Images(`intellij.platform.images`)도 원본 대신 Phantom-plugins 판(디렉터리 `images`)으로 번들해 `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`가 26개가 됐다. 이 판의 ID와 별칭은 커밋에 적혀 있지 않다. 코드만 읽었고 빌드와 릴리스는 하지 않았다 (2d876caa58).

### JetBrains 서버 통신
- 피드백 양식이 JetBrains로 보내던 것과 Tips of the Day 좋아요/싫어요를 막았다. 모든 피드백 양식이 거치는 `submitFeedback`(`GeneralFeedbackSubmit.kt`)이 `-Dphantom.jetbrains.services=true`가 아니면 요청도 지역 URL 매핑 조회도 없이 바로 돌아온다(콜백도 부르지 않으므로 블록 기반 대화상자는 감사 알림을 띄우지 않고, `LocalizationFeedbackDialog`는 알림을 건너뛴다). 레지스트리 `tips.of.the.day.feedback`의 기본값을 staging에서 nowhere로 바꾸고, 그 값이면 `TipPanel`이 좋아요/싫어요 줄을 숨긴다. 테스트 `training.ui.FeedbackSubmissionGateTest`(모듈 `intellij.featuresTrainer.tests`)를 더했다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). 이 커밋은 번들 밖 `featuresTrainer`의 `OnboardingFeedbackForm`이 띄우는 감사 알림을 건드리지 않았다 (4ca69e1a95, PR #2).
- `OnboardingFeedbackForm`(번들 밖 `featuresTrainer` 소속)이 보내지 않았는데 감사 알림을 띄우던 것을, 다른 호출자와 같은 게이트 조건(`OnboardingFeedbackForm.kt:279`)으로 건너뛰게 했다. `FeedbackSubmissionGateTest`가 요청을 기다리는 시간도 1초에서 15초로 늘렸다(게이트가 없으면 요청 앞에 느린 지역 매핑 조회가 끼어들 수 있다). 한계: 그보다 느린 매핑 조회는 고장 난 게이트를 통과시킬 수 있다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (9c32c01f49, PR #2).
- 프레임워크 라이브러리 버전 목록을 JetBrains(`www.jetbrains.com/idea/download-assistant.xml`, `frameworks.jetbrains.com`)에서 받지 않는다. New Project 마법사에서 Groovy를 고르거나 Add Framework Support 등의 라이브러리 패널이 열리면 사용자가 다운로드를 고르기 전에 목록을 받던 것이다. `-Dphantom.jetbrains.services=true`가 아니면 `LibrariesDownloadAssistant`가 번들 목록(Groovy는 `groovy.sdk.xml`, 받는 곳은 repo1.maven.org)을 쓴다. 번들 목록에 없는 최신 버전은 보이지 않을 수 있다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (cf39628de4, PR #7).
- Windows에서 Git 저장소가 있는 프로젝트를 열고 1분 쉬면 설치된 Git(MSYS·Cygwin, Git for Windows 포함)의 새 버전을 확인하려고 JetBrains의 Git 설치 목록(`download.jetbrains.com/jdk/feed/v1/gits.json.xz`)을 받던 유휴 감시를 `-Dphantom.jetbrains.services=true`가 아니면 켜지 않는다. 그래서 설정 진입점의 Git 업데이트 항목도 나타나지 않는다. Git이 없을 때 오류의 "Download and Install"은 사용자가 고른 다운로드라 그대로 이 목록을 받는다. Grazie 언어 파일과 JBR 런타임 목록은 호출자를 따라가 보니 사용자가 그 다운로드를 고를 때만 받으므로 바꾸지 않고 문서에 근거를 적었다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (cf39628de4, PR #7).
- 내장 서버의 기동 보고서 REST(`/api/startUpMeasurement`)를 JetBrains ij-perf 페이지(`ij-perf.jetbrains.com`, `ij-perf.labs.jb.gg`)가 브라우저에서 읽던 허용을 `-Dphantom.jetbrains.services=true`일 때만 둔다. 그 밖에는 다른 REST 서비스와 같은 기본 origin 검사(로컬 origin은 통과, 그 밖의 origin은 사용자에게 호스트를 신뢰할지 묻는 대화상자)를 쓴다. 보고서를 밖으로 보내는 호출은 아니었고, 이 포크의 벤치는 이 서비스를 쓰지 않는다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (cf39628de4, PR #7).

### 성능·인덱싱
- IDE가 유휴 상태가 되면 메모리를 OS에 돌려주는 정리(full GC)를 5초가 아니라 5분 뒤에 한다(레지스트리 `ide.idle.memory.cleaner.delay` 기본값 5000 → 300000, `PlatformExtensions.xml:2065-2068`). 커밋 본문에 따르면 5초일 때는 다른 앱으로 전환할 때마다 정리가 돌아, 돌아온 사용자 앞에서 IDE가 최대 1초 멈췄다. 같은 커밋에서 기본 VM 옵션의 힙 여유 비율을 10-25%에서 20-40%(`-XX:MinHeapFreeRatio=20`, `-XX:MaxHeapFreeRatio=40`, `VmOptionsGenerator.kt:26-32`)로 바꿨다. 10-25%에서는 쉬고 난 뒤 작업이 힙을 다시 키우는 수집을 연달아 일으켰다. 작성자의 예약 프로젝트에서 시작 3분 뒤 3회 측정한 footprint는 1173-1264 MB였고, 10-25%는 1093-1232 MB, 사용자가 멈춤을 피하려고 되돌리던 IntelliJ 값은 1408-1570 MB였다(커밋 본문) (1d49734b5c).
- 클래스 데이터 공유(CDS)를 켰다(`IdeaCommunityProperties.kt:144-148`의 `enableCds = true`). 빌드가 런처 JVM 인자에 `-XX:SharedArchiveFile=<IDE 캐시 폴더>/idea<빌드 번호>.jsa`와 `-XX:+AutoCreateSharedArchive`를 넣고, 그 대신 `-Djava.system.class.loader`는 넣지 않는다(`BuildContextImpl.kt:464-473`). 코드 주석에 따르면 설치나 업데이트 뒤 첫 종료 때 JVM이 로드한 클래스를 아카이브로 쓰고(약 25초, 약 370 MB), 다음 시작부터 매핑해 `phantom-bench.sh`의 Kotlin 픽스처에서 "app initialization"이 2.3-3.9 s에서 0.8-2.9 s로, 첫 smart mode가 14-19 s에서 5-9 s로 줄었다 (71ff75bf3d).
- 이전 빌드의 CDS 아카이브를 지운다. 아카이브 이름에 빌드 번호가 들어가 업데이트할 때마다 약 370 MB가 캐시 폴더에 남기 때문이다. 시작 후 활동 `PhantomCdsArchiveCleaner`(`PlatformExtensions.xml:844-845`, 단위 테스트·headless에서는 돌지 않고 앱 실행당 한 번)가 JVM 인자의 `-XX:SharedArchiveFile=` 경로를 읽어, 같은 폴더에서 이름의 앞 글자(숫자 앞까지)가 같고 바로 뒤가 숫자이며 `.jsa`로 끝나는 다른 파일을 IO 스레드에서 지운다. 테스트 `dev.phantom.ide.memory.CdsArchivesTest`(모듈 `intellij.platform.tests`)는 이 문서를 쓰며 실행하지 않았다. 코어의 Phantom 시작 후 활동이 3개가 됐다 (17cce24354).

### 기능(Phantom 플러그인)
- Phantom 코드의 사용자에게 보이는 문구 39개를 리소스 번들로 옮겼다. 문구는 그대로다. 새 번들 `PhantomIdeBundle`(`platform-impl`: GitHub 플러그인 토큰 액션·대화상자, 플러그인 추천 알림, Spring Boot 새 프로젝트 항목, 알림 그룹 이름 17개)과 `PhantomLangBundle`(`lang-impl`: Endpoints 도구 창과 액션, "Generate HTTP Request" 11개), 그리고 `IdeBundle`(`toolwindow.stripe.Endpoints`)과 `GithubBundle`(gh CLI·디바이스 플로 로그인 문구와 오류 10개)에 키를 더했다. `SetGitHubPluginToken` 액션은 `PlatformActions.xml` 끝의 자체 `<actions resource-bundle>` 블록으로 옮겼다. 고정 카테고리 중 맞는 것이 없어 가장 가까운 기능(Phantom 플러그인)에 적었다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). `GithubBundle`은 이 리포의 `plugins/github` 소스이고, 번들의 GitHub 플러그인은 6019d518b2부터 Phantom-plugins가 빌드한 판이다 (d06d0012af, PR #2).

### 빌드·배포·서명
- 빌드의 플러그인 검증이 받아들이는 벤더 목록(`JetBrainsProductProperties.kt`의 `ALLOWED_PLUGIN_VENDORS`, `:55-61`)에 `Phantom`을 더했다. 목록에 없는 벤더의 플러그인은 `validatePlugin`(`:80-96`)이 오류로 보고하는데, 벤더를 Phantom으로 바꾼 Phantom Kotlin(8d27b3dfd8)이 여기서 걸렸다(커밋 제목) (71ff75bf3d).
- `phantom-build.sh`가 추적 파일 `IdeaApplicationInfo.xml`을 더는 고쳐 쓰지 않는다. 표시 버전(빌드 날짜의 YY와 MM.DD)을 macOS와 Windows `installers.cmd` 실행에 `-Dphantom.version.major`, `-Dphantom.version.minor`로 넘기고, `ApplicationInfoPropertiesImpl`이 XML보다 먼저 읽어 `product-info.json`, Info.plist, Windows 설치 파일, 패키지에 넣는 `<version>`에 반영한다. 추적 XML에는 자리표시자 `0` / `0.0`이 남아서 소스에서 IDE를 실행하거나 `installers.cmd`를 직접 실행하면 버전 0.0으로 보인다. 빌드 뒤 작업 트리가 더러워지던 것과 릴리스 태그 커밋의 버전이 릴리스 버전과 다를 수 있던 것이 이 변경으로 사라진다. `ApplicationInfoPropertiesTest`에 테스트 2개를 더했다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). PR #2 병합(31296ada70)으로 main에 들어오면서 68ae9c8c86이 쓴 `26` / `11.13`도 이 자리표시자로 바뀌었다 (c844a9f18e, PR #2).
- `phantom-sign.sh`가 "Phantom Local" 인증서를 찾지 못하면 이유를 stderr에 쓰고 종료 코드 1로 실패한다(전에는 `exit 0`). `PHANTOM_SIGN_OPTIONAL=1`이면 경고만 쓰고 0으로 끝나며, `phantom-install.sh`가 이 값을 켜서 인증서 없는 머신에서도 설치는 ad-hoc 서명으로 끝난다. 헤더 주석이 login 키체인이라고 잘못 적던 것을 전용 키체인 `phantom-signing.keychain-db`로 고쳤다. (f74262e02d, PR #4)
- `phantom-install.sh`, `phantom-sign.sh`, `phantom-bench.sh`의 앱·설정·픽스처 경로를 환경변수로 바꿀 수 있다: `PHANTOM_APP`(세 스크립트, `phantom-sign.sh`는 인자가 먼저), `PHANTOM_CONFIG_DIR`, `PHANTOM_BENCH_FIXTURE`(벤치, `--fixture`가 먼저). 기본값은 전과 같다. (f74262e02d, PR #4)
- 로컬 서명 인증서를 저장소 스크립트로 만든다. `./phantom-sign.sh --setup`이 macOS 기본 도구(`/usr/bin/openssl`, `security`)만으로 전용 키체인, 비밀번호 파일, 자체 서명 코드 서명 인증서 "Phantom Local"(Apple 인증서 아님, 10년)을 대화상자 없이 만들고, 이미 있으면 아무것도 바꾸지 않는다. `phantom-install.sh`는 인증서가 없으면 서명 전에 이를 실행한다(`PHANTOM_SIGN_SETUP=0`이면 건너뜀, 실패하면 전처럼 ad-hoc 서명). 서명은 `codesign --keychain`으로 전용 키체인을 가리켜 키체인 검색 목록과 신뢰 설정을 바꾸지 않는다. 기존 키체인·인증서로 서명한 결과(designated requirement)는 전과 같다 (f74262e02d, PR #4).
- `phantom-sign.sh --setup`이 가져오기와 키 접근 목록 설정 사이에서 멈춘 설치(codesign이 쓸 수 없는 인증서가 남고 다시 실행하면 할 일이 없다고 하던 것)를 다시 실행할 때 접근 목록을 다시 건다. 자동 잠금 설정은 이번 실행이 만든 키체인에만 걸고, .p12 비밀번호를 환경변수로 내보내지 않으며, 임시 키 폴더를 INT, TERM, HUP에서도 지운다 (f74262e02d, PR #4).
- `phantom-install.sh`가 `PHANTOM_APP`을 지우고 바꾸므로 `.app`으로 끝나는 절대 경로가 아니면 거부한다 (f74262e02d, PR #4).
- `phantom-install.sh`가 실행 중인 Phantom을 끌 때 번들 ID(`tell application id "dev.phantom.idea"`)가 아니라 `PHANTOM_APP` 경로에서 실행 중인 프로세스(`pgrep -f "$APP/Contents/MacOS/idea"`)의 프로세스 ID로 종료를 요청한다(`osascript -l JavaScript`의 `NSRunningApplication.terminate`, Quit 메뉴와 같은 Apple 이벤트라 IDE가 상태를 저장한다). 전에는 두 복사본이 떠 있으면 다른 쪽이 꺼지고 교체할 쪽은 180초 대기 끝에 실패할 수 있었다. 기다리기와 재시작(`open "$APP"`)은 전처럼 그 경로 기준이다. 같은 번들 ID의 스테이 오픈 애플릿 두 개로 확인했고 실제 Phantom 두 개로는 실행하지 않았다 (d421f71ac4, PR #5).

### 벤치마크
- `phantom-bench.sh`가 설치되지 않은 앱 복사본(예: 풀어 둔 DMG)을 `PHANTOM_BENCH_APP`으로 잴 수 있고(`PHANTOM_APP`보다 먼저, `:21-23`), `PHANTOM_BENCH_PLUGINS`에 공백으로 나열한 플러그인 zip을 실행마다 설정 복사본의 `plugins/`에 푼다(`:89`). 릴리스가 번들하지 않는 Kotlin을 넣고 재려는 것이다 (1d49734b5c).
- 정적 게이트 `compare`가 baseline 앱의 빌드 날짜(빌드 번호 `262.<YYMMDD>`)보다 번들 목록 `IdeaCommunityProperties.kt`의 마지막 커밋이 늦으면 stderr에 `STALE BASELINE`을 쓰고, 회귀가 없어도 종료 코드 2로 끝난다. 지금의 `static-baseline.json`(26.10.27)은 이 검사에 걸린다. 빌드된 앱이 있어야 다시 만들 수 있어 파일은 그대로 두고, 재생성 명령을 `docs/phantom/bench/README.md`에 적었다 (f74262e02d, PR #4).
- baseline 앱의 빌드 번호에 날짜가 없으면(`262.SNAPSHOT` 등) 낡았다고 판정하지 않고 stderr에 안내만 쓰고 나이 검사를 건너뛴다(git 기록이 없을 때와 같다) (f74262e02d, PR #4).
- 픽스처 복사본 `out/phantom-bench/work/fixture`를 어디서 복사했는지 `out/phantom-bench/work/fixture.source`에 적는다. 같은 원본이면 복사본을 그대로 다시 쓰고, 다른 원본이거나 기록이 없으면 복사본을 바꾸며 그 위에서 예열한 `work/system-*`도 지운다. 전에는 다른 `--fixture`나 `PHANTOM_BENCH_FIXTURE`가 조용히 무시됐다. 상대 경로 픽스처는 저장소 루트가 아니라 스크립트를 부른 폴더 기준이고, 없는 픽스처 폴더는 복사 전에 실패한다 (d421f71ac4, PR #5).
- 얕은(shallow) git 클론에서는 정적 baseline의 나이 검사를 건너뛰고 stderr에 안내(`git fetch --unshallow`)를 쓴다. 얕은 클론에서는 가장 오래 받은 커밋이 번들 목록의 마지막 변경으로 보여 모든 baseline이 `STALE`(종료 코드 2)로 나왔다 (d421f71ac4, PR #5).

### 문서
- 위 네 커밋(4ca69e1a95, c844a9f18e, d06d0012af, 9c32c01f49)에 맞춰 `README.md`, `README.ko.md`, `docs/phantom/`의 문서를 고쳤다. 네 변경이 빌드·테스트 전이라는 점을 문서에 함께 적었다 (ded15d8d28, PR #2).
- 문서가 기준으로 삼던 be60914099 뒤 main에 들어온 네 커밋(9229667cc7, a199380d7d, 60ea243e02, 04dba66c3a)을 `README.md`, `README.ko.md`, `AGENTS.md`, `docs/phantom/`의 현행 문서에 반영했다. 번들 구성(빼는 업스트림 모듈 21개, Phantom-plugins에서 빌드해 넣는 8개), Phantom-plugins 체크아웃이 필요한 빌드 절차와 릴리스 워크플로, 플러그인 별칭 대체(FORK-DELTA 3.12절), 밀린 `경로:줄`, 포크 델타 숫자(d2faebf601..ded15d8d28)를 고치고 현행 문서의 기준 커밋을 ded15d8d28로 바꿨다. 숫자는 그때 값이고 지금은 아래 항목의 값이다 (f943ad3550, PR #2).
- 없는 `./build/jpsModelToBazel.cmd`를 가리키던 안내를 이 리포의 유일한 생성기 `./build/jpsModelToBazelCommunityOnly.cmd`로 고쳤다(AGENTS.md와 같은 문구, 이 포크에서 도는지는 미확인). 대상: `.ai/guide.md`, `.ai/tool-permissions.json`, `.agents/skills`와 `.claude/skills`의 module-dependencies, pseudo-kmp, testing-internals, module-set-pluginization, extract-module, `plugins/maven/.ai/index.md`, `build/libraries-dashboard`. Kotlin `projectModelUpdater.kt`는 ultimate 루트가 있을 때만 그 경로를 부르므로 그대로 두었다. (f74262e02d, PR #4)
- 표식 주석 `// phantom-idea:`(XML은 `<!-- phantom-idea: -->`)이 없던 포크 수정 덩어리 전부에 주석을 달았다. 업스트림 파일 77개와 포크가 더한 `FoojayJdkList.kt`, 모두 78개 파일에 주석 137줄을 더했고 지운 줄은 없다(동작 변화 없음). 이로써 주석을 달 수 있는 수정 업스트림 파일 101개 모두 수정 덩어리마다 표식이 있다(그중 21개 파일의 33개 덩어리는 옛 `// Phantom:` 형식만). 고정 카테고리 중 맞는 것이 없어 문서에 적었다 (d421f71ac4, PR #5).
- PR #2 뒤 main에 들어온 소유자 커밋 7개(9235351694, ac9ddb534c, 6019d518b2, b238d55e24, 68ae9c8c86, bedeb19cae, f6adc2ce77)와 이 브랜치의 수정(PR #4(f74262e02d), PR #5(d421f71ac4))을 `README.md`, `README.ko.md`, `AGENTS.md`, `docs/phantom/`의 현행 문서와 `docs/phantom/bench/README.md`에 반영했다. 번들 구성(`.removing` 31개: 그냥 빼는 14개, Phantom-plugins 판으로 바꾸는 16개, Kotlin), Phantom-plugins에서 빌드해 넣는 17개, Kotlin zip 게시, Phantom-plugins로 옮긴 플러그인의 이 리포 소스를 고쳐도 배포 앱이 바뀌는지 확인하지 못했다는 점, 표식 통계, 스크립트 `경로:줄`, 포크 델타 숫자(d2faebf601부터 PR #5(d421f71ac4)의 표식 커밋까지)를 고치고 현행 문서의 기준 커밋을 그 표식 커밋으로 바꿨다. 이 CHANGELOG에서는 v26.11.13.1 태그(f6adc2ce77)에 들어간 미배포 항목을 그 버전 절로 옮기고, 빠져 있던 해시를 채웠다 (d421f71ac4, PR #5).
- 알려진 문제 13, 14, 15, 24번(DEVELOPMENT 5절)의 "추정"이던 호출 시점을 호출자 사슬(`경로:줄`)로 바꿔 적었다. remoteDev 다운로드 주소(14번)는 이 리포에 다운로더를 부르는 곳이 없어 이 앱만으로는 나가지 않으므로 게이트 없이 두었다고 적었다(번들 밖 Code With Me·Gateway 플러그인 코드는 확인하지 못함). DEVELOPMENT 2절 (d)에 "자동이면 게이트, 사용자가 고른 다운로드면 그대로 두고 근거를 적는다"는 판단 순서를 더했다. README.md, README.ko.md의 "여전히 네트워크에 접속하는 것"도 맞췄다 (cf39628de4, PR #7).
- 옛 표식 `// Phantom:`을 `phantom-idea:`로 통일했다(`//` 20줄, KDoc `*` 3줄, XML `<!--` 1줄, `#` 형식은 없었다). 22개 파일의 표식 주석 24줄만 바꿨고 코드는 건드리지 않았다. 이전 항목(d421f71ac4, PR #5)과 DEVELOPMENT 5절 16번이 적은 "21개 파일의 33개 덩어리"와 숫자가 달라 `Phantom:` 주석을 직접 세어 다시 적었고, 차이의 이유는 확인하지 못했다. 코드가 아니라 주석이라 빌드와 테스트는 하지 않았다 (cf39628de4, PR #7).
- 알려진 문제 21, 22, 23번(DEVELOPMENT 5절)을 문서로 정리했다. 21번: phantom-icons는 모순이 아니라 ID가 둘임을 v26.11.13.1 절의 `phantom-icons` 항목과 FORK-DELTA 5.2절에 적었다. 22번: 현재 `README.md`와 `README.ko.md`에 어긋나는 곳이 없어 옛 커밋(be60914099) 기록으로 닫았다. 23번: 로드맵 머리의 상태 메모에서 확인하지 못한 "같은 플러그인 ID"를 빼고, 옮긴 커밋과 로드맵 밖에서 옮긴 플러그인(workspaces, tabs, icons)을 더했다. 문서만 바꿨다 (cf39628de4, PR #7).
- PR 없이 main에 들어온 일곱 커밋(2cff0f911e, 1d49734b5c, 8d27b3dfd8, 0748db2720, 71ff75bf3d, 17cce24354, 588cd923ce)을 `README.md`, `README.ko.md`, `AGENTS.md`, `docs/phantom/`의 현행 문서와 `docs/phantom/bench/README.md`에 반영하고 위 항목들을 더했다. 번들 구성(`.removing` 39개: 그냥 빼는 14개, Phantom-plugins 판으로 바꾸는 24개, Kotlin), Phantom-plugins에서 빌드해 넣는 25개(`PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`가 같음), Phantom Kotlin 이름·벤더, CDS와 이전 아카이브 삭제, 유휴 메모리 정리 5분과 힙 여유 20-40%, 시작 후 활동 3개, 벤치 환경변수, 표식이 없는 새 덩어리 2개, 일곱 커밋이 바꾼 파일을 가리키던 `경로:줄`을 고치고 현행 문서의 기준 커밋을 51a03a83f7로 바꿨다 (PR #8).
- 스쿼시 머지로 main에 남지 않은 브랜치 커밋 해시 21개(PR #4의 7개, PR #5의 5개, PR #7의 6개, PR #3의 3개)를 이 CHANGELOG, `AGENTS.md`, `docs/phantom/`의 문서에서 그 PR의 스쿼시 커밋(f74262e02d, d421f71ac4, cf39628de4, 51a03a83f7)으로 바꿨다. 괄호의 `(a, b, PR #N)`은 `(스쿼시 해시, PR #N)`이 되고, 본문의 여러 해시는 `PR #N(스쿼시 해시)` 하나로 모았다. 측정 기준이던 PR #5의 표식 커밋은 해시 대신 "PR #5 브랜치의 표식 커밋"으로 적었다(숫자는 그 커밋에서 센 값이라 스쿼시 커밋으로 바꾸면 뜻이 달라진다). PR 소속은 로컬 브랜치 `fix/known-problems-batch2`·`-batch3`·`-batch4`, `fix/github-browser-login-hidden`에서 그 커밋을 담은 가장 아래 브랜치로 정했다. DEVELOPMENT 4절에 스쿼시 머지한 PR의 해시 규칙을 한 문장 더했다 (PR #8).
- 이 브랜치를 만든 뒤 main에 PR 없이 들어온 네 커밋(ce70b07310, d1a97edf7a, cd1fc57a5f, 5641b42107) 위로 브랜치를 다시 쌓으며 문서를 맞췄다. 이 브랜치가 고치거나 더한 줄에도 d1a97edf7a의 이름(제품 Phantom, 앱 `Phantom.app`, Windows 이름과 설치 폴더 `Phantom`)과 cd1fc57a5f의 실행 파일 이름 `phantom`을 썼고, 그대로 남은 번들 ID `dev.phantom.idea`, 설정 폴더 `PhantomIDEA`, 산출물 `phantom-idea-*`, 표식 `phantom-idea:`는 바꾸지 않았다. 두 커밋으로 밀린 `phantom-install.sh`의 `경로:줄`을 5641b42107에서 다시 열어 고치고, BUILD.md 5장에 옛 `/Applications/Phantom IDEA.app`을 바꾸고 지우는 동작, 실행 중인 앱을 찾는 방법(`pgrep -x phantom`·`pgrep -x idea` 뒤 명령줄 비교), `idea.vmoptions`·`idea.jdk` 복사를 적었다. FORK-DELTA에는 브랜딩 행의 커밋에 d1a97edf7a·cd1fc57a5f과 `baseFileName` 행을, 1.2절에 `LICENSE.txt`·`NOTICE.txt` 변경(ce70b07310)을 더했고, 표식이 없는 덩어리 목록(AGENTS.md, FORK-DELTA 2절, DEVELOPMENT 1절)에 cd1fc57a5f의 `baseFileName`을 더했다. 이름을 바꾸며 어긋난 조사(`Phantom는` 등 5곳)를 고쳤다 (PR #8).
- 소유자가 main의 히스토리를 다시 써서(작성자 이메일 변경) 문서 PR #1 뒤의 커밋이 모두 새 해시를 갖게 되었기에, 이 CHANGELOG, `README.md`, `README.ko.md`, `AGENTS.md`, `docs/phantom/`의 현행 문서와 `docs/phantom/bench/README.md`에 적힌 옛 해시 30개(508곳)를 새 해시로 바꿨다(위 항목들의 해시도 새 값이다). 그 뒤 main에 PR 없이 들어온 세 커밋(2f607127fd, 2d876caa58, 0a09032770)도 반영했다: 산출물 이름 `phantom-<YYMMDD>.<N>`과 Linux 루트 `phantom`, Maven을 번들 대신 Kotlin처럼 zip으로 게시하는 것, Images를 Phantom-plugins 판으로 번들하는 것(`.removing` 41개: 그냥 빼는 14개, Phantom-plugins 판으로 바꾸는 25개, Kotlin과 Maven. Phantom-plugins에서 빌드해 넣는 26개, `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`가 같음), 이름 Kotlin Support·Maven Support, 표식 목록(`.idea/modules.xml`과 `GithubBundle.properties`는 표식이 생겼고 `versionCheckerConfig`의 Maven 거르기 줄은 표식이 없음), `NOTICE.txt`의 GPL 소스 고지, 그리고 세 커밋이 바꾼 `IdeaCommunityProperties.kt`, `phantom-build.sh`, `phantom-release.sh`와 이 브랜치가 줄을 더한 `README.md`, `.idea/modules.xml`을 가리키던 `경로:줄`. 위 PR #8 항목들의 숫자(39개, 25개 등)는 그때 값이다 (PR #8).

### 버그 수정

- GitHub 브라우저 로그인 client id가 비어 있는 동안 "Log In via GitHub" 진입점(계정 추가 메뉴, 클론 대화상자, Pull Request 선택기)을 숨기고 토큰 로그인만 보여 준다. 설정되어 있으면 이전과 같다. 토큰 로그인 요청은 확인 창 없이 토큰 입력 창을 바로 연다. 번들의 GitHub 플러그인은 Phantom-plugins 사본이라 배포 앱에는 그쪽 github 0.1.1로 반영된다 (51a03a83f7, PR #3).

## v26.11.13.1 (2026-11-13)

### 브랜딩·버전
- 버전을 26.10.27로 올렸다 (`IdeaApplicationInfo.xml`) (bf2b90f700).
- 버전을 26.11.13으로 올렸다 (`IdeaApplicationInfo.xml`). 이 값은 태그 커밋 f6adc2ce77에는 있지만 지금 HEAD에는 없다. 뒤에 병합된 PR #2(31296ada70)가 c844a9f18e의 자리표시자 `0` / `0.0`으로 되돌렸고, 그 뒤로 표시 버전은 빌드 날짜에서 `-Dphantom.version.*`로 들어간다 (68ae9c8c86).

### 번들 구성
- 개발자 도구 런타임(`intellij.dev`), Eclipse 가져오기, JavaFX 커뮤니티, 첫 실행 가져오기 마법사, 신규 사용자 온보딩 투어를 번들 목록에서 제외했다. 소스는 유지한다 (770810693c).
- 첫 실행 설정 가져오기 마법사를 번들에 되돌렸다. 온보딩 투어만 계속 제외한다 (a3e497f72d).
- `phantom-workspaces`를 번들에서 빼고 Phantom-plugins 저장소의 workspaces 플러그인으로 옮겼다. 플러그인 id `dev.phantom.workspaces`와 Advanced Setting id는 그대로다 (8983487a29).
- `phantom-tabs`를 번들에서 빼고 Phantom-plugins 저장소의 tabs 플러그인으로 옮겼다. 공개 플랫폼 API만 쓰도록 바꿔서, 이 플러그인을 위해 넣었던 `TabInfo.labelLeadingComponent` / `isLabelCollapsed` 등 플랫폼 변경을 upstream 상태로 되돌렸다 (b68d97332e).
- `phantom-icons`를 번들에서 빼고 Phantom-plugins 저장소의 Phantom Icon Pack으로 옮겼다. 같이 번들하면 같은 아이콘을 두 번 패치하기 때문이다. ID가 둘이다: 이 리포의 옛 플러그인은 `dev.phantom.icons`(삭제 직전 `plugins/phantom-icons/resources/META-INF/plugin.xml`, `git show 1570329708^:plugins/phantom-icons/resources/META-INF/plugin.xml`로 확인)이고, Phantom-plugins의 Icon Pack은 `dev.phantom.iconpack`이다. 후자는 이 커밋 메시지("Phantom Icon Pack, dev.phantom.iconpack, icons 0.3.0")에만 근거하고 Phantom-plugins 저장소는 읽지 못해 확인하지 못했다. 둘은 서로 다른 ID라 옛 플러그인의 설정이 이어진다고 보면 안 된다 (1570329708).
- Bytecode Viewer, WebP, Modal Commit Interface를 번들 목록에서 제외하고 Phantom-plugins의 `dev.phantom.bytecode`, `dev.phantom.webp`, `dev.phantom.modalcommit`으로 옮겼다. 소스는 upstream 병합을 위해 유지한다 (e2a4ffa4ee).
- GitLab, Eclipse/Visual Studio/NetBeans 키맵, `configurationScript`를 번들에서 제외했다. Android Gradle DSL 모델은 Gradle 버전 카탈로그 대체 경로와 Dependency Analyzer가 쓰므로 유지한다 (7c43a36532).
- Toml, YAML, Shell Script, Lombok, Decompiler, Coverage, TextMate, Markdown을 IntelliJ 원본 대신 Phantom-plugins가 만든 판(`dev.phantom.*` ID, IntelliJ ID는 별칭으로, IntelliJ 콘텐츠 모듈 이름은 그대로 유지)으로 번들한다. 둘을 함께 넣으면 같은 ID와 모듈을 두 번 차지하기 때문이다. 원본 7개는 `.removing`으로 빼고, 따로 넣던 `intellij.lombok`은 `PHANTOM_BUNDLED_PLUGINS`에서 지웠으며, 새 목록 `PHANTOM_PLUGINS_BUNDLED`의 디렉터리를 `getAdditionalPluginPaths()`로 넣는다. `phantom-build.sh`가 Phantom-plugins 체크아웃(저장소 옆, 또는 `PHANTOM_PLUGINS_REPO`)에서 이 플러그인들을 IntelliJ Community 대상으로(`-PplatformFromRepository=true`) 빌드해 `out/phantom-plugins`에 풀고, 체크아웃이나 플러그인 하나라도 없으면 빌드가 실패한다 (9229667cc7).
- Marketplace 목록이 JetBrains가 게시한 플러그인(조직 이름이 `JetBrains`로 시작)을 보여 주지 않는다. Phantom이 유지하는 것은 번들이나 Phantom 플러그인 저장소에 자체 판이 있기 때문이다. 소스 실행, internal 모드, `-Dphantom.marketplace.showAll=true`이면 전처럼 모두 보인다. JetBrains Mermaid(`intellij.mermaid`)를 번들에서 빼고 Phantom-plugins의 Phantom Mermaid(디렉터리 `mermaid`, Phantom 자체 구현 `com.phantom.mermaid`, IntelliJ ID 별칭 없음)를 넣는다 (9235351694).
- Kotlin 플러그인을 번들하지 않는다(381 MB, Java만 쓰는 프로젝트는 쓰지 않음). `.removing(KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE)`로 빼고 `pluginModulesToPublish`에 넣어 빌드가 zip으로 만든다. `phantom-release.sh`가 릴리스마다 그 zip을 Phantom plugins 저장소(이 저장소 옆 Phantom-plugins 체크아웃, 또는 `PHANTOM_PLUGINS_REPO`)의 릴리스 `kotlin-262.<YYMMDD>`로 올리고 `.phantom/marketplace.json`의 `org.jetbrains.kotlin` 항목이 그것을 가리키게 한다(그 플랫폼 빌드부터). 그 저장소는 Kotlin(`kotlin-stdlib`)이나 Gradle Kotlin DSL 스크립트를 쓰는 프로젝트에 Kotlin을 제안한다. 같은 커밋에서 릴리스 스크립트가 `origin`이 SSH 호스트 별칭(`git@<별칭>:`)일 때도 저장소 이름을 바르게 뽑게 했다 (ac9ddb534c).
- Copyright, EditorConfig, HTML Tools, TestNG, Java I18n, Grazie, Terminal, GitHub도 IntelliJ 원본(`intellij.copyright`, `intellij.editorconfig.plugin`, `intellij.html.tools`, `intellij.testng`, `intellij.java.i18n`, `intellij.grazie`, `intellij.terminal`, `intellij.vcs.github`) 대신 Phantom-plugins가 만든 판(Phantom Copyright 등, `dev.phantom.*` ID, IntelliJ ID는 별칭, IntelliJ 콘텐츠 모듈 이름 유지)으로 번들한다. `PHANTOM_PLUGINS_BUNDLED`와 `phantom-build.sh`의 `bundled_from_plugins`가 17개가 됐다. 이 리포의 `plugins/github`, `plugins/grazie`, `plugins/terminal` 등에 있는 포크 패치가 그 판에 들어 있는지는 이 리포에서 확인할 수 없다 (6019d518b2).

### JetBrains 서버 통신
- Toolbox App 연동(REST 서비스, 업데이트 알림, 종료 핸들러, 업데이트 설정의 Toolbox 홍보, Try Ultimate via Toolbox, Copy Toolbox Reference 액션)을 코드에서 삭제했다 (43b28af952).
- 설문(액션 기반, 알림 기반)이 JetBrains로 보내던 것을 `phantom.jetbrains.services`로 막고 CSAT 설문을 기본 꺼짐으로 바꿨다. 이 커밋은 Help | Submit Feedback을 플래그 뒤로 옮기지 않았다. 이 커밋은 `IntelliJIdeaExternalResourceUrls`의 `useInIdeGeneralFeedback`·`useInIdeEvaluationFeedback` 재정의를 지웠고, `feedbackReporter`를 null로 만든 것은 fdd9682290이다. `GeneralFeedbackSubmit`에도 게이트를 걸지 않았다(4ca69e1a95가 이후에 건다). 번들 Kotlin이 `.kt` 편집 때 plugins.jetbrains.com을 호출하던 것을 없앴고, Ultimate 홍보 모듈 `intellij.java.promo`, `intellij.vcs.github.ultimatePromo`, `intellij.kotlin.onboarding.promoter`와 YAML의 "Try Ultimate" 배너를 삭제했다 (3676457781).
- Help 메뉴의 JetBrains 링크(버그 신고, 지원, 피드백, YouTube, 키맵 PDF, What's New, 다운로드 페이지)를 제거했다. 시작 오류 대화상자의 "Report"는 `-Dphantom.jetbrains.services=true`일 때만 보인다. 터미널 AI 에이전트에서 Junie를 뺐고(Claude Code와 Codex는 유지), 내장 서버의 `/api/logs`는 같은 플래그 없이는 모든 요청을 거부한다 (fdd9682290).
- Grazie Cloud, JetBrains Account 로그인, 완성 통계 전송의 JetBrains 호출은 `-Dphantom.jetbrains.services=true`일 때만 켠다. Package Search 클라이언트는 플래그 없이 항상 꺼져 있고 모든 요청이 IOException으로 실패한다. ShellCheck 0.10.0은 GitHub 릴리스에서 받는다. Grazie 언어 파일 다운로드는 JetBrains에 남아 있다 (98673d9e9a).
- Download JDK 목록을 JetBrains 피드 대신 foojay Disco API에서 만든다(Temurin, Zulu, Microsoft, Semeru, GraalVM CE). JetBrains 피드는 `-Dphantom.jetbrains.services=true`일 때만 쓴다. registry `jdk.downloader.url`은 JetBrains 피드를 되살리지 않고, 값이 있으면 그 URL로 목록을 대체한다. JBR와 Git 설치 피드는 JetBrains에 남아 있다 (be60914099).

### 성능·인덱싱
- trigram 인덱스에서 라이브러리 안의 모든 텍스트 파일(Kotlin 소스, XML, properties 등)을 제외했다. 작성자의 프로젝트에서 trigram 인덱스가 369 MB에서 84 MB로, 전체 인덱스가 1.2 GB에서 916 MB로 줄었다. 같은 커밋에서 `ExternalUpdateManager.TOOLBOX`를 다시 두어 Android 플러그인 컴파일 실패를 고쳤다 (0a5ca64795).
- 인덱싱 지문(fingerprint)에 인덱스, 스텁, 파일 타입, 파서 등 인덱싱에 영향을 주는 플러그인만 반영한다. 플러그인을 갱신하면 다음 프로젝트 열기 때 전체 파일을 다시 검사하던 것(345k 파일, 약 2.3 s, CPU 약 20 s)이 사라진다 (6613a4ed82).

### 기능(Phantom 플러그인)
- 에디터 탭 그룹 플러그인 `phantom-tabs`를 번들로 추가하고, 이를 위해 `TabInfo.labelLeadingComponent`와 `isLabelCollapsed`를 플랫폼에 넣었다. 리뷰 수정과 테스트 안정화가 이어졌고, 이후 Phantom-plugins로 옮겨졌다 (f5778de337, 49b1b9e145, b02cc88cfe, 54a048eb13).
- 열린 모든 프로젝트를 한 창에 묶는 `phantom-workspaces`를 번들로 추가했다. 처음에는 macOS 기본 창 탭을 병합했고, 이후 프로젝트 탭 스트립과 겹쳐 쌓은 프로젝트 창으로 바꿨다. 리뷰 수정과 병합 커밋이 포함되고, 이후 Phantom-plugins로 옮겨졌다 (2a4c13ce66, d13dfa273f, aab0c0bb57, 4ca7ef3e8e).

### 빌드·배포·서명
- 커버리지 런타임 jar의 클래스 버전 규칙(`CE_CLASS_VERSIONS`, `BaseIdeaProperties.kt`)의 경로를 `plugins/java-coverage/lib/java-coverage-rt.jar`에서 `plugins/coverage/lib/java-coverage-rt.jar`(Phantom Code Coverage가 Phantom-plugins에서 번들되는 경로)로 바꿨다 (a199380d7d).
- 릴리스 워크플로가 올바른 YAML이 되게 고쳤다(`notes` 입력 설명의 따옴표 없는 `: ` 때문에 GitHub가 거부했다). `phantom-build.sh`가 저장소 옆의 Phantom-plugins가 필요하므로 러너가 자기 SSH 키로 그것을 클론한다(작업 토큰은 읽을 수 없다). 체크아웃은 실행 사이에 남고 매번 `origin/main`으로 맞춘다 (60ea243e02).
- 설치 파일 빌드가 플러그인을 zip으로도 만든다. `OpenSourceCommunityInstallersBuildTarget.kt`의 항상 건너뛰는 단계 목록에서 `ARCHIVE_PLUGINS`를 뺐다. 번들하지 않는 Kotlin이 `out/phantom-idea/artifacts/<제품 코드>-plugins/`에 zip으로 나오게 하려는 것이다 (b238d55e24).
- `phantom-release.sh`의 Kotlin zip 확인이 `pipefail`에서도 맞게 동작하게 고쳤다. `unzip -l | grep -q` 대신 `unzip -Z1 <zip> Kotlin/lib/kotlin-plugin.jar`로 항목을 확인하고, 버전은 jar를 임시 파일로 꺼내 `plugin.xml`에서 읽는다 (bedeb19cae).
- `phantom-release.sh`가 Kotlin zip을 찾을 때 `*.blockmap.zip`을 빼고 `Kotlin-*.zip`만 고른다 (f6adc2ce77).

### 버그 수정
- 다른 플러그인의 ID를 별칭으로 가진 플러그인(예: `dev.phantom.toml`, 별칭 `org.toml.lang`)과 그 원본이 함께 설치되면 ID 충돌로 둘 다 제외되던 것을, 대체하는 쪽만 로드하고 대체된 쪽은 "대체됨"으로 표시하며 그 의존 플러그인은 대체한 쪽으로 해석하도록 고쳤다. 대체한 쪽을 끄면 원본이 다시 로드된다. essential 플러그인은 대체되지 않는다. 새 비적재 사유 `PluginIsReplacedByAnotherPlugin`("Replaced by …")을 더했고, `PluginInitializationSelectPluginsToLoadTest`에서 둘 다 제외를 기대하던 테스트 1개를 바꾸고 3개를 더했다 (04dba66c3a).
- 동적 플러그인 로드 중 에디터 색상 스킴을 다시 읽는 동안 `Theme Phantom Dark refers to unknown color scheme` SEVERE 로그가 나던 것을, 리로드 전 스킴의 스냅샷으로 대체해 고쳤다 (d86195e55c).
- JCEF를 먼저 열 때(시작 시 복원된 Markdown 미리보기) `JBCefApp$Holder <clinit> requests ProxyMigrationService` IDE 오류가 나던 것을, `HttpConfigurable`을 Holder 초기화 전에 만들도록 고쳤다. upstream 코드의 문제다 (e1204fc098).

### 벤치마크
- `phantom-bench.sh`와 `phantom-bench-report.py`를 추가했다. 설치된 앱을 고정 프로젝트 복사본에서 격리 실행하고 플랫폼 자체 보고서로 지표를 뽑는다 (38efaf66bf).
- N회 실행의 중앙값/최소/최대 요약과 baseline 대비 회귀 검사(`compare`)를 추가했다. 시간은 10%, 메모리와 클래스 수는 5%, 멈춤 1회 이상 나빠지면 실패한다 (6767ffc6e1).
- 앱을 띄우지 않고 번들 크기, jar 수, 번들 플러그인, 사전 로드 서비스, 시작 활동 등록 수를 세는 `static` 게이트를 추가했다. baseline은 설치된 26.10.27이다 (4b3ffd9498).

### 문서
- 탭 그룹 설계와 구현 계획을 추가했다 (a8ad9cfcd9).
- 모든 프로젝트를 한 창에 두는 Workspaces 설계를 추가했다 (b9d9990ad8).
- 개발 문서 체계를 만들었다. `docs/phantom/`에 문서 색인(`README.md`), `ARCHITECTURE.md`, `FORK-DELTA.md`, `BUILD.md`, `DEVELOPMENT.md`를 더하고 이 `CHANGELOG.md`를 만들었다. `README.md`를 개편하고 한국어판 `README.ko.md`를 더했으며, `AGENTS.md`의 이 포크에 없는 경로를 고치고 Phantom 규칙을 넣었다. 개편 전 README의 인덱싱 절은 `docs/phantom/2026-10-29-indexing-changes.md`로 옮겼다 (af12411f8d, PR #1).

## v26.10.25.1 (2026-10-25)

### 성능·인덱싱
- OpenTelemetry 계측 JSON(1분마다)과 지표 CSV(4일에 약 116 MB), 스캔/인덱싱마다 쓰던 `log/indexing-diagnostic` JSON 보고서를 기본으로 끄고 Jackson Kotlin 모듈 때문에 kotlin-reflect가 로드되던 것도 없앴다. `idea.diagnostic.opentelemetry.*` 경로나 `-Dintellij.indexes.diagnostics.dump=true`로 되살린다 (53aa9e1ec7).
- non-blocking read action의 OpenTelemetry 모니터링을 기본으로 끄고(`-Didea.non-blocking-action.enable-monitoring=true`로 복구) `performanceTesting`을 번들에서 제외했다 (3718f68ff4).
- 1분마다 다시 쓰던 storage 진단 덤프 JSON을 기본으로 껐다. `-Dintellij.storage.diagnostics.dump=true`로 복구한다 (7568bf108c).
- 기본 VM 옵션에서 쓰지 않는 힙을 OS에 반환하도록 했다(주기적 G1 사이클, 더 낮은 힙 여유 비율). 시작 5.5분 뒤 측정에서 메모리 사용량이 979-1019 MB(이전 1078-1204 MB), committed heap이 320-364 MB(이전 415-548 MB)였다 (c7ea71c59e).

### 기능(Phantom 플러그인)
- phantom-icons 테스트가 실제 아이콘 로딩과 icon manager를 쓰도록 고쳐 실패하던 테스트 3개를 바로잡았다 (b5ca754c6a).

## v26.10.21.1 (2026-10-21)

### JetBrains 서버 통신
- GitHub 로그인이 account.jetbrains.com을 거치지 않는다. `gh auth token`을 재사용하고, `-Dphantom.github.oauth.client.id`가 주어지면 OAuth device flow를 쓴다. 토큰 로그인에 "From gh CLI" 버튼이 생겼다 (d7c795f347).
- `-Dphantom.jetbrains.services=true`가 아니면 brokenPlugins.json, Marketplace id 목록(전체 목록과 30초 뒤 갱신), 기능별 플러그인 추천, 플러그인 업데이트 확인의 JetBrains Marketplace 백엔드를 부르지 않는다. Marketplace 탭 탐색은 그대로 된다 (d7c795f347).
- 아래는 플래그와 상관없이 끊었다. IDE 업데이트 피드(updates.xml) 기본 URL은 삭제했고 `-Didea.updates.url`로 직접 지정해야 확인한다. ML/FUS 설정 다운로드가 되던 `MLEventLoggerProvider.isLoggingAlwaysActive()`는 false로 고정했고 되살리는 설정은 없다. JDK 업데이트 확인은 registry `jdk.updater` 기본 false, Gradle 호환성 피드는 `gradle.compatibility.update.interval` 기본 0, 시작 때 플러그인 추천은 `ide.show.plugin.suggestions.on.open=false`이며 이 셋은 registry에서 되돌린다. "Report to JetBrains" 오류 제출(`ITNReporter`) 등록은 삭제했다 (d7c795f347).
- 같은 커밋에서 변경/커밋 뷰의 삭제된 파일이 Kotlin 로고 등 파일 아이콘을 유지하도록 `phantom.icons.vcs`를 추가했다 (d7c795f347).

### 기능(Phantom 플러그인)
- phantom-icons가 Java 클래스를 종류별(class, abstract class, interface, enum, record, annotation, exception) Material 아이콘으로 표시한다. Java 플러그인이 있을 때만 로드되는 선택 모듈 `phantom.icons.java`로 구현했고 static/final 표시와 가시성은 유지한다. 그 뒤 아이콘을 생성 디렉터리 밖으로 옮기고 예외 아이콘을 바꾸는 리뷰 수정, 모듈 의존성 보강, 병합 커밋이 이어졌다 (8eb1050628, 18103ccd1a, a748f97745, 5e6de6045f).

### 빌드·배포·서명
- Windows x64 설치 프로그램(`.exe`)과 zip을 릴리스용 Mac에서 크로스 빌드한다. `PHANTOM_WINDOWS=0`이면 건너뛰고, 아이콘과 설치 마법사 이미지는 `compose.swift`가 `phantom-hood.png`에서 만들며, 서명하지 않는다. JetBrains 제거 설문 페이지는 뺐다 (d0eab60d1b).
- Windows 빌드가 실제로 설치 프로그램과 zip을 만들도록 고쳤다. 산출물은 `phantom-idea-<day>.<n>.exe`(NSIS 약 660 MB)와 `.win.zip`(약 900 MB)이다 (1191317a96).
- `phantom-install.sh --release [tag]`로 GitHub 릴리스의 DMG를 curl로 받아 설치한다. curl은 quarantine 플래그를 붙이지 않아 공증 없는 앱도 Gatekeeper에 막히지 않는다 (e299c45eef).

### 버그 수정
- Marketplace 캐시를 `.part` 파일로 받아 옮기도록 하고, 읽을 수 없는 `pluginsXMLIds.json`은 시작 실패 대신 버린다. 다운로드가 중간에 끊기면 파싱 오류가 ActionManager 초기화로 새어 나오던 문제다 (a61aa7bce3).

## v26.10.18.3 (2026-10-18)

### 기능(Phantom 플러그인)
- phantom-icons가 내비게이션 바에서도 Kotlin 파일에 Kotlin 로고를 표시한다 (f3f1b3e78d).

### 빌드·배포·서명
- `phantom-release`가 DMG 업로드가 끝난 뒤에만 릴리스를 공개하도록 바꿨다(먼저 draft로 만든다) (6c595bcb14).

## v26.10.18.2 (2026-10-18)

### 번들 구성
- HTTP Client를 코어에서 분리해 Phantom-plugins의 http-client 플러그인으로 옮겼다. 플러그인 id `dev.phantom.http`가 같아 설정을 유지하고, Endpoints 창은 여전히 `generated-requests.http`를 쓰며 플러그인이 없으면 일반 텍스트로 연다. 번들 목록, 루트 모듈, 빌드 파일 목록에서 빼는 후반 작업이 별도 커밋이다 (cab0fa60d7, ac3007c625).

### 기능(Phantom 플러그인)
- HTTP Client에 `import`와 `run` 줄(`run #name`, `run ./file.http`, `(@var=value)` 덮어쓰기), 가져온 변수, 중복/미해결 import 검사, 이동과 완성, run 줄의 실행 gutter를 추가했다 (874bbb9cc4).

### 빌드·배포·서명
- `phantom-build`가 빌드 뒤 maven-artifacts, jps, `dist.*`도 지운다 (2ad4a70448).

## v26.10.18.1 (2026-10-18)

### 브랜딩·버전
- 제품명을 Phantom IDEA로 바꿨다. 회사명 Phantom, macOS 번들 `Phantom IDEA.app`, 번들 id `dev.phantom.idea`, URL scheme `phantom`, 설정 selector `PhantomIDEA<ver>`, 산출물 `phantom-idea-<build>`, 출력 디렉터리 `out/phantom-idea`. 동시에 Ultimate 홍보 모듈(`intellij.java.promo`, `intellij.vcs.github.ultimatePromo`, `intellij.kotlin.onboarding.promoter`)과 TechInsightsLab 환영 화면 홍보를 제거하고, Android, Compose IDE, declarative Android Gradle을 번들에서 뺐으며 별도 플러그인 저장소 빌드(`buildAllCompatiblePlugins`)를 껐다. 비 JetBrains 벤더에게 주어지는 불변 목록에 `removeIf`를 호출해 `ConsentConfigurable`이 로드되지 않던 오류(`AppUIUtil.loadConsentsForEditing`)도 고쳤다 (1e0d4353f7).
- 날짜 기반 버전을 도입했다. 빌드 번호 `262.<YYMMDD>`(플러그인 호환을 위해 브랜치 262 유지), 표시 버전 `YY.MM.DD`, 설정 폴더 `PhantomIDEA`이고, `phantom-build.sh`가 날짜를 찍고 `installers.cmd`를 실행한다. 새 README와 기능 비교표 `docs/phantom/2026-10-16-feature-matrix.md`가 같은 커밋이다 (fc05eddc04).
- IDE, Info.plist, 산출물 이름에 `PH-<YYMMDD>.<N>` 빌드 번호를 표시한다. 플랫폼 빌드 `262.<YYMMDD>`는 apiVersion과 번들 플러그인 since/until-build로 남겨 플랫폼 262용 플러그인이 계속 로드된다. 같은 커밋에서 aarch64 전용 빌드, `phantom-release.sh`와 릴리스 워크플로(작성자 Mac의 self-hosted runner), `tools/phantom-runner-setup.sh`를 추가하고 매일 실패하던 upstream IntelliJ IDEA / PyCharm / Qodana 워크플로를 삭제했다 (ae2a766191).
- DMG 배경의 JetBrains IDEs 마크를 Phantom IDEA로 바꿨다 (c8cb99fb6f).
- 앱 아이콘을 검은 유령(얼굴 중앙)으로 바꾸고 가져오기 마법사에도 표시한다 (5a12a1a666).
- 앱 아이콘을 후드를 쓴 유령 그림(`phantom-hood.png`)으로 바꿨다. `compose.swift`가 전체를 앱 아이콘으로, 후드 입구를 16/32 px로 자른다 (9ad40b0e37).

### 번들 구성
- 기본 레이아웃에서 Structure와 Services를 More tool windows로 옮기고 AI Assistant를 뺐으며, 환영 화면의 Learn 탭과 feature trainer를 번들에서 제외했다. 커뮤니티 Lombok 플러그인과 phantom-theme를 번들에 넣었다 (f736b95fd0).
- MCP 서버와 Agent Client Protocol 플러그인을 번들에서 제외했다. 번들된 것 중 이들에 의존하는 것이 없고 11 MB가 줄었다 (b561ac8fd5).
- `phantom-spring`과 `phantom-spring-boot`를 번들에서 빼고 Phantom-plugins 저장소로 옮겨 Spring 프로젝트에서 설치를 제안한다. 프로덕션 빌드는 Phantom이 설치할 수 없는 Marketplace 플러그인(상용 JetBrains IDE 전용, 라이선스 플러그인 없이 유료)을 목록에서 숨기며 `-Dphantom.marketplace.showAll=true`로 모두 본다. 플러그인 추천 풍선은 하나로 합쳐 Install all 액션을 둔다 (c83c67511e).
- `phantom-jpa`를 번들에서 빼 Phantom-plugins로 옮겼다. Endpoints 도구 창은 코어에 두어 다른 플러그인은 provider만 등록하고, New Project에 플러그인 설치 전에도 "Spring Boot"가 보이며, 플러그인 추천이 파일 이름(`detectFiles`, 예 `Chart.yaml`)도 본다. `phantom-install.sh`는 고정 빌드 번호 대신 가장 새 DMG를 쓴다 (53c0592476).
- `phantom-db`를 번들에서 빼 Phantom-plugins로 옮겼다. 프로젝트를 열 때와 라이브러리가 바뀔 때, GitHub 플러그인 저장소의 detect 목록과 비교해 맞는 플러그인 설치를 한 번 제안한다 (de02add11d).

### 성능·인덱싱
- 아무것도 추가/제거하지 않는 단순 파일 타입 이벤트에서 전체 재스캔을 건너뛴다. Atom Material Icons가 시작마다 호출하는 `FileTypeManagerEx.fireFileTypesChanged()`가 시작 약 5.6 s 시점에 프로젝트 전체를 다시 스캔하게 하던 문제다. 등록된 파일 타입, 연결, 무시 목록이 직전 스냅샷과 다를 때만 재스캔한다 (46482b0bf1).
- Phantom Dark/Light를 기본 테마로 했다(숯색 패널, 앰버 강조, 전용 에디터 스킴). 폴더 아이콘을 더 선명하게 했고, `PhantomMemorySizer`가 사용자 `.vmoptions`에 RAM 크기에 맞는 `-Xmx`와 파일 페이지 캐시를 쓰며(48 GiB에서 4 GiB / 1 GiB, 사용자가 정한 값은 덮어쓰지 않음), 번들 vmoptions에서 `-ea`를 뺐다. `phantom-install`은 사용자 `TMPDIR`로 재실행한다 (a91f9ef7e8).

### 기능(Phantom 플러그인)
- 첫 Phantom 플러그인 `phantom-spring`: 주입 지점과 빈에 gutter를 달아 후보 빈과 주입 위치를 보여주고, 빈, 생성자, `@Bean` 팩토리, 리스너, 핸들러를 사용되지 않음으로 보고하지 않는다(Java, Kotlin). `spring-context` 기준으로 켜지며 시작 활동이 없다. 날짜 기반 빌드가 돌도록 `build.txt` 기준과 버전 검사도 완화했다 (e8ef2fd569).
- `phantom-spring`에 빈이 없거나 여러 개인 주입 지점 경고와 `@Qualifier`/`@Primary` 빠른 수정을 추가했다 (8f18fe3689).
- `phantom-spring-boot`: `application*.yml`/`.properties`에서 `spring-configuration-metadata.json` 기반 설정 키와 값 완성, 빠른 문서를 추가했다. 시작 작업이 없다 (9ae24548c2).
- `phantom-spring-boot`가 프로젝트의 `@ConfigurationProperties` 클래스와 `@Bean` 메서드에서 키를 읽고(`spring-boot-configuration-processor` 불필요), 키에서 Ctrl+B 이동과 deprecated/미지 키 검사를 제공한다 (913b0c937e).
- `phantom-jpa`: `@Query`와 `@NamedQuery` 안의 JPQL에 직접 만든 lexer와 관대한 파서로 이동, 완성, 오류 표시를 추가했다(Java, Kotlin). 사용자 프로젝트의 쿼리 20개에서 오탐이 없었다 (e1712f214c).
- `phantom-jpa`에 Spring Data 파생 쿼리 메서드 이름의 완성과 검사를 추가했다 (00fa0603de).
- `phantom-http`: `.http`/`.rest` 파일 실행, 변수와 환경 파일, 응답 보기 도구 창을 추가했다. 시작 활동, 인덱스, 리스너가 없다 (83d8ab6e82).
- HTTP Client에서 응답 핸들러 스크립트(`> {% %}`, `> file.js`)를 작은 JavaScript 인터프리터로 실행하고, Endpoints 창에서 Generate HTTP Request로 `generated-requests.http`에 요청을 추가한다 (ccc29997cb).
- HTTP Client에 `.http` 파일용 검사 9개(제한된 헤더, 알 수 없는 메서드, URL 공백, JSON 본문, 누락된 `###`, 깨진 `{{}}`, 잘못된/알 수 없는 헤더, 원격 호스트에 대한 평문 http)를 추가했다 (618ba1f837).
- `phantom-db`: MySQL, MariaDB, PostgreSQL, H2의 JDBC 데이터 소스, SQL 콘솔, 스키마 트리를 추가했다. 읽기 전용 소스와 WHERE 없는 UPDATE/DELETE를 막는다 (87438dbfb4).
- `phantom-icons`: Material Icon Theme(MIT) 기반으로 이름별 파일/폴더 아이콘을 Project 뷰, 탭, 파일 목록에 표시한다. SVG 562개와 규칙 2,084개이며 시작 작업이 없다 (d7310f9556).
- phantom-icons의 폴더를 한 톤의 유리 질감으로 바꿨다 (a5e319eeb6).
- phantom-icons가 Kotlin 파일과 Project 뷰의 클래스 노드에 Kotlin 로고를 표시한다. 단일 클래스 노드는 클래스 PSI에서 파일을 가져와 마지막에 장식한다 (96514e65e8, 94c2fc224d).
- `phantom-theme`: Phantom Dark와 Phantom Light(Islands 기반, 앰버 강조)를 추가했다. `tools/generate-themes.py`로 생성한다 (eb50867a56).
- 폴더 고유 색을 유지하고 "Use project colors in main toolbar"를 기본 꺼짐으로 바꿨다 (f2e7052a14).
- Spring MVC 엔드포인트를 Java와 Kotlin에서 찾아 모듈별로 보여주는 Endpoints 도구 창을 추가했다. 창을 닫은 동안은 계산하지 않는다 (60881b3dfb).
- `phantom-sync`: Backup and Sync를 사용자가 소유한 git 저장소로 하는 제공자를 추가했다. 로그인은 저장소 이름 지정이며 JetBrains 계정으로 아무것도 가지 않는다 (5f1819d799).
- GitHub 저장소의 `.phantom/marketplace.json`으로 설명된 플러그인 저장소(`owner/repo`, `owner/repo@branch`, URL)에서 설치할 수 있다. `newkayak12/phantom-plugins`는 항상 목록에 있다 (c8f33b8a61).
- 개인 GitHub 플러그인 저장소를 토큰(비밀번호 저장소, `GH_TOKEN`/`GITHUB_TOKEN`, `gh auth token`)으로 읽는다. 토큰은 설정된 저장소의 api.github.com에만 보낸다 (79d2dc6b96).
- 모듈 이름 `intellij.phantom.*`를 `phantom.*`로 바꿨다 (c06e071a51).
- 첫 실행 마법사가 설치된 JetBrains IDE의 설정을 가져온다. 이전에는 Phantom 자체 설정 디렉터리 옆만 봐서 `IntelliJIdea*`를 찾지 못했다 (048d85af98).

### 빌드·배포·서명
- `phantom-install.sh`를 추가했다(빌드, `/Applications/Phantom IDEA.app` 교체, 재시작) (a0c4e0d04a).
- `phantom-install`이 종료를 더 오래 기다리고 `/`에서 재실행해 Downloads 개인정보 권한 팝업을 피한다 (66dfca7676).
- 설치본을 고정된 로컬 인증서로 서명해 빌드가 바뀌어도 macOS 개인정보 권한이 유지된다 (e71672a6ab).
- `phantom-release`가 launcher 서명을 확인하고, 태그를 git으로 push하며, 릴리스 워크플로는 요청 시에만 돈다 (25be81d8e6).
- `phantom-release`가 포크의 upstream이 아니라 origin 저장소에 릴리스를 만든다 (c87e2a066e).

### 버그 수정
- 프로젝트 SDK로 Gradle을 실행할 수 없을 때(JDK 27과 Gradle 8.13, 또는 `misc.xml`의 SDK 미등록) 동기화를 실패시키는 대신 Gradle이 지원하는 가장 새 등록 JDK로 실행한다. 설정에서 직접 고른 Gradle JVM은 바꾸지 않는다 (9e9ab0c964).
- `phantom-spring`: `@ConfigurationProperties` 생성자를 빈이 아니라 값 바인딩으로 취급한다. 실제 Kotlin 프로젝트(빈 파일 207개)에서 data class 속성이 빈 없음으로 보고되던 문제다 (214b33702f).
- `phantom-spring`: 검색 순서가 안정적이지 않아 첫 `@Qualifier` 제안이 실행마다 바뀌던 것을 빈 이름 순으로 고정했다 (c37525a8f2).
- HTTP Client: 위에 요청 줄이 없는 `> {% %}` 줄을 `GET >` 요청으로 읽어 `Invalid URL`로 실패하던 것을 핸들러로 인식하고 경고를 낸다 (96968e343e).
- 플러그인 저장소 이름 비교를 대소문자 구분 없이 하고 실제 Phantom-plugins 저장소 이름을 쓴다 (e67f21c7b5).

### 벤치마크
- Phantom과 Ultimate를 사용자 프로젝트에서 비교한 첫 벤치마크를 기록했다 (17aac585ca).
- 예열한 프로젝트 복사본으로 두 번째 비교를 기록했다 (98c55034c1).
- 두 번째 벤치마크의 JCEF 프로세스가 복원된 Markdown 미리보기 탭 때문임을 설명했다 (2c51d57d78).
- 교대로 5회 실행한 중앙값을 기록했다. 첫 smart mode는 Phantom 3.4 s, Ultimate 3.8 s, 인덱싱 안정화는 4.5 s 대 9.0 s, IDE 프로세스 메모리는 1,005 MB 대 2,211 MB였다 (1141f80b39).
- Phantom과 Ultimate의 차이 원인(이중 스캔, 시작 시 Kotlin K2, 쓰지 않는 플러그인)을 설명했다 (e28cfc3417).
- 클래스 분류표에서 행이 이중 집계되던 것을 고쳤다 (eeda0240fc).

### 문서
- Ultimate 기능 중 Spring 백엔드 개발자가 쓰는 것에 대한 clean-room 설계 문서 14편과 번들 플러그인 빌드 방법, 우선순위가 있는 로드맵을 추가했다 (595f0c2468).
- 로드맵에서 S1을 완료로 표시했다 (447e3d0185).

## 기준 소스 (업스트림 가져오기)

- JetBrains/intellij-community 태그 `idea/262.10968.63`(커밋 `8062c061ff33e22041d9f1844db5b8b8a6b5e4d7`)의 소스를 README.md 없이 한 커밋으로 가져왔다. upstream 이력은 포함하지 않는다. `build.txt`는 `262.10968.SNAPSHOT`이다 (d2faebf601).
