// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryPlanTest {
  private val heap = "-Xmx"
  private val cache = "-Dfile-page-cache.cache-capacity-mb="

  @Test
  fun `machines get a heap and page cache by their memory`() {
    assertEquals(MemoryPlan.Target(1536, 384), MemoryPlan.forMachine(8 * 1024))
    assertEquals(MemoryPlan.Target(2048, 600), MemoryPlan.forMachine(16 * 1024))
    assertEquals(MemoryPlan.Target(3072, 800), MemoryPlan.forMachine(32 * 1024))
    assertEquals(MemoryPlan.Target(4096, 1024), MemoryPlan.forMachine(48 * 1024))
  }

  @Test
  fun `options missing from the user file are written`() {
    val changes = MemoryPlan.changes(listOf(Setting(heap, null, null) to "4096m", Setting(cache, null, null) to "1024"))
    assertEquals(listOf(heap to "4096m", cache to "1024"), changes)
  }

  @Test
  fun `a value the user set is kept`() {
    // set by hand: differs from what Phantom wrote last time
    assertEquals(emptyList<Pair<String, String>>(), MemoryPlan.changes(listOf(Setting(heap, "6000m", "4096m") to "4096m")))
    // set by hand before Phantom ever wrote one
    assertEquals(emptyList<Pair<String, String>>(), MemoryPlan.changes(listOf(Setting(heap, "1024m", null) to "4096m")))
  }

  @Test
  fun `a value Phantom wrote follows the machine`() {
    // memory upgraded from 16 to 48 GiB
    assertEquals(listOf(heap to "4096m"), MemoryPlan.changes(listOf(Setting(heap, "2048m", "2048m") to "4096m")))
    // already right: nothing to write
    assertEquals(emptyList<Pair<String, String>>(), MemoryPlan.changes(listOf(Setting(heap, "4096m", "4096m") to "4096m")))
  }

  @Test
  fun `the last line of an option wins`() {
    assertEquals("3g", MemoryPlan.lastValue(listOf("-Xms128m", "-Xmx2g", "  -Xmx3g"), heap))
    assertNull(MemoryPlan.lastValue(listOf("-Xms128m"), heap))
  }
}
