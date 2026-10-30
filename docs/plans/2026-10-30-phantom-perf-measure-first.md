# Phantom 성능 개선: 측정 → 제거·지연 → VM 옵션 A/B 구현 계획

> Produced by write:plans. Owner for execution routing: planning:executing-plans. Steps use checkbox (`- [ ]`) syntax.
> 근거: `.claude/brainstorming/perf-improvement-points.jsonl` (10인 토론, 2026-10-30)

**Goal:** 기본 기능을 유지한 채, 측정으로 고른 대상만 줄여 Phantom을 더 가볍게 하고, 그 효과와 회귀를 반복 측정으로 판정한다.

**Architecture:** 리포 루트의 `phantom-bench.sh`가 설치된 앱을 격리된 config/system 디렉터리로 N회 띄우고, 플랫폼에 이미 있는 출력(`idea.log.perf.stats.file` 시작 리포트, `idea.log`의 dumb mode·freeze 줄, `jcmd`)만 모아 실행별 JSON을 남긴다. `phantom-bench-report.py`가 중앙값을 내고 기준선과 비교한다. 제거·지연(O3)과 VM 옵션(O2′) 변경은 모두 이 비교를 통과해야 머지한다.

**Tech Stack:** zsh, python3(표준 라이브러리만), `jcmd`(JBR 25), macOS `footprint`, 기존 `phantom-build.sh`/`phantom-install.sh`.

---

## 계획 전 확인된 사실 (토론 주장 정정)

| 토론 주장 | 코드 확인 결과 | 계획에 반영 |
|---|---|---|
| `JVMStatsToOTelReporter`가 100ms 루프로 유휴 CPU를 쓴다 | 루프(`AvgMemoryUsageProvider`)는 `isInIntegrationTest()`일 때만 생성됨(`JVMStatsToOTelReporter.kt:165-171`). 일반 실행에선 게이지 등록만 함 | 확정 낭비 아님. 측정 상위에 오를 때만 대상 |
| VFS 헬스체크가 시작 직후 IO를 쓴다 | 첫 실행 10분 후, 이후 12시간 주기(`VFSHealthChecker.kt:59-65`) | 시작 비용 아님. 대상에서 제외 |
| 시작 리포트를 파일로 받으려면 코드 수정 필요 | `-Didea.log.perf.stats.file`로 이미 파일 출력 가능(`StartUpPerformanceReporter.kt:55`) | 코드 수정 없이 사용 |
| `ReservedCodeCacheSize=512m`를 줄이면 RSS가 준다 | 예약은 가상 주소 공간. 실사용 41MB만 커밋됨 | RSS 기대 낮음. A/B로만 확인 |

---

### Task 1: 단일 실행 측정 스크립트

**Files:** create `phantom-bench.sh`, create `phantom-bench-report.py`
**Interfaces:** produces `out/phantom-bench/<short-sha>/<label>/run-<n>.json`. 키: `first_smart_mode_ms`, `indexing_settled_ms`, `footprint_mb`, `live_heap_mb`, `loaded_classes`, `freezes`, `freeze_total_ms`, `startup_top`(시작 리포트 상위 20개 활동/서비스와 ms)
**Blocked by:** none
**Pass bar:** `./phantom-bench.sh --runs 1 --label smoke`가 0으로 끝나고 `run-1.json`의 모든 키가 null이 아닌 값을 가진다

