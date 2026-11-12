# 인덱싱 변경과 측정 기록 (2026-10-28 ~ 10-29)

- 종류: 측정 기록. 내용은 고치지 않는다.
- 출처: 개편 전 `README.md`(커밋 be60914099)의 "Indexing" 절을 그대로 옮겼다. README 개편(2026-11-12) 때 대문에서 요약으로 줄이면서 원문을 여기에 보존한다.
- 관련 커밋: 0a5ca64795 (2026-10-28, trigram 인덱스에서 라이브러리 텍스트 파일 제외), 6613a4ed82 (2026-10-29, 인덱싱 지문에 인덱싱에 영향을 주는 플러그인만 반영)
- 패치 위치는 [FORK-DELTA.md](FORK-DELTA.md) 3.5절에 있다.

아래는 원문(영어)이다.

## Indexing

Phantom keeps every index the platform has, but makes it cheaper to keep them current.

**Find in Files index (trigrams) leaves libraries out.** The platform already left `.java` sources in libraries
out of the trigram index. Phantom leaves out every text file in a library (Kotlin sources, XML, properties and so on).
Find in Files still searches libraries: files that are not in the index are read directly, as `.java` library sources
always were. Project files are unchanged. On a Spring Boot project the trigram index went from 369 MB to 84 MB, and
all indexes together from 1.2 GB to 916 MB.

**A plugin update no longer rescans every file.** When a project opens, the platform skips checking each file
if nothing that affects indexing changed since the last session. It decides this from a fingerprint that
covers the IDE build, the index versions and every installed plugin's version. Updating any plugin changed the
fingerprint, so the next open checked every file again (345k files on a Spring Boot project: about 2.3 s of
background work and 20 s of CPU). Phantom counts only plugins that register something that changes what or how
files are indexed: indexes, stubs, file types, parsers, word scanners, syntax highlighters, decompilers, roots,
excludes and file property pushers. Updating, adding or removing any other plugin keeps the fingerprint. The list
is broad on purpose: counting a plugin by mistake only costs one full check after its update, while leaving one out
by mistake would keep stale index data.

Reopening a project after a plugin update, 10 runs each, same machine, isolated caches:

| | before | after |
|---|---|---|
| Files checked on open | 345,122 | 0 |
| Checking time | 2,319 ms ± 98 | 36 ms ± 14 |
| CPU time of the whole session | 36.4 s ± 1.4 | 16.5 s ± 0.8 |
| Wall time (the check runs in the background) | 9.8 s | 10.1 s |

After an update of a plugin that does affect indexing (for example `db`, which adds the SQL file type), every file
is still checked, as before.
