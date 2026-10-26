// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces

import dev.phantom.workspaces.core.ProjectWindows.shouldMerge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePolicyTest {
  @Test
  fun `a second project on macOS joins the window that is already open`() {
    assertTrue(shouldMerge(isMac = true, isHeadless = false, enabled = true, openProjects = 2))
    assertTrue(shouldMerge(isMac = true, isHeadless = false, enabled = true, openProjects = 5))
  }

  @Test
  fun `the only open project has nothing to join`() {
    assertFalse(shouldMerge(isMac = true, isHeadless = false, enabled = true, openProjects = 1))
  }

  @Test
  fun `nothing happens outside macOS`() {
    assertFalse(shouldMerge(isMac = false, isHeadless = false, enabled = true, openProjects = 2))
  }

  @Test
  fun `nothing happens when the user switched it off`() {
    assertFalse(shouldMerge(isMac = true, isHeadless = false, enabled = false, openProjects = 2))
  }

  @Test
  fun `nothing happens without a screen`() {
    assertFalse(shouldMerge(isMac = true, isHeadless = true, enabled = true, openProjects = 2))
  }
}
