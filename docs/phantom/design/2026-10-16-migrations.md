# phantom-migrations 설계: Flyway / Liquibase 지원

- 날짜: 2026-10-16
- 대상 트리: `phantom-idea` (intellij-community `idea/262.10968.63` 포크, branch `main`, 커밋 `fc05eddc04`)
- 사용자 프로필: Kotlin + Spring Boot + JPA + Gradle 백엔드 개발자
- 설계 방식: **clean-room**. 근거는 네 가지뿐이다. (1) `Intellij-rea/reversed/`의 동작·비용 설명 노트, (2) Ultimate `flyway`/`liquibase` 플러그인 디스크립터의 기능 이름(체크리스트 용도), (3) 이 저장소의 커뮤니티 API, (4) Flyway/Liquibase/Spring Boot 공개 문서. 디컴파일 코드(`reversed/source/`)는 열지 않았다. 라이선스·인증 코드는 다루지 않는다. 이 문서 외에 `phantom-idea` 소스는 바꾸지 않았다.

표기
- **[V]** 검증: 이 저장소에서 경로·클래스·EP 선언을 직접 확인했다.
- **[A]** 가정: 공개 지식이나 설계 판단이며 아직 확인하지 않았다.
- **[M]** 측정: `reversed/` 노트에 기록된 Ultimate 실측값 (`03-decompiled/2026-10-16-ult-database.md` §2.5, 표 9행·117행).
- **[I]** 추론: 관측에서 끌어낸 결론이며 실행으로 확인하지 않았다.

---

## 0. 결정 요약

- 사용자 프로젝트는 **Flyway만** 쓴다. Liquibase는 쓰지 않는다 (§1.1). 그래서 v0.1은 Flyway 중심이고, Liquibase는 "스키마 기반 완성 + include 이동"만 하는 얇은 모듈로 둔다.
- Flyway 라이브러리를 **IDE JVM에 올리지 않는다.** `info`/`validate`/`migrate`는 프로젝트 자신의 runtime classpath로 띄운 별도 JVM(작은 runner)이나 Gradle 태스크로 실행한다. Ultimate가 Liquibase 라이브러리 9.2 MB를 플러그인에 통째로 번들한 것과 반대 방향이다 [M].
- 다른 플러그인에 **하드 의존하지 않는다.** Ultimate Flyway/Liquibase는 `com.intellij.database`, `jpa.jpb.model`, `javaee.jpa`에 묶여 있다 [M]. phantom-migrations는 `com.intellij.java`만 필수로 쓰고 YAML, XML, JSON, Gradle, phantom-db, phantom-jpa는 optional 콘텐츠 모듈로만 붙인다.
- 정직하게 적는다. Ultimate에서 Flyway+Liquibase의 직접 비용은 **221 클래스, 로더 9개, metaspace 0.1 MB 미만**으로 작다 [M]. 이 설계의 경량화 이득은 그 숫자 자체보다 "DB 플러그인·JPA Buddy 모델을 끌고 오지 않는 것"과 "안 쓰는 프로젝트에서 0에 가깝게 만드는 것"에 있다.

---

## 1. 목표와 범위

### 1.1 사용자가 매일 하는 일 (근거)

`~/orca/workspaces/prototype-reservation-system/infro`에서 확인했다 [V: 파일 grep/열람].

- Flyway 10.0.0 (`gradle/libs.versions.toml`: `flyway = "10.0.0"`, `flyway-core`, `flyway-mysql`, 플러그인 `org.flywaydb.flyway`).
- 마이그레이션은 `infrastructure-module/src/main/resources/db/migration/`에 22개 있다. 이름은 `V1_0__init.sql` … `V1_21__timetable_occupancy_hold.sql`이다. 즉 버전은 `1.0, 1.1, …, 1.21`이다. **문자열 정렬하면 `V1_10`이 `V1_2` 앞에 온다.** 숫자 세그먼트 정렬이 필수다.
- Spring 설정: `infrastructure-module/src/main/resources/persistence/flyway/index.yaml`에 `spring.flyway.*`가 있고, `adapter-module/src/main/resources/application.yaml`이 `spring.config.import: classpath:persistence/flyway/index.yaml`로 가져온다. 값은 `table: flyway_version_control`, `schemas: flyway`, `locations: classpath:db/migration`, `baseline-on-migrate: true`, `validate-on-migrate: true`, `clean-disabled: true`.
- `batch-module`은 `spring.flyway.enabled: false`다.
- `infrastructure-module/.../config/persistence/flyway/FlywayConfig.kt`가 `@FlywayDataSource` 빈을 따로 만든다.
- Gradle 플러그인은 `adapter-module/build.gradle.kts`에만 적용되어 있고 `flyway { url, user, password, driver }`만 설정한다. `table`, `locations`, `schemas`는 없다.

마지막 두 줄에서 중요한 추론이 나온다 [I]. `adapter-module`에서 `gradle flywayInfo`를 돌리면 기본 테이블 `flyway_schema_history`와 기본 위치(모듈 자신의 `src/main/resources/db/migration`)를 본다. 그런데 실제 앱은 `flyway_version_control`과 `infrastructure-module`의 파일을 쓴다. 그래서 **Gradle 태스크 결과와 앱이 실제로 적용하는 상태가 다를 수 있다.** 그래서 v0.1의 기본 실행 경로는 "Spring 설정을 읽어 Flyway API를 호출하는 runner"로 하고, Gradle 태스크는 보조 경로로 둔다. 이 추론은 T7에서 실제로 돌려서 확인한다.

일상 작업은 이렇다. 다음 버전 번호로 새 마이그레이션을 만든다. 이전/다음 마이그레이션을 오가며 읽는다. 이름 실수(`V1_22_foo.sql`처럼 `__`가 하나)를 잡는다. 이미 적용한 파일을 실수로 고치지 않는다. 로컬 DB에 무엇이 적용됐는지(`info`) 보고 `migrate`를 돌린다.

### 1.2 MVP (v0.1)

