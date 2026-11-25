// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhantomLazyActivationBeanTest {
  private val bean = PhantomLazyActivationBean().apply {
    fileExtensions = "md, MMD"
    actions = "Markdown.Preview"
    toolWindows = "Database"
  }

  @Test
  fun fileExtensionsMatchCaseInsensitively() {
    assertTrue(bean.matches(LazyTrigger.File("mmd")))
    assertTrue(bean.matches(LazyTrigger.File("MD")))
    assertFalse(bean.matches(LazyTrigger.File("kt")))
  }

  @Test
  fun actionsAndToolWindowsMatchExactly() {
    assertTrue(bean.matches(LazyTrigger.Action("Markdown.Preview")))
    assertFalse(bean.matches(LazyTrigger.Action("markdown.preview")))
    assertTrue(bean.matches(LazyTrigger.ToolWindow("Database")))
    assertFalse(bean.matches(LazyTrigger.ToolWindow("Project")))
  }

  @Test
  fun emptyDeclarationMatchesNothing() {
    assertFalse(PhantomLazyActivationBean().matches(LazyTrigger.File("md")))
  }
}
