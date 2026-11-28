// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Phantom's own icons; public so Phantom plugins can show the same ones. */
object PhantomIcons {
  /** Ghost Mode on: filled ghost in the accent color. */
  @JvmField
  val GhostOn: Icon = IconLoader.getIcon("/dev/phantom/icons/ghostOn.svg", PhantomIcons::class.java.classLoader)

  /** Ghost Mode off: ghost outline in the toolbar icon gray. */
  @JvmField
  val GhostOff: Icon = IconLoader.getIcon("/dev/phantom/icons/ghostOff.svg", PhantomIcons::class.java.classLoader)
}
