// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.tabs.TabInfo
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class TabLabelLeadingComponentTest : LightPlatformTestCase() {
  fun testLeadingComponentIsShownBeforeTheTitleAndCollapsesTheRest() {
    val tabs = JBTabsImpl(null, testRootDisposable)
    val info = TabInfo(JPanel()).setText("a")
    info.setTabLabelActions(DefaultActionGroup(), "test")
    tabs.addTab(info)
    val label = tabs.getTabLabel(info)!!
    val layout = label.layout as BorderLayout

    val chip = JLabel("G")
    info.labelLeadingComponent = chip
    assertSame(chip, layout.getLayoutComponent(BorderLayout.WEST))
    assertTrue(layout.getLayoutComponent(BorderLayout.CENTER).isVisible)
    assertTrue(layout.getLayoutComponent(BorderLayout.EAST).isVisible)

    info.isLabelCollapsed = true
    assertTrue(chip.isVisible)
    assertFalse(layout.getLayoutComponent(BorderLayout.CENTER).isVisible)
    assertFalse(layout.getLayoutComponent(BorderLayout.EAST).isVisible)

    info.isLabelCollapsed = false
    assertTrue(layout.getLayoutComponent(BorderLayout.CENTER).isVisible)

    info.labelLeadingComponent = null
    assertNull(layout.getLayoutComponent(BorderLayout.WEST))
    assertNull(chip.parent)
  }

  fun testRemovingTabWithHiddenSelectionTransferSelectsAVisibleTab() {
    val tabs = JBTabsImpl(null, testRootDisposable)
    val a = tabs.addTab(TabInfo(JPanel()).setText("a"))
    val hidden = tabs.addTab(TabInfo(JPanel()).setText("hidden"))
    val c = tabs.addTab(TabInfo(JPanel()).setText("c"))
    tabs.select(c, false)
    hidden.isHidden = true

    tabs.removeTab(c, forcedSelectionTransfer = hidden)

    assertSame(a, tabs.selectedInfo)
  }
}
