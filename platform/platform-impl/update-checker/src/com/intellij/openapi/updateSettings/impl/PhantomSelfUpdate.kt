// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

/**
 * phantom-idea: updates the macOS app in place from a Phantom GitHub release. Phantom releases carry no patches for IntelliJ's
 * updater, so this downloads the release's DMG instead and starts a script that waits for the IDE to quit, swaps the app
 * bundle for the DMG's copy and opens the app again. The script runs on its own, not through the restarter: the restarter
 * copy it would need (it runs from the bundle being replaced) is a new binary to Gatekeeper, which blocked it. Settings, plugins, caches and logs live outside the bundle
 * and stay. The DMG is fetched by the IDE, not a browser, so it carries no quarantine flag and Gatekeeper lets the (not
 * notarized) app start. Windows still opens the release page.
 */
@ApiStatus.Internal
object PhantomSelfUpdate {
  private val LOG = logger<PhantomSelfUpdate>()
  private val RELEASE_PAGE = Regex("""^https://github\.com/([\w.-]+/[\w.-]+)/releases/tag/([\w.-]+)$""")

  /** The app bundle this IDE runs from, when it can replace itself: macOS on Apple silicon, restart capable, a writable folder. */
  @JvmStatic
  fun appBundle(): Path? {
    if (OS.CURRENT != OS.macOS || !CpuArch.isArm64() || !ApplicationManager.getApplication().isRestartCapable) return null
    val app = PathManager.getHomeDir().parent ?: return null  // the home directory is <app>/Contents
    val folder = app.parent ?: return null
    return app.takeIf { it.fileName.toString().endsWith(".app") && Files.isWritable(folder) && Files.isWritable(it) }
  }

  /** True when [build] comes from a Phantom GitHub release this IDE can install itself (its Download button is the release page). */
  @JvmStatic
  fun canUpdate(build: BuildInfo): Boolean = appBundle() != null && release(build) != null

  private fun release(build: BuildInfo): Pair<String, String>? =
    build.downloadUrl?.let { RELEASE_PAGE.matchEntire(it) }?.destructured?.let { (repo, tag) -> repo to tag }

