// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.ghost

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus

/**
 * Ghost Mode keeps the editor's viewing features (coloring, syntax errors, completion, navigation) and turns off the heavy
 * analysis (inspections, code vision, inlay hints, background analysis), for working alongside AI agents that do the editing.
 *
 * The core owns the [REGISTRY_KEY] registry key; Phantom plugins only read `Registry.is("phantom.ghost", false)`.
 */
@ApiStatus.Internal
object PhantomGhostMode {
  const val REGISTRY_KEY: String = "phantom.ghost"

  @JvmStatic
  fun isEnabled(): Boolean = Registry.`is`(REGISTRY_KEY, false)

  @JvmStatic
  fun setEnabled(value: Boolean) {
    RegistryManager.getInstance().get(REGISTRY_KEY).setValue(value)
  }
}
