# Phantom 설계: 인프라 부가 기능 (SSH, 원격 타깃, 앱 서버) — 낮은 우선순위

- 대상: Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자
- 기준 소스: `phantom-idea` (intellij-community `idea/262.10968.63`)
- 표기: **[V]** = 경로·클래스·확장점이 커뮤니티 소스에 있는 것을 직접 확인함, **[A]** = 가정 (확인 안 함)
- 클린룸: Ultimate는 동작 분석 노트(`Intellij-rea/reversed/03-decompiled/2026-10-16-ult-other-plugins.md`, `ult-spring-javaee.md`)와 plugin.xml 기능 이름만 체크리스트로 썼다. 디컴파일 소스는 열지 않았다.

## 한 줄 결론

**거의 만들지 않는다.** Spring Boot는 내장 톰캣으로 돌고, 원격 실행은 Docker나 CI가 맡는다. 원격 JVM 디버그는 커뮤니티에 이미 있다. 새로 만들 것은 "`~/.ssh/config` 호스트를 터미널 탭으로 여는 기능" 하나뿐이고, 크기는 수백 줄이다. SFTP 탐색은 v0.2에서 사용자가 실제로 원할 때만 만든다.

## 1. 목표와 범위

### 커뮤니티가 이미 주는 것

| 기능 | 커뮤니티 위치 | 상태 |
|---|---|---|
| Remote JVM Debug 실행 구성 (JDWP attach) | `java/execution/impl/src/com/intellij/execution/remote/RemoteConfigurationType.java`, 등록은 `java/execution/impl/resources/intellij.java.execution.impl.xml:58` | **[V]** 그대로 쓴다 |
| 로컬 터미널 (pty). 시스템 `ssh` 명령은 지금도 이 터미널에서 실행된다 | `plugins/terminal`, `TerminalToolWindowManager.createNewSession(workingDirectory, tabName, shellCommand, requestFocus, defer)` | **[V]** |
| 셸 스크립트 실행 구성 (`scp` + `ssh java -jar` 같은 배포 스크립트용) | `plugins/sh/core/src/com/intellij/sh/run/ShConfigurationType.java` | **[V]** |
| 실행 타깃 프레임워크 | `platform/execution/src/com/intellij/execution/target/TargetEnvironmentType.kt` (EP `com.intellij.executionTargetType`), `LanguageRuntimeType.kt` (EP `com.intellij.executionTargetLanguageRuntimeType`) | **[V]** 프레임워크만 있다. 등록된 타깃 타입은 `WslTargetType`(Windows 전용, `PlatformExtensions.xml:218`)뿐이다. 언어 런타임은 `JavaLanguageRuntimeType`, `GradleRuntimeType`이 있다 |
| 원격 서버/배포 프레임워크 | `platform/remote-servers/impl/resources/intellij.platform.remoteServers.impl.xml` (EP `remoteServer.type`, `remoteServer.deploymentSource.type`) | **[V]** 프레임워크만 있다. Docker 설계 문서(`2026-10-16-docker-k8s.md`)가 쓸 수 있다 |
| SSH 라이브러리 | `libraries/sshj` (모듈 `intellij.libraries.sshj`, generated module set에서 `loading="embedded"`) | **[V]** 라이브러리만 있다. 이를 쓰는 커뮤니티 기능은 찾지 못했다 |
| eel over TCP/SSH | `platform/eel-tcp/src/SshEelDescriptor.kt`, `TcpEelProvider.kt` | **[V]** 클래스는 있다. SSH 연결을 실제로 세우는 구현은 커뮤니티에 없어 보인다 **[A]** |

### Ultimate에만 있는 것 (descriptor 기능 이름 기준)

