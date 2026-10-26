// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.tabs.core

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsEx
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import javax.swing.SwingConstants

/** Shows the groups of one editor pane in its tab bar: colors, chips, collapsed groups, and groups kept together. */
class TabGroupPaneController(
  val window: EditorWindow,
  val model: TabGroupModel,
  saved: PaneState? = null,
  private val onChanged: () -> Unit = {},
) {
  private val tabs: JBTabs
    get() = window.tabbedPane.tabs

  private val chips = HashMap<String, TabGroupChip>()
  private var colors: Map<String, TabGroupColor> = emptyMap()
  private var lastOrder: List<String> = emptyList()
  private var suppressExpand = false
  private var refreshing = false

  /** Saved groups whose files are not all open yet: editors come back one by one after a restart. */
  private var pending: PaneState? = saved?.takeIf { it.groups.isNotEmpty() }
  private val restored = HashMap<Int, TabGroup>()

  init {
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        if (suppressExpand || refreshing) return
        val group = newSelection?.url?.let(model::groupOf) ?: return
        if (group.collapsed) {
          group.collapsed = false
          refresh()
        }
      }

      override fun tabsMoved() {
        pending = null
        model.onMoved(lastOrder, tabs.tabs.mapNotNull { it.url }, tabs.selectedInfo?.url)
        refresh()
      }

      override fun tabRemoved(tabToRemove: TabInfo) {
        val url = tabToRemove.url ?: return
        if (model.groupOf(url) == null) return
        // the tab is being taken apart: leave its TabInfo alone
        model.remove(url)
        ApplicationManager.getApplication().invokeLater({ refresh() }, { window.isDisposed })
      }
    })
  }

  fun createGroup(files: List<VirtualFile>): TabGroup {
    pending = null
    val group = model.create(files.map { it.url }, "", model.nextColor())
    refresh()
    return group
  }

  fun addTo(file: VirtualFile, group: TabGroup) {
    pending = null
    model.add(file.url, group)
    refresh()
  }

  fun removeFromGroup(file: VirtualFile) {
    pending = null
    model.remove(file.url)
    refresh()
  }

  fun ungroup(group: TabGroup) {
    pending = null
    if (group.collapsed) {
      group.collapsed = false
      refresh()
    }
    model.ungroup(group)
    refresh()
  }

  fun closeGroup(group: TabGroup) {
    pending = null
    val files = filesOf(group)
    model.ungroup(group)
    refresh()
    for (file in files) window.manager.closeFile(file, window)
  }

  fun changed(group: TabGroup) {
    chips[group.id]?.let { it.revalidate(); it.repaint() }
    refresh()
  }

  /**
   * Collapses or expands [group]. Collapsing the group of the selected tab selects the nearest tab outside it first;
   * a group holding every tab of the pane does not collapse.
   */
  fun toggleCollapsed(group: TabGroup) {
    if (group.collapsed) {
      group.collapsed = false
      refresh()
      return
    }
    val visible = tabs.tabs.filter { !it.isHidden }
    val outside = visible.filter { it.url !in group.members }
    if (outside.isEmpty()) return
    val selected = tabs.selectedInfo
    if (selected != null && selected.url in group.members) {
      val index = visible.indexOf(selected)
      // not the chip of another collapsed group
      fun selectable(info: TabInfo) = info.url !in group.members && info.url?.let(model::groupOf)?.collapsed != true
      val target = visible.drop(index + 1).firstOrNull(::selectable) ?: visible.take(index).lastOrNull(::selectable) ?: return
      suppressExpand = true
      try {
        tabs.select(target, false)
      }
      finally {
        suppressExpand = false
      }
    }
    group.collapsed = true
    refresh()
  }

  /** Brings the tab bar in line with the model. Changes nothing for a pane that never had a group. */
  fun refresh() {
    if (window.isDisposed || refreshing) return
    refreshing = true
    try {
      doRefresh()
    }
    finally {
      refreshing = false
    }
  }

  private fun doRefresh() {
    val all = tabs.tabs
    val open = all.mapNotNullTo(HashSet()) { it.url }
    applyPending(open)
    for (group in model.groups.toList()) {
      group.members.filter { it !in open }.forEach(model::remove)
    }
    if (model.groups.isEmpty() && chips.isEmpty() && colors.isEmpty()) {
      lastOrder = all.mapNotNull { it.url }
      return
    }

    val chipsShown = chipsSupported()
    val order = model.desiredOrder(all.mapNotNull { it.url })
    // a group's first tab is its chip; the other tabs of a collapsed group are hidden
    val anchors = model.groups.associateWith { group -> order.first { it in group.members } }
    // the selected tab is never hidden: its collapsed group opens (a tab just added to it, or a group being restored)
    val selectedGroup = tabs.selectedInfo?.url?.let(model::groupOf)
    if (selectedGroup != null && selectedGroup.collapsed && anchors[selectedGroup] != tabs.selectedInfo?.url) {
      selectedGroup.collapsed = false
    }

    chips.keys.retainAll(model.groups.mapTo(HashSet()) { it.id })
    for (info in all) {
      val url = info.url ?: continue
      val group = model.groupOf(url)
      val anchor = group != null && chipsShown && anchors[group] == url
      val chip = if (anchor) chips.getOrPut(group.id) { TabGroupChip(this, group) } else null
      // TabInfo fires a change, and JBTabs relayouts, even when null is set over null
      if (info.labelLeadingComponent !== chip) info.labelLeadingComponent = chip
      if (info.isLabelCollapsed != (anchor && group.collapsed)) info.isLabelCollapsed = anchor && group.collapsed
      val hidden = chipsShown && group != null && group.collapsed && !anchor
      if (info.isHidden != hidden) info.isHidden = hidden
    }
    // hidden tabs come back at stale places, so sort after hiding and showing
    if (chipsShown) {
      val position = order.withIndex().associate { it.value to it.index }
      (tabs as? JBTabsEx)?.sortTabs(compareBy<TabInfo> { position[it.url] ?: Int.MAX_VALUE })
    }
    chips.values.forEach { it.revalidate(); it.repaint() }

    val newColors = HashMap<String, TabGroupColor>()
    for (group in model.groups) group.members.forEach { newColors[it] = group.color }
    val changed = (colors.keys + newColors.keys).filter { colors[it] != newColors[it] }
    colors = newColors
    onChanged()
    if (changed.isNotEmpty()) {
      val byUrl = all.mapNotNull { info -> (info.`object` as? VirtualFile)?.let { it.url to it } }.toMap()
      changed.mapNotNull(byUrl::get).forEach(window.manager::updateFileColor)
    }
    lastOrder = tabs.tabs.mapNotNull { it.url }
  }

  private fun applyPending(open: Set<String>) {
    val state = pending ?: return
    var missing = false
    state.groups.forEachIndexed { index, saved ->
      if (saved.urls.any { it !in open }) missing = true
      val urls = saved.urls.filter { it in open && model.groupOf(it) == null }
      if (urls.isEmpty()) return@forEachIndexed
      val group = restored[index]?.takeIf { it in model.groups }
      if (group == null) {
        val color = TabGroupColor.entries.firstOrNull { it.name == saved.color } ?: TabGroupColor.GREY
        restored[index] = model.create(urls, saved.name.orEmpty(), color).also { it.collapsed = saved.collapsed }
      }
      else {
        urls.forEach { model.add(it, group) }
      }
    }
    if (!missing) pending = null
  }

  private fun filesOf(group: TabGroup): List<VirtualFile> =
    tabs.tabs.mapNotNull { it.`object` as? VirtualFile }.filter { it.url in group.members }
}

private val TabInfo.url: String?
  get() = (`object` as? VirtualFile)?.url

/**
 * Chips and collapsing need the default tab bar: one scrolling row on top, close buttons on the right, tabs in the order
 * they are put in. Otherwise groups only color their tabs.
 */
private fun chipsSupported(): Boolean {
  val settings = UISettings.getInstance()
  return settings.editorTabPlacement == SwingConstants.TOP && settings.scrollTabLayoutInEditor &&
         settings.closeTabButtonOnTheRight && !settings.sortTabsAlphabetically
}
