package com.intellij.smartUpdate

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.actions.AboutDialog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.restartOrNotify
import com.intellij.openapi.util.NlsContexts
// phantom-idea: removed IdeUpdateStep (the IDE update step through Toolbox App) and the imports only it used

fun restartIde(project: Project, @NlsContexts.DialogTitle progressTitle: String = IdeBundle.message("action.UpdateIde.progress.title"), restart: () -> Unit) {
    restartOrNotify(project, true, progressTitle) {
      beforeRestart()
      restart()
    }
}

fun beforeRestart() {
  RecentProjectsManagerBase.getInstanceEx().forceReopenProjects()
  PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, true)
}

private const val IDE_RESTARTED_KEY = "smart.update.ide.restarted"

internal class IdeRestartedActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    val service = project.service<SmartUpdate>()
    if (PropertiesComponent.getInstance().isTrueValue(IDE_RESTARTED_KEY)) {
      PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, false)
      notifyIdeUpdate(project)
      service.execute(project)
    }
    else service.scheduleUpdate()
  }

  private fun notifyIdeUpdate(project: Project) {
    val buildInfo = AboutDialog.getBuildInfo(ApplicationInfo.getInstance()).first
    @Suppress("DialogTitleCapitalization")
    Notification("IDE and Plugin Updates", IdeBundle.message("action.UpdateIde.task.success.title"),
                 IdeBundle.message("action.UpdateIde.installed", buildInfo), NotificationType.INFORMATION)
      .notify(project)
  }
}