**Flyway**
1. **활성화 감지**: 모듈 classpath에 `org.flywaydb:flyway-core`가 있거나 Gradle 플러그인 `org.flywaydb.flyway`가 적용된 모듈만 Flyway 모듈로 본다. 없는 프로젝트에서는 툴 창, 액션, 배너가 전혀 보이지 않는다.
2. **위치 해석**: 기본 `classpath:db/migration`, 그리고 `spring.flyway.locations`(application\*.yml/yaml/properties와 `spring.config.import`의 `classpath:` 파일을 따라감). `classpath:`는 모듈과 의존 모듈의 리소스 루트에서, `filesystem:`은 모듈 루트 기준 경로에서 찾는다.
3. **마이그레이션 모델**: 파일 이름 파싱(`V`/`U`/`R`/`B` 접두사, 버전, `__`, 설명, `.sql`), Kotlin/Java 기반 마이그레이션 클래스(`V1_2__Foo`) 이름 인식, Flyway 규칙의 버전 정렬.
4. **"Flyway" 툴 창**: 모듈 → 위치 → 마이그레이션 목록(버전, 설명, 유형, 파일, 상태). 더블클릭하면 파일을 연다. 반복형(`R__`)은 아래에 따로 묶는다.
5. **이름 검사(배너)**: SQL 파일 위에 `EditorNotificationProvider` 배너로 보여 준다. 대상은 잘못된 이름(구분자 `__` 누락, 지원하지 않는 접두사), 중복 버전, 같은 위치의 반복형 설명 중복이다. SQL 언어를 누가 제공하든(phantom-db, TextMate) 동작한다.
6. **이동**: 배너에 "← V1.16 | V1.18 →" 링크. `Navigate → Related Symbol`(`GotoRelatedProvider`)로 이전/다음 버전과 같은 버전의 `U`(undo) 파일로 간다.
7. **새 마이그레이션 생성**: `New → Flyway Migration`. 다음 버전을 제안하고(마지막 세그먼트 +1: `1.21 → 1.22`), 기존 자릿수 스타일(`V1_22__` vs `V1.22__`)을 따른다. 설명은 공백을 `_`로 바꾼다.
8. **info / validate / migrate 실행**:
   - 기본: **Spring 설정 runner**. 모듈 runtime classpath로 별도 JVM을 띄워 Flyway API를 호출하고 JSON을 받는다. 프로필은 사용자가 고른다(기본 `local`).
   - 보조: 모듈에 Gradle 플러그인이 있으면 `flywayInfo`/`flywayMigrate`/`flywayValidate` 태스크를 실행한다.
   - `clean`은 제공하지 않는다. `migrate`는 확인 대화 상자를 거친다.
9. **적용 상태 표시**: 마지막 `info` 결과(버전별 Success/Pending/Failed/Ignored/Missing, 적용 시각, DB 체크섬)를 툴 창 상태 열과 배너에 보여 준다. 이미 적용된 파일을 수정하면 "적용된 마이그레이션 수정 — 다음 validate에서 체크섬 불일치" 경고를 띄운다(로컬 체크섬 계산은 §3.5).

**Liquibase (얇은 모듈)**
10. **XML changelog 완성**: `http://www.liquibase.org/xml/ns/dbchangelog` 네임스페이스를 XSD에 매핑해서 플랫폼 XML 완성/검증을 그대로 쓴다. XSD는 프로젝트의 `liquibase-core` jar 안의 것을 우선 쓴다(버전 일치) [A: jar 안에 XSD가 들어 있다는 것은 공개 배포물 기억 기반].
11. **include 이동**: XML `<include file>`, `<includeAll path>`, YAML `include: file:` 값을 파일 참조로 만든다(Ctrl+클릭, 이름 바꾸기 따라가기).

### 1.3 v0.2 이후

- v0.2
  - Liquibase YAML changelog 완성: `JsonSchemaProviderFactory`로 자체 작성 JSON 스키마(공개 문서 기준의 부분 집합: `databaseChangeLog`, `changeSet{id,author,context,labels,runOnChange,changes,rollback}`, 주요 change 타입 20여 개)를 연결.
  - Liquibase changeSet 목록(툴 창 탭): `id/author/file`, 중복 검출, `spring.liquibase.change-log` 따라가기.
  - Liquibase `update`/`status` 실행: Flyway runner와 같은 out-of-process 방식.
  - Flyway placeholder(`${name}`) 완성/검사: `spring.flyway.placeholders.*` 키에서 수집.
  - Flyway callbacks(`beforeMigrate.sql` 등) 인식, `U` 파일 생성.
  - Kotlin/Java 기반 마이그레이션 클래스: `BaseJavaMigration` 하위 클래스를 UAST로 찾아 목록에 합침.
- v0.3 이후
  - phantom-db 연동(optional 모듈): runner 대신 phantom-db 데이터 소스로 히스토리 테이블을 직접 읽기. 테이블 이름은 설정에서 가져온다.
  - **JPA 엔티티 ↔ 스키마 diff로 다음 마이그레이션 초안 생성** (Ultimate의 `CreateDiffFlyway`, `FlywayGenerateDdlByEntities`, Liquibase `DbVersioningGenerateGroup`에 해당하는 기능 이름 [V: descriptor]). phantom-jpa 모델 API와 phantom-db `SchemaProvider`(phantom-db 설계 §3.7, v0.3)가 모두 준비돼야 한다.
  - Liquibase changelog diff 생성 (같은 전제).

### 1.4 하지 않는 것 (non-goals)

- Ultimate의 미리보기 대화 상자 계열(`FlywayPreviewDialogActionGroup`, `FlywayMoveToAnotherMigrationFile`, `RemoveFromFlywayIgnore`, ddl ignore 관리), JPA 엔티티 gutter 액션 등 액션 68개 수준의 UI [M: 액션 수]. v0.1 액션은 6개 이하로 둔다.
- Flyway 라이브러리 또는 Liquibase 라이브러리를 플러그인에 번들하는 것.
- `flyway clean`, `liquibase dropAll` 같은 파괴적 명령.
- Flyway Teams/Enterprise 전용 기능(undo 실행, dry run). 파일 인식(`U` 접두사)만 한다.
- 전용 `FileBasedIndex`. 마이그레이션 디렉터리는 작아서 VFS 순회로 충분하다.
- SQL 언어 자체(렉서, 하이라이트). phantom-db나 번들 TextMate SQL(`plugins/textmate/lib/bundles/sql/` [V])이 맡는다.
- 시작 활동(`postStartupActivity`)과 시작 시 Gradle/DB 호출.