- `intellij.ssh.plugin` (SSH): `SshConfigManager`, 비밀번호 프롬프트, rsync 서비스, 프록시 설정 **[V: plugin.xml]**
- `org.jetbrains.plugins.remote-run` (SSH Remote Run): `executionTargetType`으로 SSH, Web Deployment, Vagrant 타깃 **[V: plugin.xml]**
- `webDeployment`: FTP/SFTP/WebDAV 배포와 원격 파일 탐색 (commons-net, commons-vfs, jackrabbit jar) **[V: lib 목록]**
- `javaee-app-servers-impl`, `javaee-appServers-tomcat`, `javaee-appServers-jboss` **[V: 디렉터리]**
- 분석 노트의 비용: SSH 플러그인은 로드 29 클래스, content module 3개 `required`, 시작 활동 0개. 앱 서버 두 개는 합쳐 클래스 66개, 앱 서버 리스너 3개, 앱 서비스 6개. 노트는 Spring Boot 사용자에게 앱 서버를 "불필요"로 판정했다 (`ult-spring-javaee.md` 48–49행, 200행)

### MVP (v0.1)

1. **Remote JVM Debug 확인만** — Phantom 빌드에 `RemoteConfigurationType`이 들어 있는지 확인한다. 코드는 0줄이다.
2. **`phantom-ssh-terminal`** — `~/.ssh/config`의 `Host` 항목을 터미널 새 탭 메뉴에 "SSH: <host>"로 보여 주고, 고르면 시스템 OpenSSH로 `ssh <host>` 탭을 연다. 인증, agent, known_hosts, ProxyJump는 전부 OpenSSH가 처리한다. IDE는 비밀을 저장하지 않는다.

### v0.2+ (사용자가 실제로 원할 때만)

3. **SSH 터널 before-run 작업**: `ssh -N -L <local>:localhost:<remote> <host>`를 Remote JVM Debug 실행 전에 띄우고, 디버그 세션이 끝나면 종료한다. 원격 서버의 5005 포트를 열지 않고 디버그할 수 있다.
4. **SFTP 읽기 전용 탐색기**: `intellij.libraries.sshj`로 원격 디렉터리를 보고 파일(로그, `application.yml`)을 읽기 전용 에디터로 연다. 업로드와 동기화는 없다.

### 비목표 (만들지 말 것)

| 만들지 않는 것 | 이유 |
|---|---|
| SSH 실행 타깃 (`executionTargetType` SSH 구현) | 원격 JDK 탐지, 경로 매핑, 업로드 볼륨, 포트 포워딩을 다 해야 해서 가장 비싸다. Spring Boot 개발자는 로컬, Docker, CI로 실행한다. 정말 필요하면 `ShConfigurationType` 스크립트(`./gradlew bootJar && scp ... && ssh host 'java -jar ...'`)로 충분하다 |
| Tomcat/JBoss 등 앱 서버 통합 | Spring Boot는 내장 서버다. war 배포를 쓰는 프로젝트가 아니다 |
| Web Deployment (FTP/SFTP/WebDAV 자동 업로드, rsync) | 백엔드 jar 배포는 CI 몫이다. 동기화 상태 추적은 상주 비용이 생긴다 |
| Vagrant 타깃, 원격 인터프리터/원격 SDK | 대상 사용자가 쓰지 않는다 |
| Remote Dev, Gateway, Code With Me | 범위 밖. 분석 노트에서 서비스 오버라이드 약 20개가 드는 영역이다 |
| 자체 SSH 자격 증명 저장소, 비밀번호 프롬프트 UI | OpenSSH와 ssh-agent에 맡긴다. 인증 코드를 새로 만들지 않는다 |
| 내장 sshj 기반 터미널 | 시스템 `ssh` + pty가 더 정확하고 코드가 적다 |

## 2. Ultimate 대비 경량화 원칙

