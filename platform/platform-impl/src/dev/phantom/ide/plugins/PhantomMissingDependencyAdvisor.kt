// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.plugins

import com.intellij.ide.plugins.PluginDependencyIsNotInstalled
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import dev.phantom.ide.PhantomIdeBundle
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * After the first project opens, offers to install the Phantom repository plugins that provide (by id or `aliases`) a dependency
 * that kept an installed plugin from loading, such as a third-party plugin depending on `com.intellij.java`, which `dev.phantom.java`
 * provides. Reads the repository metadata cache off the EDT, once per IDE session.
 */
internal class PhantomMissingDependencyAdvisor : ProjectActivity {
  override suspend fun execute(project: Project) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || !checked.compareAndSet(false, true)) return
    val missing = PluginManagerCore.plugins.mapNotNull { PluginManagerCore.getPluginNonLoadReason(it.pluginId) as? PluginDependencyIsNotInstalled }
    if (missing.isEmpty()) return
    val providers = RepositoryMetadataCache.get().flatMap(GitHubPluginRepository::parseProviders)
    val properties = PropertiesComponent.getInstance()
    val offers = missingProviders(missing.map { it.plugin.name to it.dependencyNameOrId }, providers)
      .filter { (provider, _) -> PluginManagerCore.getPlugin(PluginId.getId(provider.id)) == null && !properties.getBoolean(ignoreKey(provider.id)) }
    if (offers.isEmpty()) return
    LOG.info("Offering ${offers.map { it.first.id }} for plugins with missing dependencies")
    notify(project, offers)
  }

  private fun notify(project: Project, offers: List<Pair<GitHubPluginRepository.Provider, List<String>>>) {
    val content = offers.joinToString("<br>") { (provider, plugins) ->
      PhantomIdeBundle.message("phantom.plugins.missing.dependency.entry", provider.name, plugins.joinToString(", "))
    }
    val ids = offers.map { PluginId.getId(it.first.id) }.toSet()
    NotificationGroupManager.getInstance().getNotificationGroup("Phantom Plugins")
      .createNotification(PhantomIdeBundle.message("phantom.plugins.missing.dependency.title"), content, NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimpleExpiring(
        if (ids.size == 1) PhantomIdeBundle.message("phantom.plugins.advisor.install")
        else PhantomIdeBundle.message("phantom.plugins.advisor.install.all")) {
        installAndEnable(project, ids, showDialog = true) {}
      })
      .addAction(NotificationAction.createSimpleExpiring(PhantomIdeBundle.message("phantom.plugins.advisor.dont.suggest")) {
        val properties = PropertiesComponent.getInstance()
        offers.forEach { properties.setValue(ignoreKey(it.first.id), true) }
      })
      .notify(project)
  }

  private fun ignoreKey(id: String) = "phantom.plugins.missing.dependency.ignore.$id"

  private companion object {
    private val LOG = logger<PhantomMissingDependencyAdvisor>()
    private val checked = AtomicBoolean()
  }
}

/**
 * For each provider that supplies a missing dependency, the names of the plugins waiting for it.
 * [missing] pairs a plugin name with the dependency it lacks (an id, or a name when the IDE knows one).
 */
@ApiStatus.Internal
fun missingProviders(
  missing: List<Pair<String, String>>,
  providers: List<GitHubPluginRepository.Provider>,
): List<Pair<GitHubPluginRepository.Provider, List<String>>> {
  val result = LinkedHashMap<String, Pair<GitHubPluginRepository.Provider, MutableList<String>>>()
  for ((plugin, dependency) in missing) {
    val provider = providers.firstOrNull { it.provides(dependency) || it.name == dependency } ?: continue
    val names = result.getOrPut(provider.id) { provider to ArrayList() }.second
    if (plugin !in names) names += plugin
  }
  return result.values.toList()
}
