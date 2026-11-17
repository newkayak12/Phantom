// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

// phantom-idea: removed the AllIcons import, used only by the removed Junie tests
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.terminal.frontend.action.TerminalAgentsSelectorPresentationUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.DefaultTerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalAgentSelectorPresentationTest : BasePlatformTestCase() {
  // phantom-idea: removed the two Junie presentation tests (Junie is no longer a bundled agent)
  @Test
  fun `plain agents keep plain selector text`() {
    val codex = bundledAgentByKey("codex")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, codex, TerminalAgentMode.RUN, showNewBadge = false)

    assertThat(presentation.text).isEqualTo("Codex")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  // phantom-idea: removed the Junie new-badge test (Junie is no longer a bundled agent)
  private fun bundledAgentByKey(agentKey: String) =
    DefaultTerminalAgentProvider().getTerminalAgents().first { it.agentKey.key == agentKey }
}
