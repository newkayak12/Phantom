# Phantom IDEA 문서 안내

- 목적: Phantom IDEA 문서가 무엇이 있고, 어떤 순서로 읽고, 어느 것이 지금도 맞는지 알려 준다.
- 기준 커밋: 64dc82b3f2
- 작성일: 2026-11-12

Phantom IDEA는 JetBrains intellij-community 태그 `idea/262.10968.63`의 개인 포크다. 저장소 루트가 곧 community 루트다.

## 여기서 시작

1. **쓰거나 빌드하려는 사람**: [README.md](../../README.md)(한국어 [README.ko.md](../../README.ko.md)) → [BUILD.md](BUILD.md)(준비물, 빌드, 설치, 릴리스) → [CHANGELOG.md](../../CHANGELOG.md)(무엇이 바뀌었나).
2. **코드를 고치려는 사람**: [ARCHITECTURE.md](ARCHITECTURE.md)(지도) → [FORK-DELTA.md](FORK-DELTA.md)(업스트림과 다른 곳 전부) → [DEVELOPMENT.md](DEVELOPMENT.md)(작업별 방법, 검증, 알려진 문제) → [BUILD.md](BUILD.md) 7-8절(벤치, 테스트).
3. **AI 에이전트**: [AGENTS.md](../../AGENTS.md)(자동으로 읽힘) → 이 문서 → DEVELOPMENT.md 1절(규칙)과 5절(알려진 문제) → 고칠 주제의 FORK-DELTA 절 → 필요하면 ARCHITECTURE.md. 현행 문서의 `경로:줄`은 기준 커밋 기준이므로 HEAD에서 다시 열어 확인한다.

## 문서 목록

유형: **현행 문서**(지금 코드를 설명, 고칠 때 함께 갱신) / **설계 기록**(만들기 전 설계) / **측정 기록**(그때의 측정값) / **계획 기록**(작업 계획). 설계·측정·계획 기록은 고쳐 쓰지 않는다.

| 경로 | 무엇 | 유형 | 지금 유효한가 |
|---|---|---|---|
| `README.md`, `README.ko.md` (루트) | 프로젝트 소개와 빠른 시작(영어/한국어) | 현행 문서 | 유효. 기준 커밋 판 README의 오래된 주장은 FORK-DELTA 7절에 정리 |
| `AGENTS.md` (루트) | AI 에이전트 지침. 업스트림 생성본을 이 포크에 맞게 손으로 고침(2026-11-12) | 현행 문서 | 유효. `.ai/render-guides.mjs`로 다시 만들면 안 됨(파일 첫머리 주석) |
| `CHANGELOG.md` (루트) | 릴리스별 변경 이력과 유지 규칙 | 현행 문서 | 유효 |
| `docs/phantom/README.md` | 이 문서 | 현행 문서 | 유효 |
| `docs/phantom/ARCHITECTURE.md` | 디렉터리, 모듈, 기동 흐름, 외부 통신 구조 | 현행 문서 | 유효 |
| `docs/phantom/FORK-DELTA.md` | 업스트림 대비 패치 지점, 플래그, 이동한 플러그인, 충돌 위험 파일 | 현행 문서 | 유효. 7절의 README 표는 개편 전 README(be60914099) 기준 |
| `docs/phantom/DEVELOPMENT.md` | 작업별 방법, 검증, 커밋 규칙, 알려진 문제 모음 | 현행 문서 | 유효 |
| `docs/phantom/BUILD.md` | 빌드, 서명, 설치, 릴리스, 벤치, 테스트 | 현행 문서 | 유효 |
| `docs/phantom/bench/README.md` | 벤치 도구 사용법, 기준선, 허용 오차 | 현행 문서 | 유효. 가리키는 런타임 기준선 `docs/phantom/bench/baseline.json`은 없음 |
| `docs/phantom/bench/static-baseline.json` | 정적 게이트 기준선(26.10.27) | 측정 기록 | 유효(게이트 입력) |
| `docs/phantom/2026-10-16-feature-matrix.md` | Ultimate 기능 대비 Phantom 계획 표 | 설계 기록 | 작성 시점 기준. 플러그인 위치는 FORK-DELTA 5절로 다시 본다 |
| `docs/phantom/2026-10-29-indexing-changes.md` | trigram 인덱스·인덱싱 지문 변경과 측정값(기준 커밋 판 README의 Indexing 절을 옮김) | 측정 기록 | 당시 측정값. 패치 위치는 FORK-DELTA 3.5절 |
| `docs/phantom/design/2026-10-16-roadmap.md` | 설계 14건의 우선순위와 S1–S5 진행 | 계획 기록 | 머리의 2026-11-12 상태 메모 참고. S1–S5는 Phantom-plugins로 이동 |
| `docs/plans/2026-10-30-phantom-perf-measure-first.md` | 측정 우선 성능 계획(Task 1–6) | 계획 기록 | Task 1–3은 커밋됨(38efaf66bf, 6767ffc6e1, 4b3ffd9498). Task 4–6 진행은 미확인. 체크박스는 갱신 안 됨 |
| `docs/superpowers/specs/2026-10-26-phantom-tab-groups-design.md`, `docs/superpowers/plans/2026-10-26-phantom-tab-groups.md` | 탭 그룹 설계와 구현 계획 | 설계 기록, 계획 기록 | (ii) 이동: `phantom-tabs`는 b68d97332e에서 Phantom-plugins로 |
| `docs/superpowers/specs/2026-10-26-phantom-workspaces-design.md` | 한 창에 여러 프로젝트 | 설계 기록 | (ii) 이동: `phantom-workspaces`는 8983487a29에서 Phantom-plugins로 |