- **로드 시점**: Ultimate SSH 플러그인은 content module 3개가 `required`라서 켜는 순간 로드된다 (관측). `phantom-ssh-terminal`은 content module 하나에 `loading="optional"`로 두고, 의존성은 `intellij.terminal`뿐이다 **[A: optional로 충분한지 빌드에서 확인 필요]**.
- **시작 활동 0개, 앱 서비스 0개, 리스너 0개**: 호스트 목록은 사용자가 터미널 새 탭 드롭다운을 열 때 처음 읽는다.
- **증분 캐시**: `~/.ssh/config`와 `Include`된 파일의 (경로, mtime, size)를 키로 결과를 캐시한다. 파일 감시자(`BulkFileListener`, VFS)는 쓰지 않는다. 다음 호출에서 mtime만 다시 본다.
- **EDT 금지**: 파일 파싱은 백그라운드에서 한다. `listOpenPredefinedTerminalActions`가 EDT에서 불린다면 마지막 캐시만 즉시 돌려주고 갱신은 백그라운드로 넘긴다 **[A: 호출 스레드는 미확인]**.
- **프로젝트 인덱스와 무관**: PSI, 인덱스, 프로젝트 모델을 건드리지 않는다. 프로젝트 전체 무효화 같은 비용이 원천적으로 생기지 않는다.
- **외부 프로세스는 사용자가 연 탭에만**: 상주 SSH 연결이 없다.

## 3. 아키텍처

### 모듈 구성

```
plugins/phantom-infra/
  ssh-terminal/                      # content module: intellij.phantom.infra.sshTerminal (v0.1)
    resources/intellij.phantom.infra.sshTerminal.xml
    src/.../SshConfigHosts.kt        # ~/.ssh/config 파서 + mtime 캐시
    src/.../SshPredefinedTerminalProvider.kt
  ssh-tunnel/                        # v0.2, 의존: intellij.java.execution.impl
    src/.../SshTunnelBeforeRunTaskProvider.kt
  sftp-browser/                      # v0.2, 의존: intellij.libraries.sshj
```

### 확장점

| 용도 | 확장점 | 확인 |
|---|---|---|
| 터미널 새 탭 메뉴에 호스트 추가 | `org.jetbrains.plugins.terminal.openPredefinedTerminalProvider` → `org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider.listOpenPredefinedTerminalActions(project): List<AnAction>` (`plugins/terminal/resources/META-INF/terminal.xml:166`) | **[V]** EP 존재. 커뮤니티 안에는 구현체가 없다 |
| 탭 열기 | `TerminalToolWindowManager.getInstance(project).createNewSession(null, "ssh $host", listOf("ssh", host), true, true)` | **[V]** 시그니처 확인 (`TerminalToolWindowManager.java:185`) |
| 터널 (v0.2) | `com.intellij.stepsBeforeRunProvider` → `com.intellij.execution.BeforeRunTaskProvider` (`LangExtensionPoints.xml:262`) | **[V]** |
| 원격 디버그 | `RemoteConfigurationType` (재사용) | **[V]** |
| SFTP (v0.2) | 툴 윈도 `com.intellij.toolWindow` (lazy factory) + `LightVirtualFile` 읽기 전용 | **[A]** 일반 플랫폼 API, 세부 미확인 |

### 데이터 모델

```kotlin
data class SshHost(val alias: String, val hostName: String?, val user: String?, val port: Int?)
```

- `Host` 패턴 중 와일드카드(`*`, `?`, `!`)가 있는 항목은 메뉴에서 뺀다.
- `Include`는 `~/.ssh/` 기준으로 상대 경로와 glob만 따라간다. `Match` 블록은 무시한다. 정확한 해석은 어차피 `ssh`가 한다. 메뉴용 목록일 뿐이다.
- Windows는 `%USERPROFILE%\.ssh\config`를 본다 **[A]**.

### 스레드

- 파싱: `Dispatchers.IO` 코루틴, 결과는 `@Volatile` 캐시 필드 하나.
- 터널 프로세스(v0.2): `GeneralCommandLine` + `OSProcessHandler`, 디버그 `ProcessHandler` 종료 리스너에서 `destroyProcess()`.

### Kotlin K2/UAST

이 기능은 소스 코드를 분석하지 않는다. 해당 없음.

## 4. 구현 단계

