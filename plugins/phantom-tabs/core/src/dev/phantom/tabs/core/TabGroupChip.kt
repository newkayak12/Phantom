// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs.core

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.GraphicsUtil
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager

val TAB_GROUP: DataKey<TabGroup> = DataKey.create("phantom.tabs.group")
val TAB_GROUP_CONTROLLER: DataKey<TabGroupPaneController> = DataKey.create("phantom.tabs.controller")

const val GROUP_CHIP_MENU: String = "PhantomTabs.GroupChipMenu"

/** The pill in front of a group's first tab: click to collapse or expand, right-click for the group menu. */
class TabGroupChip(private val controller: TabGroupPaneController, val group: TabGroup) : JComponent(), UiDataProvider {
  init {
    isOpaque = false
    font = JBFont.small().asBold()
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger) controller.toggleCollapsed(group)
      }
    })
    (ActionManager.getInstance().getAction(GROUP_CHIP_MENU) as? ActionGroup)?.let {
      PopupHandler.installPopupMenu(this, it, "PhantomTabGroupChip")
    }
    ToolTipManager.sharedInstance().registerComponent(this)
  }

  private val text: String
    get() = when {
      group.collapsed && group.name.isEmpty() -> group.members.size.toString()
      group.collapsed -> "${group.name}  ${group.members.size}"
      else -> group.name
    }

  override fun getToolTipText(): String = group.name.ifEmpty { PhantomTabsBundle.message("chip.tooltip.unnamed") }

  override fun getPreferredSize(): Dimension {
    val height = JBUI.scale(16)
    val text = text
    if (text.isEmpty()) return Dimension(JBUI.scale(12) + JBUI.scale(6), height)
    return Dimension(getFontMetrics(font).stringWidth(text) + JBUI.scale(12) + JBUI.scale(6), height)
  }

  override fun getMinimumSize(): Dimension = preferredSize

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAntialiasing(g2)
      val gap = JBUI.scale(6)
      val pillHeight = JBUI.scale(16)
      val y = (height - pillHeight) / 2
      val width = width - gap
      g2.color = group.color.strong
      val text = text
      if (text.isEmpty()) {
        val d = JBUI.scale(10)
        g2.fillOval((width - d) / 2, (height - d) / 2, d, d)
        return
      }
      g2.fillRoundRect(0, y, width, pillHeight, pillHeight, pillHeight)
      g2.color = onStrong
      g2.font = font
      val metrics = g2.fontMetrics
      g2.drawString(text, (width - metrics.stringWidth(text)) / 2, y + (pillHeight - metrics.height) / 2 + metrics.ascent)
    }
    finally {
      g2.dispose()
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[TAB_GROUP] = group
    sink[TAB_GROUP_CONTROLLER] = controller
  }
}
