// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs.core

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI

private val AnActionEvent.tabFile: VirtualFile?
  get() = getData(CommonDataKeys.VIRTUAL_FILE)

private val AnActionEvent.tabWindow: EditorWindow?
  get() = getData(EditorWindow.DATA_KEY)

/** The controller and file of the tab the menu is for, when the tab can go into a group (pinned tabs cannot). */
private fun AnActionEvent.groupableTab(): Pair<TabGroupPaneController, VirtualFile>? {
  val project = project ?: return null
  val window = tabWindow?.takeIf { !it.isDisposed } ?: return null
  val file = tabFile?.takeIf { window.isFileOpen(it) } ?: return null
  if (window.isFilePinned(file) || window.getComposite(file)?.isPreview == true) return null
  return TabGroupsService.getInstance(project).controllerFor(window) to file
}

private fun groupTitle(group: TabGroup): String = group.name.ifEmpty { PhantomTabsBundle.message("group.unnamed") }

private fun colorIcon(color: TabGroupColor) = JBUI.scale(ColorIcon(12, color.strong))

private fun askName(controller: TabGroupPaneController, group: TabGroup) {
  val name = Messages.showInputDialog(
    controller.window.manager.project,
    PhantomTabsBundle.message("rename.message"),
    PhantomTabsBundle.message("rename.title"),
    null,
    group.name,
    null,
  ) ?: return
  group.name = name.trim()
  controller.changed(group)
}

internal abstract class TabAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class AddToNewGroupAction : TabAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.groupableTab() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val (controller, file) = e.groupableTab() ?: return
    askName(controller, controller.createGroup(listOf(file)))
  }
}

internal class AddToGroupActionGroup : ActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = targets(e).isNotEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return EMPTY_ARRAY
    val (controller, file) = e.groupableTab() ?: return EMPTY_ARRAY
    return targets(e).map { group ->
      object : DumbAwareAction(groupTitle(group), null, colorIcon(group.color)) {
        override fun actionPerformed(e: AnActionEvent) = controller.addTo(file, group)
      }
    }.toTypedArray()
  }

  private fun targets(e: AnActionEvent): List<TabGroup> {
    val (controller, file) = e.groupableTab() ?: return emptyList()
    val own = controller.model.groupOf(file.url)
    return controller.model.groups.filter { it !== own }
  }
}

internal class RemoveFromGroupAction : TabAction() {
  override fun update(e: AnActionEvent) {
    val tab = e.groupableTab()
    e.presentation.isEnabledAndVisible = tab != null && tab.first.model.groupOf(tab.second.url) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val (controller, file) = e.groupableTab() ?: return
    controller.removeFromGroup(file)
  }
}

/** The chip menu's actions: they act on the group of the chip. */
internal abstract class ChipAction : TabAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(TAB_GROUP) != null && e.getData(TAB_GROUP_CONTROLLER) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    perform(e.getData(TAB_GROUP_CONTROLLER) ?: return, e.getData(TAB_GROUP) ?: return)
  }

  abstract fun perform(controller: TabGroupPaneController, group: TabGroup)
}

internal class RenameGroupAction : ChipAction() {
  override fun perform(controller: TabGroupPaneController, group: TabGroup) = askName(controller, group)
}

internal class UngroupAction : ChipAction() {
  override fun perform(controller: TabGroupPaneController, group: TabGroup) = controller.ungroup(group)
}

internal class CloseGroupAction : ChipAction() {
  override fun perform(controller: TabGroupPaneController, group: TabGroup) = controller.closeGroup(group)
}

internal class GroupColorActionGroup : ActionGroup() {
  private val children: Array<AnAction> = TabGroupColor.entries.map(::SetColorAction).toTypedArray()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(TAB_GROUP) != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = children
}

private class SetColorAction(private val color: TabGroupColor) : DumbAwareToggleAction(
  PhantomTabsBundle.message("color.${color.name.lowercase()}"), null, colorIcon(color),
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = e.getData(TAB_GROUP)?.color == color

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val group = e.getData(TAB_GROUP) ?: return
    val controller = e.getData(TAB_GROUP_CONTROLLER) ?: return
    group.color = color
    controller.changed(group)
  }
}