- [ ] 1: 인자 정의. `--runs N`(기본 5), `--label L`(기본 `base`), `--fixture DIR`(기본 `~/projects/private/prototype-reservation-system`, `out/phantom-bench/work/fixture`로 1회 복사해 사용), `--vm-extra FILE`(Task 6용 추가 VM 옵션), `--settle SEC`(기본 180)
- [ ] 2: 격리. 실행마다 `mktemp -d`로 config/system/log 디렉터리를 만든다. 사용자 config는 1회 복사해 고정 템플릿으로 쓴다(벤치 문서와 같은 조건). `idea.properties`로 `idea.config.path`, `idea.system.path`, `idea.log.path`를 지정하고 `IDEA_PROPERTIES`로 넘긴다. VM 옵션은 번들 파일 + `-Didea.log.perf.stats.file=<log>/perf.json` + `--vm-extra` 내용을 합친 파일을 `IDEA_VM_OPTIONS`로 넘긴다
- [ ] 3: 확인 실행. 앱을 fixture로 띄우고 `idea.log`에 `-Didea.log.perf.stats.file` 값이 찍히는지, 번들 옵션(`-Xmx2048m`)이 유지되는지 확인한다. `IDEA_VM_OPTIONS`가 번들 파일을 대체하는지, 추가하는지 여기서 확정하고 스크립트 주석에 적는다
- [ ] 4: 수집. `--settle`초 뒤 `jcmd <pid> GC.run` → `GC.heap_info`(live heap), `PerfCounter.print`의 `java.cls.loadedClasses`, `footprint -p <pid>`. `idea.log`에서 첫/마지막 `exit dumb mode` 시각(프로세스 시작 기준 ms), `UI was frozen` 줄 수와 합계 ms. `perf.json`에서 상위 20개 항목
- [ ] 5: 종료와 정리. 앱을 정상 종료(`osascript quit`)하고, 10초 안에 안 끝나면 kill. 임시 디렉터리 삭제. 실행 사이에 서로 다른 PID인지 확인
- [ ] 6: `phantom-bench-report.py`는 이 Task에선 JSON 1개를 읽어 표로 출력만 한다
- [ ] 7: commit `Phantom bench: one isolated run of the installed app, metrics from the platform's own reports`

### Task 2: 반복 실행, 중앙값, 기준선 비교

**Files:** modify `phantom-bench.sh`, modify `phantom-bench-report.py`, create `docs/phantom/bench/baseline.json`
**Interfaces:** consumes Task 1의 `run-<n>.json`. produces `summary.json`(지표별 중앙값·최소·최대), `report.py compare <baseline.json> <summary.json>` → 종료 코드 0(통과) / 1(회귀)
**Blocked by:** Task 1
**Pass bar:** 같은 빌드로 두 번 측정해 compare하면 0, 기준선의 `footprint_mb`를 15% 낮춘 가짜 기준선과 compare하면 1

- [ ] 1: `--runs N`이면 N회 반복 후 `summary.json` 작성
- [ ] 2: compare 규칙. 지표별 허용폭: 시간 지표 +10%, 메모리·클래스 수 +5%, `freezes`는 기준선 중앙값 +1회. 넘으면 해당 지표와 수치를 출력하고 1로 종료
- [ ] 3: 노이즈 확인. 현재 main 빌드로 5회 × 2세트 측정해 세트 간 중앙값 차이를 기록. 허용폭보다 크면 허용폭을 그 차이의 2배로 올리고 근거를 `docs/phantom/bench/README.md`에 적는다
- [ ] 4: 첫 세트 `summary.json`을 `docs/phantom/bench/baseline.json`으로 커밋(빌드 sha 포함)
- [ ] 5: commit `Phantom bench: median of N runs and a regression check against a committed baseline`

### Task 3: 정적 지표 게이트 (실행 없이)

**Files:** modify `phantom-bench-report.py`, create `docs/phantom/bench/static-baseline.json`
**Interfaces:** consumes `phantom-build.sh` 산출 앱 번들. produces `report.py static <app>` → 배포 크기, `lib/`+`plugins/` jar 수, 번들 플러그인 수, `plugin.xml`/`META-INF/*.xml`에 등록된 `postStartupActivity`·`backgroundPostStartupActivity`·`applicationActivity` 수
**Blocked by:** Task 2 (compare 규칙 재사용)
**Pass bar:** 현재 앱에 대해 값을 출력하고, 같은 앱으로 static compare 시 0

- [ ] 1: 앱 번들의 jar를 열어 등록 수를 센다(zipfile만 사용)
- [ ] 2: 기준선 저장, compare는 Task 2와 같은 방식(허용폭: 등록 수 +0, 크기 +2%)
- [ ] 3: commit `Phantom bench: static gate on bundle size and startup activity count`

### Task 4: O3 후보 목록 (데이터로 선정)

**Files:** create `docs/phantom/2026-10-30-startup-cost-candidates.md`
**Interfaces:** consumes `baseline.json`의 `startup_top`, Task 1의 실행별 `perf.json`
**Blocked by:** Task 2
**Pass bar:** 문서에 비용 상위 10개 항목이 ms·소속 모듈·기능 영향·결정(제거/지연/유지)과 함께 있고, 각 결정에 근거 한 줄이 있다

