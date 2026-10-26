# Phantom Workspaces: 여러 프로젝트를 한 창에서

날짜: 2026-10-26. 플러그인: `plugins/phantom-workspaces` (`dev.phantom.workspaces`).

## 요구

> 프로젝트 based 잖아? IDE가 여러 개 뜨고 그걸 하나로 합쳐서 볼 수 있게 해줘. (플러그인으로)

IntelliJ는 프로젝트 하나에 OS 창 하나를 연다. 열린 프로젝트들을 한 창에서 보고, 빠르게 오가고 싶다.
사용자 환경은 macOS (Apple Silicon).

## 플랫폼이 이미 가진 것 (조사)

- 프로젝트와 창은 1:1이다. `ProjectFrameAllocator`가 프로젝트마다 `ProjectFrameHelper`/`IdeFrameImpl`을 만들고,
  `WindowManagerImpl`이 `Project -> ProjectFrameHelper`로 관리한다. 툴 윈도우(`ToolWindowManagerImpl`), 에디터
  (`FileEditorManagerImpl`), 상태바, 메뉴/툴바 데이터 컨텍스트가 모두 "이 창의 프로젝트" 하나를 전제로 붙는다.
- macOS 네이티브 창 탭이 이미 있다. JBR의 `Window.setTabbingMode`(`JdkEx.setTabbingMode`)로 모든 프로젝트 창이
  같은 tabbing identifier를 갖고, `Window | Merge All Project Windows`(`MergeAllWindowsAction`)가
  `[NSWindow mergeAllWindows:]`로 창들을 한 창의 탭으로 합친다. New UI에서는 `MacWinTabsHandlerV2` +
  `WindowTabsComponent`가 탭 바를 IDE 스타일로 직접 그린다 (드래그로 탭 순서 변경, 탭을 끌어내 새 창으로).
  탭 하나하나는 그대로 온전한 프로젝트 창이다.
- 전환 단축키도 이미 있다: `Next/Previous Project Window` (Cmd+` / Cmd+Shift+`), `Window` 메뉴의 프로젝트 목록.
- 빠진 것은 하나: 합치기가 수동이다. 새 프로젝트를 열 때마다 사용자가 메뉴를 눌러야 한다. 자동으로 합쳐지는 경우는
  macOS 설정 "문서를 열 때 탭 선호: 항상"(시스템 전체 설정)이거나 전체 화면 시작 시 뿐이다.

## 후보

1. **새 프로젝트 창을 자동으로 기존 창의 네이티브 탭으로 합친다** (채택)
   - 플랫폼 수정 0줄. 플랫폼 액션(`MergeAllWindowsAction`)을 그대로 호출하므로 전체 화면 처리, 탭 바 갱신
     (`updateTabBarsAfterMerge`), 닫기 동작(`CloseProjectWindowHelper.isTabbedWindow`)까지 검증된 경로를 탄다.
   - 각 탭이 진짜 프로젝트 창이라 툴 윈도우/에디터/상태바/Run 등이 깨질 것이 없다.
   - 한계: macOS + JBR + New UI 전용. Windows/Linux에서는 아무것도 하지 않는다.
2. **모든 창에 플러그인이 그리는 프로젝트 탭 스트립 + 클릭 시 해당 창 활성화**
   - 여러 OS 창은 그대로 남는다. 1번이 있으면 macOS에서는 플랫폼 탭 바와 중복. Windows/Linux용으로는 의미가
     있지만 "창 하나"는 아니다. 보류.
3. **창 하나가 프로젝트 루트를 바꿔 끼운다 (한 프레임에 여러 프로젝트)**
   - `IdeFrameImpl`/`ProjectFrameHelper`, `ToolWindowManagerImpl`(창의 툴 윈도우 패널), `FileEditorManagerImpl`
     (에디터 스플리터), `IdeRootPane`(상태바, 메인 툴바), 데이터 컨텍스트, `WindowManagerImpl`의 1:1 맵, 창 위치/
     상태 저장, 프로젝트 닫기 흐름이 모두 1:1 전제다. 바꾸려면 플랫폼 곳곳을 고쳐야 하고 upstream 병합이 매번
     깨진다. 플러그인으로는 불가능. 기각.
   - 하나의 프로젝트에 여러 루트를 붙이는 방식(“Attach project”, 멀티 모듈)은 이미 있지만, 프로젝트별 SDK/
     Run 설정/VCS 설정이 섞인다. 사용자가 말한 "여러 IDE를 합쳐 보기"와 다르다. 기각.

## 설계 (채택안)

- `MergeProjectWindowActivity` (`ProjectActivity`): 프로젝트가 열리면 실행.
  1. 백그라운드에서 바로 판단: macOS가 아니거나, headless이거나, 설정이 꺼졌거나, 열린 프로젝트가 1개뿐이면
     끝. (EDT를 건드리지 않는다.)
  2. 아니면 EDT에서 그 프로젝트의 창을 컨텍스트로 `MergeAllWindowsAction`을 update -> perform.
     액션의 update가 "탭 기능 사용 가능 && 창 수 != 탭 수"일 때만 enable되므로, 이미 다 합쳐져 있으면 아무 일도
     없다. 새 프로젝트 창이 receiver라 합친 뒤에도 그 탭이 선택된 상태로 남는다.
- 설정: `Settings | Advanced Settings | Phantom Workspaces | Open each project as a tab of the open window`
  (`phantom.workspaces.merge.project.windows`, 기본 켜짐).
- 탭을 끌어내 따로 띄운 창은 다시 자동으로 합치지 않는다 (합치기는 프로젝트가 "열릴 때"만).
- README 규칙: 필요할 때만 동작 (macOS, 두 번째 프로젝트부터), EDT 작업은 합칠 때 한 번, 네트워크 없음.
  `ProjectActivity`는 "프로젝트가 열렸다"를 알 수 있는 공개 훅이 이것뿐이라 필요한 시작 작업이다.

## 내가 대신 정한 것

- 접근 1번 채택, 3번 기각, 2번 보류 (위 이유).
- 기본값 켜짐. 플러그인을 깔았다는 것 자체가 "한 창"을 원한다는 뜻이라고 봤다.
- 플랫폼 hook 없음. 합치기 경로는 플랫폼 액션을 ID(`MergeAllWindowsAction`)로 부른다; 테스트가 이 ID가 사라지면
  깨진다.
- "프로젝트 열기: 이 창 / 새 창" 질문(`Settings | Appearance & Behavior | System Settings | Project`)은
  건드리지 않았다. "새 창"을 고르면 이제 새 탭이 된다.

## 사용자가 정할 것

- Windows/Linux도 필요하면 2번(플러그인 탭 스트립)을 따로 만들지.
- 앱을 다시 켤 때(여러 프로젝트 재오픈) 자동 합치기가 매끄러운지는 실제 화면에서 확인해야 한다 (아래).

## 검증하지 못한 것

- 실제 macOS 화면에서의 동작 (합쳐지는 타이밍, 깜빡임, 재시작 시 여러 프로젝트가 한꺼번에 열릴 때). 테스트는
  headless라 판단 로직, 등록, 플랫폼 액션 ID 존재, "다른 창이 없으면 아무것도 안 함"까지만 확인한다.