---

## 2. Ultimate 대비 경량화 원칙

| Ultimate 관측 비용 | 근거 | phantom-migrations 대응 |
|---|---|---|
| 안 써도 221 클래스, 로더 9개 적재. 키맵·액션 트리 구성 때 액션 클래스가 로드됨 (`com.intellij.flyway.structure.new`) | [M] ult-database §2.5, 표 9행 | 액션은 6개 이하. 액션 클래스는 얇게 두고 `update()`는 캐시된 감지 결과 하나만 본다. 무거운 로직은 서비스로 넘겨 처음 실행할 때만 로드한다 |
| 액션 68개 등록 (Flyway 36 + Liquibase) | [M] 표 117행, [V] descriptor | §1.4 non-goal. New 메뉴 1개, 툴 창 툴바 3–4개 |
| Liquibase 13.5 MB 중 9.2 MB가 서드파티 Liquibase 라이브러리 번들 | [M] §2.5 | 라이브러리를 번들하지 않는다. 실행은 프로젝트 classpath로 별도 JVM에서 한다. XSD도 프로젝트 jar에서 읽는다 |
| `intellij.database`, `jpa.jpb.model`, `javaee.jpa`에 의존 → DB/JPA 플러그인을 끌 수 없게 묶음 | [M] §2.5, §3 표 2행 | 필수 의존은 `com.intellij.java`뿐이다. 나머지는 optional 콘텐츠 모듈로 둔다 (§3.1) |
| Liquibase `referenceProvider` 66개, XML/YAML/Properties `completion.contributor`, `lineMarkerProvider` 8개 → 열린 XML/YAML 파일마다 호출 | [M] §2.5 | contributor는 **패턴 앞단에서 파일 판정**을 한다. 파일이 changelog인지(루트 태그·네임스페이스·최상위 키 `databaseChangeLog`)를 `CachedValue`로 한 번 계산한 뒤, 아니면 즉시 빠진다. Properties contributor와 line marker는 v0.1에 두지 않는다 |
| 시작 활동·인덱스 없음 (Ultimate도 이 부분은 이미 가볍다) | [M] §2.5 | 같은 원칙을 유지한다. 시작 활동 0, 인덱스 0 |

원칙 정리
1. **기술을 쓸 때만 켠다.** 판정은 `JavaLibraryUtil.hasLibraryJar(Module, "org.flywaydb:flyway-core")` [V: `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` 176행]와 Gradle 플러그인 적용 여부로 한다. 결과는 `CachedValue`로 두고 `JavaLibraryModificationTracker` [V]와 `ProjectRootModificationTracker` [V]에 의존시킨다. 툴 창은 `library.toolWindow` EP [V: `PlatformExtensionPoints.xml` 218행]로 등록한다. 그러면 플랫폼의 `LibraryDependentToolWindowManager`(이미 존재하는 백그라운드 시작 활동, `PlatformLangComponents.xml` 91행 [V])가 대신 판정하고, 우리는 시작 활동을 추가하지 않는다.
2. **증분 모델.** 마이그레이션 목록은 파일 *이름*에서만 나온다. 그래서 `VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS` [V: `VirtualFileManager.java` 42행]에만 의존한다. 파일 내용을 고쳐도 목록은 다시 계산하지 않는다. 체크섬은 파일별로 요청 시에만 계산하고 `modificationStamp`로 캐시한다.
3. **프로젝트 전역 무효화 금지.** `PsiModificationTracker` 전역 카운트에 의존하지 않는다. Liquibase changeSet 모델은 changelog 파일별 `CachedValuesManager.getCachedValue(psiFile)` [V: `platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java`]로, 그 파일에만 의존한다.
4. **지연 서비스.** `projectService`는 처음 쓸 때 생성된다(플랫폼 기본). 생성자에서 I/O를 하지 않는다.
5. **IDE 밖에서 실행.** DB 연결, Flyway/Liquibase 라이브러리 로드는 자식 JVM에서 한다. IDE 힙과 metaspace에 드라이버·Flyway 클래스가 남지 않는다.
6. **EDT 금지.** 감지, VFS 순회, YAML 읽기, 체크섬, 프로세스 출력 파싱은 모두 백그라운드(`readAction {}` 코루틴, `smartReadAction`은 PSI가 필요할 때만)에서 한다. EDT는 그리기만 한다.

---

## 3. 아키텍처

### 3.1 모듈 레이아웃

```
plugins/phantom-migrations/
├── BUILD.bazel, phantom.migrations.iml
├── resources/META-INF/plugin.xml          # id: phantom.migrations
├── core/       → phantom.migrations.core        필수. 감지, 위치 해석, Flyway 이름 모델, 정렬, 서비스
├── flyway/     → phantom.migrations.flyway      필수. 툴 창, 배너, GotoRelated, New 액션, 파일 템플릿, runner 실행기
├── spring/     → phantom.migrations.spring      optional (intellij.yaml.backend, intellij.properties.backend.psi). spring.flyway.* / spring.liquibase.* 읽기
├── gradle/     → phantom.migrations.gradle      optional (org.jetbrains.plugins.gradle). flyway* 태스크 감지·실행
├── liquibase/  → phantom.migrations.liquibase   optional (XML). XSD 매핑, include 참조
├── liquibase-yaml/ → phantom.migrations.liquibase.yaml  optional (yaml + json backend). v0.2 JSON 스키마
├── db/         → phantom.migrations.db          optional (phantom.db API). v0.3
├── runner/     → phantom-migrations-runner.jar           IDE 의존성 없는 순수 Java 11 jar (§3.6)
└── testSrc/, testData/
```

`plugin.xml` 골격 [A: 콘텐츠 모듈 문법은 phantom-spring-boot 설계와 같고 `plugins/yaml/resources/META-INF/plugin.xml` [V]를 참고]:

