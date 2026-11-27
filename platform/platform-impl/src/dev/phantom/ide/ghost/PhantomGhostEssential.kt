// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.ide.EssentialHighlightingMode
import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.ApiStatus

/**
 * Mirrors Ghost Mode onto essential highlighting mode: turning Ghost Mode on enables essential highlighting, turning it off
 * restores the earlier value. Whether Ghost Mode was the one that enabled it is kept across restarts.
 */
@ApiStatus.Internal
object PhantomGhostEssential {
  private const val TURNED_ON_BY_GHOST = "phantom.ghost.essential.turned.on"

  /** Applies [ghost] to essential highlighting mode; does nothing when both already agree. */
  fun sync(ghost: Boolean) {
    val properties = PropertiesComponent.getInstance()
    val essential = EssentialHighlightingMode.isEnabled()
    val next = mirror(ghost, essential, properties.getBoolean(TURNED_ON_BY_GHOST))
    if (next.essential != essential) {
      EssentialHighlightingMode.setEnabled(next.essential)
    }
    properties.setValue(TURNED_ON_BY_GHOST, next.turnedOnByGhost)
  }

  data class State(val essential: Boolean, val turnedOnByGhost: Boolean)

  /** The essential mode and "turned on by Ghost Mode" flag that should follow from the current values. */
  fun mirror(ghost: Boolean, essential: Boolean, turnedOnByGhost: Boolean): State = when {
    ghost && essential -> State(essential = true, turnedOnByGhost = turnedOnByGhost)
    ghost -> State(essential = true, turnedOnByGhost = true)
    turnedOnByGhost -> State(essential = false, turnedOnByGhost = false)
    else -> State(essential = essential, turnedOnByGhost = false)
  }
}
