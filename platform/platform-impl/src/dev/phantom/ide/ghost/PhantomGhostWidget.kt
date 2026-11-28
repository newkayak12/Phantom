// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.ui.JBColor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.phantom.ide.PhantomIdeBundle
import dev.phantom.ide.icons.PhantomIcons
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

private const val WIDGET_ID = "PhantomGhostMode"

/** Status bar entry showing whether Ghost Mode is on; one click flips it. */
internal class PhantomGhostWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = PhantomIdeBundle.message("phantom.ghost.widget.name")

  override fun createWidget(project: Project): StatusBarWidget = PhantomGhostWidget()
}

private class PhantomGhostWidget : CustomStatusBarWidget {
  private val label = JBLabel().apply {
    border = JBUI.Borders.empty(0, 6)
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) PhantomGhostMode.setEnabled(!PhantomGhostMode.isEnabled())
      }
    })
  }

  init {
    update()
    RegistryManager.getInstance().get(PhantomGhostMode.REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        UIUtil.invokeLaterIfNeeded { update() }
      }
    }, this)
  }

  private fun update() {
    val on = PhantomGhostMode.isEnabled()
    label.icon = if (on) PhantomIcons.GhostOn else PhantomIcons.GhostOff
    label.text = PhantomIdeBundle.message(if (on) "phantom.ghost.widget.on" else "phantom.ghost.widget.off")
    label.foreground = if (on) GHOST_ACCENT else UIUtil.getContextHelpForeground()
    label.toolTipText = PhantomIdeBundle.message(if (on) "phantom.ghost.widget.tooltip.on" else "phantom.ghost.widget.tooltip.off")
  }

  override fun ID(): String = WIDGET_ID

  override fun getComponent(): JComponent = label

  override fun dispose() {}
}

// Phantom theme accent (PhantomLight accent-brand / PhantomDark accent-brand-border), as in the ghostOn icons.
private val GHOST_ACCENT = JBColor(0xB5711F, 0xE0A250)
