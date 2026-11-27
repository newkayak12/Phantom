# 개인정보

[English](PRIVACY.md)

최종 수정일: 2026-11-28

Phantom과 Phantom 플러그인은 사용자나 IDE 사용에 관한 데이터를 수집하지 않는다. Phantom 서버는 없다. Phantom
개발자에게 보내는 것은 없고, 사용 통계, 오류 보고, 원격 측정 서비스도 없다. 사용 통계는 켤 수 없다. Phantom에는 사용
통계 동의 항목이 없어서 동의할 것이 없고, 업로더는 보내지 않는다.

## IDE가 접속하는 곳

Phantom은 아래 기능에서만 다른 서비스에 접속한다. 각 요청은 그 서비스의 개인정보 처리방침을 따른다.

| 언제 | 어디로 | 보내는 것 |
| --- | --- | --- |
| 프로젝트를 열거나 라이브러리가 바뀔 때 | GitHub(`raw.githubusercontent.com`)의 Phantom 플러그인 목록 `marketplace.json` | 목록 다운로드뿐이고 프로젝트 정보는 없다. `-Dphantom.plugins.official=false`로 끈다 |
| Phantom 플러그인을 설치·업데이트하거나 Phantom 릴리스를 받을 때 | GitHub(릴리스 다운로드) | 다운로드뿐 |
| JSON·YAML 파일을 편집할 때(기본으로 켜짐) | `schemastore.org` JSON 스키마 카탈로그와 거기 실린 스키마 | 다운로드뿐. Settings &#124; Languages & Frameworks &#124; Schemas and DTDs &#124; Remote JSON Schemas에서 끈다 |
| JDK를 내려받을 때 | `api.foojay.io`와 JDK 배포처 다운로드 사이트 | 고른 JDK, OS, 아키텍처 |
| Spring Boot 프로젝트를 만들 때(Spring 플러그인) | `start.spring.io` | 고른 프로젝트 옵션 |
| 데이터베이스 드라이버를 내려받을 때 | Maven Central | 드라이버 아티팩트 |
| `*.versions.toml` 파일에서 "Check for Newer Versions on Maven Central"을 실행할 때만(Version Catalog 플러그인) | Maven Central(`repo1.maven.org`의 `<그룹 경로>/<아티팩트>/maven-metadata.xml`) | `[libraries]` 항목의 그룹·아티팩트 ID(`[plugins]`는 건너뛴다). 프로젝트 파일은 보내지 않는다 |
| 사용자가 고를 때만: Marketplace 탭 탐색·설치, 문법 검사용 언어 데이터 다운로드, Choose Boot Java Runtime 열기, Windows에서 Git이 없을 때 Download and Install | JetBrains(`plugins.jetbrains.com`, `download.jetbrains.com`) 또는 플러그인의 다운로드 위치 | 그 다운로드 요청. Phantom은 기기 ID를 붙이지 않는다 |

IDE를 `-Dphantom.jetbrains.services=true`로 실행하면 Phantom이 기본으로 꺼 둔 JetBrains 서비스(피드백 양식, 프레임워크
라이브러리 버전 목록 등)가 켜진다. 그 요청은 JetBrains로 가고
[JetBrains 개인정보 처리방침](https://www.jetbrains.com/legal/docs/privacy/privacy/)을 따른다.

## AI Integration 플러그인

AI Integration 플러그인은 사용자가 직접 설치한 AI 명령줄 도구(Claude Code 또는 Codex)를 실행한다. 사용자가 넘긴
프롬프트, 코드, 데이터베이스 조회 결과는 사용자 자신의 계정으로 그 도구의 제공자(Anthropic 또는 OpenAI)에게 가고, 그
제공자의 약관과 개인정보 처리방침을 따른다. Phantom은 이를 보거나 저장하지 않는다.

## 자격 증명

비밀번호와 토큰(GitHub 토큰, 데이터베이스 비밀번호 등)은 IDE의 비밀번호 저장소를 거쳐 운영체제 키체인에 저장된다. HTTP
Client 환경 파일은 프로젝트 안의 보통 파일이라, 거기 적은 값은 평문으로 저장된다.

## 문의

개인정보에 관한 문의는 https://github.com/newkayak12/Phantom/issues 에 이슈로 남긴다.