| # | 작업 | 공수 | 사용자가 IDE에서 보는 확인 |
|---|---|---|---|
| 1 | Phantom 빌드에 Remote JVM Debug, Sh 실행 구성, 터미널이 들어 있는지 확인 | 0.25일 | Run > Edit Configurations > + 에 "Remote JVM Debug"와 "Shell Script"가 보인다 |
| 2 | `SshConfigHosts` 파서 + mtime 캐시 + light test | 1일 | (테스트) 샘플 config에서 별칭 3개, 와일드카드 0개 |
| 3 | `SshPredefinedTerminalProvider` + content module 등록 | 0.5일 | 터미널 툴 윈도의 새 탭 드롭다운에 "SSH: <host>" 항목이 보이고, 고르면 탭 이름이 `ssh <host>`인 세션이 열려 원격 프롬프트가 뜬다 |
| 4 | 시작 비용 확인 | 0.25일 | `idea.log`/시작 측정에서 이 모듈의 서비스, 활동이 0개다. 드롭다운을 열기 전에는 클래스가 로드되지 않는다 |
| — | **MVP 합계** | **약 2일** | |
| 5 (v0.2) | SSH 터널 before-run 작업 | 1.5일 | Remote JVM Debug 구성의 Before launch에 "SSH tunnel"을 추가하면 원격 5005로 attach되고, 디버그를 멈추면 `ssh -N` 프로세스가 사라진다 |
| 6 (v0.2) | SFTP 읽기 전용 탐색기 | 4–5일 | 툴 윈도에서 호스트를 고르면 원격 디렉터리가 보이고, `app.log`를 더블클릭하면 읽기 전용 에디터로 열린다 |

## 5. 테스트 방법

- **light test**: `BasePlatformTestCase`(또는 JUnit5 `@TestApplication`)로 `SshConfigHosts`를 테스트한다. 임시 디렉터리에 config, Include 파일, 와일드카드 항목을 만들고 mtime을 바꿔 캐시 무효화를 본다. 터미널 provider는 `listOpenPredefinedTerminalActions` 반환 목록의 텍스트만 검증한다.
- **수동 확인**: `~/orca/workspaces/prototype-reservation-system`을 연 상태에서
  1. 터미널 드롭다운에서 SSH 호스트 탭을 연다 (사용자 `~/.ssh/config`에 호스트가 있어야 한다).
  2. 로컬에서 `./gradlew bootRun --debug-jvm` 또는 `JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`로 띄우고 Remote JVM Debug로 attach해서 컨트롤러 브레이크포인트에 멈추는지 본다. 이것은 커뮤니티 기능 검증이다.
  3. 프로젝트를 열고 닫는 동안 JFR이나 `-Didea.log.debug.categories`로 이 모듈 클래스가 로드되지 않는지 본다.

## 6. 리스크와 남은 의문

- **[A]** `OpenPredefinedTerminalActionProvider`의 드롭다운이 Reworked Terminal(새 터미널 UI)과 Classic 양쪽에서 같은 위치에 보이는지 확인하지 않았다.
- **[A]** `listOpenPredefinedTerminalActions`가 EDT에서 호출되는지 확인하지 않았다. EDT라면 캐시만 돌려주는 위 설계가 필요하다.
- **[A]** content module을 `optional`로 두었을 때 Phantom Bazel 빌드의 product layout에 넣는 절차가 따로 필요할 수 있다.
- **[A]** `eel-tcp`의 `SshEelDescriptor`가 커뮤니티에서 동작하는 SSH 백엔드를 갖고 있는지 모른다. 갖고 있다면 v0.2 SFTP를 sshj 대신 eel의 NIO 파일 시스템으로 만들 수 있어 코드가 줄어든다. 확인 전까지는 sshj를 기준으로 한다.
- 시스템 `ssh`가 없는 환경(일부 Windows)에서는 기능이 동작하지 않는다. 메뉴에 표시하지 않는 것으로 처리한다.
- 사용자가 실제로 원격 실행 타깃을 매일 쓴다면 이 문서의 판단(비목표)을 다시 봐야 한다. 지금은 근거가 없다.
