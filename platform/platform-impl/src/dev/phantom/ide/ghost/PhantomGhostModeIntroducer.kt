// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.phantom.ide.PhantomIdeBundle
import dev.phantom.ide.icons.PhantomIcons

private const val INTRODUCED = "phantom.ghost.introduced"

/**
 * Brings essential highlighting in line with a Ghost Mode value set while the IDE was not running (a system property), and
 * introduces Ghost Mode once per installation. Both are a few property reads once the introduction was shown.
 */
internal class PhantomGhostModeIntroducer : ProjectActivity {
  override suspend fun execute(project: Project) {
    PhantomGhostEssential.sync(PhantomGhostMode.isEnabled())
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) return
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(INTRODUCED) || PhantomGhostMode.isEnabled()) return
    properties.setValue(INTRODUCED, true)

    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Phantom Plugins").createNotification(
      PhantomIdeBundle.message("phantom.ghost.intro.title"),
      PhantomIdeBundle.message("phantom.ghost.intro.content"),
      NotificationType.INFORMATION,
    )
    notification.icon = PhantomIcons.GhostOn
    notification.addAction(NotificationAction.createSimpleExpiring(PhantomIdeBundle.message("phantom.ghost.intro.enable")) {
      PhantomGhostMode.setEnabled(true)
    })
    notification.addAction(NotificationAction.createSimpleExpiring(PhantomIdeBundle.message("phantom.ghost.intro.dont.show")) {})
    notification.notify(project)
  }
}
