# Phantom Workspaces: 여러 프로젝트를 한 창에서

날짜: 2026-10-26. 플러그인: `plugins/phantom-workspaces` (`dev.phantom.workspaces`).

## 요구

> 프로젝트 based 잖아? IDE가 여러 개 뜨고 그걸 하나로 합쳐서 볼 수 있게 해줘. (플러그인으로)

IntelliJ는 프로젝트 하나에 OS 창 하나를 연다. 열린 프로젝트들을 브라우저 탭처럼 한 창에서 보고, 빠르게 오가고 싶다.
사용자 환경은 macOS (Apple Silicon).

## 변경 이력: macOS 네이티브 창 탭을 버린 이유

첫 구현은 프로젝트가 열릴 때마다 플랫폼의 `Window | Merge All Project Windows`(`MergeAllWindowsAction`,
`MacWinTabsHandler`)를 자동으로 불러 모든 프로젝트 창을 macOS 네이티브 창 탭으로 합쳤다.
사용자가 거부했다: **macOS 네이티브 창 탭은 버그가 많다.** 그래서 네이티브 창 탭은 전혀 쓰지 않는다.
`MergeProjectWindowActivity`, 설정 `phantom.workspaces.merge.project.windows`, 관련 테스트를 지웠다.

## 플랫폼이 이미 가진 것 (조사)

- 프로젝트와 창은 1:1이다. `ProjectFrameAllocator`가 프로젝트마다 `ProjectFrameHelper`/`IdeFrameImpl`을 만들고,
  툴 윈도우, 에디터, 상태바, 데이터 컨텍스트가 모두 "이 창의 프로젝트" 하나를 전제로 붙는다. 한 프레임에 여러
  프로젝트를 넣는 것은 플랫폼 곳곳을 고쳐야 해서 플러그인으로 불가능 (기각).
- 프레임 위쪽 영역에 컴포넌트를 붙이는 공개 확장점: `com.intellij.ideRootPaneNorth`
  (`IdeRootPaneNorthExtension.createComponent(project, isDocked)`). 내비게이션 바, 구 UI 메인 툴바가 이걸 쓴다.
  `IdeProjectFrameHelper.installNorthComponents`가 프로젝트 프레임마다 메인 툴바 아래, 툴 윈도우/에디터 영역 위의
  north 패널에 넣는다. 확장이 동적으로 빠지면 컴포넌트도 빠진다.
