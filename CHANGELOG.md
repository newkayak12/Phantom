# Phantom 변경 이력

이 파일은 JetBrains intellij-community를 포크한 개인 프로젝트 Phantom의 변경 이력이다. 기준(base)은 upstream 태그 `idea/262.10968.63`이며, 루트 커밋 `58acf7643d`가 이 태그의 소스를 한 번에 가져온 것이다. 그 이후의 모든 커밋이 Phantom 변경이다.

2026-11-19에 공개 준비로 이력을 커밋 2개(업스트림 가져오기와 그 시점의 Phantom 전체)로 압축했다. 그 이전 항목에 적힌 커밋 해시는 이제 저장소에 없고, PR 번호만 참고용으로 남는다.

버전 표기는 두 가지다. 릴리스 태그는 `v<YY>.<MM>.<DD>.<N>` 형식이고, 빌드 번호는 `PH-<YYMMDD>.<N>` 형식이다. 플랫폼 빌드 `262.<YYMMDD>`는 내부 apiVersion과 번들 플러그인의 since/until-build로만 쓴다.

## 이 파일을 유지하는 방법

- 변경을 커밋할 때 같은 커밋에서 `미배포` 아래에 항목을 추가한다.
- `phantom-release.sh`가 릴리스 태그를 만들면 `미배포` 항목을 그 버전 제목(태그와 날짜) 아래로 옮긴다.
- 사용자나 개발자에게 보이는 변경마다 한 줄을 쓰고, 끝에 짧은 커밋 해시와 PR 번호를 괄호로 붙인다: `(2aa424a169, PR #4)`. PR 없이 main에 바로 넣은 커밋은 해시만 쓴다: `(b5ba0196ab)`.
- 항목은 고정 카테고리(브랜딩·버전 / 번들 구성 / JetBrains 서버 통신 / 성능·인덱싱 / 기능(Phantom 플러그인) / 빌드·배포·서명 / 벤치마크 / 버그 수정 / 문서)로 나누고 빈 카테고리는 생략한다.
- 해시는 커밋 후에야, PR 번호는 PR을 연 뒤에야 알 수 있으므로, 같은 커밋에 쓸 수 없으면 다음 커밋에서 채운다.
- PR을 스쿼시 머지하거나 브랜치를 리베이스하면 브랜치 커밋의 해시는 main에 남지 않는다. 그 경우에도 PR 번호로 변경을 찾을 수 있다. 해시는 main에 있는 것(스쿼시 커밋)으로 고치고, 고칠 수 없으면 PR 번호만 남긴다.

## 미배포 (v26.11.13.1 이후)

- 프로젝트를 처음 열 때 README를 미리보기 전용(SHOW_PREVIEW)으로 열던 힌트를 뺐다. README는 에디터 기본 배치로 열리고, 내장 브라우저(JCEF)는 미리보기를 켤 때만 뜬다. 별도 프로필 측정에서 JCEF 프로세스 5개(529 MB)가 0개가 됐다(Phantom-plugins의 Markdown 쪽 변경과 함께).

- 플러그인이 무거운 상태를 처음 쓸 때 만들도록 확장 지점 `com.intellij.phantom.lazyActivation`(`PhantomLazyActivation`)을 추가했다. 플러그인은 XML에 파일 확장자, 액션 id, 도구 창 id를 트리거로 적고, 프로젝트마다 첫 트리거에서 `activate`가 백그라운드로 한 번 불린다. idle 신호 뒤에는 다시 트리거되면 또 불린다. 선언한 플러그인이 없으면 구독도 하지 않는다.

- 플러그인용 활동 신호 `dev.phantom.ide.activity.PhantomActivityListener`(idle, prewarm, active)와 프로젝트 단위 `PhantomProjectActivityListener`(dormant, awake; 숨긴 workspace 탭처럼 열려 있지만 보이지 않는 프로젝트용, `PhantomActivitySignals.dormant/awake`로 보냄)를 추가했다. 10분 동안 입력이 없으면 idle, 그 뒤 창이 포커스를 받으면 prewarm, 첫 입력에 active를 보낸다(`-Dphantom.activity.idle.minutes`, 0이면 끔). 플러그인은 idle에 다시 만들 수 있는 상태를 놓아 GC가 힙을 돌려받게 한다. 체크아웃 기록 `dev.phantom.ide.checkout.PhantomCheckoutLink`는 프로젝트가 저장소의 main checkout인지, 어느 base의 worktree인지를 `.git`만 읽어 `.idea/workspace.xml`에 남기고, 시스템 디렉터리 `phantom/checkouts.json`에 저장소별 목록을 둔다. Gradle 모델 재사용과 local shared index가 이 기록을 읽는다. 플러그인은 `dev.phantom.modules.activity`, `dev.phantom.modules.checkout`에 선택적으로 의존한다.

- `phantom-bench-report.py`가 JSON 문서 여러 개가 이어 붙은 시작 보고서 파일도 읽는다. 이전에는 "Extra data" 오류로 벤치 요약이 실패했다. 같은 벤치로 `-XX:CICompilerCount=4`를 재 보니 기본값 2보다 첫 smart mode가 1.9-2.7 s에서 3.1-4.3 s로 느려져 기본값을 그대로 둔다.

- 릴리스 워크플로가 macOS와 Windows를 동시에 빌드한다. `prepare`가 태그 번호를 먼저 정해 두 빌드가 같은 버전을 쓰고, Windows 파일은 macOS 릴리스가 나온 뒤에 올린다. macOS가 실패하면 아무것도 배포하지 않고, Windows가 실패하면 macOS 릴리스는 그대로 두고 Windows만 다시 돌린다. 걸리는 시간은 macOS 빌드 하나 정도로 줄어든다.

