// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import dev.phantom.ide.ghost.PhantomGhostEssential.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhantomGhostEssentialTest {
  private fun mirror(ghost: Boolean, essential: Boolean, turnedOnByGhost: Boolean) =
    PhantomGhostEssential.mirror(ghost, essential, turnedOnByGhost)

  @Test
  fun `turning ghost on enables essential mode and remembers it`() {
    assertEquals(State(essential = true, turnedOnByGhost = true), mirror(ghost = true, essential = false, turnedOnByGhost = false))
  }

  @Test
  fun `turning ghost on keeps essential mode the user enabled`() {
    assertEquals(State(essential = true, turnedOnByGhost = false), mirror(ghost = true, essential = true, turnedOnByGhost = false))
  }

  @Test
  fun `turning ghost off restores essential mode it enabled`() {
    assertEquals(State(essential = false, turnedOnByGhost = false), mirror(ghost = false, essential = true, turnedOnByGhost = true))
  }

  @Test
  fun `turning ghost off keeps essential mode the user enabled`() {
    assertEquals(State(essential = true, turnedOnByGhost = false), mirror(ghost = false, essential = true, turnedOnByGhost = false))
  }

  @Test
  fun `repeating the same value changes nothing`() {
    val on = mirror(ghost = true, essential = false, turnedOnByGhost = false)
    assertEquals(on, mirror(ghost = true, essential = on.essential, turnedOnByGhost = on.turnedOnByGhost))
    val off = mirror(ghost = false, essential = on.essential, turnedOnByGhost = on.turnedOnByGhost)
    assertEquals(off, mirror(ghost = false, essential = off.essential, turnedOnByGhost = off.turnedOnByGhost))
  }
}
