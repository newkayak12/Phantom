// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.checkout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GitCheckoutsTest {
  @TempDir
  lateinit var tmp: Path

  @Test
  fun mainCheckoutIsBase() {
    val main = Files.createDirectories(tmp.resolve("main/.git")).parent
    val git = GitCheckouts.resolve(main)!!
    assertEquals(CheckoutRole.BASE, git.role)
    assertNull(git.basePath)
  }

  @Test
  fun linkedWorktreePointsToItsBase() {
    val common = Files.createDirectories(tmp.resolve("main/.git"))
    val wtGitDir = Files.createDirectories(common.resolve("worktrees/feature"))
    Files.writeString(wtGitDir.resolve("commondir"), "../..\n")
    val wt = Files.createDirectories(tmp.resolve("feature"))
    Files.writeString(wt.resolve(".git"), "gitdir: $wtGitDir\n")
    val git = GitCheckouts.resolve(wt)!!
    assertEquals(CheckoutRole.WORKTREE, git.role)
    assertEquals(tmp.resolve("main").toRealPath(), git.basePath)
  }

  @Test
  fun relativeGitDirIsResolvedFromTheRoot() {
    val common = Files.createDirectories(tmp.resolve("main/.git"))
    val wtGitDir = Files.createDirectories(common.resolve("worktrees/rel"))
    Files.writeString(wtGitDir.resolve("commondir"), "../..")
    val wt = Files.createDirectories(tmp.resolve("rel"))
    Files.writeString(wt.resolve(".git"), "gitdir: ../main/.git/worktrees/rel")
    assertEquals(CheckoutRole.WORKTREE, GitCheckouts.resolve(wt)!!.role)
  }

  @Test
  fun submoduleOrNoGitIsNull() {
    val plain = Files.createDirectories(tmp.resolve("plain"))
    assertNull(GitCheckouts.resolve(plain))
    val sub = Files.createDirectories(tmp.resolve("sub"))
    val modules = Files.createDirectories(tmp.resolve("main/.git/modules/sub"))
    Files.writeString(sub.resolve(".git"), "gitdir: $modules")
    assertNull(GitCheckouts.resolve(sub))
  }

  @Test
  fun indexKeepsBaseAndWorktreesPerRepository() {
    val index = PhantomCheckouts(tmp.resolve("system/checkouts.json"))
    index.update("/r/.git", "/r", "b", CheckoutRole.BASE)
    index.update("/r/.git", "/w1", "w1", CheckoutRole.WORKTREE)
    index.update("/r/.git", "/w1", "w1", CheckoutRole.WORKTREE)
    val reread = PhantomCheckouts(tmp.resolve("system/checkouts.json"))
    assertEquals(CheckoutEntry("/r", "b"), reread.of("/r/.git").base)
    assertEquals(listOf(CheckoutEntry("/w1", "w1")), reread.of("/r/.git").worktrees)
    assertEquals("w1", reread.idOf("/w1"))
  }
}