```xml
<idea-plugin>
  <id>phantom.migrations</id>
  <name>Database Migrations (Phantom)</name>
  <incompatible-with>com.intellij.flyway</incompatible-with>
  <incompatible-with>com.intellij.liquibase</incompatible-with>
  <dependencies>
    <plugin id="com.intellij.java"/>
  </dependencies>
  <content>
    <module name="phantom.migrations.core"/>
    <module name="phantom.migrations.flyway"/>
    <module name="phantom.migrations.spring"/>          <!-- depends: intellij.yaml.backend, intellij.properties.backend.psi -->
    <module name="phantom.migrations.gradle"/>          <!-- depends: intellij.gradle (plugin org.jetbrains.plugins.gradle) -->
    <module name="phantom.migrations.liquibase"/>       <!-- depends: intellij.xml.psi -->
    <module name="phantom.migrations.liquibase.yaml"/>  <!-- v0.2 -->
  </content>
</idea-plugin>
```

- 번들 등록: `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt` 61행 `productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf(...)`에 추가한다 [V: 위치].
- phantom-spring-boot와의 경계: Spring 설정 키 완성은 phantom-spring-boot 담당이다(Boot jar의 메타데이터에 `spring.flyway.*`가 있다 [A]). 여기서는 **값을 읽기만** 한다. phantom-spring-boot가 "프로필별 해석된 설정 값" API를 내놓으면 `spring` 모듈은 그 API로 바꾼다 [A: 두 설계 문서를 맞춰야 함].

### 3.2 커뮤니티 확장 지점과 API

| 용도 | EP / API | 경로 [V] |
|---|---|---|
| 라이브러리 감지 | `JavaLibraryUtil.hasLibraryJar(Module, String)`, `getLibraryVersion(Module, String)` | `java/openapi/src/com/intellij/java/library/JavaLibraryUtil.java` 155·176·197행 |
| 감지 캐시 무효화 | `JavaLibraryModificationTracker`, `ProjectRootModificationTracker` | `java/openapi/src/com/intellij/java/library/JavaLibraryModificationTracker.java`, `platform/core-api/src/com/intellij/openapi/roots/ProjectRootModificationTracker.java` |
| 라이브러리 의존 툴 창 | `library.toolWindow` → `LibraryDependentToolWindow`(`librarySearchClass` = `LibrarySearchHelper`, `factoryClass` = `ToolWindowFactory`) | `platform/platform-resources/src/META-INF/PlatformExtensionPoints.xml` 218–221행, `platform/platform-api/src/com/intellij/openapi/wm/ext/LibraryDependentToolWindow.java`, `.../LibrarySearchHelper.java` |
| 툴 창 | `ToolWindowFactory` | `platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt` |
| 파일 배너 | `editorNotificationProvider` → `EditorNotificationProvider` | `PlatformExtensionPoints.xml` 274행, `platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java` |
| 관련 파일 이동 | `gotoRelatedProvider` → `GotoRelatedProvider` | `LangExtensionPoints.xml` 157행, `platform/lang-api/src/com/intellij/navigation/GotoRelatedProvider.java` |
| 새 파일 | `CreateFileFromTemplateAction`, `fileTemplateGroup` EP | `platform/lang-impl/src/com/intellij/ide/actions/CreateFileFromTemplateAction.java`, `LangExtensionPoints.xml` 212행 |
| VFS 구조 변경 추적 | `VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS` | `platform/core-api/src/com/intellij/openapi/vfs/VirtualFileManager.java` 42행 |
| 파일별 캐시 | `CachedValuesManager` | `platform/core-api/src/com/intellij/psi/util/CachedValuesManager.java` |
| 설정 파일 찾기 | `FilenameIndex` (플랫폼 기존 인덱스) | `platform/indexing-api/src/com/intellij/psi/search/FilenameIndex.java` |
| classpath 구성 | `OrderEnumerator`, `JavaParameters` | `platform/projectModel-api/src/com/intellij/openapi/roots/OrderEnumerator.java`, `java/execution/openapi/src/com/intellij/execution/configurations/JavaParameters.java` |
| 자식 프로세스 | `GeneralCommandLine`, `OSProcessHandler` | `platform/platform-util-io/src/com/intellij/execution/configurations/GeneralCommandLine.java`, `platform/platform-util-io/src/com/intellij/execution/process/OSProcessHandler.java` |
| Gradle 태스크 실행 | `ExternalSystemUtil.runTask(ExternalSystemTaskExecutionSettings, …)`, `setExternalProjectPath`, `setTaskNames`, `GradleConstants.SYSTEM_ID` | `platform/external-system-impl/src/com/intellij/openapi/externalSystem/util/ExternalSystemUtil.java` 866–884행, `platform/external-system-api/.../ExternalSystemTaskExecutionSettings.java` 90·114행, `plugins/gradle/settings/src/util/GradleConstants.java` |
| Gradle 태스크 목록 | `ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.TASK)` | `platform/external-system-api/src/com/intellij/openapi/externalSystem/util/ExternalSystemApiUtil.java` [V: 클래스], [A: 메서드 사용법은 구현 때 확인] |
| XML 네임스페이스 → XSD | `standardResourceProvider` → `StandardResourceProvider`, `standardResource` EP; 대안 `xml.schemaProvider` → `XmlSchemaProvider` | `xml/xml-psi-api/resources/intellij.xml.psi.xml` 17·21–22행, `xml/xml-psi-api/src/com/intellij/javaee/StandardResourceProvider.java`, `xml/xml-psi-api/src/com/intellij/xml/XmlSchemaProvider.kt` |
| YAML JSON 스키마 (v0.2) | `JavaScript.JsonSchema.ProviderFactory` → `JsonSchemaProviderFactory`, `JsonSchemaFileProvider` | `json/backend/resources/intellij.json.backend.xml` 157–158행, `json/backend/src/com/jetbrains/jsonSchema/extension/JsonSchemaProviderFactory.java` |
| 참조 | `psi.referenceContributor` | `platform/core-impl/resources/META-INF/CoreImpl.analyzer.xml` 34행 |
| Kotlin/Java 마이그레이션 클래스 (v0.2) | UAST, `UastReferenceRegistrar`; K2 구현 `plugins/kotlin/uast/uast-kotlin-fir`, `uast-kotlin-idea-fir` | `uast/uast-common-ide/src/com/intellij/psi/UastReferenceRegistrar.kt` |
| (쓰지 않음) 시작 활동 | `postStartupActivity` | `platform/core-api/resources/META-INF/Core.xml` 16행 |

