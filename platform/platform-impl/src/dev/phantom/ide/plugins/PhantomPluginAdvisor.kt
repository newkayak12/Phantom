// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package dev.phantom.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Suggests a plugin from a GitHub plugin repository when the project uses one of the libraries that plugin lists under
 * `detect` in `.phantom/marketplace.json`. Runs after the project opens and again when its libraries change (a Gradle
 * or Maven import), never during startup itself, and suggests each plugin once per project until it is installed or
 * dismissed.
 */
internal class PhantomPluginAdvisor : ProjectActivity {
  override suspend fun execute(project: Project) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) return
    val advisor = project.serviceAsync<PhantomPluginAdvisorService>()
    project.messageBus.connect(advisor.scope).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = advisor.schedule()
    })
    advisor.schedule()
  }
}

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class PhantomPluginAdvisorService(private val project: Project, val scope: CoroutineScope) {
  // Conflated, so a request made before the collector starts is kept, and a burst of root changes is one check
  private val requests = Channel<Unit>(Channel.CONFLATED)
  private val suggested = ConcurrentHashMap.newKeySet<String>()

  init {
    scope.launch { requests.receiveAsFlow().debounce(5.seconds).collect { check() } }
  }

  fun schedule() {
    requests.trySend(Unit)
  }

  private suspend fun check() {
    val used = readAction { libraryCoordinates(project) }
    if (used.isEmpty()) return
    val properties = PropertiesComponent.getInstance(project)
    for (plugin in DetectCache.get()) {
      if (plugin.id in suggested || properties.getBoolean(ignoreKey(plugin.id))) continue
      if (PluginManagerCore.getPlugin(PluginId.getId(plugin.id)) != null) continue
      val match = plugin.detect.firstOrNull { it in used } ?: continue
      suggested += plugin.id
      LOG.info("Suggesting ${plugin.id}: the project uses $match")
      notify(plugin, match)
    }
  }

  private fun notify(plugin: GitHubPluginRepository.Detect, library: String) {
    NotificationGroupManager.getInstance().getNotificationGroup("Phantom Plugins")
      .createNotification("${plugin.name} is available", "This project uses $library.", NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimpleExpiring("Install") {
        installAndEnable(project, setOf(PluginId.getId(plugin.id)), showDialog = true) {}
      })
      .addAction(NotificationAction.createSimpleExpiring("Don't suggest") {
        PropertiesComponent.getInstance(project).setValue(ignoreKey(plugin.id), true)
      })
      .notify(project)
  }

  private fun ignoreKey(id: String) = "phantom.plugins.ignore.$id"

  private companion object {
    private val LOG = logger<PhantomPluginAdvisorService>()
  }
}

/** `group:artifact` of every library in the project, read from the names Gradle and Maven give imported libraries. */
internal fun libraryCoordinates(project: Project): Set<String> {
  val result = HashSet<String>()
  OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { library ->
    coordinatesOf(library.name)?.let(result::add)
    true
  }
  return result
}

/** `Gradle: org.postgresql:postgresql:42.7.3` and `Maven: org.postgresql:postgresql:42.7.3` give `org.postgresql:postgresql`. */
@ApiStatus.Internal
fun coordinatesOf(libraryName: String?): String? {
  val coordinates = libraryName?.substringAfter(": ", "")?.split(':') ?: return null
  return if (coordinates.size >= 2 && coordinates[0].isNotBlank() && coordinates[1].isNotBlank()) "${coordinates[0]}:${coordinates[1]}" else null
}

/** The `detect` lists of all GitHub plugin repositories, fetched at most every few hours. */
private object DetectCache {
  private val LOG = logger<DetectCache>()
  private val ttl = 6.hours
  @Volatile private var entry: Pair<TimeSource.Monotonic.ValueTimeMark, List<GitHubPluginRepository.Detect>>? = null

  suspend fun get(): List<GitHubPluginRepository.Detect> {
    entry?.let { (fetched, plugins) -> if (fetched.elapsedNow() < ttl) return plugins }
    val plugins = withContext(Dispatchers.IO) {
      RepositoryHelper.getCustomPluginRepositoryHosts().mapNotNull(GitHubPluginRepository::parse).flatMap { ref ->
        try {
          GitHubPluginRepository.parseDetect(GitHubPluginRepository.loadMetadata(ref))
        }
        catch (e: IOException) {
          LOG.info("Cannot read ${ref.host}: ${e.message}")
          emptyList()
        }
      }
    }
    // An empty answer (offline, or nothing to suggest) is asked again next time
    if (plugins.isNotEmpty()) entry = TimeSource.Monotonic.markNow() to plugins
    return plugins
  }
}
