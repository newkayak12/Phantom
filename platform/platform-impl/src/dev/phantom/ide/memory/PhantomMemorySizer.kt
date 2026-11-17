// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.memory

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Pair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<PhantomMemorySizer>()

/**
 * Sizes the heap and the file page cache for the machine instead of the fixed 2 GiB / 600 MiB: a laptop with 8 GiB
 * gets less, a workstation with 32 GiB or more gets room for big projects without GC churn. The values go to the
 * user's .vmoptions file and apply from the next start. A value the user set themselves (Help | Change Memory
 * Settings, or by hand) is never touched: only a value that is absent or that this class wrote last time is updated.
 */
internal class PhantomMemorySizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || !ran.compareAndSet(false, true) || !VMOptions.canWriteOptions()) return
    withContext(Dispatchers.IO) { adjust() }
  }

  private fun adjust() {
    val physicalMb = physicalMemoryMb() ?: return
    val properties = PropertiesComponent.getInstance()
    val target = MemoryPlan.forMachine(physicalMb)
    // only the user's file: the bundled one always has -Xmx, and that is the value to replace
    val userLines = VMOptions.getUserOptionsFile()?.takeIf { Files.exists(it) }?.let { Files.readAllLines(it, VMOptions.getFileCharset()) } ?: emptyList()
    val heap = Setting(HEAP_OPTION, MemoryPlan.lastValue(userLines, HEAP_OPTION), properties.getValue(HEAP_KEY))
    val cache = Setting(PAGE_CACHE_OPTION, MemoryPlan.lastValue(userLines, PAGE_CACHE_OPTION), properties.getValue(CACHE_KEY))
    val changes = MemoryPlan.changes(listOf(heap to "${target.heapMb}m", cache to "${target.pageCacheMb}"))
    if (changes.isEmpty()) return
    try {
      VMOptions.setOptions(changes.map { Pair(it.first, it.second) })
    }
    catch (e: IOException) {
      LOG.warn("Cannot write memory settings to ${VMOptions.getUserOptionsFile()}", e)
      return
    }
    properties.setValue(HEAP_KEY, "${target.heapMb}m")
    properties.setValue(CACHE_KEY, "${target.pageCacheMb}")
    LOG.info("Memory for this machine (${physicalMb / 1024} GiB): heap ${target.heapMb} MiB, file page cache ${target.pageCacheMb} MiB" +
             " (${changes.joinToString { it.first + it.second }}); applies from the next start")
  }

  private fun physicalMemoryMb(): Long? =
    (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize?.let { it shr 20 }?.takeIf { it > 0 }

  companion object {
    private val ran = AtomicBoolean()
    private val HEAP_OPTION = VMOptions.MemoryKind.HEAP.option
    private const val PAGE_CACHE_OPTION = "-Dfile-page-cache.cache-capacity-mb="
    private const val HEAP_KEY = "phantom.memory.auto.heap"
    private const val CACHE_KEY = "phantom.memory.auto.page.cache"
  }
}

/** An option as it is now in the user's .vmoptions file, and the value Phantom wrote there last time. */
@ApiStatus.Internal
data class Setting(val option: String, val current: String?, val lastAuto: String?)

@ApiStatus.Internal
object MemoryPlan {
  data class Target(val heapMb: Int, val pageCacheMb: Int)

  fun forMachine(physicalMb: Long): Target {
    val gb = physicalMb / 1024.0
    return when {
      gb <= 8.5 -> Target(1536, 384)
      gb <= 16.5 -> Target(2048, 600)
      gb <= 32.5 -> Target(3072, 800)
      else -> Target(4096, 1024)
    }
  }

  /** The value of the last line starting with [prefix]: the one the launcher uses. */
  fun lastValue(lines: List<String>, prefix: String): String? =
    lines.map { it.trim() }.lastOrNull { it.startsWith(prefix) }?.substring(prefix.length)

  /** The options to write: those the user has not set themselves and whose value differs from the target. */
  fun changes(settings: List<kotlin.Pair<Setting, String>>): List<kotlin.Pair<String, String>> =
    settings.filter { (setting, value) -> (setting.current == null || setting.current == setting.lastAuto) && setting.current != value }
      .map { (setting, value) -> setting.option to value }
}