### 3.3 데이터 모델 (core)

```kotlin
enum class FlywayKind { VERSIONED, UNDO, REPEATABLE, BASELINE }   // V, U, R, B

@JvmInline value class FlywayVersion(val parts: List<java.math.BigInteger>) : Comparable<FlywayVersion>
// "1_21" / "1.21" → [1, 21]. 비교는 세그먼트별 숫자 비교, 짧은 쪽은 0으로 채움 (1.0 == 1) [A: Flyway 문서의 MigrationVersion 의미]

data class MigrationFile(
  val file: VirtualFile,              // SQL 또는 .kt/.java 파일
  val kind: FlywayKind,
  val version: FlywayVersion?,        // REPEATABLE이면 null
  val description: String,            // "_" → " "
  val rawSeparatorStyle: Char,        // '_' 또는 '.' (새 파일 생성 시 따라 함)
  val problems: List<NameProblem>,    // MISSING_SEPARATOR, BAD_PREFIX, DUPLICATE_VERSION, ...
)

data class MigrationLocation(val module: Module, val spec: String /* classpath:db/migration */, val roots: List<VirtualFile>)
data class FlywayModuleModel(val module: Module, val config: FlywayConfigView, val locations: List<MigrationLocation>, val files: List<MigrationFile>)

data class FlywayConfigView(          // 출처별 병합 결과. 출처를 함께 들고 다닌다
  val locations: List<Sourced<String>>, val table: Sourced<String>?, val schemas: List<String>,
  val url: Sourced<String>?, val user: String?, val enabled: Boolean,
  val sqlMigrationPrefix: String = "V", val separator: String = "__", val suffixes: List<String> = listOf(".sql"),
)

data class AppliedState(val version: FlywayVersion?, val description: String, val state: String /*Success|Pending|Failed|...*/,
                        val installedOn: String?, val checksum: Int?)
data class InfoSnapshot(val module: Module, val profile: String, val takenAt: Long, val rows: List<AppliedState>, val source: String /*runner|gradle*/)
```

- `sqlMigrationPrefix`/`separator`/`suffixes`는 `spring.flyway.sql-migration-prefix`, `-separator`, `-suffixes`에서 바뀔 수 있다 [A: Spring Boot `FlywayProperties` 공개 키]. 이름 파싱은 이 값을 받는다.
- `InfoSnapshot`은 프로젝트 서비스 메모리에만 둔다. 디스크에는 저장하지 않는다(접속 정보와 상태가 오래되면 오히려 해롭다). 툴 창에는 "n분 전 local 프로필 기준" 표시를 붙인다.

**Liquibase (v0.1 범위)**: 모델을 두지 않는다. XSD 매핑과 참조만 한다. v0.2에서 `ChangeSetRef(id, author, file, offset)` 목록을 파일별 `CachedValue`로 추가한다.

### 3.4 위치·설정 해석 (spring 모듈)

1. 후보 파일: 모듈 리소스 루트의 `application*.{yml,yaml,properties}`(`FilenameIndex`로 이름 검색, 모듈 scope).
2. 그 파일들의 `spring.config.import` 중 `classpath:` 항목을 따라간다. 의존 모듈 리소스 루트까지 찾고, 깊이는 최대 3단계로 둔다. 사용자 프로젝트의 `adapter-module → persistence/flyway/index.yaml(infrastructure-module)` 경로가 이 규칙으로 잡힌다 [I].
3. 키 `spring.flyway.locations`, `.table`, `.schemas`, `.url`, `.user`, `.enabled`, `.sql-migration-*`를 읽는다. 프로필 문서(`---` + `spring.config.activate.on-profile`)는 v0.1에서 선택 프로필 하나로 단순 병합한다. **비밀번호는 모델에 넣지 않는다.** runner에게는 실행 시점에 다시 읽어서 환경 변수로만 넘긴다.
4. 결과는 모듈별 `CachedValue`이고, 의존 대상은 해당 설정 파일들의 PSI(파일별)와 `VFS_STRUCTURE_MODIFICATIONS`다.
5. spring 모듈이 없거나(YAML 플러그인 비활성) Spring을 쓰지 않으면 기본 `classpath:db/migration`만 쓴다.

Gradle `flyway { }` 블록은 정적으로 파싱하지 않는다(Kotlin DSL 해석 비용·정확도 문제). Gradle 경로를 고르면 Gradle이 알아서 읽는다.

### 3.5 캐싱·무효화

| 대상 | 저장 위치 | 무효화 조건 | 비용 |
|---|---|---|---|
| 모듈이 Flyway/Liquibase를 쓰는가 | 모듈별 `CachedValue` | `JavaLibraryModificationTracker`, `ProjectRootModificationTracker` | 라이브러리 목록 순회 1회 |
| `FlywayConfigView` | 모듈별 `CachedValue` | 관련 설정 파일 PSI(파일별), VFS 구조 | YAML 파일 몇 개 |
| 마이그레이션 목록 | 모듈별 `CachedValue` | `VFS_STRUCTURE_MODIFICATIONS` + 위 config | 디렉터리 순회 (사용자: 파일 22개) |
| 로컬 체크섬 | 파일별 `UserData` + `modificationStamp` | 파일 내용 변경 | 요청 시에만 계산 |
| `InfoSnapshot` | 프로젝트 서비스 메모리 | 사용자가 다시 실행할 때, 프로필이 바뀔 때 | 0 (수동) |

로컬 체크섬: Flyway의 SQL 체크섬은 BOM을 빼고 줄 단위로 읽은 내용에 대한 CRC32다 [A: 공개 문서/동작 기억 기반, 줄 끝 처리 규칙은 구현 때 runner 결과와 대조해 맞춘다]. 일치를 보장하지 못하면 "DB 체크섬과 로컬 체크섬이 다름"을 단정하지 않는다. 대신 "적용 이후 이 파일이 IDE에서 수정됨"(적용 시각 이후 `modificationStamp` 변화)만 경고한다. T9에서 runner가 계산한 값(Flyway API 자체)과 비교해 결정한다.

