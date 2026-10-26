// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.phantom.workspaces.core.ProjectWindows
import javax.swing.JPanel

class WorkspacesInIdeTest : BasePlatformTestCase() {
  fun testMergingIsOnByDefault() {
    assertTrue(AdvancedSettings.getBoolean(ProjectWindows.MERGE_SETTING_ID))
  }

  // The plugin drives the platform's own "Window | Merge All Project Windows"; this breaks loudly if upstream renames it.
  fun testPlatformStillHasTheMergeAction() {
    assertNotNull(ActionManager.getInstance().getAction(ProjectWindows.MERGE_ACTION_ID))
  }

  fun testMergeRunsWhenAProjectOpens() {
    val activities = ExtensionPointName.create<Any>("com.intellij.postStartupActivity").extensionList
    assertTrue(activities.any { it.javaClass.name == "dev.phantom.workspaces.core.MergeProjectWindowActivity" })
  }

  fun testAWindowWithNoOtherProjectWindowIsLeftAlone() {
    assertFalse(ProjectWindows.mergeInto(project, JPanel()))
  }
}
