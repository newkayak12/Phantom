// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A project that opens while another is open becomes a tab of the window that is already there (macOS). */
internal class MergeProjectWindowActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val merge = ProjectWindows.shouldMerge(
      isMac = SystemInfoRt.isMac,
      isHeadless = ApplicationManager.getApplication().isHeadlessEnvironment,
      enabled = AdvancedSettings.getBoolean(ProjectWindows.MERGE_SETTING_ID),
      openProjects = ProjectManager.getInstance().openProjects.size,
    )
    if (!merge) return

    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext
      val frame = WindowManager.getInstance().getFrame(project) ?: return@withContext
      ProjectWindows.mergeInto(project, frame)
    }
  }
}