- 릴리스 워크플로가 macOS DMG를 먼저 릴리스하고, 그 릴리스가 성공하면 별도 job이 릴리스 태그에서 Windows 설치 프로그램과 zip을 빌드해 같은 릴리스에 올린다. Windows 빌드가 실패해도 macOS 릴리스는 나가고, 실패한 Windows job만 다시 돌리거나 워크플로를 `windows_tag`에 릴리스 태그를 넣어 실행하면 macOS는 건너뛰고 Windows만 빌드해 올린다. 로컬에서는 `./phantom-release.sh --windows <태그>`로 같은 일을 한다. (PR #92)

- macOS 앱 제자리 업데이트(#91)의 교체 스크립트가 Kotlin 문자열 템플릿으로 읽혀 IDE가 컴파일되지 않던 것을 고쳤다(`$$"""`). (PR #93)

- Help > About의 저작권 표기가 "Copyright © 2000–현재 Phantom"으로 나와 JetBrains의 저작권을 Phantom 것으로 보였다. 이제 "Copyright © 2000–현재 JetBrains s.r.o. and contributors" 줄을 따로 두고, Phantom 줄은 2026년부터로 적는다.

- Phantom이 고친 번들 플러그인 Artifacts Repository Search, IDE Features Trainer, Shell Script, Completion Stats Collector의 vendor를 git4idea처럼 JetBrains에서 Phantom으로 바꿨다.

- classic UI 테마 이름 "IntelliJ"와 "IntelliJ Light"를 "Classic Light"와 "Classic Bright"로 바꿨다. 색 구성표 "IntelliJ Light"의 표시 이름도 "Classic Bright"다. 테마와 구성표 id는 그대로라 기존 설정은 유지된다.

- Groovy 테스트 데이터에서 Sun JDK 5 사본(`plugins/groovy/testdata/mockJDK`, 소스에 "SUN PROPRIETARY/CONFIDENTIAL" 표기, 라이선스 없음)을 지웠다. 테스트는 GPL+CE인 `java/mockJDK-1.7`을 쓰고, 그 안에 있던 Groovy 1.0은 `testdata/lib`로 옮겨 라이브러리로 붙인다.

- Kotlin·Maven 플러그인 이름을 "Kotlin Support"·"Maven Support"에서 "Support for Kotlin"·"Support for Apache Maven"으로 바꿨다. 상표는 "for" 뒤에만 써서 무엇을 지원하는지 설명하는 데만 쓰고, Phantom 이름을 앞세우지 않는다. 플러그인 id는 그대로다.

- `license/`에 목록에 나오지만 전문이 없던 라이선스(MIT, BSD 2/3-Clause, Unicode 3.0, zlib, EDL 1.0, JDOM, Codehaus classworlds, CC0)를 넣었다. 배포판과 Kotlin·Maven ZIP에 함께 들어간다. Kotlin ZIP의 NOTICE에는 IDE 목록에 없는 vavr, javax.inject, Kotlin 컴파일러를 적는다(`build/phantom-plugin-notices/`). 라이브러리 표에 MIT로 잘못 적힌 vavr 0.10.x는 Apache 2.0으로 고쳤다.

- NOTICE.txt에서 Phantom-plugins 번들 플러그인의 라이브러리 버전은 third-party-libraries.html이 아니라 각 jar의 `META-INF/THIRD-PARTY-NOTICES.txt`에 있다고 고쳐 적고, LanguageTool 버전(6.8.27)을 넣었다.

- jar에 남아 있던 IntelliJ IDEA·PyCharm·Toolbox 로고 SVG 3개를 중립 자리표시자로 바꿨다. 새 프로젝트 마법사의 Spring Boot 아이콘은 Spring 잎 모양 대신 Phantom-plugins와 같은 원과 화살표 그림이다.

- Bazel downloader가 더 쓰지 않는 `native-3.42.0-jb.1`과 `tips-intellij-idea-community-241.62`를 phantom-libs로 돌리지 않는다.

- DMG를 만들 때 `hdiutil create`가 "Resource busy"로 실패하면 최대 5번까지 다시 시도한다. GitHub 호스팅 macOS runner에서 가끔 나는 오류로, 한 시간 넘게 컴파일한 릴리스 빌드가 이 한 번 때문에 실패했다.

- 배포 빌드 대상 목록에 Windows 실행 파일을 만드는 `launcher-generator`를 넣었다. macOS 빌드 보고서로 만든 목록에 빠져 있어서, 이전 빌드 결과가 없는 깨끗한 머신(GitHub 호스팅 runner)에서는 Windows 빌드가 실패했다.

- 릴리스 workflow가 self-hosted runner 대신 GitHub 호스팅 macOS runner(macos-15)에서 DMG와 Windows 설치 파일을 빌드하고 릴리스한다. 캐시 없이 DMG만 70분에 빌드됐다(#72). Phantom-plugins 쪽 Kotlin·Maven 릴리스는 secret `PHANTOM_PLUGINS_TOKEN`이 있을 때만 한다. 없으면 본체만 릴리스하고 두 플러그인은 빌드하지 않는다(`PHANTOM_RELEASE_PLUGINS=0`). 시험용 workflow와 `tools/phantom-runner-setup.sh`는 지웠다.

- Bazel downloader 허용 목록에 `swcdn.apple.com`을 넣는다. llvm 툴체인의 macOS SDK(`@llvm//osx:macos_sdk`)를 Apple Command Line Tools 패키지에서 받는데 막혀 있어서, 저장소 캐시가 없는 깨끗한 머신(GitHub 호스팅 runner 등)에서는 빌드가 분석 단계에서 실패했다. Apple이 고정된 패키지 주소를 내려(403) llvm 모듈의 두 번째 주소인 web.archive.org 사본(같은 sha256)도 허용한다. 빌드에만 쓰고 앱에는 들어가지 않는다.

- 한·영 README의 번들 플러그인 조달 및 Kotlin·Maven 배포 설명과 Product DSL 작업 지침 경로를 갱신했다.

- Windows에서 빌드할 때 쓰는 Bazel `java_tools_windows`를 JetBrains가 VC++ 런타임 DLL을 넣어 다시 묶은 판(`packages.jetbrains.team`) 대신 rules_java 기본값(같은 v19.0, mirror.bazel.build·GitHub)에서 받는다. 루트와 `platform/build-scripts/bazel`의 `MODULE.bazel`에서 덮어쓰기를 지웠다. Windows 빌드 머신에는 Microsoft VC++ 재배포 패키지가 깔려 있어야 한다(rules_java#316). macOS에서 만드는 Windows 설치 파일과는 관계없고, 이제 어느 빌드도 JetBrains 호스팅 파일을 받지 않는다.

- macOS 앱에 AOT 캐시(`Contents/lib/phantom.aot`, JDK 25 `-XX:AOTCache`)를 넣는다. 빌드가 만든 앱으로 작은 Java 프로젝트를 여는 학습 실행을 돌리고(`-XX:AOTMode=record`), 기록한 것으로 캐시를 만든다(`PhantomAotCache.kt`). 이 앱은 런처의 동적 CDS 인자 대신 `-XX:AOTCache`로 시작한다. 9,878개 파일 프로젝트에서 시작부터 프로젝트가 열릴 때까지가 동적 CDS 1.88초에서 1.62초로 줄었다(5회 중앙값). 다른 위치(`/Applications`)로 옮긴 앱에서 캐시를 쓸 수 있게 boot classpath jar 두 개(`intellij.libraries.jbr.jar`, `intellij.libraries.xz.jar`)의 `Sealed`를 뺀다. 빌드는 복제한 앱으로 `-XX:AOTMode=on` 시작을 확인하고, 실패하면 캐시를 넣지 않는다. 빌드 머신과 OS·아키텍처가 다르거나 학습 실행이 실패하면 캐시 없이 이전처럼 동적 CDS를 쓴다. `-Dphantom.aot.cache=false`로 끌 수 있다. 앱 크기가 약 250MB 늘어난다.

- `makedmg.sh`가 DMG 파일 시각을 맞출 때 분 자리에 월을 넣던 것(`date +%Y%m%d%H%m`)을 `%M`으로 고친다.

- 동적 CDS 아카이브 이름에 그날의 빌드 순번을 붙인다(`phantom262.261121.2.jsa`). 같은 날 빌드가 이름(`phantom262.<YYMMDD>.jsa`)을 같이 써서, 다른 빌드가 남긴 아카이브가 있으면 런처가 새 아카이브를 만들지 못하고 기본 아카이브만으로 시작했다. AOT 캐시로 시작하는 macOS 앱은 시작 후 이전 빌드들의 `.jsa`를 모두 지운다.

- 게시하는 Kotlin·Maven 플러그인에서 Grazie 연동 모듈(`intellij.kotlin.grazie`, `intellij.maven.proofreading`)을 뺀다. Phantom은 Grazie를 번들하지 않아(Phantom-plugins grammar가 대체) 두 모듈은 원래 로드되지 않았다. 게시 빌드 목록은 Grazie 모듈로 들어가지 않고, 어느 빌드도 `ai.grazie` 라이브러리를 받지 않는다. 게시 빌드 content report에서 빠진 것은 두 모듈뿐이다.

- 테스트 프레임워크(`platform/testFramework/core`)가 실패 보고에 코드 소유자를 붙이지 않는다(`codeOwnerResolver` 제거). 소유자 해석기가 JetBrains 소유의 라이선스 없는 patronus codeowners를 썼다. 게시 빌드 목록은 테스트 의존을 따라가되 코드 소유자 모듈과 IDE metrics benchmark로는 들어가지 않는다. 일반·게시 빌드 어디서도 patronus를 받지 않고, 일반 빌드가 받는 JetBrains 호스팅 파일은 보류 중인 java_tools_windows 하나다. content report는 이전과 같다.

- Maven 아티팩트 단계를 끈다(`mavenArtifacts`를 비워 빌드가 단계를 건너뜀). Phantom은 Maven 아티팩트를 게시하지 않고 phantom-build.sh가 결과를 지웠다. 일반 빌드 목록이 715개에서 676개 모듈로 준다. 게시 빌드 목록은 TEST 범위로만 닿는 모듈의 라이브러리를 넣지 않는다. patronus codeowners는 빌드 도구 자체(`platform/build-scripts` → `testFramework.core` → codeowners)가 컴파일에 써서 남는다.

- 일반 빌드 목록이 모듈의 TEST 범위 의존을 따라가지 않는다(797 → 715 모듈). 일반 빌드가 Grazie 라이브러리 54개(JetBrains 소유, 라이선스 없음)를 더는 받지 않고, 받는 JetBrains 호스팅 파일이 8개(completion-ranking 2, terminal completion 2, patronus 3, 보류 1)로 준다. standalone JPS의 테스트 jar 모듈 2개는 이름으로 넣는다. Grazie는 게시 빌드(Kotlin의 Grazie 연동)만 받는다. 일반·게시 빌드 content report는 이전과 같다.

- 라이선스 없는 JetBrains 파일 5개를 빌드에서 뺐다. 완성 순위 ML 모델 `completion-ranking-java`·`-kotlin`·`-sh`는 앱에 이미 안 들어가던 것이라 읽는 클래스(`JavaMLRankingProvider`·`KotlinMLRankingProvider`·`ShMLRankingProvider`)와 그 테스트까지 지웠다. 터미널 `completion-db-with-extensions`의 JSON 모델 클래스는 Phantom-plugins terminal의 자체 클래스(`dev.phantom.terminal.completion.spec`, Apache-2.0)로 바꿨고, `completion-spec` jar는 같은 API 소스가 `plugins/terminal/completion`에 이미 있어 의존만 뺐다. 일반 빌드 타깃 빌드가 통과하고 다섯 파일을 받지 않는다.

- 빌드가 쓰는 JetBrains 전용 오픈소스 라이브러리 69개와 sources jar 64개(133개)를 phantom-libs 릴리스 `jetbrains-hosted-4`에서 받는다(Kotlin 컴파일러·분석 jar, LanguageTool 6.8.27, Hunspell 사전, Apache-2.0 포크들). 원본 URL에서 받은 sha256과 업로드 뒤 sha256이 133개 모두 고정값과 같고, JetBrains 호스트를 막은 채 133개 저장소를 모두 받았다. 일반 빌드 목록은 TEST 범위 라이브러리를 빼서(studio-test-platform 등) 받는 JetBrains 호스팅 파일이 62개(Grazie 53, JetBrains 소유·라이선스 없음 8, 보류 1)로 줄고, 게시 빌드는 2개 더 받는다. 남은 파일은 라이선스가 없거나 JetBrains 소유라 미러하지 않았다.

- 빌드 타깃의 `data`에서 라이브러리 타깃 전체(`ALL_LIBRARY_COMMUNITY_TARGETS`, 1,209개)를 빼고, 배포 모듈과 게시 플러그인(Kotlin·Maven)이 쓰는 라이브러리 jar만 넣는다. `tools/phantom-dist-targets.py`가 모듈의 라이브러리, content report의 `module:`·`library:` 항목, 빌드가 이름으로 찾는 라이브러리(Dokka, `kotlinc.kotlin-dist`)를 목록에 더한다. 일반 빌드가 받는 JetBrains 호스팅 파일이 200개에서 91개로 줄고(게시 빌드는 43개 더), 95개는 어느 빌드도 받지 않는다. content report는 일반·게시 빌드 모두 이전과 같다. 쓰지 않는 `swcdn.apple.com` → JetBrains 미러 rewrite 규칙도 지웠다.

- 일반 빌드가 Kotlin·Maven 플러그인을 컴파일하지 않는다. 두 zip은 `PHANTOM_PUBLISH_PLUGINS=1`(phantom-release.sh가 켬)일 때만 새 타깃 `//build:i_build_target_published`로 만든다. 일반 빌드 타깃은 983개에서 795개가 되고, YAML 플러그인(`plugins/yaml`)도 Kotlin 쪽 사슬로만 필요해 빠진다. `tools/phantom-dist-targets.py`는 두 목록(`PHANTOM_DIST_TARGETS`, `PHANTOM_PUBLISHED_TARGETS`)을 쓰고, 게시 플러그인 모듈은 `build/phantom-published-modules.txt`에 둔다. (#40 4단계)
- Plugins 창이 JetBrains Marketplace(plugins.jetbrains.com)를 부르지 않는다. 검색, 상세 정보, 리뷰, 로고, 가격, 설치된 Marketplace 플러그인의 업데이트 확인이 모두 해당된다. 목록에는 Phantom 플러그인 저장소와 사용자가 추가한 저장소만 나온다. `-Dphantom.jetbrains.services=true`로 다시 켤 수 있다. 기존에는 Marketplace 탭을 열면 JetBrains에서 목록을 가져왔다. (PR #55)
- 번들 repository-search를 0.1.2로 올렸다. JetBrains Package Search 클라이언트와, JetBrains 전용 저장소에서만 받을 수 있던 라이브러리 7개를 뺐다(Phantom-plugins #51). 번들 `phantom-plugins-5fb6dda5e6`. (PR #56)
- JetBrains 사용 통계의 이벤트 스키마와 사전(`event-log-metadata/`, 11MB)을 앱에서 뺐다. Phantom은 통계를 모으지 않아 쓰지 않던 파일이다. (PR #58)

- Endpoints 도구 창을 본체(`platform/lang-impl`의 `dev.phantom.ide.endpoints`와 `PhantomLangBundle`)에서 빼고 Phantom-plugins의 Phantom Endpoints(`dev.phantom.endpoints`, Phantom-plugins #48)로 번들한다. 도구 창 id(`Endpoints`)와 동작은 그대로다. `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`에 `endpoints`를 더했다. (#40 1단계, PR #52)
- 오류 리포트(ea-report·exceptions.marketplace.jetbrains.com)와 로그 업로드(uploads.jetbrains.com)를 보내는 마지막 지점(`ITNProxy.sendError`, `LogUploader.uploadFile`)에서 `-Dphantom.jetbrains.services=true`가 없으면 막는다. 마켓 플러그인이 JetBrains 리포터를 등록해도 오류 창에 Report 버튼이 나오지 않는다. 기존에는 버튼을 누르면 JetBrains로 보냈다.
- 오류 리포트(ea-report·exceptions.marketplace.jetbrains.com)와 로그 업로드(uploads.jetbrains.com)를 보내는 마지막 지점(`ITNProxy.sendError`, `LogUploader.uploadFile`)에서 `-Dphantom.jetbrains.services=true`가 없으면 막는다. 마켓 플러그인이 JetBrains 리포터를 등록해도 오류 창에 Report 버튼이 나오지 않는다. 기존에는 버튼을 누르면 JetBrains로 보냈다. (PR #53)
- Try Ultimate가 IDE를 download.jetbrains.com에서 받아 자동 설치하던 기본 동작과, Choose Boot Java Runtime 창이 JetBrains Runtime 목록을 받아오던 동작을 같은 게이트 뒤로 옮겼다. 게이트가 없으면 Try Ultimate는 브라우저로 다운로드 페이지만 열고, 런타임 창에는 번들 런타임과 로컬 JDK만 나온다. (PR #53)

- 원본 브랜드 그림(`community-resources/phantom-brand-src`, PNG 3장 3.1MB)과 아이콘·스플래시를 만드는 `compose.swift`를 phantom-libs의 `brand/`로 옮겼다. README 로고는 그 주소를 쓴다. 앱에 들어가는 아이콘과 스플래시는 그대로다. (#40 4단계)

- `phantom-bench.sh`가 실행마다 GC 직후의 class histogram(`histogram.txt`)도 저장한다. 두 빌드의 heap 차이를 클래스 단위로 비교할 수 있고, 이것으로 local-shared-index 키 맵이 heap 약 146MB를 쓰는 것을 찾았다(Phantom-plugins #46에서 76MB 줄임).

- 플러그인 호환성 검사가 번들 플러그인의 content module 의존성(Java PSI, 테스트 러너, 구조 뷰 등)을 Plugin Verifier에 해당 번들 플러그인 의존성으로 알려 주도록 하고, 라이브러리의 선택 기능 때문에 생기는 오탐만 이유와 함께 무시 목록에 올렸다. coverage 0.1.3(rt jar의 중복 plugin.xml 제거)과 json 0.1.2(final 메서드 오버라이드로 보고되던 이름 충돌 해소)를 새 번들(`phantom-plugins-2a9d27d645`)에 담았다. (PR #40 후속)

- 저장소 품질 검사를 매주 실행하고 서비스 설정·권한·실패 확인 절차를 제품 README와 분리한 운영 문서에 정리했다.

- SonarQube용 과거 Git 객체를 자동 lazy fetch 대신 인증을 명시한 배치 fetch로 받도록 보완했다. 누락 객체가 남으면 분석 전에 실패시킨다.

- 새 PR 관리 스크립트 끝의 빈 줄 때문에 실패한 CI를 고치고, 품질 도구가 git add 전의 새 파일도 Git 공백 검사로 확인하도록 했다.
- SonarQube의 JGit blame이 부분 클론의 과거 blob 누락으로 실패하던 것을 고쳤다. 분석 대상 경로의 Git 이력 객체만 미리 받아 소스 분석 범위와 blame을 유지한다.

- 참조 저장소의 PR 규모·충돌 라벨, 테스트 결과 요약, 정기 검사 흐름을 두 저장소에 맞게 추가했다. 비활성 이슈·PR은 60일 뒤 표시만 하고 자동 종료하지 않으며, 저장소 첫 화면을 가리는 README 재추가를 품질 검사에서 막는다.

- GitHub 첫 화면에서 제품 README를 가리던 `.github/README.md`를 `.github/WORKFLOWS.md`로 옮겼다. 루트의 Phantom 소개·장점·설치와 배지가 표시된다.

- README의 불필요한 서비스 토큰 설정·등록 이력과 중복 CI 설명을 걷어내고, 배지와 실제 커버리지 범위만 간단히 남겼다. 기능·설치·빌드 안내, 법적 출처는 유지했다.

- 최신 main의 정책·배포 변경을 기반 설정 PR에 통합하고, PR 작성자를 제외한 collaborator 리뷰 요청을 PR 생성·재오픈·ready·업데이트 및 수동 실행에서 처리하도록 했다. 플러그인은 Spotless·detekt와 테스트·패키징 workflow를 중복 없이 나눈다.
- Jewel의 업스트림 PR 형식 검사용 파일 필터도 업스트림에서만 실행하고, checkout에 필요한 contents 읽기 권한을 명시했다.

### 브랜딩·버전
- 제품 이름을 Phantom IDEA에서 Phantom으로 바꿨다(IntelliJ IDEA 상표와 혼동을 피하려고). 앱은 `Phantom.app`, Windows 이름과 설치 폴더는 `Phantom`, 스플래시(`phantom_logo*.png`, `compose.swift splash phantom-white.png`로 다시 만듦)와 DMG 배경의 글자도 Phantom이다. 설정이 이어지도록 번들 ID `dev.phantom.idea`, 설정 폴더 이름 `PhantomIDEA`, 산출물 이름 `phantom-idea-*`는 그대로 둔다. `phantom-install.sh`는 기본 경로에서 `Phantom.app`이 아직 없고 `/Applications/Phantom IDEA.app`이 있으면 그 앱을 종료하고 새 앱으로 바꾼 뒤 지운다. 이 변경 이력의 이전 항목은 당시 이름 그대로 둔다.
- 실행 파일 이름을 `idea`에서 `phantom`으로 바꿨다(`baseFileName`, `IdeaApplicationInfo.xml`의 `script`). 그래서 `Contents/MacOS/phantom`, 번들 `bin/phantom.vmoptions`, 사용자 설정의 `phantom.vmoptions`·`phantom.jdk`, 환경 변수 `PHANTOM_VM_OPTIONS`·`PHANTOM_PROPERTIES`·`PHANTOM_JDK`가 된다. `phantom-install.sh`는 설정 폴더의 `idea.vmoptions`·`idea.jdk`를 새 이름 파일이 없을 때 한 번 복사하고, 교체할 앱이 아직 `MacOS/idea`로 떠 있어도 찾아 종료한다. DMG를 직접 설치하면 이 복사가 없어 기존 `idea.vmoptions`가 적용되지 않는다. 소개 문구(motto)는 "Built on the open-source IntelliJ Platform"으로 바꾸고 스플래시를 다시 만들었다.
- 산출물 이름을 `phantom-idea-<YYMMDD>.<N>`에서 `phantom-<YYMMDD>.<N>`으로 바꿨다(DMG, exe, win.zip; `getBaseArtifactName`). Linux 루트 폴더도 `phantom`이다. 출력 폴더 `out/phantom-idea`와 설정 폴더 `PhantomIDEA`는 그대로 둔다. `phantom-install.sh`와 `phantom-build.sh`의 정리 단계는 `phantom-*` 패턴이라 예전 이름의 DMG도 찾고 지운다.
- `NOTICE.txt`에 GPL 2.0 + Classpath Exception 구성 요소(JetBrains Runtime, jb-jdi, sa-jdwp, java-compatibility)의 소스 위치와, 요청하면 3년 동안 소스 사본을 제공한다는 서면 제안을 넣었다. 바이너리 배포에 GPLv2 3조가 요구하는 것이다. JBR 소스 태그는 `jbr-release-<runtimeBuild>`이다.
- 수정한 원본 파일 중 표시가 없던 두 파일(`.idea/modules.xml`, `GithubBundle.properties`)에 `phantom-idea:` 표시를 달았다.
- `LICENSE.txt`를 JetBrains Open-Source Build Terms에서 Apache License 2.0 원문으로 바꾸고, `NOTICE.txt`에 포크 사실과 JetBrains와 무관하다는 문구를 넣었다 (ff512ae2df).
- Windows 설치 파일이 JetBrains를 게시자로 적던 것을 Phantom으로 바꿨다: 버전 정보의 `CompanyName`은 Phantom, `LegalCopyright`는 "Copyright 2000-<연도> JetBrains s.r.o. and contributors; modifications Copyright 2026 Phantom contributors", 제거 목록 레지스트리의 `Publisher`는 Phantom, `URLInfoAbout`은 https://github.com/newkayak12/Phantom 이다(`build/conf/nsis/idea.nsi`). 설치 옵션 "Install JetBrains ETW Host Service"(ja/ko/zh_CN 포함)에서 JetBrains를 뺐다. 같은 저작권 문구를 macOS `InfoPlist.strings`(`CFBundleGetInfoString`, 5개 언어)와 `bin/phantom.exe`·`phantom64.exe`의 `LegalCopyright`(`WindowsDistributionBuilder.kt`)에도 쓴다. About의 회사 URL과 러너 등록 스크립트(`tools/phantom-runner-setup.sh`)가 없는 저장소 `newkayak12/phantom-idea`를 가리키던 것을 `newkayak12/Phantom`으로 고쳤다 (f6f9ce083c, PR #12).
- 배포판의 `license/` 폴더에 링크만 있던 라이선스의 전문(GPL 2.0 + Classpath Exception, GPL 3.0, LGPL 2.1, LGPL 3.0, EPL 1.0, EPL 2.0, MPL 2.0, CDDL 1.1, OFL 1.1, CC BY 2.5; SPDX license-list-data 원문)을 넣고, 들어 있지 않은 구성 요소의 파일(`javahelp_license.txt`, `javolution_license.txt`, `saxon-conditions.html`, `yourkit-license-redist.txt`)을 지웠다(빌드된 앱의 모든 jar에 해당 패키지가 없음을 확인). `third-party-libraries.html`에 빠져 있던 라이브러리를 `CommunityLibraryLicenses.kt`에 더했다: JetBrains 소유로 표시돼 목록에서 빠지던 tips-idea-ce, FUS fus-api·ap-validation-all(POM 기준 Apache 2.0), kotlinx-collections-immutable, JetBrains 그룹 ID라 건너뛰던 SSHJ와 Ktor Server CIO, 다른 라이브러리 안에 묶인 asn-one, eddsa(CC0), Typesafe Config, json-smart, accessors-smart, JavaEWAH, commons-beanutils, Eclipse Sisu Inject(EPL 2.0). `NOTICE.txt`에 JBR 글꼴(OFL·Apache)과 Windows의 OpenConsole/conpty·winpty(MIT), Phantom-plugins 번들 플러그인의 LGPL/EPL 라이브러리(openregex, english-pos-dict, JaCoCo)의 소스 위치, 플러그인 라이브러리 목록이 각 jar의 META-INF에 있다는 안내, 소스 요청 연락 방법을 더했다 (0461177481, PR #12).
- JetBrains 도움말·지원으로 보내던 링크와 문구를 뺐다. `IntelliJIdeaExternalResourceUrls.kt`의 웹 도움말(`jetbrains.com/help/idea/<버전>`)과 Getting Started는 null(F1 웹 도움말 없음, Getting Started 액션 숨김), 제품 페이지와 패치 다운로드 기준 URL은 Phantom 저장소다. "Please reach out to us at YouTrack"(Settings Sync 알림 2개)는 Phantom 이슈 트래커 안내로, macOS 15 네트워크 오류의 JetBrains 지원 문서 링크는 로컬 네트워크 권한 안내로, WSL1 Git 오류의 YouTrack 링크는 WSL 2 변환 안내로 바꿨다. 제품을 "IntelliJ IDEA"라 부르던 상속 문구(ProjectModelBundle의 라이브러리 설명 3개, JavaBundle 4개(추론 애노테이션 설명의 JetBrains 문서 링크 포함), JavaCompilerBundle의 tools.jar, JavaUiBundle의 Spring Boot)는 "the IDE" 같은 중립 표현으로 바꿨다. `.iml` 형식 이름 같은 사실 표기는 그대로다 (8b874f830d, PR #12).
- jar에 남아 있던 JetBrains 로고·제품 이미지를 바꾸거나 지웠다. `community-resources`의 `idea_community_logo*.png`(Kotlin 15 판 포함)와 `idea-ce*.svg`를 지우고 이를 가리키던 `PlatformLangXmlApplicationInfo.xml`은 `phantom_logo.png`·`phantom.svg`를 쓴다. `SubscriptionExpirationDialog`(Phantom에서는 뜨지 않음)의 `IdeaBannerLogo.png`·`PyCharmBannerLogo.png`를 지우고 `phantom_logo.png`를 가리킨다. 아이콘 클래스가 경로로 읽는 `idea_logo_welcome.png`와 Java 온보딩 투어 배너 `idea-onboarding-tour*.png`는 Phantom 아이콘으로, `ultimate/ideaUltimatePromo.svg`, `codeWithMe/cwmLogoToolbar.svg`, `welcome/learnTab/jetBrainsAcademy.svg`, Settings Sync의 `expui/jetBrains.svg`는 중립 자리표시자(테두리 사각형)로 내용만 바꿨다. 이전 Phantom의 아이콘 캐시가 남은 설치에서는 바뀐 SVG 대신 예전 그림이 보일 수 있다. Android·MPS의 ApplicationInfo는 지운 파일을 여전히 가리키지만 Phantom 빌드에 들어가지 않는다 (f6a13d06c1, PR #12).
- Phantom이 고친 번들 플러그인 네 개(Java `com.intellij.java`, Git `Git4Idea`, Gradle `com.intellij.gradle`, JCEF)의 `<vendor>`를 JetBrains에서 Phantom(https://github.com/newkayak12/Phantom)으로 바꿨다. d2faebf601 이후 변경이 그 플러그인의 모듈에 닿는지로 골랐고, 고치지 않은 나머지 13개(Grid Core, platform-* 등)는 JetBrains 그대로다. vendor가 JetBrains인지에 따라 달라지는 코드를 확인했다: 번들 플러그인끼리 파일 형식이 겹칠 때의 우선순위(`ConflictingFileTypeMappingTracker`)는 지금 번들에 겹치는 확장자가 없어 영향이 없고, 바뀌는 것은 오류 보고 대화상자의 "외부 플러그인" 안내, 설정 내보내기 목록의 항목 이름(리소스 번들 없는 플러그인), 통계(꺼져 있음) 정도다 (4807eaa716, PR #12).

### 번들 구성
- Navbar, Recent Files, Services View 플랫폼 플러그인을 IntelliJ 원본 대신 Phantom-plugins가 만든 판(`dev.phantom.navbar`, `dev.phantom.recentfiles`, `dev.phantom.serviceview`, IntelliJ ID는 별칭, 콘텐츠 모듈 이름은 그대로)으로 번들한다. 셋 다 IDE의 Java·Gradle·Kotlin 모듈이 컴파일할 때 쓰지 않아 IDE 소스에서 떼어낼 수 있는 것들이다. `.removing`으로 원본을 빼고 `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`에 더했다.
  번들은 `phantom-plugins-917b1dfd8a`(33개)로 바꿨다. (PR #49)
- 배포 빌드(`//build:i_build_target`)가 저장소의 모든 production·test 모듈 타깃 4,148개(Python, Android, 배포하지 않는 플러그인과 모든 테스트 포함) 대신, 배포에 들어가는 모듈과 Maven·standalone JPS 모듈, 그리고 그 모듈 의존성의 production 타깃 983개만 컴파일한다. 목록은 `tools/phantom-dist-targets.py`가 content report로 `build/phantom_dist_targets.bzl`에 쓴다. `bazel clean` 뒤 빌드한 결과의 content report와 앱 파일 1,431개가 이전과 같다. (PR #49)
- SQLite 네이티브 라이브러리(`libsqliteij`)를 JetBrains의 3.42.0-jb.1 대신 phantom-libs에서 소스로 빌드한 SQLite 3.54.1 빌드로 바꿨다. JetBrains는 JNI 소스를 공개하지 않아 SQLite 3.42 이후의 보안 수정을 받을 수 없었다. JNI 부분은 xerial/sqlite-jdbc를 바탕으로 새로 작성했고 `//platform/sqlite:sqlite-tests_test` 7개가 통과한다. JPS 라이브러리 정의(`.idea/libraries/sqlite_native.xml`)도 같은 좌표로 맞췄다. 배포 빌드가 이 정의에서 jar 이름을 가져오기 때문이다. 이 라이브러리는 기본값이 꺼진 VCS 로그 SQLite 저장소(`vcs.log.index.sqlite.storage`)만 쓴다. (PR #46)
- JCEF를 144(Chromium 144.0.7559)에서 150(Chromium 150.0.7871.129)으로 올렸다. 그 사이의 Chromium 보안 수정을 받는다. 업스트림 263 라인이 쓰는 빌드(`jcefBuild=263-b21`)이며 API 버전(1.21)은 그대로다. JCEF jar는 phantom-libs의 `jetbrains-hosted-3`에 미러했다. (PR #43)
- 앱에 들어가는 JGit 6.6.1(jb)과 ini4j 0.5.5-2를 보안 패치한 빌드로 바꿨다. JGit은 CVE-2025-4949(XXE, `ManifestParser`·`AmazonS3`), ini4j는 CVE-2022-41404(`fetch()`의 무한 변수 치환)를 고친다. 바뀐 클래스만 원래 jar에 바꿔 넣은 파일을 phantom-libs의 `phantom-patched-1` 릴리스에서 받는다(패치와 해시는 phantom-libs의 PATCHED.csv, patches/). (PR #43)
- `phantom-theme`과 `phantom-sync`를 이 저장소에서 빌드하지 않고 Phantom-plugins의 `theme`·`sync`를 phantom-libs 릴리스에서 받아 번들한다. 플러그인 ID(`dev.phantom.theme`, `dev.phantom.sync`)는 그대로라 기존 설정이 유지된다 (PR #42).
- 쓰는 곳이 없는 gRPC 라이브러리 두 개(`intellij.libraries.grpc`, `intellij.libraries.grpc.netty.shaded`, 약 34MB)를 앱에서 뺀다. 쓰던 Grazie는 grammar 플러그인으로 바뀌었고, 프로세스 mediator는 어느 제품에도 들어가지 않는다.
- 이모지 선택기(`intellij.emojipicker`, 리소스 약 10MB)를 앱에서 뺀다. 리눅스에서만 켜지는 기능이라 Phantom이 빌드하는 macOS와 Windows에서는 쓰이지 않는다. (PR #41)
- VS Code·Eclipse·다른 JetBrains IDE에서 설정을 가져오는 첫 실행 마법사(`intellij.ide.startup.importSettings`, 약 28MB, 모든 OS용 sqlite 네이티브 포함)를 앱에서 뺀다. 이전 Phantom 버전의 설정을 이어받는 기본 가져오기는 그대로다.
- 번들 Java 런타임을 JBR 25.0.4b508.27에서 25.0.4.1b508.41로 올렸다. 유출된 eMudhra 중간 인증서 8개를 신뢰하지 않게 하는 JetBrains 수정이 들어 있다(같은 b508 계열). Java4Shell(CVE-2026-114938, x86-64만 해당)은 이 빌드에 아직 수정이 없다. `NOTICE.txt`의 JBR 소스 태그도 `jbr-release-25.0.4.1b508.41`로 바꿨다 (PR #20).

- Grid Core 대신 Phantom Grid(`dev.phantom.grid`, Phantom-plugins `grid/`)를 번들한다. 데이터 그리드와 CSV/TSV 표 편집기이며, Phantom이 번들하는 IntelliJ 플러그인 중에는 이것에 의존하는 것이 없고 Phantom-plugins의 ai-integrations·mybatis가 쓴다. `intellij.grid.core.plugin`은 별칭으로, 콘텐츠 모듈 이름은 그대로 남는다. CSV 사용 통계 수집기와 리스너(`intellij.grid.impl.ide`)는 빠졌다.
- IntelliJ Git 플러그인(`intellij.vcs.git`, `Git4Idea`) 대신 Phantom-plugins의 Git Support(`dev.phantom.git`, `git/`)를 번들한다. `Git4Idea`는 별칭으로, 콘텐츠 모듈 이름(`intellij.vcs.git.backend`, `intellij.vcs.git.shared` 등)은 IntelliJ 것 그대로 남아 GitHub, Settings Sync, Groovy 등 Git에 의존하는 플러그인과 모듈은 그대로 연결된다. 사용 통계 수집기가 빠지고 원격 API는 애플리케이션 서비스다. `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`에 `git`을 더하고 `.removing("intellij.vcs.git")`을 넣었다.
- Grazie(Phantom Natural Languages, `dev.phantom.grazie`) 대신 Phantom Grammar(`dev.phantom.grammar`, Phantom-plugins `grammar/`)를 번들한다. JetBrains의 비공개 `ai.grazie.*` 라이브러리를 빼고 LanguageTool만으로 문법·철자를 검사한다. 예전 ID와 `tanvd.grazi`는 별칭으로 남아 Kotlin·Python·TOML·Maven의 문법 검사 연결은 그대로다. 같은 빌드부터 터미널(0.2.0)도 라이선스 없는 JetBrains 명령 스펙·ML 순위 라이브러리 대신 Fig 명령 스펙(MIT)으로 만든 판이다. `PHANTOM_PLUGINS_BUNDLED`와 `bundled_from_plugins`에서 `grazie`를 `grammar`로 바꿨다.
- 라이선스가 없는 JetBrains 완성 순위 ML 모델을 뺐다. Kotlin 플러그인 zip에서 콘텐츠 모듈 `intellij.kotlin.completion.ml`(`completion-ranking-kotlin`, `kotlin_model/`·`kotlin_features/`)을 뺐고(`KotlinPluginBuilder.kt`, `kotlin.plugin.k2.xml`), Java 플러그인의 `intellij.java.impl.jar`에 함께 묶이던 `completion-ranking-java`(`java_model/`·`java_features/`)를 `excludeModuleLibrary`로 뺐으며(`JavaPluginLayout.kt`) 그 모델을 읽는 `JavaMLRankingProvider` 등록을 주석 처리했다(`JavaPlugin.xml`). 두 언어의 완성 목록은 ML 재정렬 없이 기본 순서로 나온다. 빌드된 IDE와 Kotlin·Maven zip을 훑어 다른 `*_model/`·`model.bin`이 없음을 확인했다 (07838d4514, PR #12).
- Tips of the Day에서 "Try YouTrack" 팁(`YouTrack_integration.html`, ja/ko/zh 번역과 이미지)을 뺐다. JetBrains 팁 아티팩트(`tips-intellij-idea-community` 241.62)를 커스터마이징 jar에 묶을 때 `JarPackager.kt`가 이 팁 파일들과 아티팩트의 팁 목록 `META-INF/tips-intellij-idea-community.xml`을 거르고, YouTrack 항목만 뺀 같은 이름의 목록을 `community-resources`에서 넣는다. 번들 팁 중 "IntelliJ IDEA Community"라는 이름이 나오는 것은 이 팁의 번역뿐이었다 (f880d444b2, PR #12).
- Search Everywhere 액션 탭의 오타 교정이 JetBrains의 비공개 `ai.grazie.spell:gec-spell-engine-local`과 그 영어 Hunspell 사전 대신 모듈 안의 `ActionsSpeller`를 쓴다. 액션·설정 이름에서 모은 단어 중 길이에 따라 1~3 편집 거리(인접 글자 바꿈 포함) 안의 단어를 거리와 빈도로 고른다. 3글자 미만이나 라틴 문자가 없는 단어는 고치지 않고, 액션 이름에 없는 일반 영어 단어로는 고치지 않는다. typos 모듈에서 `intellij.libraries.ai.grazie.spell.gec.engine.local`과 `intellij.grazie.core` 의존을 뺐다.
- 오늘의 팁 내용(HTML·이미지·일본어/한국어/중국어 번역, 등록 파일 `META-INF/tips-intellij-idea-community.xml`의 팁 138개)을 싣던 JetBrains 라이브러리 `tips-idea-ce`(POM 기준 Apache 2.0, JetBrains 저장소에서만 받음)를 `intellij.idea.community.customization`에서 빼서 더는 배포하지 않는다. Phantom에 필요 없는 내용이고 JetBrains 저장소 의존이 하나 준다. 이 라이브러리용으로 넣었던 팁 목록 사본(`community-resources/.../tips-intellij-idea-community.xml`)과 YouTrack 팁 필터(`JarPackager.kt`)도 지웠다. 시작 시 팁 표시는 원래 꺼져 있다. 오늘의 팁에는 이 리포에 파일이 있는 팁만 남는다: 번들에서는 `CommandCompletion`, `InjectSQLLanguage`, 설치하면 Ant의 `CreateTargetTag`와 Kotlin의 `Kotlin_mix`, `Kotlin_Java_convert`. Kotlin이 등록하던 `Kotlin.html`, `Kotlin_project.html`은 업스트림부터 파일이 없어 등록을 뺐다. 생산성 가이드는 팁 파일이 없는 기능을 원래대로 목록에서 뺀다. PyCharm용 `tips-pycharm-community`는 Phantom이 빌드하지 않는 제품이라 그대로 두었다.

### JetBrains 서버 통신
- 피드백 양식이 JetBrains로 보내던 것과 Tips of the Day 좋아요/싫어요를 막았다. 모든 피드백 양식이 거치는 `submitFeedback`(`GeneralFeedbackSubmit.kt`)이 `-Dphantom.jetbrains.services=true`가 아니면 요청도 지역 URL 매핑 조회도 없이 바로 돌아온다(콜백도 부르지 않으므로 블록 기반 대화상자는 감사 알림을 띄우지 않고, `LocalizationFeedbackDialog`는 알림을 건너뛴다). 레지스트리 `tips.of.the.day.feedback`의 기본값을 staging에서 nowhere로 바꾸고, 그 값이면 `TipPanel`이 좋아요/싫어요 줄을 숨긴다. 테스트 `training.ui.FeedbackSubmissionGateTest`(모듈 `intellij.featuresTrainer.tests`)를 더했다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). 이 커밋은 번들 밖 `featuresTrainer`의 `OnboardingFeedbackForm`이 띄우는 감사 알림을 건드리지 않았다 (6e71ed80c4, PR #2).
- `OnboardingFeedbackForm`(번들 밖 `featuresTrainer` 소속)이 보내지 않았는데 감사 알림을 띄우던 것을, 다른 호출자와 같은 게이트 조건(`OnboardingFeedbackForm.kt:279`)으로 건너뛰게 했다. `FeedbackSubmissionGateTest`가 요청을 기다리는 시간도 1초에서 15초로 늘렸다(게이트가 없으면 요청 앞에 느린 지역 매핑 조회가 끼어들 수 있다). 한계: 그보다 느린 매핑 조회는 고장 난 게이트를 통과시킬 수 있다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (5185f60295, PR #2).
- 프레임워크 라이브러리 버전 목록을 JetBrains(`www.jetbrains.com/idea/download-assistant.xml`, `frameworks.jetbrains.com`)에서 받지 않는다. New Project 마법사에서 Groovy를 고르거나 Add Framework Support 등의 라이브러리 패널이 열리면 사용자가 다운로드를 고르기 전에 목록을 받던 것이다. `-Dphantom.jetbrains.services=true`가 아니면 `LibrariesDownloadAssistant`가 번들 목록(Groovy는 `groovy.sdk.xml`, 받는 곳은 repo1.maven.org)을 쓴다. 번들 목록에 없는 최신 버전은 보이지 않을 수 있다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (9f63128702, PR #7).
- Windows에서 Git 저장소가 있는 프로젝트를 열고 1분 쉬면 설치된 Git(MSYS·Cygwin, Git for Windows 포함)의 새 버전을 확인하려고 JetBrains의 Git 설치 목록(`download.jetbrains.com/jdk/feed/v1/gits.json.xz`)을 받던 유휴 감시를 `-Dphantom.jetbrains.services=true`가 아니면 켜지 않는다. 그래서 설정 진입점의 Git 업데이트 항목도 나타나지 않는다. Git이 없을 때 오류의 "Download and Install"은 사용자가 고른 다운로드라 그대로 이 목록을 받는다. Grazie 언어 파일과 JBR 런타임 목록은 호출자를 따라가 보니 사용자가 그 다운로드를 고를 때만 받으므로 바꾸지 않고 문서에 근거를 적었다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (0cfd99df92, PR #7).
- 내장 서버의 기동 보고서 REST(`/api/startUpMeasurement`)를 JetBrains ij-perf 페이지(`ij-perf.jetbrains.com`, `ij-perf.labs.jb.gg`)가 브라우저에서 읽던 허용을 `-Dphantom.jetbrains.services=true`일 때만 둔다. 그 밖에는 다른 REST 서비스와 같은 기본 origin 검사(로컬 origin은 통과, 그 밖의 origin은 사용자에게 호스트를 신뢰할지 묻는 대화상자)를 쓴다. 보고서를 밖으로 보내는 호출은 아니었고, 이 포크의 벤치는 이 서비스를 쓰지 않는다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다 (b69ed94772, PR #7).
- 플러그인 목록·다운로드 요청(plugins.jetbrains.com)에 기기 ID(`uuid=`)를 붙이지 않는다. `PluginDownloader.getMarketplaceDownloadsUUID()`가 기기 ID를 만들지 않고 빈 값을 돌려주며, 빈 값이면 `MarketplaceUrls.getPluginDownloadUrl`이 `uuid` 파라미터를 빼고, `RepositoryHelper`의 플러그인 목록 요청도 붙이지 않는다. 함께 JetBrains 사용권 계약·개인정보 처리방침·동의 목록(`eua.html`, `euaCommunity.html`, `PrivacyPolicy.html`, `privacyEap.html`, `consents.json`, `localConsents.json`)을 `intellij.platform.resources`에서 배포 jar로 넣지 않는다(`IdeaCommunityProperties.kt`의 `excludeFromModule`; 이 파일들은 vendor가 JetBrains일 때만 읽히거나 없어도 빈 목록이 된다. `eua.html`은 SBOM 생성기가 소스에서 읽어 소스에는 남긴다). 오류 보고 대화상자의 "privacy policy에 동의" 문구는 보고를 받는 쪽의 정책이 적용된다는 중립 문구로 바꿨다 (15f3743d6f, PR #12).
- 사용 통계(FUS)는 `-Dphantom.usage.statistics=true`가 아니면 아무것도 모으지 않는다. 플래그가 꺼져 있으면 모든 레코더가 빈 로거를 받아 이벤트를 검증·기록·저장하지 않고(`StatisticsEventLogProvidersHolder`), `StatisticsUploadAssistant.isCollectAllowed`도 거짓이다. 켜면 이벤트는 코드에 선언된 필드 규칙(`{enum:..}`, `{regexp#..}`, `{util#..}` 등)으로만 검증해 로컬 이벤트 로그(`<system>/event-log-data/logs`)에 쓴다. 전송기는 없다: 원격 설정·메타데이터 내려받기, 외부 업로더 프로세스, 동의 직후 업로드를 지웠고 `isSendAllowed`는 항상 거짓이다. 소스도 라이선스도 없는 JetBrains 라이브러리 `com.jetbrains.fus.reporting:ap-validation-all`을 더 이상 싣지 않는다. 쓰던 형식(`LogEvent`, `EventContext`, `EventLogSystemEvents`, `StatisticsEventEscaper` 등)은 이 리포의 사용처만 보고 새로 썼고, Apache 2.0 소스가 공개된 `fus-api`는 그대로 둔다. 번들 밖 통계 개발 도구 모듈(`intellij.platform.statistics.devkit`, `.envTests`)과 원격 검증·전송을 시험하던 테스트는 지웠다. 단위 테스트에서는 플래그가 켜진 것처럼 동작한다. 바뀐 모듈의 Bazel 빌드와 `intellij.platform.statistics.tests`(29개), `intellij.platform.tests`의 `com.intellij.internal.statistics.*`(397개) 테스트가 통과했고, 빌드한 앱에서 플래그를 켜고 끈 동작은 확인하지 않았다.

### 성능·인덱싱
- macOS 빌드가 번들 JBR에 `lib/server/classes_coh.jsa`를 만든다(`java -XX:+UseCompactObjectHeaders -Xshare:dump`, `PhantomCdsArchive.kt`). 번들 vmoptions의 `-XX:+UseCompactObjectHeaders`에서는 JVM이 이 파일만 쓰는데 JBR(b508.27, b508.41)에 없어서, IDE가 클래스 데이터 공유 없이 시작했고 런처의 `-XX:+AutoCreateSharedArchive`(동적 AppCDS)도 아카이브를 만들지 못했다. 빌드 머신과 같은 OS·아키텍처일 때만 만들므로 macOS에서 빌드하는 Windows판에는 아직 없다 (e623ad9711, PR #28).

### 기능(Phantom 플러그인)
- Phantom 코드의 사용자에게 보이는 문구 39개를 리소스 번들로 옮겼다. 문구는 그대로다. 새 번들 `PhantomIdeBundle`(`platform-impl`: GitHub 플러그인 토큰 액션·대화상자, 플러그인 추천 알림, Spring Boot 새 프로젝트 항목, 알림 그룹 이름 17개)과 `PhantomLangBundle`(`lang-impl`: Endpoints 도구 창과 액션, "Generate HTTP Request" 11개), 그리고 `IdeBundle`(`toolwindow.stripe.Endpoints`)과 `GithubBundle`(gh CLI·디바이스 플로 로그인 문구와 오류 10개)에 키를 더했다. `SetGitHubPluginToken` 액션은 `PlatformActions.xml` 끝의 자체 `<actions resource-bundle>` 블록으로 옮겼다. 고정 카테고리 중 맞는 것이 없어 가장 가까운 기능(Phantom 플러그인)에 적었다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). `GithubBundle`은 이 리포의 `plugins/github` 소스이고, 번들의 GitHub 플러그인은 ed67704350부터 Phantom-plugins가 빌드한 판이다 (a1313a347c, PR #2).

### 빌드·배포·서명
- Bazel이 공개 라이브러리를 JetBrains 다운로드 프록시(`cache-redirector.jetbrains.com`)를 거치지 않고 원래 출처(Maven Central, Google Maven, GitHub, npm, PyPI 등)에서 받는다(`build/bazel_downloader.cfg`). `lib/MODULE.bazel`의 URL은 그대로 두고 다운로더가 `cache-redirector.jetbrains.com/<출처>/...`를 `<출처>/...`로 바꾼다. 고정된 sha256은 같다. 원래 출처가 없는 JetBrains 호스팅 아티팩트(packages.jetbrains.team, JBR)는 계속 JetBrains에서 받는다.
- 호환성 검사: since-build가 날짜형(262.261123)인 플러그인도 Plugin Verifier로 검사한다. 검사용 사본에서만 since-build를 브랜치(262)로 바꾼다.
- packages.jetbrains.team에 있는 빌드 아티팩트 중 Maven Central에 sha256이 같은 파일이 있는 15개를 Maven Central에서 받는다. rd 라이브러리(`com.jetbrains.rd`, 배포 앱에 들어감) 10개는 `build/bazel_downloader.cfg`의 그룹 rewrite로, Kotlin 테스트용 표준 라이브러리 5개(`kotlin-stdlib` 2.1.21·1.9.22, `-common`, `-jdk8`, `-js`)는 `plugins/kotlin/kotlin_test_dependencies.bzl`의 URL을 바꿨다. `lib/MODULE.bazel`은 고치지 않았다. 나머지 JetBrains 전용 아티팩트는 이슈 #29로 정리했다 (7d77dac2cb, 203cffa30f, PR #30).
- 앱에 들어가는 오픈소스 라이브러리 중 JetBrains(`packages.jetbrains.team`)만 호스팅하는 39개와 그 sources jar 35개를 내용 그대로 [newkayak12/phantom-libs](https://github.com/newkayak12/phantom-libs) 릴리스 `jetbrains-hosted-1`에 올리고, `build/bazel_downloader.cfg`가 그 파일 이름만 릴리스 URL로 보낸다. 고정 sha256은 그대로다. 업스트림 라이선스 목록이 JetBrains 자체로 적은 4개(`debugger-agent`, `debugger-memory-agent`, `rwmutex-idea`, TeamCity `serviceMessages`)도 소스 헤더와 업스트림 저장소상 Apache 2.0이라 sources jar와 함께 `jetbrains-hosted-2`에 올리고, `CommunityLibraryLicenses.kt`에도 Apache 2.0으로 적는다.
- `product-info.json`의 `layout`이 Java·Gradle 플러그인의 모듈 jar 22개를 실제로 없는 `lib/modules/`에 있다고 적던 것을 배포본의 실제 jar(플러그인 레이아웃이 정한 이름 또는 `lib/<모듈>.jar`)로 고친다. Plugin Verifier 같은 도구가 그 플러그인을 읽지 못하던 문제다.
- 릴리스 checkout과 Gradle wrapper 검증 액션을 커밋 SHA로 고정하고, wrapper 검증을 PR·main 변경에 맞췄다. GitHub Actions 의존성은 Dependabot이 주간 묶음 PR로 갱신한다.
- Jewel의 YouTrack 토큰·단일 커밋 형식 검증은 업스트림 저장소에서만 실행하고, Phantom PR에는 포크의 커밋 규칙을 적용한다. Jewel 빌드·테스트 작업은 유지한다.
- SonarQube가 찾은 품질 도구의 Git PATH 의존, 정렬 비교, 복잡도 문제를 수정했다 (PR #9).
- Cloud의 새 분석 모드에서 테스트 코드가 제품 커버리지에 포함되지 않도록 테스트 파일을 분석 대상에서 제외했다. 테스트 실행과 Codecov 보고서는 유지한다 (PR #9).
- 부분 체크아웃에 없는 업스트림 파일을 Codecov의 자동 줄 보정이 읽다가 실패하지 않도록, 생성된 LCOV를 보정 없이 올리게 했다 (PR #9).
- 분석 CI는 처음 체크아웃부터 포크 경로만 받아 대형 업스트림 파일 전체를 내려받지 않도록 했다 (PR #9).
- 품질 도구의 실제 CLI 성공·잘못된 옵션·누락된 기준 이력 경로를 회귀 테스트로 검증한다 (PR #9).
- Cloud가 포함 패턴도 무시하는 것을 실제 재실행에서 확인해, 분석 직전 sparse checkout으로 포크 소스만 남기도록 범위 제한을 보강했다 (PR #9).
- Codecov 업로더 서명 키 변경에 대응한 공식 Action v7.1.1로 갱신했다. 서명 검증은 유지한다 (PR #9).
- SonarQube Cloud가 `sonar.sources`를 무시하는 분석 모드에서도 포크 소스만 검사하도록 포함 패턴을 명시했다. 실제 첫 분석이 업스트림 전체를 읽다가 메모리 부족으로 실패한 데 따른 수정이다 (PR #9).
- 이슈·PR 라벨 추가와 라벨 동기화, 병합 후 연결 이슈 종료를 자동화했다. Node 품질 도구의 LCOV 생성 및 SonarQube·Codecov 연결을 추가했으며 외부 서비스 자격 증명이 없으면 미실행으로 표시한다 (PR #9).
- 제공된 `default-ruleset.json`의 `protect-main` 설정을 기준으로 PR용 ruleset을 맞추고 승인 0명은 유지하면서 관리자 우회를 추가했다 (PR #9).
- PR 작성자를 assignee로 지정하고 작성자를 제외한 collaborator에게 리뷰를 자동 요청한다. 초안은 리뷰 요청을 미루고 ready 상태가 되면 요청한다 (PR #9).
- Phantom 변경 파일용 품질 검사와 PR CI를 추가했다. 스크립트 구문, GitHub Actions와 Bash 정적 분석을 검사하며, IDE 모듈 컴파일·테스트는 별도 실행한다 (PR #9).
- 기본 브랜치의 PR 필수·승인 0명·관리자 우회 ruleset을 JSON으로 관리한다. 비공개 저장소의 GitHub 요금제 제한(HTTP 403)으로 실제 설정 적용은 대기 상태다 (PR #9).
- 로컬 worktree, AI 개인 설정·세션·자격 증명과 루트 환경 변수 파일을 gitignore에 추가했다. 공유 지침·스킬·IDE 프로젝트 파일은 계속 추적한다 (PR #9).
- `phantom-build.sh`가 추적 파일 `IdeaApplicationInfo.xml`을 더는 고쳐 쓰지 않는다. 표시 버전(빌드 날짜의 YY와 MM.DD)을 macOS와 Windows `installers.cmd` 실행에 `-Dphantom.version.major`, `-Dphantom.version.minor`로 넘기고, `ApplicationInfoPropertiesImpl`이 XML보다 먼저 읽어 `product-info.json`, Info.plist, Windows 설치 파일, 패키지에 넣는 `<version>`에 반영한다. 추적 XML에는 자리표시자 `0` / `0.0`이 남아서 소스에서 IDE를 실행하거나 `installers.cmd`를 직접 실행하면 버전 0.0으로 보인다. 빌드 뒤 작업 트리가 더러워지던 것과 릴리스 태그 커밋의 버전이 릴리스 버전과 다를 수 있던 것이 이 변경으로 사라진다. `ApplicationInfoPropertiesTest`에 테스트 2개를 더했다. 코드만 바꿨고 빌드와 테스트는 아직 하지 않았다(리뷰어의 빌드 대기). PR #2 병합(fe53ddfed4)으로 main에 들어오면서 7fe0ab9436이 쓴 `26` / `11.13`도 이 자리표시자로 바뀌었다 (048852ca23, PR #2).
- `phantom-sign.sh`가 "Phantom Local" 인증서를 찾지 못하면 이유를 stderr에 쓰고 종료 코드 1로 실패한다(전에는 `exit 0`). `PHANTOM_SIGN_OPTIONAL=1`이면 경고만 쓰고 0으로 끝나며, `phantom-install.sh`가 이 값을 켜서 인증서 없는 머신에서도 설치는 ad-hoc 서명으로 끝난다. 헤더 주석이 login 키체인이라고 잘못 적던 것을 전용 키체인 `phantom-signing.keychain-db`로 고쳤다. (2aa424a169, PR #4)
- `phantom-install.sh`, `phantom-sign.sh`, `phantom-bench.sh`의 앱·설정·픽스처 경로를 환경변수로 바꿀 수 있다: `PHANTOM_APP`(세 스크립트, `phantom-sign.sh`는 인자가 먼저), `PHANTOM_CONFIG_DIR`, `PHANTOM_BENCH_FIXTURE`(벤치, `--fixture`가 먼저). 기본값은 전과 같다. (256d3b35c3, PR #4)
- 로컬 서명 인증서를 저장소 스크립트로 만든다. `./phantom-sign.sh --setup`이 macOS 기본 도구(`/usr/bin/openssl`, `security`)만으로 전용 키체인, 비밀번호 파일, 자체 서명 코드 서명 인증서 "Phantom Local"(Apple 인증서 아님, 10년)을 대화상자 없이 만들고, 이미 있으면 아무것도 바꾸지 않는다. `phantom-install.sh`는 인증서가 없으면 서명 전에 이를 실행한다(`PHANTOM_SIGN_SETUP=0`이면 건너뜀, 실패하면 전처럼 ad-hoc 서명). 서명은 `codesign --keychain`으로 전용 키체인을 가리켜 키체인 검색 목록과 신뢰 설정을 바꾸지 않는다. 기존 키체인·인증서로 서명한 결과(designated requirement)는 전과 같다 (9e69bba61f, PR #4).
- `phantom-sign.sh --setup`이 가져오기와 키 접근 목록 설정 사이에서 멈춘 설치(codesign이 쓸 수 없는 인증서가 남고 다시 실행하면 할 일이 없다고 하던 것)를 다시 실행할 때 접근 목록을 다시 건다. 자동 잠금 설정은 이번 실행이 만든 키체인에만 걸고, .p12 비밀번호를 환경변수로 내보내지 않으며, 임시 키 폴더를 INT, TERM, HUP에서도 지운다 (3929d7998b, PR #4).
- `phantom-install.sh`가 `PHANTOM_APP`을 지우고 바꾸므로 `.app`으로 끝나는 절대 경로가 아니면 거부한다 (b5a1bc773a, PR #4).
- `phantom-install.sh`가 실행 중인 Phantom을 끌 때 번들 ID(`tell application id "dev.phantom.idea"`)가 아니라 `PHANTOM_APP` 경로에서 실행 중인 프로세스(`pgrep -f "$APP/Contents/MacOS/idea"`)의 프로세스 ID로 종료를 요청한다(`osascript -l JavaScript`의 `NSRunningApplication.terminate`, Quit 메뉴와 같은 Apple 이벤트라 IDE가 상태를 저장한다). 전에는 두 복사본이 떠 있으면 다른 쪽이 꺼지고 교체할 쪽은 180초 대기 끝에 실패할 수 있었다. 기다리기와 재시작(`open "$APP"`)은 전처럼 그 경로 기준이다. 같은 번들 ID의 스테이 오픈 애플릿 두 개로 확인했고 실제 Phantom 두 개로는 실행하지 않았다 (eff1276d6a, PR #5).
- `main`은 관리자도 pull request로만 바꾼다. GitHub 규칙 `.github/rulesets/main.json`(PR 필수, force push·삭제 금지, 관리자는 PR 안에서만 우회)은 저장소를 공개로 바꾼 뒤 적용한다. `phantom-release.sh`는 `origin/main`에 이미 있는 커밋에서만 릴리스하고(직접 push하지 않음), Phantom-plugins의 marketplace.json도 pull request로 고친다. `CLAUDE.md`(AGENTS.md를 불러오고 Git·Claude 규칙을 더함)와 `.claude/settings.json`을 넣고, `docs/superpowers`를 지우고 에이전트 작업 파일을 무시한다 (PR #10).
- `phantom-build.sh`가 번들할 Phantom-plugins 플러그인 28개를 체크아웃에서 빌드하지 않고, `build/phantom-plugins-bundle.txt`가 가리키는 newkayak12/phantom-libs 릴리스에서 zip을 받아 sha256을 확인한 뒤 푼다. 그래서 Phantom-plugins 읽기 권한 없이도 빌드된다. `PHANTOM_PLUGINS_REPO`를 주면 전처럼 그 체크아웃에서 빌드한다. 플러그인이 바뀌면 `tools/phantom-plugins-bundle.sh`로 새 릴리스를 올리고 그 파일을 커밋한다. (PR #36)
- Gradle 플러그인의 Gradle API jar(`gradle-api-9.6.0.jar`)를 JetBrains 저장소(packages.jetbrains.team의 `org.jetbrains.intellij.deps:gradle-api`)에서 받지 않고, services.gradle.org의 Gradle 9.6.0 배포본(Gradle이 공개한 sha256으로 확인)으로 빌드할 때 직접 만든다(`lib/gradle-api.bzl`, IntelliJ의 `build/dependencies/gradle-api-deps` 레시피와 같은 내용, 결과 sha256 고정). 클래스 파일은 JetBrains 것과 같고 Gradle의 LICENSE·NOTICE가 `META-INF/gradle/`에 더 들어간다.
- Gradle 플러그인에서 중복 Groovy(`groovy.jar`, 20 MB)와 `groovy-ant.jar`, `lib/ant/`를 뺐다. Groovy는 Groovy Scripting의 `intellij.libraries.groovy` 모듈을 쓴다.
- Gradle API jar 안의 Gradle 의존성(`org.gradle.internal.impldep`, JCIFS·JNA LGPL 2.1 포함)을 `NOTICE.txt`와 서드파티 목록(`CommunityLibraryLicenses.kt`)에 적었다.
- 번들한 Phantom-plugins zip이 Phantom 빌드와 호환되는지 확인하는 `tools/phantom-plugin-compat.sh`와 `phantom-plugin-compat` workflow를 더했다. phantom-libs에서 받은 zip의 sha256, since/until-build, 필수 의존성을 정적으로 확인하고 JetBrains IntelliJ Plugin Verifier(Apache 2.0)를 Phantom.app에 돌려, 호환성 문제가 있으면 실패한다. 알려진 오탐은 `build/phantom-plugin-compat-ignore.txt`에 사유와 함께 적는다. v26.11.19.2 기준으로는 30개 중 14개 플러그인에서 문제가 보고된다(Java·구조 뷰·테스트 플러그인 클래스 해석 실패, coverage 설명자 중복 등). (PR #45)

### 벤치마크
- 정적 게이트 `compare`가 baseline 앱의 빌드 날짜(빌드 번호 `262.<YYMMDD>`)보다 번들 목록 `IdeaCommunityProperties.kt`의 마지막 커밋이 늦으면 stderr에 `STALE BASELINE`을 쓰고, 회귀가 없어도 종료 코드 2로 끝난다. 지금의 `static-baseline.json`(26.10.27)은 이 검사에 걸린다. 빌드된 앱이 있어야 다시 만들 수 있어 파일은 그대로 두고 (e29d855fff, PR #4).
- baseline 앱의 빌드 번호에 날짜가 없으면(`262.SNAPSHOT` 등) 낡았다고 판정하지 않고 stderr에 안내만 쓰고 나이 검사를 건너뛴다(git 기록이 없을 때와 같다) (b5a1bc773a, PR #4).
- 픽스처 복사본 `out/phantom-bench/work/fixture`를 어디서 복사했는지 `out/phantom-bench/work/fixture.source`에 적는다. 같은 원본이면 복사본을 그대로 다시 쓰고, 다른 원본이거나 기록이 없으면 복사본을 바꾸며 그 위에서 예열한 `work/system-*`도 지운다. 전에는 다른 `--fixture`나 `PHANTOM_BENCH_FIXTURE`가 조용히 무시됐다. 상대 경로 픽스처는 저장소 루트가 아니라 스크립트를 부른 폴더 기준이고, 없는 픽스처 폴더는 복사 전에 실패한다 (ecf939da71, PR #5).
- 얕은(shallow) git 클론에서는 정적 baseline의 나이 검사를 건너뛰고 stderr에 안내(`git fetch --unshallow`)를 쓴다. 얕은 클론에서는 가장 오래 받은 커밋이 번들 목록의 마지막 변경으로 보여 모든 baseline이 `STALE`(종료 코드 2)로 나왔다 (aadcfe0bc7, PR #5).

### 문서
- 두 저장소의 README·한국어 안내에서 현재 번들 구성과 구현된 플러그인 상태를 바로잡고, AI·자동화·커버리지 범위를 연결했다. 기여·보안·행동 규칙, 이슈·PR 템플릿과 기반 구성 현황 문서로 프로젝트 작업의 진입점을 정리했다. 법적 출처 고지는 유지했다.
- 기여·보안 제보·행동 규칙을 Phantom 유지관리 범위로 고치고, Claude·Codex 커밋·푸시 스킬의 업스트림 절차와 충돌을 없앴다. 로컬 환경 파일과 에이전트 인증·설정 파일의 무시 규칙을 보완했다 (PR #9).
- README 첫 소개와 배지에서 IntelliJ 업스트림 표시를 걷어내고 Phantom 중심으로 정리했다. 법적 출처는 해당 절에 남기고 실제 SonarQube 품질 게이트 배지를 연결했다 (PR #9).
- 영어·한국어 README에 main 기준 품질·분석 CI와 Codecov 배지를 추가하고, SonarQube는 프로젝트 설정 전까지 설정 대기로 표시한다 (PR #9).
- 소개를 Phantom 기능 중심으로 정리하고 IntelliJ 소스 열기·실행 안내를 추가하고 오래된 생성기 안내를 바로잡았다 (PR #9).
- Claude Code가 공통 AGENTS.md를 읽도록 CLAUDE.md를 추가하고, Claude·Codex 작업별 스킬 선택·검증 안내와 Phantom에서 업스트림 AI 지침 생성기를 실행하지 않는 이유를 정리했다 (PR #9).
- 없는 `./build/jpsModelToBazel.cmd`를 가리키던 안내를 이 리포의 유일한 생성기 `./build/jpsModelToBazelCommunityOnly.cmd`로 고쳤다(AGENTS.md와 같은 문구, 이 포크에서 도는지는 미확인). 대상: `.ai/guide.md`, `.ai/tool-permissions.json`, `.agents/skills`와 `.claude/skills`의 module-dependencies, pseudo-kmp, testing-internals, module-set-pluginization, extract-module, `plugins/maven/.ai/index.md`, `build/libraries-dashboard`. Kotlin `projectModelUpdater.kt`는 ultimate 루트가 있을 때만 그 경로를 부르므로 그대로 두었다. (303c4a933a, PR #4)
- 표식 주석 `// phantom-idea:`(XML은 `<!-- phantom-idea: -->`)이 없던 포크 수정 덩어리 전부에 주석을 달았다. 업스트림 파일 77개와 포크가 더한 `FoojayJdkList.kt`, 모두 78개 파일에 주석 137줄을 더했고 지운 줄은 없다(동작 변화 없음). 이로써 주석을 달 수 있는 수정 업스트림 파일 101개 모두 수정 덩어리마다 표식이 있다(그중 21개 파일의 33개 덩어리는 옛 `// Phantom:` 형식만). 고정 카테고리 중 맞는 것이 없어 문서에 적었다 (ea745003d1, PR #5).
- 옛 표식 `// Phantom:`을 `phantom-idea:`로 통일했다(`//` 20줄, KDoc `*` 3줄, XML `<!--` 1줄, `#` 형식은 없었다). 22개 파일의 표식 주석 24줄만 바꿨고 코드는 건드리지 않았다. 코드가 아니라 주석이라 빌드와 테스트는 하지 않았다 (ee343c00f6, PR #7).
- 저장소 루트에 `PRIVACY.md`(영어)와 `PRIVACY.ko.md`를 더했다: Phantom과 플러그인은 아무것도 수집하지 않고 Phantom 서버도 없다는 점, 밖으로 나가는 연결 표(GitHub의 `marketplace.json`과 릴리스, schemastore.org 카탈로그와 끄는 곳, foojay·JDK 배포처, start.spring.io, Maven Central, 사용자가 고를 때만 가는 JetBrains, `-Dphantom.jetbrains.services=true`), AI Integration 플러그인의 데이터 흐름, 자격 증명 저장, 문의처(GitHub 이슈). README 두 개의 "사용 통계 업로드는 동의 설정이 결정한다"를, Phantom에는 사용 통계 동의 항목이 없어(동의 파일 `consents-Phantom.json`이 없고 공유 동의 파일도 `~/Library/Application Support/Phantom` 아래라 JetBrains IDE와 섞이지 않는다) 켤 수 없다는 설명으로 바꾸고 PRIVACY 문서를 링크했다 (c7559f5fe2, PR #12).
- 새 플러그인·모듈이나 큰 변경의 계획은 `docs/`가 아니라 GitHub 이슈에 쓰고, 구현 PR에 `Part of #N`/`Closes #N`을 달아 머지 때 이슈가 닫히게 하는 규칙을 AGENTS.md와 CONTRIBUTING.md에 추가. 이슈에는 비공개 정보를 쓰지 않는다 (PR #23)
- 개발 문서 디렉터리를 지우고, 남은 문서와 스킬에서 그 링크를 정리했다. 열려 있던 PR(#11, #13, #22, #27, #28, #32, #34)을 합친 뒤 이력을 커밋 2개로 압축했다.

### 버그 수정

- GitHub 브라우저 로그인 client id가 비어 있는 동안 "Log In via GitHub" 진입점(계정 추가 메뉴, 클론 대화상자, Pull Request 선택기)을 숨기고 토큰 로그인만 보여 준다. 설정되어 있으면 이전과 같다. 토큰 로그인 요청은 확인 창 없이 토큰 입력 창을 바로 연다. 번들의 GitHub 플러그인은 Phantom-plugins 사본이라 배포 앱에는 그쪽 github 0.1.1로 반영된다 (f42b7a6de3, 2881746b8b, 8e1aebcc8a, PR #3).

## v26.11.13.1 (2026-11-13)

### 브랜딩·버전
- 버전을 26.10.27로 올렸다 (`IdeaApplicationInfo.xml`) (bf2b90f700).
- 버전을 26.11.13으로 올렸다 (`IdeaApplicationInfo.xml`). 이 값은 태그 커밋 b5ba0196ab에는 있지만 지금 HEAD에는 없다. 뒤에 병합된 PR #2(fe53ddfed4)가 048852ca23의 자리표시자 `0` / `0.0`으로 되돌렸고, 그 뒤로 표시 버전은 빌드 날짜에서 `-Dphantom.version.*`로 들어간다 (7fe0ab9436).

### 번들 구성
- 개발자 도구 런타임(`intellij.dev`), Eclipse 가져오기, JavaFX 커뮤니티, 첫 실행 가져오기 마법사, 신규 사용자 온보딩 투어를 번들 목록에서 제외했다. 소스는 유지한다 (770810693c).
- 첫 실행 설정 가져오기 마법사를 번들에 되돌렸다. 온보딩 투어만 계속 제외한다 (a3e497f72d).
- `phantom-workspaces`를 번들에서 빼고 Phantom-plugins 저장소의 workspaces 플러그인으로 옮겼다. 플러그인 id `dev.phantom.workspaces`와 Advanced Setting id는 그대로다 (8983487a29).
- `phantom-tabs`를 번들에서 빼고 Phantom-plugins 저장소의 tabs 플러그인으로 옮겼다. 공개 플랫폼 API만 쓰도록 바꿔서, 이 플러그인을 위해 넣었던 `TabInfo.labelLeadingComponent` / `isLabelCollapsed` 등 플랫폼 변경을 upstream 상태로 되돌렸다 (b68d97332e).
- `phantom-icons`를 번들에서 빼고 Phantom-plugins 저장소의 Phantom Icon Pack으로 옮겼다. 같이 번들하면 같은 아이콘을 두 번 패치하기 때문이다. ID가 둘이다: 이 리포의 옛 플러그인은 `dev.phantom.icons`(삭제 직전 `plugins/phantom-icons/resources/META-INF/plugin.xml`, `git show 1570329708^:plugins/phantom-icons/resources/META-INF/plugin.xml`로 확인)이고, Phantom-plugins의 Icon Pack은 `dev.phantom.iconpack`이다. 후자는 이 커밋 메시지("Phantom Icon Pack, dev.phantom.iconpack, icons 0.3.0")에만 근거하고 Phantom-plugins 저장소는 읽지 못해 확인하지 못했다. 둘은 서로 다른 ID라 옛 플러그인의 설정이 이어진다고 보면 안 된다 (1570329708).
- Bytecode Viewer, WebP, Modal Commit Interface를 번들 목록에서 제외하고 Phantom-plugins의 `dev.phantom.bytecode`, `dev.phantom.webp`, `dev.phantom.modalcommit`으로 옮겼다. 소스는 upstream 병합을 위해 유지한다 (e2a4ffa4ee).
- GitLab, Eclipse/Visual Studio/NetBeans 키맵, `configurationScript`를 번들에서 제외했다. Android Gradle DSL 모델은 Gradle 버전 카탈로그 대체 경로와 Dependency Analyzer가 쓰므로 유지한다 (7c43a36532).
- Toml, YAML, Shell Script, Lombok, Decompiler, Coverage, TextMate, Markdown을 IntelliJ 원본 대신 Phantom-plugins가 만든 판(`dev.phantom.*` ID, IntelliJ ID는 별칭으로, IntelliJ 콘텐츠 모듈 이름은 그대로 유지)으로 번들한다. 둘을 함께 넣으면 같은 ID와 모듈을 두 번 차지하기 때문이다. 원본 7개는 `.removing`으로 빼고, 따로 넣던 `intellij.lombok`은 `PHANTOM_BUNDLED_PLUGINS`에서 지웠으며, 새 목록 `PHANTOM_PLUGINS_BUNDLED`의 디렉터리를 `getAdditionalPluginPaths()`로 넣는다. `phantom-build.sh`가 Phantom-plugins 체크아웃(저장소 옆, 또는 `PHANTOM_PLUGINS_REPO`)에서 이 플러그인들을 IntelliJ Community 대상으로(`-PplatformFromRepository=true`) 빌드해 `out/phantom-plugins`에 풀고, 체크아웃이나 플러그인 하나라도 없으면 빌드가 실패한다 (9229667cc7).
- Marketplace 목록이 JetBrains가 게시한 플러그인(조직 이름이 `JetBrains`로 시작)을 보여 주지 않는다. Phantom이 유지하는 것은 번들이나 Phantom 플러그인 저장소에 자체 판이 있기 때문이다. 소스 실행, internal 모드, `-Dphantom.marketplace.showAll=true`이면 전처럼 모두 보인다. JetBrains Mermaid(`intellij.mermaid`)를 번들에서 빼고 Phantom-plugins의 Phantom Mermaid(디렉터리 `mermaid`, Phantom 자체 구현 `com.phantom.mermaid`, IntelliJ ID 별칭 없음)를 넣는다 (a464f46719).
- Kotlin 플러그인을 번들하지 않는다(381 MB, Java만 쓰는 프로젝트는 쓰지 않음). `.removing(KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE)`로 빼고 `pluginModulesToPublish`에 넣어 빌드가 zip으로 만든다. `phantom-release.sh`가 릴리스마다 그 zip을 Phantom plugins 저장소(이 저장소 옆 Phantom-plugins 체크아웃, 또는 `PHANTOM_PLUGINS_REPO`)의 릴리스 `kotlin-262.<YYMMDD>`로 올리고 `.phantom/marketplace.json`의 `org.jetbrains.kotlin` 항목이 그것을 가리키게 한다(그 플랫폼 빌드부터). 그 저장소는 Kotlin(`kotlin-stdlib`)이나 Gradle Kotlin DSL 스크립트를 쓰는 프로젝트에 Kotlin을 제안한다. 같은 커밋에서 릴리스 스크립트가 `origin`이 SSH 호스트 별칭(`git@<별칭>:`)일 때도 저장소 이름을 바르게 뽑게 했다 (9433fdbe0f).
- Copyright, EditorConfig, HTML Tools, TestNG, Java I18n, Grazie, Terminal, GitHub도 IntelliJ 원본(`intellij.copyright`, `intellij.editorconfig.plugin`, `intellij.html.tools`, `intellij.testng`, `intellij.java.i18n`, `intellij.grazie`, `intellij.terminal`, `intellij.vcs.github`) 대신 Phantom-plugins가 만든 판(Phantom Copyright 등, `dev.phantom.*` ID, IntelliJ ID는 별칭, IntelliJ 콘텐츠 모듈 이름 유지)으로 번들한다. `PHANTOM_PLUGINS_BUNDLED`와 `phantom-build.sh`의 `bundled_from_plugins`가 17개가 됐다. 이 리포의 `plugins/github`, `plugins/grazie`, `plugins/terminal` 등에 있는 포크 패치가 그 판에 들어 있는지는 이 리포에서 확인할 수 없다 (ed67704350).

### JetBrains 서버 통신
- Toolbox App 연동(REST 서비스, 업데이트 알림, 종료 핸들러, 업데이트 설정의 Toolbox 홍보, Try Ultimate via Toolbox, Copy Toolbox Reference 액션)을 코드에서 삭제했다 (43b28af952).
- 설문(액션 기반, 알림 기반)이 JetBrains로 보내던 것을 `phantom.jetbrains.services`로 막고 CSAT 설문을 기본 꺼짐으로 바꿨다. 이 커밋은 Help | Submit Feedback을 플래그 뒤로 옮기지 않았다. 이 커밋은 `IntelliJIdeaExternalResourceUrls`의 `useInIdeGeneralFeedback`·`useInIdeEvaluationFeedback` 재정의를 지웠고, `feedbackReporter`를 null로 만든 것은 fdd9682290이다. `GeneralFeedbackSubmit`에도 게이트를 걸지 않았다(6e71ed80c4가 이후에 건다). 번들 Kotlin이 `.kt` 편집 때 plugins.jetbrains.com을 호출하던 것을 없앴고, Ultimate 홍보 모듈 `intellij.java.promo`, `intellij.vcs.github.ultimatePromo`, `intellij.kotlin.onboarding.promoter`와 YAML의 "Try Ultimate" 배너를 삭제했다 (3676457781).
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
- 설치 파일 빌드가 플러그인을 zip으로도 만든다. `OpenSourceCommunityInstallersBuildTarget.kt`의 항상 건너뛰는 단계 목록에서 `ARCHIVE_PLUGINS`를 뺐다. 번들하지 않는 Kotlin이 `out/phantom-idea/artifacts/<제품 코드>-plugins/`에 zip으로 나오게 하려는 것이다 (80b3e36e3d).
- `phantom-release.sh`의 Kotlin zip 확인이 `pipefail`에서도 맞게 동작하게 고쳤다. `unzip -l | grep -q` 대신 `unzip -Z1 <zip> Kotlin/lib/kotlin-plugin.jar`로 항목을 확인하고, 버전은 jar를 임시 파일로 꺼내 `plugin.xml`에서 읽는다 (a9c5f07bd5).
- `phantom-release.sh`가 Kotlin zip을 찾을 때 `*.blockmap.zip`을 빼고 `Kotlin-*.zip`만 고른다 (b5ba0196ab).

### 버그 수정
- 다른 플러그인의 ID를 별칭으로 가진 플러그인(예: `dev.phantom.toml`, 별칭 `org.toml.lang`)과 그 원본이 함께 설치되면 ID 충돌로 둘 다 제외되던 것을, 대체하는 쪽만 로드하고 대체된 쪽은 "대체됨"으로 표시하며 그 의존 플러그인은 대체한 쪽으로 해석하도록 고쳤다. 대체한 쪽을 끄면 원본이 다시 로드된다. essential 플러그인은 대체되지 않는다. 새 비적재 사유 `PluginIsReplacedByAnotherPlugin`("Replaced by …")을 더했고, `PluginInitializationSelectPluginsToLoadTest`에서 둘 다 제외를 기대하던 테스트 1개를 바꾸고 3개를 더했다 (04dba66c3a).
- 동적 플러그인 로드 중 에디터 색상 스킴을 다시 읽는 동안 `Theme Phantom Dark refers to unknown color scheme` SEVERE 로그가 나던 것을, 리로드 전 스킴의 스냅샷으로 대체해 고쳤다 (d86195e55c).
- JCEF를 먼저 열 때(시작 시 복원된 Markdown 미리보기) `JBCefApp$Holder <clinit> requests ProxyMigrationService` IDE 오류가 나던 것을, `HttpConfigurable`을 Holder 초기화 전에 만들도록 고쳤다. upstream 코드의 문제다 (e1204fc098).

### 벤치마크
- `phantom-bench.sh`와 `phantom-bench-report.py`를 추가했다. 설치된 앱을 고정 프로젝트 복사본에서 격리 실행하고 플랫폼 자체 보고서로 지표를 뽑는다 (38efaf66bf).
- N회 실행의 중앙값/최소/최대 요약과 baseline 대비 회귀 검사(`compare`)를 추가했다. 시간은 10%, 메모리와 클래스 수는 5%, 멈춤 1회 이상 나빠지면 실패한다 (6767ffc6e1).
- 앱을 띄우지 않고 번들 크기, jar 수, 번들 플러그인, 사전 로드 서비스, 시작 활동 등록 수를 세는 `static` 게이트를 추가했다. baseline은 설치된 26.10.27이다 (4b3ffd9498).

### 문서
- 개발 문서 체계를 만들었다. 이 `CHANGELOG.md`를 만들었다. `README.md`를 개편하고 한국어판 `README.ko.md`를 더했으며, `AGENTS.md`의 이 포크에 없는 경로를 고치고 Phantom 규칙을 넣었다. (ff39526a22, PR #1).

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
- 날짜 기반 버전을 도입했다. 빌드 번호 `262.<YYMMDD>`(플러그인 호환을 위해 브랜치 262 유지), 표시 버전 `YY.MM.DD`, 설정 폴더 `PhantomIDEA`이고, `phantom-build.sh`가 날짜를 찍고 `installers.cmd`를 실행한다. 새 README가 같은 커밋이다 (fc05eddc04).
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

## 기준 소스 (업스트림 가져오기)

- JetBrains/intellij-community 태그 `idea/262.10968.63`(커밋 `8062c061ff33e22041d9f1844db5b8b8a6b5e4d7`)의 소스를 README.md 없이 한 커밋으로 가져왔다. upstream 이력은 포함하지 않는다. `build.txt`는 `262.10968.SNAPSHOT`이다 (d2faebf601).
