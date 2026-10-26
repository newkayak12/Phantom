# Phantom Tab Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chrome-style editor tab groups (name, color, chip, collapse/expand, drag in/out, per-pane, persisted) as the bundled plugin `phantom.tabs`.

**Architecture:** A pure model (`TabGroupModel`) decides membership and tab order for one editor pane. A per-pane controller binds it to that pane's `JBTabs` (colors, chip, hidden flags, `sortTabs`). A project service owns the controllers and persists them. The platform gets one small hook: a leading component on `TabLabel`, plus a "collapsed label" flag that shows only that component.

**Tech Stack:** Kotlin, IntelliJ Platform (`JBTabs`/`TabInfo`, `EditorWindow`, `FileEditorManagerEx`), JUnit 4 (`BasePlatformTestCase`, `FileEditorManagerTestCase`), Bazel and `.iml` files kept in sync by hand.

**Spec:** `docs/superpowers/specs/2026-10-26-phantom-tab-groups-design.md`

## Global Constraints

- Plugin id `dev.phantom.tabs`, module `phantom.tabs` (`plugins/phantom-tabs`), package `dev.phantom.tabs`. Tests module `phantom.tabs.tests` (`plugins/phantom-tabs/tests`).
- Register it the same way as `phantom-sync` (commit 5f1819d799): `.idea/modules.xml`, root `BUILD.bazel` (main and test_lib lists), `build/bazel-generated-file-list.txt`, `intellij.idea.community.main.iml` (RUNTIME), `PHANTOM_BUNDLED_PLUGINS` in `build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt`, and the README plugin table.
- Platform edits are marked `// phantom-idea:` and stay inside `TabInfo.kt`, `TabLabel.kt` and `JBTabsImpl.kt`.
- If nobody uses groups, nothing in the tab bar changes: no chip, no reordering, no hidden tabs, no color.
- Collapsing only hides tabs (`TabInfo.isHidden`). It never closes a file.
- Chip and collapse work only in the single-row scrollable layout with the close button on the right. In other layouts, groups only color their tabs.
- The tab color comes from `EditorTabColorProvider`, which is per file. If the same file is grouped in two panes, it takes the color of the first group found. This is an accepted v1 limit.
- Palette, in this order: grey, blue, red, yellow, green, pink, purple, cyan (`TabGroupColor` enum).

## Review Focus

1. **Opening a file that sits in a collapsed group** (Go to Declaration, Recent Files, the debugger): the group expands and the file is selected. Covered by Task 4 `testSelectingHiddenMemberExpandsGroup`.
2. **Collapsing the group that holds the selected tab:** another tab outside the group gets selected and the group stays collapsed, so it does not immediately re-expand. If every tab in the pane is in the group, collapse does nothing. Covered by Task 4 `testCollapseMovesSelectionOutOfGroup` and `testCollapseNoopWhenAllTabsInGroup`.
3. **Going over the tab limit with a collapsed group:** none of its files are closed. Covered by Task 5 `testGroupedTabsSurviveTabLimit`.
4. **A new tab opened from inside a group** (the platform puts it next to the current tab): it lands after the group and does not join it. Covered by Task 2 `desiredOrder` tests and Task 4 `testNewTabOpensAfterGroup`.
5. **A grouped file closed, moved to the other split, or pinned:** it leaves its group, and a group with no tabs left disappears. Covered by Task 2 `remove` tests and Task 4 `testPinnedTabLeavesGroup` and `testClosedTabLeavesGroup`.

---

### Task 1: Platform hook: leading component on a tab label

**Files:**
- Modify: `platform/platform-api/src/com/intellij/ui/tabs/TabInfo.kt` (new property constant and two properties)
- Modify: `platform/platform-api/src/com/intellij/ui/tabs/impl/TabLabel.kt` (new `updateLeading()`)
- Modify: `platform/platform-api/src/com/intellij/ui/tabs/impl/JBTabsImpl.kt` (`propertyChange` dispatch, and calling `updateLeading()` when a label is created)
- Test: `platform/platform-tests/testSrc/com/intellij/ui/tabs/impl/TabLabelLeadingComponentTest.kt`

**Interfaces:**
- Produces:
  - `TabInfo.LABEL_LEADING: String = "labelLeading"`
  - `var TabInfo.labelLeadingComponent: JComponent?`, which fires `LABEL_LEADING`
  - `var TabInfo.isLabelCollapsed: Boolean`, which fires `LABEL_LEADING`
  - `fun TabLabel.updateLeading()`

