// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces.core

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Component

/**
 * One window for all open projects. On macOS every project window can be a native tab of one window; the platform's
 * "Window | Merge All Project Windows" does the merging, and this pulls a newly opened project into it.
 */
object ProjectWindows {
  const val MERGE_SETTING_ID: String = "phantom.workspaces.merge.project.windows"
  const val MERGE_ACTION_ID: String = "MergeAllWindowsAction"

  /** Whether a project that has just opened should join the window of the projects already open. */
  fun shouldMerge(isMac: Boolean, isHeadless: Boolean, enabled: Boolean, openProjects: Int): Boolean =
    isMac && !isHeadless && enabled && openProjects > 1

  /**
   * Merges all project windows into the window of [frame], which stays the selected tab.
   * Returns false when there is nothing to merge (no window tabs here, or every window is already a tab of one).
   */
  @RequiresEdt
  fun mergeInto(project: Project, frame: Component): Boolean {
    val action = ActionManager.getInstance().getAction(MERGE_ACTION_ID) ?: return false
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, frame)
      .build()
    val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    ActionUtil.updateAction(action, event)
    if (!event.presentation.isEnabled) return false
    return ActionUtil.performAction(action, event).isPerformed
  }
}
