// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces

import dev.phantom.workspaces.core.StackedWindow
import dev.phantom.workspaces.core.WindowStack
import dev.phantom.workspaces.core.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle

class WindowStackTest {
  private val log = mutableListOf<String>()

  private inner class FakeWindow(val name: String, var current: WindowState) : StackedWindow {
    override var isActive: Boolean = false
    val applied = mutableListOf<WindowState>()
    override val state: WindowState get() = current
    override fun apply(state: WindowState) {
      log += "apply $name"
      applied += state
      current = state
    }

    override fun activate() {
      log += "activate $name"
    }
  }

  private val home = WindowState(Rectangle(100, 100, 1200, 800), maximized = false, fullScreen = false)
  private val elsewhere = WindowState(Rectangle(500, 50, 900, 700), maximized = false, fullScreen = false)
  private val stack = WindowStack()

  @Test
  fun `the first window sets where every project window goes`() {
    val a = FakeWindow("a", home)
    stack.add(a)
    assertEquals(home, stack.shared)
    assertTrue(a.applied.isEmpty())
  }

  @Test
  fun `a project that opens later opens exactly on top of the others`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", elsewhere)
    stack.add(a)
    stack.add(b)
    assertEquals(home, b.state)
  }

  @Test
  fun `a window that opens while the stack is off is left where it is`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", elsewhere)
    stack.add(a)
    stack.add(b, align = false)
    assertEquals(elsewhere, b.state)
  }

  @Test
  fun `moving or resizing the window in front moves the windows behind it`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    a.isActive = true
    a.current = elsewhere
    stack.changed(a)
    assertEquals(elsewhere, b.state)
    assertEquals(elsewhere, stack.shared)
  }

  @Test
  fun `changes of a window behind are ignored, so syncing cannot echo back and forth`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    a.isActive = true
    b.current = elsewhere
    stack.changed(b)
    assertEquals(home, a.state)
    assertEquals(home, stack.shared)
    assertTrue(a.applied.isEmpty())
  }

  @Test
  fun `a window that is already in place is not touched`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    a.isActive = true
    a.current = elsewhere
    stack.changed(a)
    stack.changed(a)
    stack.changed(a)
    assertEquals(1, b.applied.size)
    assertTrue(a.applied.isEmpty())
  }

  @Test
  fun `maximizing the window in front maximizes the others`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    a.isActive = true
    a.current = WindowState(Rectangle(0, 25, 1920, 1055), maximized = true, fullScreen = false)
    stack.changed(a)
    assertTrue(b.state.maximized)
    assertEquals(Rectangle(0, 25, 1920, 1055), b.state.bounds)
  }

  @Test
  fun `full screen is not pushed to the windows behind, it follows on switch`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    a.isActive = true
    a.current = WindowState(Rectangle(0, 0, 1920, 1080), maximized = false, fullScreen = true)
    stack.changed(a)
    assertTrue(b.applied.isEmpty())
    // the shared bounds stay the bounds to come back to when leaving full screen
    assertEquals(home.copy(fullScreen = true), stack.shared)

    stack.switchTo(b)
    assertTrue(b.state.fullScreen)
    assertEquals(listOf("apply b", "activate b"), log)
  }

  @Test
  fun `switching puts the target in place before bringing it to front`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    b.current = elsewhere // e.g. restored from its own saved bounds after the stack was off
    stack.switchTo(b)
    assertEquals(home, b.state)
    assertEquals(listOf("apply b", "activate b"), log)
  }

  @Test
  fun `switching to a window that is in place only brings it to front`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    stack.switchTo(b)
    assertEquals(listOf("activate b"), log)
  }

  @Test
  fun `a window brought to front some other way is put in place too`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    b.current = elsewhere
    b.isActive = true
    stack.activated(b)
    assertEquals(home, b.state)
  }

  @Test
  fun `a closed window is no longer moved`() {
    val a = FakeWindow("a", home)
    val b = FakeWindow("b", home)
    stack.add(a)
    stack.add(b)
    stack.remove(b)
    a.isActive = true
    a.current = elsewhere
    stack.changed(a)
    assertEquals(home, b.state)
  }
}
