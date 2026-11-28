// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import dev.phantom.ide.icons.PhantomIcons

/** Turns Ghost Mode on and off from the View menu, the main toolbar and Find Action. */
internal class PhantomGhostModeAction : ToggleAction(), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean = PhantomGhostMode.isEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PhantomGhostMode.setEnabled(state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = if (PhantomGhostMode.isEnabled()) PhantomIcons.GhostOn else PhantomIcons.GhostOff
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
