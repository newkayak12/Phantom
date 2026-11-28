// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.lang.ghost

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.ui.EditorNotifications
import dev.phantom.ide.ghost.PhantomGhostEssential
import dev.phantom.ide.ghost.PhantomGhostMode

/**
 * Applies a Ghost Mode change: mirrors it onto essential highlighting, makes the inlay hint and code vision passes run again
 * so hints left from before disappear (or come back), and refreshes the editor banner. Instantiated on the first registry change.
 */
internal class PhantomGhostModeListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key != PhantomGhostMode.REGISTRY_KEY) return
    PhantomGhostEssential.sync(value.asBoolean())
    ApplicationManager.getApplication().invokeLater {
      InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
      DeclarativeInlayHintsPassFactory.resetModificationStamp()
      ModificationStampUtil.clearModificationStamp()
      for (project in ProjectManager.getInstance().openProjects) {
        if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("Ghost Mode changed")
      }
      EditorNotifications.updateAll()
    }
  }
}
