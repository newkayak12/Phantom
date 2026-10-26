// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.impl.EditorAutoClosingHandler
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.tabs.TabInfo
import dev.phantom.tabs.core.TabGroupChip
import dev.phantom.tabs.core.TabGroupPaneController
import dev.phantom.tabs.core.TabGroupsService
import javax.swing.SwingConstants

class TabGroupPaneControllerTest : FileEditorManagerTestCase() {
  private lateinit var a: VirtualFile
  private lateinit var b: VirtualFile
  private lateinit var c: VirtualFile

  override fun setUp() {
    super.setUp()
    a = myFixture.addFileToProject("a.txt", "a").virtualFile
    b = myFixture.addFileToProject("b.txt", "b").virtualFile
    c = myFixture.addFileToProject("c.txt", "c").virtualFile
    open(a)
    open(b)
    open(c)
  }

  private fun open(file: VirtualFile) {
    manager!!.openFile(file, true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private val window: EditorWindow
    get() = manager!!.currentWindow!!

  private val controller: TabGroupPaneController
    get() = TabGroupsService.getInstance(project).controllerFor(window)

  private fun info(file: VirtualFile): TabInfo = window.tabbedPane.tabs.tabs.first { it.`object` == file }

  private fun order(): List<VirtualFile> = window.tabbedPane.tabs.tabs.filter { !it.isHidden }.map { it.`object` as VirtualFile }

  fun testCreateGroupShowsChipOnFirstMember() {
    controller.createGroup(listOf(a, b))
    assertInstanceOf(info(a).labelLeadingComponent, TabGroupChip::class.java)
    assertNull(info(b).labelLeadingComponent)
    assertNull(info(c).labelLeadingComponent)
  }

  fun testCollapseHidesAllButAnchor() {
    val group = controller.createGroup(listOf(a, b))
    open(c)
    controller.toggleCollapsed(group)
    assertTrue(group.collapsed)
    assertTrue(info(a).isLabelCollapsed)
    assertFalse(info(a).isHidden)
    assertTrue(info(b).isHidden)
    assertTrue(manager!!.isFileOpen(b))
  }

  fun testSelectingHiddenMemberExpandsGroup() {
    val group = controller.createGroup(listOf(a, b))
    open(c)
    controller.toggleCollapsed(group)
    open(b)
    assertFalse(group.collapsed)
    assertFalse(info(b).isHidden)
    assertFalse(info(a).isLabelCollapsed)
    assertEquals(b, window.selectedFile)
  }

  fun testCollapseMovesSelectionOutOfGroup() {
    val group = controller.createGroup(listOf(a, b))
    open(b)
    controller.toggleCollapsed(group)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertTrue(group.collapsed)
    assertEquals(c, window.selectedFile)
  }

  fun testCollapseNoopWhenAllTabsInGroup() {
    val group = controller.createGroup(listOf(a, b, c))
    controller.toggleCollapsed(group)
    assertFalse(group.collapsed)
    assertFalse(info(b).isHidden)
  }

  fun testNewTabOpensAfterGroup() {
    controller.createGroup(listOf(a, b))
    open(a)
    val d = myFixture.addFileToProject("d.txt", "d").virtualFile
    open(d)
    assertEquals(listOf(a, b), order().take(2))
    assertNull(controller.model.groupOf(d.url))
  }

  fun testPinnedTabLeavesGroup() {
    controller.createGroup(listOf(a, b))
    window.setFilePinned(b, true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertNull(controller.model.groupOf(b.url))
    assertNotNull(controller.model.groupOf(a.url))
  }

  fun testClosedTabLeavesGroup() {
    controller.createGroup(listOf(a, b))
    manager!!.closeFile(b)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertNull(controller.model.groupOf(b.url))
    manager!!.closeFile(a)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertTrue(controller.model.groups.isEmpty())
  }

  fun testGroupedTabIsNotAutoClosable() {
    controller.createGroup(listOf(a, b))
    assertTrue(TabGroupsService.isGroupedAnywhere(b))
    assertTrue(EditorAutoClosingHandler.EP_NAME.extensionList.map { it.javaClass.name }.toString(),
               EditorAutoClosingHandler.EP_NAME.extensionList.any { it.javaClass.name == "dev.phantom.tabs.core.TabGroupAutoClosingHandler" })
    assertFalse(EditorAutoClosingHandler.isClosingAllowed(window.getComposite(b)!!))
  }

  fun testGroupedTabsSurviveTabLimit() {
    val settings = UISettings.getInstance()
    val oldLimit = settings.editorTabLimit
    try {
      settings.editorTabLimit = 2
      val group = controller.createGroup(listOf(a, b))
      controller.toggleCollapsed(group)
      open(myFixture.addFileToProject("d.txt", "d").virtualFile)
      open(myFixture.addFileToProject("e.txt", "e").virtualFile)
      assertTrue(manager!!.isFileOpen(a))
      assertTrue(manager!!.isFileOpen(b))
    }
    finally {
      settings.editorTabLimit = oldLimit
    }
  }

  fun testNewGroupActionDisabledForPinnedTab() {
    val action = ActionManager.getInstance().getAction("PhantomTabs.AddToNewGroup")
    fun enabledFor(file: VirtualFile): Boolean {
      val context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.VIRTUAL_FILE, file)
        .add(EditorWindow.DATA_KEY, window)
        .build()
      val event = TestActionEvent.createTestEvent(action, context)
      action.update(event)
      return event.presentation.isEnabledAndVisible
    }
    assertTrue(enabledFor(a))
    window.setFilePinned(a, true)
    assertFalse(enabledFor(a))
  }

  fun testColorProviderReturnsGroupColor() {
    controller.createGroup(listOf(a))
    assertNotNull(dev.phantom.tabs.core.TabGroupColorProvider().getEditorTabColor(project, a))
    assertNull(dev.phantom.tabs.core.TabGroupColorProvider().getEditorTabColor(project, b))
  }

  private fun split(window: EditorWindow): EditorWindow {
    val before = manager!!.windows.toSet()
    manager!!.createSplitter(SwingConstants.VERTICAL, window)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    return manager!!.windows.first { it !in before }
  }

  private fun newFile(name: String): VirtualFile = myFixture.addFileToProject(name, name).virtualFile

  fun testPinningInOneSplitWorksWithGroupsInTheOther() {
    val left = window
    val right = split(left)
    val d = newFile("d.txt")
    manager!!.openFile(d, right, FileEditorOpenOptions(requestFocus = true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val service = TabGroupsService.getInstance(project)
    service.controllerFor(left).createGroup(listOf(a, b))
    service.controllerFor(right).createGroup(listOf(d))

    left.setFilePinned(a, true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertNull(service.controllerFor(left).model.groupOf(a.url))
    assertNotNull(service.controllerFor(right).model.groupOf(d.url))
  }

  fun testCollapseAndExpandKeepGroupOrder() {
    val d = newFile("d.txt")
    open(d)
    val group = controller.createGroup(listOf(a, b, c))
    controller.toggleCollapsed(group)
    controller.toggleCollapsed(group)
    assertEquals(listOf(a, b, c, d), order())
  }

  fun testAddingSelectedTabToCollapsedGroupExpandsIt() {
    val group = controller.createGroup(listOf(a, b))
    controller.toggleCollapsed(group)
    assertEquals(c, window.selectedFile)
    controller.addTo(c, group)
    assertFalse(group.collapsed)
    assertFalse(info(c).isHidden)
    assertEquals(c, window.selectedFile)
  }

  fun testCollapseDoesNotSelectAnotherCollapsedGroup() {
    val d = newFile("d.txt")
    open(d)
    val other = controller.createGroup(listOf(c))
    controller.toggleCollapsed(other)
    open(b)
    val group = controller.createGroup(listOf(a, b))
    controller.toggleCollapsed(group)
    assertTrue(group.collapsed)
    assertEquals(d, window.selectedFile)
  }

  fun testPanesAreSavedInScreenOrder() {
    val left = window
    split(left)
    val middle = split(left)
    try {
      TabGroupsService.getInstance(project).controllerFor(middle).createGroup(listOf(middle.selectedFile!!))
      val panes = TabGroupsService.getInstance(project).state.panes
      assertEquals(3, panes.size)
      assertEquals(1, panes[1].groups.size)
      assertEquals(0, panes[2].groups.size)
    }
    finally {
      manager!!.unsplitAllWindow()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  fun testControllersOfClosedSplitsAreDropped() {
    val service = TabGroupsService.getInstance(project)
    service.controllerFor(split(window))
    service.controllerFor(window)
    manager!!.unsplitAllWindow()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertEquals(1, service.allControllers().size)
    assertEquals(1, service.controllerCount)
  }

  fun testRenamedFileStaysInGroup() {
    val group = controller.createGroup(listOf(a, b))
    WriteAction.runAndWait<Throwable> { b.rename(this, "b2.txt") }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    controller.refresh()
    assertSame(group, controller.model.groupOf(b.url))
  }

  fun testPreviewTabCannotBeGrouped() {
    UISettings.getInstance().openInPreviewTabIfPossible = true
    val d = newFile("d.txt")
    manager!!.openFile(d, window, FileEditorOpenOptions(requestFocus = true, usePreviewTab = true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertTrue(window.getComposite(d)!!.isPreview)
    val action = ActionManager.getInstance().getAction("PhantomTabs.AddToNewGroup")
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, d)
      .add(EditorWindow.DATA_KEY, window)
      .build()
    val event = TestActionEvent.createTestEvent(action, context)
    action.update(event)
    UISettings.getInstance().openInPreviewTabIfPossible = false
    assertFalse(event.presentation.isEnabledAndVisible)
  }
}
