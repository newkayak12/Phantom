// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.memory

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.name

private val LOG = logger<PhantomCdsArchiveCleaner>()

/**
 * Deletes the class data sharing archives of earlier builds. The launcher names the archive after the build
 * (-XX:SharedArchiveFile=<cache dir>/idea262.261116.jsa, see enableCds in IdeaCommunityProperties), so each update
 * leaves the previous one, about 370 MB, behind in the cache directory.
 */
internal class PhantomCdsArchiveCleaner : ProjectActivity {
  override suspend fun execute(project: Project) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || !ran.compareAndSet(false, true)) return
    val current = currentArchive() ?: return
    withContext(Dispatchers.IO) { deleteOthers(current) }
  }

  companion object {
    private val ran = AtomicBoolean()

    private fun currentArchive(): Path? =
      ManagementFactory.getRuntimeMXBean().inputArguments.lastOrNull { it.startsWith(OPTION) }
        ?.removePrefix(OPTION)?.let { Path.of(it).toAbsolutePath() }

    private fun deleteOthers(current: Path) {
      val dir = current.parent ?: return
      val siblings = try { Files.list(dir).use { it.toList() } } catch (_: IOException) { return }
      for (old in CdsArchives.stale(current, siblings)) {
        try {
          Files.deleteIfExists(old)
          LOG.info("Deleted the class data sharing archive of an earlier build: $old")
        }
        catch (e: IOException) {
          LOG.warn("Cannot delete $old", e)
        }
      }
    }

    private const val OPTION = "-XX:SharedArchiveFile="
  }
}

@ApiStatus.Internal
object CdsArchives {
  /** Archives next to [current] whose name differs only in the build: same leading letters, `.jsa`. */
  fun stale(current: Path, siblings: List<Path>): List<Path> {
    val prefix = current.name.takeWhile { !it.isDigit() }
    if (prefix.isEmpty()) return emptyList()
    return siblings.filter { it.name != current.name && it.name.startsWith(prefix) && it.name.endsWith(".jsa") &&
                             it.name.removePrefix(prefix).firstOrNull()?.isDigit() == true }
  }
}
