// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.workspaces

import dev.phantom.workspaces.core.FrameWindow
import dev.phantom.workspaces.core.WindowStack
import dev.phantom.workspaces.core.WindowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** The stack on real AWT frames. Needs a screen; skipped when the tests run headless. */
class FrameWindowTest {
  private val frames = mutableListOf<JFrame>()

  @Before
  fun needsScreen() {
    assumeFalse("no screen", GraphicsEnvironment.isHeadless())
  }

  @After
  fun disposeFrames() {
    SwingUtilities.invokeAndWait { frames.forEach(JFrame::dispose) }
  }

  private fun frame(bounds: Rectangle): JFrame = JFrame().also {
    it.bounds = bounds
    frames += it
  }

  @Test
  fun `a second frame takes the bounds of the first`() {
    SwingUtilities.invokeAndWait {
      val a = FrameWindow(frame(Rectangle(100, 100, 800, 600)), null)
      val b = FrameWindow(frame(Rectangle(300, 200, 500, 400)), null)
      val stack = WindowStack()
      stack.add(a)
      stack.add(b)
      assertEquals(Rectangle(100, 100, 800, 600), frames[1].bounds)
    }
  }

  @Test
  fun `state reads bounds and maximized from the frame`() {
    SwingUtilities.invokeAndWait {
      val window = FrameWindow(frame(Rectangle(120, 130, 640, 480)), null)
      assertEquals(WindowState(Rectangle(120, 130, 640, 480), maximized = false, fullScreen = false), window.state)
      assertFalse(window.isActive)
    }
  }
}