- [ ] **Step 1: Write a failing test.** Build a `JBTabsImpl` with `TabInfo(JPanel()).setText("a")` and set `labelLeadingComponent = JLabel("G")`. Assert that the label's `BorderLayout.WEST` component is that `JLabel`. Then set `isLabelCollapsed = true` and assert the center placeholder and the action panel are invisible while the leading component stays visible. Set `labelLeadingComponent = null` and assert there is no WEST component.
- [ ] **Step 2: Run it.** `./tests.cmd --module intellij.platform.ide.impl.tests --test com.intellij.ui.tabs.impl.TabLabelLeadingComponentTest` fails because the property does not exist.
- [ ] **Step 3: Implement.**
  - `TabLabel.updateLeading()` removes the previous leading component and adds `info.labelLeadingComponent` at `BorderLayout.WEST`, but only when `isTabActionsOnTheRight`, because WEST is otherwise taken by the action panel. It sets `labelPlaceholder.isVisible` and `actionPanel?.isVisible` to `!info.isLabelCollapsed`.
  - `JBTabsImpl.propertyChange` handles `LABEL_LEADING` with `tabInfo.tabLabel?.updateLeading(); relayout(forced = true, layoutNow = false)`.
  - When a label is created, call `updateLeading()` after the tab actions are installed.
- [ ] **Step 4: Run the test** and confirm it passes.
- [ ] **Step 5: Commit** with the message `Tabs: a leading component on a tab label, and a collapsed label that shows only it`.

### Task 2: Plugin skeleton and `TabGroupModel`

**Files:**
- Create the module and its registration files: `plugins/phantom-tabs/{BUILD.bazel, phantom.tabs.iml, resources/META-INF/plugin.xml, resources/messages/PhantomTabsBundle.properties}`, `src/dev/phantom/tabs/PhantomTabsBundle.kt`, and the tests module `plugins/phantom-tabs/tests/{BUILD.bazel, phantom.tabs.tests.iml}`. Add all the global registration lines listed under Global Constraints.
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupModel.kt`
- Test: `plugins/phantom-tabs/tests/testSrc/dev/phantom/tabs/TabGroupModelTest.kt`

**Interfaces:**
- Produces, keyed by file URL (`String`):
  - `enum class TabGroupColor { GREY, BLUE, RED, YELLOW, GREEN, PINK, PURPLE, CYAN }`
  - `class TabGroup(val id: String, var name: String, var color: TabGroupColor, var collapsed: Boolean, val members: MutableList<String>)`
  - `class TabGroupModel` with:
    - `val groups: List<TabGroup>`
    - `fun groupOf(url: String): TabGroup?`
    - `fun create(urls: List<String>, name: String, color: TabGroupColor): TabGroup`. It removes the URLs from their old groups first.
    - `fun add(url: String, group: TabGroup)`
    - `fun remove(url: String)`. It drops groups that become empty.
    - `fun ungroup(group: TabGroup)`
    - `fun desiredOrder(current: List<String>): List<String>`
    - `fun onMoved(before: List<String>, after: List<String>)`
  - `fun nextColor(): TabGroupColor`, which returns the first palette color no group uses, or `GREY` if all are taken.

- [ ] **Step 1: Write failing tests.**
  - `desiredOrder(listOf("a","b","x","c"))` with group {a,c} returns `[a,c,b,x]`. A group is placed where its first member sits, and its members keep their relative order.
  - With group {a,b}, `current = [a,n,b]` (a new tab `n` opened next to `a`) returns `[a,b,n]`.
  - With no groups, the order is unchanged.
  - `onMoved([a,b,x,y],[a,x,b,y])` with group {a,b}: `b` now sits between `a` and `x`, which are not both members, and it already had a neighbor `a` in its group, so it stays. `onMoved([a,b,x,y],[a,b,y,x])` with group {a,b}: `y` has neighbor `b` in the group and none on the right, so it does not join. Moving `x` between `a` and `b` makes it join. Moving `b` to the end makes it leave, and since `a` is still there the group survives.
  - `remove` of the last member deletes the group.
  - `create` steals the URL from its old group.
  - `nextColor` returns BLUE after GREY has been used.
- [ ] **Step 2: Run them.** `./tests.cmd --module phantom.tabs.tests --test dev.phantom.tabs.TabGroupModelTest` fails because nothing compiles yet.
- [ ] **Step 3: Implement `TabGroupModel`.** `onMoved` first finds the one element whose removal makes `before` equal `after`, and does nothing if there is none. The moved tab joins group G if both neighbors are in G. It stays in G if it was in G and at least one neighbor is in G. Otherwise it leaves its group.
- [ ] **Step 4: Run the tests** and confirm they pass.
- [ ] **Step 5: Commit** with the message `phantom-tabs: plugin skeleton and the tab group model`.

### Task 3: Persisted state

**Files:**
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupsState.kt`
- Test: `plugins/phantom-tabs/tests/testSrc/dev/phantom/tabs/TabGroupsStateTest.kt`

