// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package dev.phantom.ide.plugins

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.auth.PluginRepositoryAuthProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import dev.phantom.ide.PhantomIdeBundle
import org.jetbrains.annotations.ApiStatus

/**
 * The GitHub token Phantom uses for private plugin repositories, looked up in this order:
 * the IDE password safe (set with "Set GitHub Token for Plugin Repositories"), `GH_TOKEN` / `GITHUB_TOKEN`,
 * and `gh auth token` when the GitHub CLI is logged in. Public repositories are read without a token.
 */
@ApiStatus.Internal
object GitHubTokens {
  private val LOG = logger<GitHubTokens>()
  private val ATTRIBUTES = CredentialAttributes(generateServiceName("Phantom", "GitHub plugin repositories"))

  @Volatile private var cli: Lookup? = null

  private class Lookup(val token: String?)

  /** Never call on the EDT: the password safe and the GitHub CLI may block. */
  @JvmStatic
  fun token(): String? {
    PasswordSafe.instance.getPassword(ATTRIBUTES)?.takeIf { it.isNotBlank() }?.let { return it }
    for (name in listOf("GH_TOKEN", "GITHUB_TOKEN")) {
      System.getenv(name)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return (cli ?: Lookup(fromGitHubCli()).also { cli = it }).token
  }

  fun store(token: String?) {
    PasswordSafe.instance.set(ATTRIBUTES, token?.takeIf { it.isNotBlank() }?.let { Credentials("token", it) })
  }

  private fun fromGitHubCli(): String? = try {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine("gh", "auth", "token").withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE), 5_000)
    output.stdout.trim().takeIf { output.exitCode == 0 && it.isNotEmpty() }
  }
  catch (e: Exception) {
    LOG.debug("GitHub CLI is not available: ${e.message}")
    null
  }
}

/**
 * Adds the GitHub token to GitHub API requests for the configured GitHub plugin repositories, so private ones can be
 * listed and downloaded. The token goes only to `api.github.com/repos/<owner>/<repo>/` of a configured repository;
 * the signed download URL GitHub redirects to gets no header, because the platform asks again for every redirect.
 */
internal class GitHubPluginRepositoryAuthProvider : PluginRepositoryAuthProvider {
  override fun canHandle(url: String): Boolean {
    if (!url.startsWith(GitHubPluginRepository.API_REPOS)) return false
    val path = url.removePrefix(GitHubPluginRepository.API_REPOS).split('/')
    if (path.size < 3) return false
    val repo = "${path[0]}/${path[1]}"
    return RepositoryHelper.getCustomPluginRepositoryHosts().any { GitHubPluginRepository.parse(it)?.let { ref -> "${ref.owner}/${ref.repo}".equals(repo, ignoreCase = true) } == true }
  }

  override fun getAuthHeaders(url: String): Map<String, String> {
    val token = GitHubTokens.token() ?: return emptyMap()
    val accept = when {
      "/releases/assets/" in url -> "application/octet-stream"
      "/contents/" in url -> "application/vnd.github.raw+json"
      else -> "application/vnd.github+json"
    }
    return mapOf("Authorization" to "Bearer $token", "Accept" to accept, "X-GitHub-Api-Version" to "2022-11-28")
  }
}

internal class SetGitHubPluginTokenAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val token = Messages.showPasswordDialog(
      e.project,
      PhantomIdeBundle.message("phantom.plugins.token.dialog.message"),
      PhantomIdeBundle.message("phantom.plugins.token.dialog.title"),
      null,
    ) ?: return
    GitHubTokens.store(token)
  }
}
