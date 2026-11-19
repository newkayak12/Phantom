// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager

internal class UpdateManagerUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("platform.installer", 5)

  // phantom-idea: no "Toolbox App" value (Toolbox App integration dropped)
  private val MANAGERS = listOf("Snap", "Flatpak", "Homebrew", "Other", "IDE")

  private val UPDATE_MANAGER = GROUP.registerEvent("Update_Manager", EventFields.String("value", MANAGERS))

  override fun getMetrics(): Set<MetricEvent> = setOf(UPDATE_MANAGER.metric(
    when (val manager = ExternalUpdateManager.ACTUAL) {
      // phantom-idea: TOOLBOX, kept only for the Android plugin and never detected, is reported as Other
      ExternalUpdateManager.SNAP, ExternalUpdateManager.FLATPAK, ExternalUpdateManager.BREW -> manager.toolName
      ExternalUpdateManager.UNKNOWN, ExternalUpdateManager.TOOLBOX -> "Other"
      null -> "IDE"
    }
  ))

  override fun getGroup(): EventLogGroup = GROUP
}
