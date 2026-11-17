// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

/**
 * What the JetBrains Marketplace listing shows in Phantom. A production build hides plugins Phantom cannot install:
 * ones built only for commercial JetBrains IDEs, and paid ones while JetBrains' licensing plugin
 * ("JetBrains Marketplace Licensing", installable from the Marketplace) is not installed. Installable plugins are
 * listed as usual. Plugins JetBrains publishes are not listed at all: Phantom ships its own versions of the ones it keeps,
 * in its bundle or the Phantom plugins repository. Running from sources, internal mode or
 * `-Dphantom.marketplace.showAll=true` shows everything.
 */
@ApiStatus.Internal
object PhantomMarketplace {
  @JvmStatic
  fun hidesUninstallable(): Boolean {
    if (System.getProperty("phantom.marketplace.showAll").toBoolean()) return false
    if (PluginManagerCore.isRunningFromSources()) return false
    val app = ApplicationManager.getApplication()
    return app == null || !app.isInternal
  }

  /** True for a Marketplace plugin published by JetBrains (its organization), which the listing leaves out. */
  @JvmStatic
  fun isJetBrainsPlugin(organization: String?): Boolean =
    organization != null && organization.trim().startsWith("JetBrains", ignoreCase = true)

  private const val LICENSING_PLUGIN = "com.intellij.marketplace"

  /** True when a search result can be installed here: compatible with this product, and not paid unless licensing is installed. */
  @JvmStatic
  fun isInstallable(isPaid: Boolean, compatibleWithThisIde: Boolean): Boolean =
    compatibleWithThisIde && (!isPaid || PluginManagerCore.getPlugin(PluginId.getId(LICENSING_PLUGIN)) != null)
}
