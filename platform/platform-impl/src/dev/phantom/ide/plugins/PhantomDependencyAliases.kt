// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * The `aliases` of the GitHub plugin repositories the plugin manager has read, so that installing a plugin which depends on
 * an IntelliJ plugin or module Phantom replaces (`com.intellij.java`) installs the Phantom plugin providing it (`dev.phantom.java`).
 */
@ApiStatus.Internal
object PhantomDependencyAliases {
  private val byRepository = ConcurrentHashMap<String, List<GitHubPluginRepository.Provider>>()

  fun remember(repository: String, providers: List<GitHubPluginRepository.Provider>) {
    byRepository[repository] = providers
  }

  /** The plugin that lists [dependencyId] among its aliases, or null when none does (then the id is resolved as usual). */
  @JvmStatic
  fun resolve(dependencyId: String): PluginId? =
    aliasOwner(byRepository.values.flatten(), dependencyId)?.let { PluginId.getId(it.id) }

  @JvmStatic
  fun aliasOwner(providers: List<GitHubPluginRepository.Provider>, dependencyId: String): GitHubPluginRepository.Provider? =
    providers.firstOrNull { dependencyId in it.aliases }
}
