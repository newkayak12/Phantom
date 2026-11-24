// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.LazyExtensionInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

/**
 * Lazy start of a plugin's heavy state. The plugin stays installed and its descriptor is read at startup, but
 * [activate] runs only when the user first touches what it serves, once per project, and again after an idle
 * period ([PhantomActivityListener.idle], where the plugin released that state).
 *
 * Triggers are declared in XML so nothing of the plugin is loaded before one fires:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <phantom.lazyActivation implementation="my.plugin.MyActivation"
 *                           fileExtensions="md,mmd" actions="My.Action" toolWindows="My Tool Window"/>
 * </extensions>
 * ```
 * Keep [activate] cheap to call twice. It runs on a background thread; do not block the EDT.
 */
@ApiStatus.Experimental
interface PhantomLazyActivation {
  suspend fun activate(project: Project)
}

@ApiStatus.Experimental
class PhantomLazyActivationBean : LazyExtensionInstance<PhantomLazyActivation>() {
  @Attribute("implementation")
  @JvmField
  var implementation: String? = null

  /** Comma-separated file extensions, without dots; matched case-insensitively when a file is opened in an editor. */
  @Attribute("fileExtensions")
  @JvmField
  var fileExtensions: String? = null

  /** Comma-separated action ids; matched before the action runs. */
  @Attribute("actions")
  @JvmField
  var actions: String? = null

  /** Comma-separated tool window ids; matched when the tool window is shown. */
  @Attribute("toolWindows")
  @JvmField
  var toolWindows: String? = null

  override fun getImplementationClassName(): String? = implementation

  internal fun matches(trigger: LazyTrigger): Boolean = when (trigger) {
    is LazyTrigger.File -> split(fileExtensions).any { it.equals(trigger.extension, ignoreCase = true) }
    is LazyTrigger.Action -> trigger.id in split(actions)
    is LazyTrigger.ToolWindow -> trigger.id in split(toolWindows)
  }

  private fun split(value: String?): List<String> = value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PhantomLazyActivationBean> = ExtensionPointName("com.intellij.phantom.lazyActivation")
  }
}

@ApiStatus.Internal
sealed interface LazyTrigger {
  data class File(val extension: String) : LazyTrigger
  data class Action(val id: String) : LazyTrigger
  data class ToolWindow(val id: String) : LazyTrigger
}