**Interfaces:**
- Produces:
  - `class TabGroupsState : BaseState`, holding `var panes by list<PaneState>()`
  - `class PaneState : BaseState`, holding `var groups by list<GroupState>()`
  - `class GroupState : BaseState`, holding `name`, `color` (an enum name), `collapsed`, and `urls: MutableList<String>`
  - `fun TabGroupModel.toPaneState(): PaneState`
  - `fun TabGroupModel.restore(state: PaneState, openUrls: Set<String>)`, which keeps only URLs that are open in this pane and drops groups left empty

- [ ] **Step 1: Write a failing test.** A model is converted to state and restored into a fresh model, and its name, color, collapsed flag and member order come back. A URL that is not open is dropped. An unknown color name falls back to `GREY`.
- [ ] **Step 2: Run it** and confirm it fails.
- [ ] **Step 3: Implement it.**
- [ ] **Step 4: Run it** and confirm it passes.
- [ ] **Step 5: Commit** with the message `phantom-tabs: save and restore groups per pane`.

### Task 4: The pane controller, the service, and the chip

**Files:**
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupChip.kt`. This is the chip component: a rounded pill in the group's strong color showing the name, plus the member count while collapsed. Left click calls `controller.toggleCollapsed(group)`. Right click shows the `PhantomTabs.GroupChipMenu` popup with the group in the data context.
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupPaneController.kt`
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupsService.kt`
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupsListener.kt` (a `FileEditorManagerListener`)
- Modify: `plugin.xml`, to register the service, the project listener, and the `TabGroupColor` light and dark colors (in the `TabGroupColor` enum or a `TabGroupColors.kt`)
- Test: `plugins/phantom-tabs/tests/testSrc/dev/phantom/tabs/TabGroupPaneControllerTest.kt`, which extends `FileEditorManagerTestCase`

**Interfaces:**
- Consumes Task 1 (`labelLeadingComponent`, `isLabelCollapsed`), Task 2 (`TabGroupModel`), and Task 3 (state).
- Produces:
  - `class TabGroupPaneController(val window: EditorWindow, val model: TabGroupModel)` with:
    - `fun refresh()`, which applies colors, chips, hidden flags and `sortTabs(desiredOrder)`
    - `fun toggleCollapsed(group: TabGroup)`
    - `fun createGroup(files: List<VirtualFile>): TabGroup`, where the name is empty and the color is `model.nextColor()`
    - `fun addTo(file: VirtualFile, group: TabGroup)`
    - `fun removeFromGroup(file: VirtualFile)`
    - `fun ungroup(group: TabGroup)`
    - `fun closeGroup(group: TabGroup)`, which closes the member files
  - `@Service(Service.Level.PROJECT) class TabGroupsService(project: Project) : SerializablePersistentStateComponent`, stored in `workspace.xml` under `PhantomTabGroups`, with:
    - `fun controllerFor(window: EditorWindow): TabGroupPaneController`
    - `fun groupColorOf(file: VirtualFile): TabGroupColor?`
    - `companion fun getInstance(project: Project)`

- [ ] **Step 1: Write failing tests**, all in `FileEditorManagerTestCase`. Open `a.txt`, `b.txt` and `c.txt`, and use `manager!!.currentWindow!!`.
  - `testCreateGroupShowsChipOnFirstMember`: the first member's `TabInfo.labelLeadingComponent` is a `TabGroupChip`, and the other tabs have none.
  - `testCollapseHidesAllButAnchor`: after collapsing, `a` (the anchor) has `isLabelCollapsed = true` and is not hidden, `b` is hidden, and `fileEditorManager.isFileOpen(b)` is still true.
  - `testSelectingHiddenMemberExpandsGroup`: collapse, then `manager.openFile(b)` expands the group (`!collapsed`, nothing hidden) and `b` is selected.
  - `testCollapseMovesSelectionOutOfGroup`: with `b` selected and {a,b} grouped, collapsing selects `c` and the group stays collapsed.
  - `testCollapseNoopWhenAllTabsInGroup`: with {a,b,c} grouped, collapse leaves `collapsed = false`.
  - `testNewTabOpensAfterGroup`: with {a,b} grouped and `a` selected, opening `d` gives the tab order `a,b,…` with `d` not in the group.
  - `testPinnedTabLeavesGroup`: pinning `b` takes it out of the group.
  - `testClosedTabLeavesGroup`: closing `b` takes it out of the group, and closing `a` as well makes the group disappear.