### 3.6 실행 경로

**A. Spring 설정 runner (기본)**

- `runner/`는 IDE 클래스를 쓰지 않는 작은 Java 11 jar다. 소스에는 `org.flywaydb.core.Flyway`를 **리플렉션으로만** 호출한다. 그래서 Flyway 9/10/11 어느 버전이 classpath에 있어도 같은 jar가 돈다 [A: `Flyway.configure()`, `FluentConfiguration.dataSource/table/schemas/locations/baselineOnMigrate`, `load().info().all()`, `migrate()`, `validateWithResult()`가 9–11에서 유지된다는 공개 API 기억].
- 실행: `OrderEnumerator.orderEntries(module).runtimeOnly().recursively().classes()`(+ 리소스 루트)로 classpath를 만든다 [A: 메서드 체인 이름은 구현 때 확인]. `java -cp runner.jar:<classpath> phantom.migrations.runner.Main info`를 `GeneralCommandLine`으로 띄운다. JDK는 모듈 SDK를 쓴다. 힙은 `-Xmx128m`이다.
- 입력: 설정 값은 커맨드 라인 인수로, 비밀번호는 환경 변수 `PHANTOM_FLYWAY_PASSWORD`로 넘긴다. stdout으로 JSON 한 줄을 받는다(`{"rows":[...],"error":null}`).
- `classpath:db/migration`이 리소스 루트에서 바로 보이도록 리소스 **소스** 폴더를 classpath에 넣는다. 빌드 산출물이 없어도 동작하게 하기 위해서다.
- `migrate`는 확인 대화 상자(대상 URL, 테이블, 대기 중인 버전 목록)를 거친 뒤 실행한다. `clean`은 runner에 아예 구현하지 않는다.
- 프로세스는 명령 하나를 처리하고 끝난다. 상주하지 않는다. 타임아웃은 60초다.

**B. Gradle 태스크 (보조)**

- gradle 모듈이 `ExternalSystemApiUtil`로 모듈의 Gradle 태스크 목록에서 `flywayInfo` 등이 있는지 본다. 있으면 툴 창 실행 버튼 드롭다운에 "Gradle: flywayInfo"를 추가한다.
- `ExternalSystemUtil.runTask(settings(externalProjectPath = 모듈 Gradle 경로, taskNames = ["flywayInfo"]), DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID, …)`로 실행한다. 출력은 Run 툴 창에 그대로 보여 준다.
- v0.1에서는 Gradle 출력 표를 파싱하지 않는다. 출력 형식이 버전마다 다를 수 있기 때문이다 [A]. 상태 열은 runner 결과로만 채운다.
- §1.1의 테이블·위치 불일치가 감지되면(Spring 설정의 `table`이 기본값과 다르고 Gradle 블록에서는 확인 불가) Gradle 실행 전에 "Gradle 설정이 Spring 설정과 다를 수 있음" 안내를 한 번 띄운다.

### 3.7 스레딩

- 감지·모델 계산: 백그라운드 `readAction {}`(non-blocking). 툴 창은 `ToolWindowFactory.createToolWindowContent`에서 빈 패널만 만들고, 모델은 코루틴으로 채운다.
- 배너: `EditorNotificationProvider.collectNotificationData`는 BGT에서 호출되는 계약이다 [A: 262의 계약, 구현 때 확인]. 여기서는 캐시된 모델만 조회한다. 모델이 없으면 배너를 띄우지 않는다(계산을 기다리지 않음).
- dumb mode: 이름 모델과 runner는 인덱스가 필요 없다(`DumbAware`). `FilenameIndex`를 쓰는 설정 해석만 smart mode를 기다린다. 기다리는 동안에는 기본 위치를 쓴다.
- 프로세스 출력 수집은 `OSProcessHandler` 스레드에서 하고, 결과 반영만 EDT `invokeLater`로 한다.

### 3.8 Kotlin K2 / UAST

- v0.1의 Flyway 기능은 SQL 파일 이름 기반이라 Kotlin 분석이 필요 없다.
- Kotlin 기반 마이그레이션(`class V1_22__Backfill : BaseJavaMigration()`)은 v0.1에서는 **파일 이름만** 인식한다. Flyway 규칙상 클래스 이름이 버전을 결정하고, Kotlin에서는 파일 이름이 대개 클래스 이름과 같기 때문이다 [A]. v0.2에서 UAST로 `BaseJavaMigration`/`JavaMigration` 하위 클래스를 찾는다. 이때 K1/K2 공통 코드로 동작한다(`uast-kotlin-fir` [V]). Analysis API를 직접 부르지 않는다.
- Spring 설정이 Kotlin 코드(`FlywayConfigurationCustomizer` 빈)로 바뀌는 경우는 정적으로 따라가지 않는다. 툴 창에 "코드로 설정된 값은 반영되지 않음" 주석을 붙인다.

---

## 4. 구현 단계

