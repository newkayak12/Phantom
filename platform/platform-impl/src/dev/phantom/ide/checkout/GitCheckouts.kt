// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.checkout

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/** How a project root relates to its git repository. */
@ApiStatus.Experimental
enum class CheckoutRole { BASE, WORKTREE, CLONE }

/** What `<root>/.git` says about a checkout, read without running git. */
@ApiStatus.Internal
data class GitCheckout(val role: CheckoutRole, val commonDir: Path, val basePath: Path?)

@ApiStatus.Internal
object GitCheckouts {
  /**
   * `.git` directory: the main checkout (BASE). `.git` file pointing into `<common>/worktrees/<name>`: a linked
   * worktree whose base is the directory holding `<common>`. Anything else (no git, submodule): null.
   * A second clone is also BASE here; making it a CLONE of another checkout is a user choice.
   */
  fun resolve(root: Path): GitCheckout? {
    val dotGit = root.resolve(".git")
    if (Files.isDirectory(dotGit)) return GitCheckout(CheckoutRole.BASE, dotGit.toRealPathOrSelf(), null)
    if (!Files.isRegularFile(dotGit)) return null
    val gitDirLine = Files.readAllLines(dotGit).firstOrNull { it.startsWith("gitdir:") } ?: return null
    val gitDir = root.resolve(gitDirLine.removePrefix("gitdir:").trim()).normalize()
    val commonDirFile = gitDir.resolve("commondir")
    if (!Files.isRegularFile(commonDirFile)) return null
    val commonDir = gitDir.resolve(Files.readString(commonDirFile).trim()).normalize().toRealPathOrSelf()
    if (commonDir.fileName?.toString() != ".git") return null // bare repository: no checkout to call base
    return GitCheckout(CheckoutRole.WORKTREE, commonDir, commonDir.parent)
  }

  private fun Path.toRealPathOrSelf(): Path = try { toRealPath() } catch (_: Exception) { this }
}
