// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces.core

import com.intellij.openapi.wm.ex.IdeFrameEx
import java.awt.Frame
import javax.swing.JFrame

/** A project window for [WindowStack]. [ideFrame] gives full screen; without it full screen is left alone. */
class FrameWindow(
  val frame: JFrame,
  private val ideFrame: IdeFrameEx?,
  private val bringToFront: () -> Unit = { frame.toFront() },
) : StackedWindow {
  override val state: WindowState
    get() = WindowState(
      bounds = frame.bounds,
      maximized = frame.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH,
      fullScreen = ideFrame?.isInFullScreen == true,
    )

  override val isActive: Boolean get() = frame.isActive

  override fun apply(state: WindowState) {
    val current = this.state
    if (state.fullScreen != current.fullScreen) {
      val ide = ideFrame ?: return
      ide.toggleFullScreen(state.fullScreen)
      if (state.fullScreen) return
    }
    if (current.maximized) frame.extendedState = frame.extendedState and Frame.MAXIMIZED_BOTH.inv()
    frame.bounds = state.bounds
    if (state.maximized) frame.extendedState = frame.extendedState or Frame.MAXIMIZED_BOTH
  }

  override fun activate() {
    bringToFront()
  }
}
