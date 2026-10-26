// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces.core

import java.awt.Rectangle

/** Where a window is. While [fullScreen], [bounds] are the bounds to return to, not the screen. */
data class WindowState(val bounds: Rectangle, val maximized: Boolean, val fullScreen: Boolean)

interface StackedWindow {
  val state: WindowState
  val isActive: Boolean
  fun apply(state: WindowState)
  fun activate()
}

/**
 * Keeps every project window exactly on top of each other, so the windows look like one window whose content changes.
 *
 * Only the window in front leads: its moves and resizes become the [shared] state and are pushed to the windows behind.
 * Changes of windows behind are ignored and windows already in place are not touched, so pushing cannot echo back.
 * Full screen is not pushed to the windows behind (on macOS every full-screen window gets its own Space); a window
 * takes it when it comes to front.
 */
class WindowStack {
  private val windows = ArrayList<StackedWindow>()

  var shared: WindowState? = null
    private set

  /** A new window; it is moved on top of the others unless [align] is false (the stack is switched off). */
  fun add(window: StackedWindow, align: Boolean = true) {
    if (windows.any { it === window }) return
    windows += window
    val target = shared
    if (target == null) shared = window.state
    else if (align) alignTo(window, target)
  }

  fun remove(window: StackedWindow) {
    windows.removeIf { it === window }
  }

  /** [window] came to front, by the strip or any other way (Window menu, next project window, Dock). */
  fun activated(window: StackedWindow) {
    if (windows.none { it === window }) return
    val target = shared
    if (target == null) shared = window.state else alignTo(window, target)
  }

  /** [window] moved, resized, maximized, or entered or left full screen. */
  fun changed(window: StackedWindow) {
    if (!window.isActive || windows.none { it === window }) return
    val now = window.state
    val next = if (now.fullScreen) (shared ?: now).copy(fullScreen = true) else now
    shared = next
    if (next.fullScreen) return
    for (other in windows) {
      if (other !== window && !other.state.fullScreen) alignTo(other, next)
    }
  }

  /** Puts [window] in place, then brings it to front. */
  fun switchTo(window: StackedWindow) {
    shared?.let { alignTo(window, it) }
    window.activate()
  }

  private fun alignTo(window: StackedWindow, target: WindowState) {
    if (!inPlace(window.state, target)) window.apply(target)
  }

  private fun inPlace(state: WindowState, target: WindowState): Boolean =
    state.fullScreen == target.fullScreen &&
    (state.fullScreen || (state.maximized == target.maximized && state.bounds == target.bounds))
}
