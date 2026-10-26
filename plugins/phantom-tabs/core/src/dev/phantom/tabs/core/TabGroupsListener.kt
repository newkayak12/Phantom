// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/** Keeps every pane's groups in line when tabs open, get selected or get pinned. */
internal class TabGroupsListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) = refreshLater(source)

  override fun selectionChanged(event: FileEditorManagerEvent) = refreshLater(event.manager)

  override fun filePinStateChanged(source: FileEditorManager, file: VirtualFile) {
    ApplicationManager.getApplication().invokeLater({
      val service = TabGroupsService.getInstance(source.project)
      for (controller in service.allControllers()) {
        if (controller.window.isFilePinned(file)) controller.removeFromGroup(file) else controller.refresh()
      }
    }, source.project.disposed)
  }

  private fun refreshLater(source: FileEditorManager) {
    ApplicationManager.getApplication().invokeLater({
      TabGroupsService.getInstance(source.project).allControllers().forEach { it.refresh() }
    }, source.project.disposed)
  }
}
