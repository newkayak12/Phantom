// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import dev.phantom.ide.PhantomIdeBundle
import dev.phantom.ide.icons.PhantomIcons
import java.util.function.Function
import javax.swing.JComponent

/** Banner above editors while Ghost Mode is on; once dismissed it stays hidden until the IDE restarts. */
internal class PhantomGhostEditorNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (dismissed || !PhantomGhostMode.isEnabled()) return null
    return Function { editor ->
      EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
        text = PhantomIdeBundle.message("phantom.ghost.banner.text")
        icon(PhantomIcons.GhostOn)
        createActionLabel(PhantomIdeBundle.message("phantom.ghost.banner.turn.off")) { PhantomGhostMode.setEnabled(false) }
        setCloseAction {
          dismissed = true
          EditorNotifications.updateAll()
        }
      }
    }
  }

  private companion object {
    @Volatile
    var dismissed = false
  }
}
