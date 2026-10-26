// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import java.util.WeakHashMap

/** The tab groups of a project: one controller per editor pane, saved in the workspace in the order of the panes. */
@Service(Service.Level.PROJECT)
@State(name = "PhantomTabGroups", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TabGroupsService(private val project: Project) : PersistentStateComponent<TabGroupsState> {
  private val controllers = WeakHashMap<EditorWindow, TabGroupPaneController>()
  private var saved = TabGroupsState()

  // read off the EDT: by the color provider and when settings are saved
  @Volatile private var stateSnapshot: TabGroupsState? = null
  @Volatile private var colorSnapshot: Map<String, TabGroupColor> = emptyMap()

  init {
    synchronized(live) { live.add(this) }
  }

  @RequiresEdt
  fun controllerFor(window: EditorWindow): TabGroupPaneController = controllers.getOrPut(window) {
    val index = windows().indexOf(window)
    TabGroupPaneController(window, TabGroupModel(), saved.panes.getOrNull(index), ::onChanged)
  }

  @RequiresEdt
  private fun onChanged() {
    val live = controllers.filterKeys { !it.isDisposed }
    stateSnapshot = TabGroupsState().apply {
      for (window in windows()) panes.add(live[window]?.model?.toPaneState() ?: PaneState())
    }
    val colors = HashMap<String, TabGroupColor>()
    for (controller in live.values) {
      for (group in controller.model.groups) group.members.forEach { colors.putIfAbsent(it, group.color) }
    }
    colorSnapshot = colors
  }

  /** Controllers of the open panes, made for panes that have none yet. */
  @RequiresEdt
  fun allControllers(): List<TabGroupPaneController> = windows().filter { !it.isDisposed }.map(::controllerFor)

  /** The color of the first group [file] is in, in any pane. */
  fun groupColorOf(file: VirtualFile): TabGroupColor? = colorSnapshot[file.url]

  fun isGrouped(file: VirtualFile): Boolean = groupColorOf(file) != null

  private fun windows(): List<EditorWindow> = FileEditorManagerEx.getInstanceEx(project).windows.toList()

  override fun getState(): TabGroupsState = stateSnapshot ?: saved

  override fun loadState(state: TabGroupsState) {
    saved = state
  }

  companion object {
    private val live = ContainerUtil.createWeakSet<TabGroupsService>()

    fun getInstance(project: Project): TabGroupsService = project.service()

    /** Whether [file] is in a group in any pane of any project. */
    fun isGroupedAnywhere(file: VirtualFile): Boolean = synchronized(live) { live.toList() }.any { it.isGrouped(file) }
  }
}
