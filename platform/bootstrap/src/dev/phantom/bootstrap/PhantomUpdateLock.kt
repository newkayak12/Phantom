// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.bootstrap

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime

/**
 * The lock the macOS self-update script (PhantomSelfUpdate) holds beside the app bundle while it swaps the bundle. A launch
 * that starts then may load classes from both bundles and crash, so it quits before loading anything else: the script
 * opens the app itself once the swap is done. Only JDK classes here, no work but one file check, no network.
 */
internal object PhantomUpdateLock {
  /** A lock older than this is left over from a script that died and is ignored. */
  const val MAX_AGE_MS: Long = 120_000

  /** `<folder>/.<Name>.app.updating` for `<folder>/<Name>.app`; the script writes the same path. */
  fun lockFile(app: Path): Path = app.resolveSibling(".${app.fileName}.updating")

  /** The app bundle of a process started from [command] (the launcher or the bundled JBR), or null outside a bundle. */
  fun appBundle(command: String): Path? {
    val end = command.indexOf(".app/Contents/")
    return if (end < 0) null else Path.of(command.substring(0, end + ".app".length))
  }

  fun isUpdating(app: Path, nowMs: Long): Boolean {
    val lock = lockFile(app)
    return try {
      Files.exists(lock) && nowMs - lock.getLastModifiedTime().toMillis() in 0 until MAX_AGE_MS
    }
    catch (_: Exception) {
      false
    }
  }

  /** True when this process runs from a macOS app bundle the update script is swapping right now. */
  @JvmStatic
  fun isCurrentAppUpdating(): Boolean {
    if (!System.getProperty("os.name", "").startsWith("Mac")) return false
    val app = ProcessHandle.current().info().command().orElse(null)?.let(::appBundle) ?: return false
    return isUpdating(app, System.currentTimeMillis())
  }
}
