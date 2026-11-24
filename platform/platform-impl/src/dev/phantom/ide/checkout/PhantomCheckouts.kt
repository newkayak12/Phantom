// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.checkout

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val LOG = logger<PhantomCheckouts>()

@Serializable
@ApiStatus.Internal
data class CheckoutEntry(val path: String, val id: String)

@Serializable
@ApiStatus.Internal
data class RepositoryCheckouts(val base: CheckoutEntry? = null, val worktrees: List<CheckoutEntry> = emptyList())

/**
 * App-level index of known checkouts per repository (git common dir), in `<system>/phantom/checkouts.json`, so a
 * base can list its worktrees without scanning. Entries are added when a checkout is opened.
 */
@ApiStatus.Experimental
@Service(Service.Level.APP)
class PhantomCheckouts internal constructor(private val file: Path) {
  @Suppress("unused")
  constructor() : this(PathManager.getSystemDir().resolve("phantom/checkouts.json"))

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
  private var cache: MutableMap<String, RepositoryCheckouts>? = null

  /** Checkouts of the repository whose git common dir is [commonDir]. */
  @Synchronized
  fun of(commonDir: String): RepositoryCheckouts = load()[commonDir] ?: RepositoryCheckouts()

  /** The id recorded for the checkout at [path], if it was opened before. */
  @Synchronized
  fun idOf(path: String): String? = load().values.asSequence()
    .flatMap { sequenceOf(it.base) + it.worktrees }
    .firstOrNull { it?.path == path }?.id

  @Synchronized
  internal fun register(link: PhantomCheckoutLink) {
    val commonDir = link.commonDir ?: return
    val path = link.rootPath ?: return
    update(commonDir, path, link.checkoutId, link.role ?: return)
  }

  @Synchronized
  internal fun update(commonDir: String, path: String, id: String, role: CheckoutRole) {
    val all = load()
    val entry = CheckoutEntry(path, id)
    val current = all[commonDir] ?: RepositoryCheckouts()
    val next = when (role) {
      CheckoutRole.BASE -> current.copy(base = entry)
      else -> current.copy(worktrees = current.worktrees.filter { it.path != path } + entry)
    }
    if (next == current) return
    all[commonDir] = next
    save(all)
  }

  private fun load(): MutableMap<String, RepositoryCheckouts> {
    cache?.let { return it }
    val loaded = try {
      if (Files.isRegularFile(file)) json.decodeFromString<Map<String, RepositoryCheckouts>>(Files.readString(file)).toMutableMap()
      else mutableMapOf()
    }
    catch (e: Exception) {
      LOG.warn("Cannot read $file, starting a new index", e)
      mutableMapOf()
    }
    cache = loaded
    return loaded
  }

  private fun save(all: Map<String, RepositoryCheckouts>) {
    try {
      Files.createDirectories(file.parent)
      val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
      Files.writeString(tmp, json.encodeToString(all))
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (e: Exception) {
      LOG.warn("Cannot write $file", e)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): PhantomCheckouts = service()
  }
}
