# Phantom IDEA 기능 매트릭스 (FE 제외)

기준: 사용자가 정리한 IntelliJ IDEA Ultimate 기능 목록(2026-10-16)에서 프론트엔드(JS/TS, React/Vue/Angular, HTML/CSS, Node.js)를 뺀 전체.

- **C** = intellij-community에 이미 있다. Phantom에 그대로 들어 있다
- **U** = Ultimate 전용이다. Phantom 플러그인으로 새로 만든다 (클린룸)
- **P** = 일부만 community에 있다

"설계" 열은 어떤 설계 문서가 담당하는지다 (`docs/phantom/design/2026-10-16-<key>.md`).

## Spring / Spring Boot — 전부 U

| 기능 | 구분 | 설계 |
|---|---|---|
| Spring Initializr로 프로젝트 생성, 기존 프로젝트에 Starter 추가 | U | spring-boot |
| Bean 자동 완성, 의존성 주입 코드 생성, 주입 지점에서 Bean 선언으로 이동 | U | spring-core |
| Bean 주입 누락·후보 충돌 검사, Spring 전용 검사·빠른 수정 | U | spring-core |
| 컴포넌트 논리 구조 탐색, 이벤트 발행자·리스너 간 이동 | U | spring-core |
| Bean 의존 관계 다이어그램 | U | diagrams |
| application.yml/properties 자동 완성, 속성 문서·타입, 속성↔코드 이동 | U | spring-boot |
| 프로파일별 실행 설정, Spring Boot 실행·디버그 구성 | U | spring-boot |
| Services 창에서 여러 앱 관리 | P (Services 창은 C, Spring Boot 항목은 U) | spring-runtime |
| Actuator Bean·Health·Mappings·Environment | U | spring-runtime |
| 런타임 Bean 검사, 런타임 설정값과 출처, Bean 메서드 평가·실행, 트랜잭션 상태 | U | spring-runtime |
| DevTools 재시작 연동 | U | spring-runtime |

## API 개발

| 기능 | 구분 | 설계 |
|---|---|---|
| Endpoints 창, URL로 엔드포인트 검색, 엔드포인트→Controller 이동 | U | spring-boot |
| Controller에서 HTTP 요청 생성 | U | http-client |
| .http 파일 작성·실행, 환경별 변수, 인증 헤더·토큰, 요청 간 값 재사용, 응답 확인 | U | http-client |
| 응답 검증 스크립트 | U | api-extras |
| OpenAPI / Swagger 명세 편집 | U | api-extras |
| GraphQL 요청 실행 | P (JetBrains GraphQL 플러그인은 무료이고 사용자가 이미 설치함. 실행은 HTTP client에 통합) | api-extras |
| WebSocket 연결·메시지, gRPC 요청 | U | api-extras |
| 클라이언트 코드↔서버 구현 이동 | U | api-extras |

## DB / SQL / JPA

| 기능 | 구분 | 설계 |
|---|---|---|
| DB 연결, 테이블·컬럼·인덱스 탐색, SQL 콘솔 | U | database |
| SQL 자동 완성(테이블·컬럼), 문법 검사·포맷팅, 실행 계획 | U | database |
| 데이터 조회·편집, 가져오기·내보내기 | U | database |
| DB 구조 다이어그램 | U | diagrams |
| Java/Kotlin 문자열 안의 SQL 편집 | U (SQL 언어가 Ultimate) | database |
| JPA 엔티티·매핑 탐색, JPQL/HQL, Spring Data Repository, 파생 쿼리 메서드, @Query | U | jpa |
| DB 테이블에서 JPA 엔티티 생성, JPA Console | U | jpa-extras |
| Flyway / Liquibase | U | migrations |

## 코드 작성·자동화 — 거의 C

자동 완성, 스마트 완성, import, 생성자·Getter·Setter·equals/hashCode/toString 생성, 구현·오버라이드, Live Templates, Postfix, Surround With, 포맷팅, 저장 시 포맷팅, 검사·빠른 수정, null 분석, JSON 언어 주입 → **C**

- 예외: 문자열 안 **SQL** 주입은 SQL 언어가 필요하므로 U (database)

## 코드 탐색·리팩터링 — 거의 C

Search Everywhere, 클래스·파일·심볼 찾기, 선언·구현·상위 이동, 사용처 찾기, 호출·타입 계층, 구조 보기, 최근 파일·위치, 북마크, 이름 변경, 추출, 시그니처 변경, 이동, 인라인, 안전 삭제, 구조 기반 검색·치환 → **C**

- 예외: **중복 코드 탐지**는 U (`intellij.platform.duplicatesDetector`가 Ultimate lib에만 있음) → profiler-duplicates

## 디버깅·성능 분석

| 기능 | 구분 | 설계 |
|---|---|---|
| 브레이크포인트 전 종류, 예외·필드 감시, 값 검사, Watch, Evaluate, 값 변경, Smart Step Into, 멀티스레드, 원격 JVM, HotSwap | C | — |
| Stream 처리 시각화 | C (`plugins/stream-debugger`) | — |
| 스레드 덤프 분석 | C (Analyze Stack Trace / Thread Dump) | — |
| JVM 프로파일링, CPU·메모리 분석, 힙 덤프 분석 | U | profiler-duplicates |

## 테스트·빌드 — C

JUnit/TestNG, 메서드별 실행·디버그, 테스트 생성, 실패 재실행, 커버리지(`plugins/coverage`), 결과·시간, Maven/Gradle 동기화·실행, 의존성 분석(external-system dependency analyzer), 실행 구성 저장·공유·Compound, Before launch → **C**

## Git·변경 관리 — C

비교, 부분 커밋, 브랜치, 3-way merge, Log 그래프, Annotate, Cherry-pick·Rebase, Stash·Shelf, 커밋 전 검사, Local History → **C**

## 인프라 (FE 제외)

| 기능 | 구분 | 설계 |
|---|---|---|
| Docker 이미지·컨테이너, Docker Compose, Kubernetes | U | docker-k8s |
| 애플리케이션 서버 실행·배포 (Tomcat 등) | U | infra-extras (우선순위 낮음, Spring Boot는 내장 서버) |
| SSH·원격 개발 | P (SSH 터미널 등 일부 C, Remote Dev·원격 인터프리터는 U) | infra-extras |

## 자잘한 편의 기능 — C

내장 터미널, Scratch 작성·실행, 에디터 분할, 다중 커서, 클립보드 기록, 줄·블록 이동, 줄 복제·삭제·합치기, 선택 확장, 자동 괄호, 매개변수 정보, 빠른 문서, Inlay Hints, TODO, 파일·폴더 비교, JSON·XML 포맷팅, EditorConfig, 키맵·코드 스타일 → **C**

## 요약

- **C**는 이미 Phantom에 들어 있다. 목록의 절반 이상이 여기에 해당한다.
- **U**는 설계 문서 12개로 나뉜다.
  - 1차: spring-core, spring-boot, jpa, database, http-client, docker-k8s, build-wiring
  - 2차: spring-runtime, api-extras, jpa-extras, migrations, diagrams, profiler-duplicates, infra-extras
