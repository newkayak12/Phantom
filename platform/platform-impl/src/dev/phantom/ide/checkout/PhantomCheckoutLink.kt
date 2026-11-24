// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.checkout

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.UUID

/** A checkout as other checkouts see it. */
@ApiStatus.Experimental
data class CheckoutRef(val path: String, val id: String?)

class CheckoutLinkState : BaseState() {
  var checkoutId by string()
  var role by string()
  var basePath by string()
  var baseCheckoutId by string()
  var commonDir by string()
}

/**
 * Which checkout of a repository this project is: the main checkout (base), a linked worktree, or a second clone.
 * Kept in the workspace file (.idea/workspace.xml), which is never shared, so worktrees do not get a git change
 * each. Gradle model reuse and the local shared index read the same record.
 *
 * [ensure] fills it synchronously from `<root>/.git` (two small file reads, no git process), so it can be read
 * before the external-system auto-import starts.
 */
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
@State(name = "PhantomCheckoutLink", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class PhantomCheckoutLink(private val project: Project) : SimplePersistentStateComponent<CheckoutLinkState>(CheckoutLinkState()) {
  val checkoutId: String get() = ensure().checkoutId!!
  val role: CheckoutRole? get() = ensure().role?.let { runCatching { CheckoutRole.valueOf(it) }.getOrNull() }

  /** The base checkout for a worktree or a linked clone; null for a base or outside git. */
  fun base(): CheckoutRef? = ensure().basePath?.let { CheckoutRef(it, state.baseCheckoutId ?: PhantomCheckouts.getInstance().idOf(it)) }

  /** Links a second clone to a base the user picked. */
  fun linkToBase(base: CheckoutRef) {
    ensure()
    state.role = CheckoutRole.CLONE.name
    state.basePath = base.path
    state.baseCheckoutId = base.id
    PhantomCheckouts.getInstance().register(this)
  }

  @Synchronized
  fun ensure(): CheckoutLinkState {
    val s = state
    if (s.checkoutId == null) s.checkoutId = UUID.randomUUID().toString()
    if (s.role == null) {
      val root = project.guessProjectDir()?.toNioPathOrNull()
      val git = root?.let { GitCheckouts.resolve(it) }
      if (git != null) {
        s.role = git.role.name
        s.commonDir = git.commonDir.toString()
        s.basePath = git.basePath?.toString()
      }
    }
    return s
  }

  internal val commonDir: String? get() = ensure().commonDir
  internal val rootPath: String? get() = project.basePath

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PhantomCheckoutLink = project.service()
  }
}

private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNull(): Path? = try { toNioPath() } catch (_: UnsupportedOperationException) { null }

/** Records the opened checkout in the app index, off the startup path. */
internal class PhantomCheckoutRegistrar : ProjectActivity {
  override suspend fun execute(project: Project) {
    withContext(Dispatchers.IO) {
      PhantomCheckouts.getInstance().register(PhantomCheckoutLink.getInstance(project))
    }
  }
}