설계 문서 14건(`docs/phantom/design/2026-10-16-*.md`, 모두 설계 기록)의 플러그인 상태. (i) 이 리포에 있음 / (ii) Phantom-plugins로 이동 / (iii) 이 리포에서 만든 적 없음. (ii)는 FORK-DELTA 5.2절, (iii)은 `git log --all -- plugins/phantom-<이름>` 결과 0건으로 판정했다. (iii)이 Phantom-plugins에 있는지는 이 리포에서 확인할 수 없다(미확인).

| 설계 문서 | 플러그인 | 상태 |
|---|---|---|
| [build-wiring](design/2026-10-16-build-wiring.md) | (공통 배선, 플러그인 아님) | 해당 없음. 현행 절차는 DEVELOPMENT.md 2절 (c) |
| [spring-core](design/2026-10-16-spring-core.md) | phantom-spring | (ii) c83c67511e |
| [spring-boot](design/2026-10-16-spring-boot.md) | phantom-spring-boot | (ii) c83c67511e |
| [jpa](design/2026-10-16-jpa.md) | phantom-jpa | (ii) 53c0592476 |
| [jpa-extras](design/2026-10-16-jpa-extras.md) | phantom-jpa 확장 | (ii) 대상 플러그인은 이동. 확장 기능 구현 여부는 미확인 |
| [http-client](design/2026-10-16-http-client.md) | phantom-http | (ii) cab0fa60d7, ac3007c625 |
| [database](design/2026-10-16-database.md) | phantom-db | (ii) de02add11d |
| [docker-k8s](design/2026-10-16-docker-k8s.md) | phantom-containers | (iii) |
| [spring-runtime](design/2026-10-16-spring-runtime.md) | phantom-spring-runtime | (iii) |
| [migrations](design/2026-10-16-migrations.md) | phantom-migrations | (iii) |
| [api-extras](design/2026-10-16-api-extras.md) | phantom-api | (iii) |
| [diagrams](design/2026-10-16-diagrams.md) | phantom-diagrams | (iii) |
| [profiler-duplicates](design/2026-10-16-profiler-duplicates.md) | phantom-profiler, phantom-duplicates | (iii) |
| [infra-extras](design/2026-10-16-infra-extras.md) | phantom-ssh-terminal | (iii) |

(i)에 해당하는 설계 문서는 없다. 이 리포에 남은 Phantom 플러그인 `phantom-sync`, `phantom-theme`에는 설계 문서가 없다. 업스트림 문서(`docs/*.puml`, `docs/notebooks/`, `docs/configurable.md`)는 ARCHITECTURE.md 8절이 안내한다.

## 문서 유지 규칙

- 현행 문서는 머리에 **기준 커밋**과 **작성일**을 둔다. 내용을 고치면 둘 다 바꾸고, 바꾼 `경로:줄`은 그 커밋에서 다시 연다.
- 패치 지점, 플래그, 번들 목록이 바뀌면 FORK-DELTA.md의 해당 표를 고친다.
- 기동 흐름, 모듈 구조, 외부 통신 구조가 바뀌면 ARCHITECTURE.md를 고친다.
- `phantom-*.sh`, `phantom-bench-report.py`, 릴리스 워크플로가 바뀌면 BUILD.md를 고친다.
- 작업 방법이나 검증 절차가 바뀌거나 알려진 문제가 생기고 풀리면 DEVELOPMENT.md를 고친다.
- 모든 변경은 CHANGELOG.md `미배포`에 한 줄을 더한다(CHANGELOG 머리의 규칙).
- 문서를 더하거나 지우면 이 문서의 목록을 고친다. 설계·측정·계획 기록은 본문을 고치지 않고, 상태가 바뀌면 이 표나 머리의 날짜 붙은 메모로 알린다.
