// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.newui.PluginDtoModelBuilderFactory
import dev.phantom.ide.plugins.GitHubPluginRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GitHubPluginRepositoryTest {
  @Test
  fun `accepts every spelling of a GitHub repository`() {
    assertEquals("newkayak12/phantom-plugins", GitHubPluginRepository.normalize("newkayak12/phantom-plugins"))
    assertEquals("newkayak12/phantom-plugins", GitHubPluginRepository.normalize(" github:newkayak12/phantom-plugins "))
    assertEquals("newkayak12/phantom-plugins", GitHubPluginRepository.normalize("https://github.com/newkayak12/phantom-plugins.git"))
    assertEquals("a/b@dev", GitHubPluginRepository.normalize("https://github.com/a/b/tree/dev"))
    assertEquals("a/b.c@feature/x", GitHubPluginRepository.normalize("a/b.c@feature/x"))
  }

  @Test
  fun `leaves plugin repository URLs alone`() {
    assertNull(GitHubPluginRepository.normalize("https://plugins.example.com/updatePlugins.xml"))
    assertNull(GitHubPluginRepository.normalize("plugins.example.com/repo"))
    assertNull(GitHubPluginRepository.normalize("file:///tmp/plugins.xml"))
  }

  @Test
  fun `metadata URL follows the branch`() {
    assertEquals("https://raw.githubusercontent.com/a/b/HEAD/.phantom/marketplace.json", GitHubPluginRepository.parse("a/b")!!.metadataUrl)
    assertEquals("https://raw.githubusercontent.com/a/b/dev/.phantom/marketplace.json", GitHubPluginRepository.parse("a/b@dev")!!.metadataUrl)
  }

  @Test
  fun `reads plugins from marketplace json`() {
    val json = """
      { "name": "phantom-plugins",
        "plugins": [
          { "id": "dev.phantom.db", "name": "Phantom DB", "version": "0.2.0", "source": "./db",
            "description": "SQL console", "category": "Database", "since": "262", "until": "262.*",
            "depends": ["com.intellij.modules.platform"], "download": "db-0.2.0/phantom-db-0.2.0.zip",
            "detect": ["org.postgresql:postgresql"] },
          { "id": "x.y", "download": "https://example.com/x.zip" },
          { "name": "no id, skipped", "download": "a/b.zip" } ] }
    """.trimIndent()
    val ref = GitHubPluginRepository.parse("newkayak12/phantom-plugins")!!
    val models = GitHubPluginRepository.parseMetadata(json.byteInputStream(), ref, PluginDtoModelBuilderFactory)
    assertEquals(listOf("dev.phantom.db", "x.y"), models.map { it.pluginId.idString })

    val db = models[0]
    assertEquals("Phantom DB", db.name)
    assertEquals("0.2.0", db.version)
    assertEquals("https://github.com/newkayak12/phantom-plugins/releases/download/db-0.2.0/phantom-db-0.2.0.zip", db.downloadUrl)
    assertEquals("262", db.sinceBuild)
    assertEquals("https://example.com/x.zip", models[1].downloadUrl)
    assertEquals("newkayak12", models[1].vendor)

    assertEquals(mapOf("dev.phantom.db" to listOf("org.postgresql:postgresql")), GitHubPluginRepository.parseDetect(json.byteInputStream()))
  }

  @Test(expected = IOException::class)
  fun `rejects metadata without a plugins array`() {
    GitHubPluginRepository.parseMetadata("{}".byteInputStream(), GitHubPluginRepository.parse("a/b")!!, PluginDtoModelBuilderFactory)
  }

  @Test
  fun `only the Phantom repository is official`() {
    assertTrue(GitHubPluginRepository.isOfficial("newkayak12/phantom-plugins"))
    assertTrue(GitHubPluginRepository.isOfficial("https://github.com/newkayak12/phantom-plugins"))
    assertFalse(GitHubPluginRepository.isOfficial("someone/phantom-plugins"))
    assertFalse(GitHubPluginRepository.isOfficial(null))
  }
}