- 프로젝트 창 전환: `ProjectUtil.focusProjectWindow(project)`. 다음/이전 프로젝트 창 액션
  `NextProjectWindow`/`PreviousProjectWindow`(`ProjectWindowActionGroup`)가 이미 있다. 단축키: macOS keymap
  Cmd+Alt+` / Cmd+Shift+Alt+`, 기본 keymap Ctrl+Alt+] / Ctrl+Alt+[. 열린 순서대로 순환한다.
- 프로젝트 닫기: 플랫폼 액션 `CloseProject` (마지막 프로젝트면 Welcome 화면까지 처리).
- 이벤트: `ProjectCloseListener`(앱), `ProjectNameListener`(프로젝트, 이름 변경), `AdvancedSettingsChangeListener`.
  `ProjectManagerListener.projectOpened`는 제거 예정(deprecated)이라 쓰지 않는다.

## 설계 (채택안): 플러그인이 그리는 프로젝트 탭 스트립 + 창 겹치기

창은 여전히 프로젝트마다 하나다. 대신 모든 프로젝트 창이 같은 위치/크기/최대화/전체 화면 상태로 정확히 겹쳐 있고,
각 창 위쪽에 같은 탭 스트립이 있어서 탭을 누르면 해당 창이 맨 앞으로 온다. 사용자에게는 창 하나에 탭이 바뀌는 것으로
보인다. 모든 OS, New UI 기준.

1. **탭 스트립** (`ProjectTabStrip`, `ideRootPaneNorth` 확장, `order="last"`): 메인 툴바 아래, 에디터/툴 윈도우
   영역 바로 위. 프로젝트 하나당 탭 하나, 열린 순서. 이 창의 프로젝트는 에디터 탭처럼 배경 + 밑줄로 강조.
   클릭 = 전환, 가운데 클릭 또는 x 버튼 = 닫기, 툴팁 = 경로 (홈은 `~`). 이름이 같은 프로젝트는 경로로 구분.
   열린 프로젝트가 하나뿐이면 숨는다 (프로젝트 하나일 때 IDE 모양은 그대로). 분리된 에디터 창(docked)에는 없음.
2. **목록 갱신** (`ProjectTabsService`, 앱 서비스): 스트립이 프레임에 붙을 때(= 프로젝트 창이 생김) 등록,
   떨어질 때 해제. 프로젝트 닫힘/이름 변경/설정 변경은 선언형 리스너가 서비스가 이미 있을 때만 알린다
   (`serviceIfCreated`). 탭 목록이 바뀌지 않으면 컴포넌트를 다시 만들지 않는다.
3. **창 겹치기** (`WindowStack`, 순수 로직 + `FrameWindow` AWT 어댑터):
   - 처음 붙은 창의 상태가 공유 상태가 된다. 나중에 열리는 프로젝트 창은 붙는 즉시 공유 위치/크기/최대화로 옮긴다.
   - 앞 창(마지막으로 앞에 온 창, IDE가 백그라운드여도)이 이끈다. 이동/크기 변경/최대화는 이벤트 시점에 "앞 창의
     변경"으로 기록하고 300ms 디바운스 후 공유 상태에 반영해 뒤 창들을 옮긴다. 다른 창이 앞에 오거나 전환하기 전에
     밀린 변경을 먼저 반영한다 (리사이즈 직후 전환해도 잃지 않음).
   - 되먹임 방지: 뒤 창의 이동 이벤트는 무시. 이미 같은 상태인 창은 건드리지 않는다.
   - 최대화: 공유 상태의 bounds는 항상 "보통 크기"다. 최대화는 플래그로만 퍼지고 뒤 창의 보통 크기를 화면 크기로
     덮지 않는다.
   - 전체 화면: 탭 스트립에서 전환할 때만 대상 창에 적용한다. 뒤 창으로 퍼뜨리지 않고(macOS에서 창마다 Space가
     생김), 새로 열리는 창도 전체 화면으로 만들지 않으며, 전체 화면인 창을 전환 없이 꺼내지 않는다. 전체 화면
     진입/해제는 비동기라 끝난 뒤에 bounds를 적용하고, 전환 중인 창의 bounds는 공유 상태로 가져가지 않는다.
   - 전환: 대상 창을 공유 상태로 맞춘 뒤 `ProjectUtil.focusProjectWindow`. 다른 경로(다음 프로젝트 창 액션, Window
     메뉴, Dock)로 앞에 온 창은 위치/크기/최대화만 맞춘다 (전체 화면은 그대로).
   - 설정을 다시 켜면 그때의 앞 창이 공유 상태가 된다.
4. **키보드**: 플랫폼의 `Next/Previous Project Window`를 그대로 쓴다 (열린 순서 순환 = 탭 순서). 새 액션/단축키는
   만들지 않았다 (충돌 없음).
5. **설정**: `Settings | Advanced Settings | Phantom Workspaces | Show open projects as tabs of one window`
   (`phantom.workspaces.project.tabs`, 기본 켜짐). 끄면 스트립이 숨고 창 동기화가 멈춘다 (재시작 불필요).
6. **README 규칙**: 네트워크 없음, 시작 작업(`postStartupActivity`) 없음. 쉬는 동안 하는 일 없음: 타이머는 사용자가
   창을 옮길 때만 한 번 돈다. EDT 작업은 탭 목록이 바뀔 때와 창 이벤트 때뿐. 플랫폼 수정 0줄.

## 내가 대신 정한 것

- 스트립 위치: `ideRootPaneNorth`의 마지막 (메인 툴바/내비게이션 바 아래, 에디터 위). 공개 EP 중 가장 덜 침습적.
- 프로젝트가 하나면 스트립을 숨김.
- 다음/이전 탭은 플랫폼 액션 재사용. 탭 순서 = 열린 순서 (드래그 재정렬 없음).
- 전체 화면은 탭 스트립 전환 시점에만 맞춤 (위 이유). 다음 프로젝트 창 액션/Window 메뉴로 온 창은 전체 화면을 바꾸지 않는다.
- 닫기는 플랫폼 `CloseProject` 액션을 ID로 실행; 테스트가 ID가 사라지면 깨진다.
- 최소화(Cmd+M)는 동기화하지 않는다: 앞 창을 최소화하면 뒤 창이 보인다.

## 검증하지 못한 것 (실제 화면 필요)

테스트는 headless로 돈다: 탭 모델, 겹치기 로직(가짜 창), 스트립 등록/갱신(실제 프로젝트 2개 열고 닫기, 이름 변경,
설정 끄기), EP 등록, 플랫폼 액션 ID. 실제 AWT 프레임 테스트(`FrameWindowTest`)는 화면이 있을 때만 돈다.
- 실제 화면에서 탭 스트립 모양 (높이, 색, 많은 프로젝트일 때 넘침: 지금은 잘린다).
- 새 프로젝트 창이 먼저 저장된 위치에 보였다가 겹치는 위치로 튀는지 (프레임이 프로젝트보다 먼저 보이는 경우).
- 전환 시 깜빡임, macOS 전체 화면 전환 애니메이션, 여러 모니터, 재시작 시 여러 프로젝트가 한꺼번에 열릴 때.
