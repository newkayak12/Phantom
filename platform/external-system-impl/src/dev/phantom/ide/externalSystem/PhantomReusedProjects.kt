// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.externalSystem

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * External projects whose model was applied from another checkout (see the Gradle sync reuse), so the auto-import
 * tracker takes them as synced on first registration instead of reloading them.
 */
@ApiStatus.Internal
object PhantomReusedProjects {
  private val KEY = Key.create<MutableSet<String>>("phantom.reused.external.projects")

  fun mark(project: Project, id: ExternalSystemProjectId) {
    (project as UserDataHolderEx).putUserDataIfAbsent(KEY, ConcurrentHashMap.newKeySet<String>()).add(keyOf(id))
  }

  /** True once for a project marked by [mark]. */
  fun consume(project: Project, id: ExternalSystemProjectId): Boolean {
    return project.getUserData(KEY)?.remove(keyOf(id)) == true
  }

  private fun keyOf(id: ExternalSystemProjectId): String = id.systemId.id + "|" + id.externalProjectPath
}