| # | 작업 | 일 | 사용자가 IDE에서 확인하는 합격 기준 |
|---|---|---|---|
| T0 | 플러그인 골격: `plugins/phantom-migrations`, 콘텐츠 모듈, `.iml`/`BUILD.bazel`, 번들 목록 등록, `./installers.cmd` 빌드 | 1 | Settings → Plugins에 "Database Migrations (Phantom)"이 번들로 보인다. Flyway가 없는 프로젝트에서는 Flyway 툴 창 버튼과 New 메뉴 항목이 없다 |
| T1 | 감지(`FlywayDetector`, `LiquibaseDetector`) + `library.toolWindow` 등록 + 빈 툴 창 | 1 | 사용자 프로젝트를 열면 "Flyway" 툴 창이 나타난다. 모듈 목록에 `infrastructure-module`(flyway-core)과 `adapter-module`(Gradle 플러그인)이 보이고 `batch-module`에는 "disabled" 표시가 붙는다 |
| T2 | 이름 파서·버전 정렬·문제 검출 (core, 순수 Kotlin) + 단위 테스트 | 1.5 | 툴 창에 22개가 `1.0, 1.1, 1.2 … 1.9, 1.10 … 1.21` 순서로 나온다(`1.10`이 `1.2` 앞에 오지 않음) |
| T3 | 위치 해석: 기본 위치 + spring 모듈(application\*.yaml, `spring.config.import` 추적) | 2 | 툴 창 위치 노드에 `classpath:db/migration (persistence/flyway/index.yaml)`처럼 출처가 보인다. 테이블 이름 `flyway_version_control`이 모듈 노드 툴팁에 보인다 |
| T4 | `EditorNotificationProvider` 배너 + `GotoRelatedProvider` | 1.5 | `V1_17__…sql`을 열면 "Flyway V1.17 · ← V1.16 | V1.18 →" 배너가 뜨고 클릭하면 이동한다. 파일을 `V1_17_add.sql`로 바꾸면 "구분자 `__` 누락" 경고 배너로 바뀐다. 같은 버전 파일을 하나 더 만들면 "중복 버전" 경고가 뜬다 |
| T5 | New → Flyway Migration (템플릿, 다음 버전 제안, 스타일 유지) | 1 | `db/migration` 폴더에서 실행하면 기본값이 `V1_22__`이고, 설명 "add index"를 넣으면 `V1_22__add_index.sql`이 생긴다 |
| T6 | runner jar (리플렉션 기반 info/validate/migrate, JSON 출력) + 빌드 규칙 | 2 | (CLI로 먼저) `java -cp runner.jar:<cp> … info`가 로컬 MySQL에 대해 JSON을 출력한다 |
| T7 | runner 실행기: classpath 구성, 프로필 선택, 비밀번호 환경 변수, 툴 창 상태 열, migrate 확인 대화 상자 | 2.5 | 툴 창 "Info (local)"를 누르면 각 버전에 Success/Pending이 표시된다. 새 `V1_22`는 Pending으로 보인다. "Migrate"는 대상 URL·테이블·대기 버전을 보여 주는 확인 창을 띄운다. 실행 중 IDE가 멈추지 않는다 |
| T8 | gradle 모듈: Gradle flyway 태스크 감지·실행, 불일치 안내 | 1 | `adapter-module` 드롭다운에 "Gradle: flywayInfo"가 있다. 누르면 Run 창에 Gradle 출력이 나온다. 처음 한 번 "Spring 설정(table=flyway_version_control)과 다를 수 있음" 안내가 뜬다 |
| T9 | 적용된 파일 수정 경고 + 로컬 체크섬 실험 | 1 | Info 후 `V1_0__init.sql`에 공백 한 줄을 넣으면 "적용된 마이그레이션 수정됨" 배너가 뜬다. 체크섬 일치율 실험 결과를 이 문서 §6에 기록한다 |
| T10 | Liquibase XML: 네임스페이스→XSD(프로젝트 jar 우선), include/includeAll 참조 | 2 | Liquibase 샘플 프로젝트(testData)에서 `<changeSet>` 안에 `<` 입력 시 `createTable`, `addColumn` 등이 완성된다. `<include file="…"/>`를 Ctrl+클릭하면 파일이 열린다 |
| T11 | 비용 측정·회귀 점검 (§5.3) | 1 | Flyway 없는 프로젝트에서 phantom-migrations 로더 클래스가 확장 등록 수준(목표 30개 이하)이다. 사용자 프로젝트 유휴 90초 JFR에 phantom-migrations 프레임이 없다 |

**v0.1 합계 약 17.5일.** T10(Liquibase)은 사용자 프로젝트와 무관하므로 일정이 밀리면 v0.2로 넘긴다(그러면 15.5일).

v0.2 대략: Liquibase YAML JSON 스키마 3일, changeSet 목록 2일, Liquibase runner 2일, placeholder 1.5일, callbacks/`U` 파일 1일, UAST 마이그레이션 클래스 1.5일.

---

## 5. 테스트 방법

### 5.1 자동 테스트 (커뮤니티 테스트 프레임워크)

- **core 순수 단위 테스트**(JUnit 5, IDE 불필요): 이름 파싱 표 기반 테스트. `V1_0__init`, `V1.10__x`, `V2__x`, `R__view`, `U1_2__x`, `B1__base`, 오류 사례 `V1_2_x`, `v1__x`(소문자 접두사), `V__x`(버전 없음), `V1__`(설명 없음 허용 여부 [A]), 버전 비교 `1.2 < 1.10`, `1.0 == 1`.
- **Light 테스트**(`BasePlatformTestCase`/`LightJavaCodeInsightFixtureTestCase` 계열 [A: 262의 정확한 기반 클래스 이름은 구현 때 확인]):
  - 라이브러리 감지: fixture 모듈에 `org.flywaydb:flyway-core` 가짜 라이브러리를 추가하기 전후의 감지 결과와 캐시 무효화.
  - 위치 해석: `testData/spring/`에 `application.yaml` + `spring.config.import` 체인을 두고 해석된 위치·테이블 검증. 사용자 프로젝트 구조(두 모듈, import 체인)를 축소해서 재현한다.
  - 배너: `myFixture.configureByFile("db/migration/V1_2_bad.sql")` 후 `EditorNotificationProvider` 결과 검사.
  - GotoRelated: 이전/다음 항목 목록 검사.
  - New 액션: 다음 버전 제안 값 검사.
  - Liquibase: `myFixture.completeBasic()` 결과에 `createTable` 포함, include 참조 resolve.
- **runner 통합 테스트**: H2 in-memory + Flyway jar(테스트 의존성)로 `info`/`migrate` JSON 출력 검증. Testcontainers는 쓰지 않는다(CI 단순화).

### 5.2 사용자 프로젝트 수동 점검 (`~/orca/workspaces/prototype-reservation-system/infro`)