  /** Downloads the release DMG in the background, then restarts the IDE through the script that swaps the app. */
  @JvmStatic
  fun downloadAndRestart(project: Project?, build: BuildInfo) {
    val app = appBundle() ?: return
    val (repo, tag) = release(build) ?: return
    object : Task.Backgroundable(project, IdeBundle.message("update.preparing"), true) {
      override fun run(indicator: ProgressIndicator) {
        val command = try {
          val dmg = download(repo, tag, indicator)
          listOf("/usr/bin/nohup", "/bin/zsh", writeScript(dmg.parent).toString(), dmg.toString(), app.toString(),
                 ProcessHandle.current().pid().toString())
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          LOG.warn("Cannot download Phantom $tag", e)
          val title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().fullProductName)
          UpdateChecker.getNotificationGroupForIdeUpdateResults()
            .createNotification(title, IdeBundle.message("update.downloading.patch.error", e.message ?: e.toString()), NotificationType.ERROR)
            .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("update.downloading.patch.open")) { BrowserUtil.browse(build.downloadUrl!!) })
            .setDisplayId("phantom.self.update.failed")
            .notify(project)
          return
        }
        val application = ApplicationManager.getApplication() as ApplicationEx
        application.invokeLater {
          // the script waits for this process to end and then reopens the app
          ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of(command[3]).resolveSibling("update.log").toFile()))
            .start()
          application.exit(ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE)
        }
      }
    }.queue()
  }

  private fun download(repo: String, tag: String, indicator: ProgressIndicator): Path {
    val release = Json.parseToJsonElement(
      HttpRequests.request("https://api.github.com/repos/$repo/releases/tags/$tag").productNameAsUserAgent().readString(indicator)
    ).jsonObject
    val asset: JsonObject = release["assets"]?.jsonArray?.map { it.jsonObject }
                              ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith("-aarch64.dmg") == true }
                            ?: throw IOException("release $tag has no macOS DMG")
    val name = asset["name"]!!.jsonPrimitive.content
    val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: throw IOException("no download URL for $name")
    // not under the temp directory, which the IDE may clear while a download runs (a retry lost its DMG that way)
    val dir = Files.createDirectories(Path.of(PathManager.getSystemPath(), "phantom-update"))
    val dmg = dir.resolve(name)
    val part = dir.resolve("$name.part")
    indicator.text = name
    HttpRequests.request(url).productNameAsUserAgent().saveToFile(part, indicator)
    Files.move(part, dmg, StandardCopyOption.REPLACE_EXISTING)
    // GitHub publishes each asset's sha256 as "digest": "sha256:<hex>"
    asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?.let { expected ->
      if (!sha256(dmg).equals(expected, ignoreCase = true)) {
        Files.deleteIfExists(dmg)
        throw IOException("$name does not match the sha256 GitHub lists for it")
      }
    }
    return dmg
  }

  private fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** The script that waits for the IDE to quit, then mounts the DMG, copies its app next to ours, swaps, cleans up and reopens it. */
  private fun writeScript(dir: Path): Path {
    val script = dir.resolve("phantom-update.sh")
    Files.writeString(script, SCRIPT)
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"))
    return script
  }

  // $$: the shell variables in the script are not Kotlin string templates
  private val SCRIPT = $$"""
    |#!/bin/zsh
    |# Phantom self-update (PhantomSelfUpdate): waits for the IDE (pid $3) to quit, replaces the app bundle with the DMG's
    |# copy and opens whichever app is then in place; on any failure the old app stays.
    |set -u
    |dmg="$1"; app="$2"; pid="$3"
    |logfile="${dmg:h}/update.log"
    |log() { print -r -- "$(date '+%F %T') $*" >> "$logfile" }
    |for _ in {1..600}; do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    |if kill -0 "$pid" 2>/dev/null; then log "the IDE did not quit; not updating"; exit 0; fi
    |mnt="$(mktemp -d)"
    |if ! hdiutil attach -nobrowse -readonly -noverify -mountpoint "$mnt" "$dmg" -quiet; then
    |  log "cannot mount $dmg"; rmdir "$mnt"; exit 0
    |fi
    |src=("$mnt"/*.app(N))
    |new="${app:r}.new.app"; old="${app:r}.old.app"
    |rm -rf "$new" "$old"
    |# a launch while the bundle is swapped loads classes from both bundles and crashes: the lock beside the app makes it
    |# quit at once (dev.phantom.bootstrap.PhantomUpdateLock, ignored after 120 s), and the swap waits for launches already
    |# past that check to end
    |lock="${app:h}/.${app:t}.updating"
    |running() { pgrep -f -- "$app/Contents/" >/dev/null }
    |if (( ${#src} == 1 )) && ditto "${src[1]}" "$new"; then
    |  xattr -dr com.apple.quarantine "$new" 2>/dev/null
    |  touch "$lock"
    |  for _ in {1..40}; do running || break; sleep 0.5; done
    |  if running; then
    |    log "$app is running again; not updating"
    |  elif mv "$app" "$old"; then
    |    if mv "$new" "$app"; then
    |      rm -rf "$old"; log "updated $app from ${dmg:t}"
    |    else
    |      mv "$old" "$app"; log "cannot move the new app into place; kept the old one"
    |    fi
    |  else
    |    log "cannot move $app aside; kept it"
    |  fi
    |else
    |  log "cannot copy the app out of ${dmg:t}"
    |fi
    |rm -f "$lock"
    |rm -rf "$new"
    |hdiutil detach "$mnt" -quiet; rmdir "$mnt" 2>/dev/null
    |rm -f "$dmg"
    |open "$app"
    |exit 0
    |""".trimMargin()
}