- [ ] **Step 2: Run them.** `./tests.cmd --module phantom.tabs.tests --test dev.phantom.tabs.TabGroupPaneControllerTest` fails.
- [ ] **Step 3: Implement the controller.**
  - Get the pane's tabs with `window.tabbedPane.tabs as JBTabsImpl`.
  - The file of a tab is `info.`object` as VirtualFile`.
  - Install a `TabsListener` for the window's lifetime (`window` is disposed together with its tabs; use `Disposer` with the tabs' parent disposable or `EditorWindow`'s `coroutineScope`):
    - `beforeSelectionChanged`: when the new tab is in a collapsed group and the `suppressExpand` flag is not set, expand the group.
    - `tabsMoved`: call `model.onMoved(lastOrder, currentOrder)` and then `refresh()`.
    - `tabRemoved`: call `model.remove(url)` and then `refresh()`.
  - `toggleCollapsed` when collapsing: if the selected tab is a member, select the nearest visible non-member under `suppressExpand`. If there is none, do nothing.
  - `refresh()` changes nothing when there are no groups. When the layout is not the single-row scrollable one with the close button on the right, it applies only the color.
  - After changing membership, call `FileEditorManagerEx.getInstanceEx(project).updateFileColor(file)` for each affected file.
- [ ] **Step 4: Implement the service and the listener.**
  - The service maps `EditorWindow` to its controller, lazily and through a weak map.
  - `getState` saves the panes in `FileEditorManagerEx.windows` order.
  - When a controller is first created, it restores pane state number `i`, where `i` is the window's index.
  - The listener handles `fileOpened`, `selectionChanged` and `filePinStateChanged`. It makes sure each window has a controller and calls `refresh()` on the EDT. For `filePinStateChanged` with a pinned file, it first calls `removeFromGroup` in every window.
- [ ] **Step 5: Run the tests** and confirm they pass.
- [ ] **Step 6: Commit** with the message `phantom-tabs: per-pane controller, chip, collapse, persistence`.

### Task 5: Color provider, tab limit protection, and actions

**Files:**
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupColorProvider.kt` (`EditorTabColorProvider`, which returns the soft tab color of `groupColorOf(file)`)
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/TabGroupAutoClosingHandler.kt` (`EditorAutoClosingHandler`, where `isClosingAllowed` returns false when the file is in a group in that composite's window)
- Create: `plugins/phantom-tabs/src/dev/phantom/tabs/actions/TabGroupActions.kt`, with these actions:
  - `AddToNewGroupAction` ("Add Tab to New Group"). It creates the group and then opens the rename popup.
  - `AddToGroupActionGroup` ("Add Tab to Group"), a dynamic submenu of the pane's groups, shown only when groups exist.
  - `RemoveFromGroupAction` ("Remove from Group")
  - On the chip menu: `RenameGroupAction`, `GroupColorActionGroup` (palette, each item with a color icon), `UngroupAction` and `CloseGroupAction`.
  - All of them read `EditorWindow.DATA_KEY` and `CommonDataKeys.VIRTUAL_FILE`. They are disabled for pinned files.
- Modify: `plugin.xml`. Register the actions, add them to `EditorTabPopupMenu` (`IdeActions.GROUP_EDITOR_TAB_POPUP`) with `anchor="first"` and a separator, and register the `PhantomTabs.GroupChipMenu` group, `editorTabColorProvider` and `editorAutoClosingHandler`.
- Test: add to `TabGroupPaneControllerTest`.

- [ ] **Step 1: Write failing tests.**
  - `testGroupedTabsSurviveTabLimit`: set `UISettings.getInstance().editorTabLimit = 2`, group {a,b}, collapse it, open `c` and `d`, and check that `a` and `b` are still open.
  - `testColorProviderReturnsGroupColor`: `TabGroupColorProvider().getEditorTabColor(project, a)` is non-null for a grouped file and null for an ungrouped one.
  - `testActionsEnabledOnlyForUnpinned`: run `AddToNewGroupAction` update through `TestActionEvent` with a pinned file and check it is disabled.
- [ ] **Step 2: Run them** and confirm they fail.
- [ ] **Step 3: Implement it.**
- [ ] **Step 4: Run the whole module.** `./tests.cmd --module phantom.tabs.tests --test 'dev.phantom.tabs.*'` should all pass.
- [ ] **Step 5: Commit** with the message `phantom-tabs: group colors, tab limit protection, tab and chip menus`.

### Task 6: Build check

- [ ] **Step 1:** Run `./bazel.cmd build //plugins/phantom-tabs/... //platform/platform-api:ide` and confirm it succeeds.
- [ ] **Step 2:** Run `./tests.cmd --module phantom.tabs.tests --test 'dev.phantom.tabs.*'` and the Task 1 test once more, and confirm they are green.
- [ ] **Step 3:** Commit any leftover registration fixes.