- [ ] 1: 5회 중앙값 기준 상위 항목을 표로 만든다
- [ ] 2: 각 항목을 분류. `*-api`·EP 계약이면 유지. Kotlin/Java/Gradle 플러그인 소속이면 유지(규칙상 수정 금지). 플랫폼 impl이고 사용자 기능과 무관하면 제거 후보. 기능은 필요하지만 시작 시점이 아니어도 되면 지연 후보
- [ ] 3: 위 "정정" 표의 두 항목은 측정값과 함께 결론을 적는다
- [ ] 4: commit `Startup cost candidates from the bench baseline`

### Task 5: O3 적용 (항목당 1커밋)

**Files:** modify `platform/platform-resources/src/META-INF/PlatformExtensions.xml` 또는 해당 항목의 등록 파일(Task 4 문서에 항목별 경로)
**Interfaces:** consumes Task 4의 후보와 Task 2의 compare
**Blocked by:** Task 3, Task 4
**Pass bar (항목마다):** (a) `phantom-bench.sh --runs 5 --label <item>` 후 compare가 0이고 `startup_top`에서 그 항목이 사라짐 (b) Task 3 static 게이트에서 등록 수가 1 줄어듦 (c) 해당 모듈의 기존 테스트 통과

- [ ] 1: 후보 하나를 고른다. 제거면 등록 줄 삭제, 지연이면 `postStartupActivity`를 `backgroundPostStartupActivity`로 옮기거나 처음 쓰는 시점에 초기화되도록 등록만 바꾼다(API 시그니처 불변)
- [ ] 2: `phantom-build.sh` → `phantom-install.sh --no-build` → 벤치
- [ ] 3: 개선이 허용폭 안(노이즈)이면 되돌리고 문서에 "효과 없음"으로 기록
- [ ] 4: 개선이면 static 기준선 갱신과 함께 commit `<항목>: drop/defer at startup — <측정 전후 수치>`
- [ ] 5: 다음 후보. 3개 연속 효과 없음이면 O3를 멈추고 결과를 문서에 정리

### Task 6: O2′ VM 옵션 A/B

**Files:** modify `platform/build-scripts/src/org/jetbrains/intellij/build/impl/VmOptionsGenerator.kt` (채택된 옵션만), create `docs/phantom/bench/vm-ab/*.vmoptions`
**Interfaces:** consumes Task 1의 `--vm-extra`, Task 2 compare
**Blocked by:** Task 2
**Pass bar (실험마다):** 실험 라벨의 `summary.json`과 base를 compare한 결과와 판정(채택/기각)이 `docs/phantom/bench/vm-ab/README.md` 표에 있다

- [ ] 1: 실험 A `-Xmx1536m`: footprint·live heap·freezes 비교. freezes 증가 또는 OOM 로그 있으면 기각
- [ ] 2: 실험 B `-XX:ReservedCodeCacheSize=256m`: 기대 낮음(정정 표). footprint 변화가 허용폭 밖일 때만 채택
- [ ] 3: AOT 1회 확인 `-Xlog:aot*=info,cds=info`로 1회 실행, 아카이브에서 로드된 클래스 수를 기록. 전체 로드 클래스의 30% 미만이면 AOT 검토 종료를 README에 기록
- [ ] 4: 채택된 옵션만 `VmOptionsGenerator.kt`의 `COMMON_VM_OPTIONS`에 `// phantom-idea:` 주석(측정 수치 포함)과 함께 반영, 빌드 후 벤치 재확인
- [ ] 5: commit `VM options: <옵션> — <측정 전후 수치>`

---

## 이번 범위 밖 (O1 데이터가 나오면 재검토)

- O4 플랫폼 동시성 코어 수정. 예외: freeze 상위 스택이 한 지점을 반복 지목할 때 그 지점만
- Phantom Spring 지원의 resolve 경계
- 빌드 폴더 이벤트 watcher 차단, 캐시 상한
- 타이핑 지연 p99, 장시간 힙 기울기, IO 카운터 (2단계 지표)
