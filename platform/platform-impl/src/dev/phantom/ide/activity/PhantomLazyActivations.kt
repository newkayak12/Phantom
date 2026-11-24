// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<PhantomLazyActivations>()

/** Runs [PhantomLazyActivation.activate] on its first trigger in this project, and again after an idle period. */
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class PhantomLazyActivations(private val project: Project, private val scope: CoroutineScope) {
  private val started = AtomicBoolean()
  private val active: MutableSet<String> = Collections.synchronizedSet(HashSet())

  /** Activates every plugin whose declaration matches [trigger] and that is not active in this project yet. */
  fun trigger(trigger: LazyTrigger) {
    PhantomLazyActivationBean.EP_NAME.processWithPluginDescriptor { bean, descriptor ->
      val key = descriptor.pluginId.idString + "/" + bean.implementation
      if (bean.matches(trigger) && active.add(key)) {
        scope.launch {
          try {
            bean.getInstance(ApplicationManager.getApplication(), descriptor).activate(project)
          }
          catch (e: Exception) {
            active.remove(key)
            LOG.warn("Lazy activation $key failed", e)
          }
        }
      }
    }
  }

  /** Plugins released their state on idle; the next trigger activates them again. */
  internal fun reset() = active.clear()

  internal fun start() {
    if (!started.compareAndSet(false, true)) return
    val connection = project.messageBus.connect(scope)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        file.extension?.let { trigger(LazyTrigger.File(it)) }
      }
    })
    connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) = trigger(LazyTrigger.ToolWindow(toolWindow.id))
    })
    connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (event.project != project) return
        ActionManager.getInstance().getId(action)?.let { trigger(LazyTrigger.Action(it)) }
      }
    })
    ApplicationManager.getApplication().messageBus.connect(scope).subscribe(PhantomActivityListener.TOPIC, object : PhantomActivityListener {
      override fun idle() = reset()
    })
    // files already open when the project was restored
    FileEditorManager.getInstance(project).openFiles.forEach { file -> file.extension?.let { trigger(LazyTrigger.File(it)) } }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PhantomLazyActivations = project.service()
  }
}

/** Subscribes only when some plugin declares a lazy activation, so it costs nothing otherwise. */
internal class PhantomLazyActivationsStarter : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (PhantomLazyActivationBean.EP_NAME.hasAnyExtensions()) PhantomLazyActivations.getInstance(project).start()
  }
}