1. 프로젝트를 연다. 인덱싱이 끝나기 전에도 Flyway 툴 창에 기본 위치 기준 목록이 보이는지 본다.
2. 22개 정렬 순서를 확인한다(`1.9 → 1.10`).
3. `V1_17__add_optimistic_lock_for_timetable.sql`을 열어 배너와 이전/다음 이동을 확인한다.
4. New → Flyway Migration으로 `V1_22__test.sql`을 만들고, 이어서 삭제한다.
5. 로컬 MySQL(`localhost:3306`, docker compose)을 띄우고 Info(local) → 상태 열 확인. **이 단계는 사용자가 DB를 띄웠을 때만** 한다.
6. `adapter-module`의 "Gradle: flywayInfo"를 실행해 runner 결과와 비교한다. §1.1의 추론(테이블·위치 불일치)을 확인하고 결과를 이 문서 §6에 적는다.
7. `migrate`는 테스트용 스키마에서만 사용자가 직접 실행한다.

### 5.3 비용 측정 (Ultimate와 같은 방법)

| 항목 | 방법 | 목표 |
|---|---|---|
| 안 쓰는 프로젝트의 클래스 적재 | 빈 Kotlin 프로젝트, `jcmd <pid> VM.classloader_stats` / `-Xlog:class+load` grep `phantom.migrations` | 30개 이하 (Ultimate 221 [M]) |
| 등록 액션 수 | `ActionManager` 덤프 또는 plugin.xml 집계 | 6개 이하 (Ultimate 68 [M]) |
| 사용하는 프로젝트의 metaspace | `jcmd VM.metaspace` 로더별 | 1 MB 미만 |
| 유휴 CPU | 90초 idle JFR (`02-runtime/2026-10-16-startup-jfr.md`와 같은 설정) | phantom-migrations 프레임 0 |
| EDT | freeze 리포트, JFR EDT 샘플 | phantom-migrations 프레임 0 |
| 상주 프로세스 | `ps` | runner는 명령 종료 후 0개 |

---

## 6. 리스크와 남은 의문

| # | 리스크 / 의문 | 영향 | 대응 |
|---|---|---|---|
| R1 | Gradle 플러그인 설정과 Spring 설정이 다르다는 추론(§1.1)이 틀렸을 수 있다 (예: Gradle 플러그인이 다른 곳의 설정을 읽음) | 기본 실행 경로 선택 | T8에서 실제로 실행해 확인한다. 맞든 틀리든 runner는 유지한다(앱과 같은 설정을 보는 유일한 경로) |
| R2 | runner의 리플렉션 호출이 Flyway 주요 버전 간 API 변경으로 깨질 수 있다 [A] | info/migrate 실패 | 버전별 분기를 runner 안에 두고 `getLibraryVersion`으로 미리 경고. 지원 범위는 9–11로 명시 |
| R3 | Flyway 10은 DB별 모듈(`flyway-mysql`)이 classpath에 있어야 한다. runtime classpath에서 빠지면 "Unsupported database" | runner 실패 | 오류 메시지를 그대로 보여 주고 "flyway-mysql 의존성 확인"을 안내. 사용자 프로젝트는 `libs.bundles.flyway`에 포함되어 있다 [V] |
| R4 | runtime classpath에 빌드 산출물이 없으면(한 번도 빌드 안 함) 의존 jar는 있어도 `FlywayConfig` 같은 사용자 코드는 없다 | 사용자 코드 콜백/Java 마이그레이션 누락 | runner는 사용자 코드를 실행하지 않는다(설정은 인수로 받음). Kotlin 기반 마이그레이션이 있으면 "빌드 후 실행" 안내 |
| R5 | 비밀번호를 YAML에서 읽어 자식 프로세스에 넘기는 것 | 비밀 노출 | 환경 변수로만 전달, 로그·모델·디스크에 남기지 않음. 값이 `${ENV}` 플레이스홀더면 IDE 환경에서 치환하지 않고 사용자에게 물어 `PasswordSafe`에 저장(선택) |
| R6 | 로컬 체크섬 규칙(줄 끝, BOM, 인코딩)이 Flyway와 다를 수 있다 [A] | 오탐 경고 | §3.5처럼 "수정됨"만 경고. T9 실험 후 결정 |
| R7 | 프로필 병합 단순화(v0.1)로 실제 값과 다를 수 있다 | 잘못된 DB 대상 | migrate 확인 창에 URL·테이블·스키마를 크게 보여 주고 출처 파일을 링크. phantom-spring-boot 프로필 API가 생기면 교체 |
| R8 | `library.toolWindow`의 `LibrarySearchHelper`가 Gradle 플러그인만 적용된 모듈(라이브러리 없음)을 놓친다 | 툴 창 미표시 | 사용자 프로젝트에서는 `infrastructure-module`이 flyway-core를 가지므로 영향 없음 [V]. 일반화는 helper에서 Gradle 태스크 데이터도 보도록 확장 |
| R9 | `incompatible-with com.intellij.flyway` 문법·의미가 262에서 기대대로인지 | 동시 설치 충돌 | phantom-spring-boot와 같은 방식. 첫 빌드에서 확인 [A] |
| R10 | Liquibase XSD가 프로젝트 jar에 없는 버전 | 완성 없음 | 마지막 폴백으로 공개 XSD URL을 플랫폼 "Fetch external resource" 흐름에 맡긴다. Liquibase는 Apache 2.0이라 XSD 동봉도 가능하지만 v0.1에서는 하지 않는다 [A] |
| Q1 | Flyway 10 Gradle 플러그인이 `flyway { table }`이 없을 때 Spring 설정을 읽는 기능이 있는가 | R1 | 공개 문서 확인 + T8 실행 |
| Q2 | `EditorNotificationProvider` 대신 SQL 파일에 `localInspection`을 쓰는 편이 나은가 (문제 창 통합) | UX | SQL 언어 ID가 phantom-db(`SQL`)로 확정되면 v0.2에서 inspection을 추가 검토. v0.1은 언어 독립인 배너로 간다 |
| Q3 | v0.3 diff 생성의 입력(JPA 모델, 스키마 스냅샷) API가 phantom-jpa/phantom-db 설계와 맞는가 | v0.3 범위 | 두 문서의 `JpaFacade`, `SchemaProvider` 제안과 맞춘 뒤 확정 |
