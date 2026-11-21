# 성능

2026-11-21에 한 대의 기기(Apple M1 Pro, RAM 32GB, macOS 27.0)에서 측정했다. 대부분 한 번씩 잰 값이라 정확한 수치가 아니라 대략적인 크기로 봐야 한다.

## Phantom과 IntelliJ IDEA Ultimate 비교

Phantom PH-261121.4와 같은 업스트림 기반의 IntelliJ IDEA Ultimate 262.10968.63을 비교했다. 둘 다 새 설정·시스템 폴더로 cold 시작했고, 약 9,900개 파일로 만든 Java 프로젝트를 열었다.

| | Ultimate | Phantom |
|---|---|---|
| 실행 → 프로젝트 열림 | 5.14초 | 1.72초 |
| 상주 메모리(RSS) | 807MB | 298MB |
| 강제 GC 후 힙 | 257MB | 120MB |
| 로드된 플러그인 | 162개 | 47개 |
| 인덱싱 | 4.8초 | 5.6초 |

- 실행 → 프로젝트 열림은 시작 리포트(`-Didea.log.perf.stats.file`)의 `totalDuration`이다.
- RSS는 시작이 끝난 뒤 `ps`로, 힙은 `jcmd GC.run` 다음 `jcmd GC.heap_info`로 읽었다.
- 인덱싱 두 값은 재는 방식이 같지 않다. "비슷한 수준"으로만 본다.
- warm 시작 비교는 쓸 수 없었다. 측정하는 동안 다른 작업 때문에 부하 평균이 57까지 올라갔다. 기계가 한가할 때 다시 재야 한다.

## Phantom 변경별 효과

| 변경 | 이전 | 이후 |
|---|---|---|
| 동적 CDS 아카이브 대신 앱과 함께 빌드한 AOT 캐시(#65), 실행 → 프로젝트 열림(5회 중앙값) | 1.88초 | 1.62초 |
| TextMate 문법을 전부가 아니라 22개만 동봉(#66): smart mode가 TextMate를 기다리는 시간 | 646ms | 384ms |
| 같은 변경, TextMate 백그라운드 클래스 로딩 | 1,348ms | 366ms |

- AOT 캐시 때문에 앱이 약 345MB 커진다. DMG는 648MB에서 692MB가 된다.

## 재현 방법

- `PHANTOM_PROPERTIES`(Ultimate는 `IDEA_PROPERTIES`)로 앱에 전용 설정·시스템·플러그인·로그 폴더를 준다.
- `PHANTOM_VM_OPTIONS`로 시작 리포트 경로와 신뢰·동의 플래그를 넘긴다.
- 명령줄에서 프로젝트를 연다.
- 앱은 정상 종료한다(`NSRunningApplication terminate`, `confirmExit` 끔). SIGTERM으로 끝내면 동적 CDS 아카이브가 저장되지 않아 다음 시작이 실제보다 느리게 나온다.
- AOT 빌드라면 캐시가 실제로 쓰였는지 확인한다. `jcmd <pid> VM.info`에 `CDS: on`이 보여야 한다. jar의 시각이나 크기가 맞지 않으면 JVM은 경고 없이 캐시를 버린다.
