// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class CdsArchivesTest {
  private val dir = Path.of("/cache")

  @Test
  fun `archives of other builds are stale, everything else is kept`() {
    val current = dir.resolve("idea262.261116.jsa")
    val siblings = listOf("idea262.261116.jsa", "idea262.261113.jsa", "idea261.261025.jsa", "phantom-cds-test.jsa",
                          "ideaX.jsa", "idea262.261113.log", "index").map(dir::resolve)
    assertEquals(listOf("idea262.261113.jsa", "idea261.261025.jsa").map(dir::resolve), CdsArchives.stale(current, siblings))
  }

  @Test
  fun `an archive name without a prefix deletes nothing`() {
    val current = dir.resolve("262.jsa")
    assertEquals(emptyList<Path>(), CdsArchives.stale(current, listOf(dir.resolve("261.jsa"))))
  }
}
