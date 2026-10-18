// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.icons

import com.intellij.ide.FileIconProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.phantom.icons.core.KotlinFileNavBarIcon
import dev.phantom.icons.core.MaterialIcons

class MaterialIconsTest : BasePlatformTestCase() {
  companion object {
    init {
      System.setProperty("idea.load.plugins.id", "dev.phantom.icons")
    }
  }

  fun testFileNamesAndExtensions() {
    assertEquals("gradle", MaterialIcons.iconName("build.gradle.kts"))
    assertEquals("gradle", MaterialIcons.iconName("libs.versions.toml"))
    assertEquals("yaml", MaterialIcons.iconName("application-local.yml"))
    assertEquals("docker", MaterialIcons.iconName("Dockerfile"))
    assertEquals("readme", MaterialIcons.iconName("README.md"))
    assertEquals("http", MaterialIcons.iconName("api.http"))
    assertEquals("tune", MaterialIcons.iconName(".env"))
    assertEquals("database", MaterialIcons.iconName("V1__init.sql"))
    assertEquals("file", MaterialIcons.iconName("notes.zzz"))
  }

  fun testJavaKeepsClassIconsKotlinShowsLogo() {
    assertNull(MaterialIcons.iconName("Main.java"))
    assertEquals("kotlin", MaterialIcons.iconName("UserService.kt"))
    assertEquals("kotlin", MaterialIcons.iconName("script.main.kts"))
  }

  fun testFolders() {
    val root = myFixture.tempDirFixture.findOrCreateDir("")
    assertEquals("folder-src", MaterialIcons.folderIconName(root, project))
    assertEquals("folder-docs", MaterialIcons.folderIconName(myFixture.tempDirFixture.findOrCreateDir("docs"), project))
    assertEquals("folder-config", MaterialIcons.folderIconName(myFixture.tempDirFixture.findOrCreateDir("Config"), project))
    assertEquals("folder", MaterialIcons.folderIconName(myFixture.tempDirFixture.findOrCreateDir("reservation"), project))
  }

  fun testProvidersAnswerFirst() {
    val psiFile = myFixture.configureByText("build.gradle.kts", "")
    assertTrue(IconProvider.EXTENSION_POINT_NAME.extensionList.first().javaClass.simpleName == "MaterialPsiIconProvider")
    assertTrue(FileIconProvider.EP_NAME.extensionList.first().javaClass.simpleName == "MaterialFileIconProvider")
    assertNotNull(IconProvider.EXTENSION_POINT_NAME.extensionList.first().getIcon(psiFile, 0))
    assertNotNull(IconProvider.EXTENSION_POINT_NAME.extensionList.first().getIcon(psiFile.containingDirectory, 0))
  }

  fun testEveryMappedIconLoadsAtSixteenPixels() {
    val text = MaterialIcons::class.java.getResource("/icons/material/mapping.txt")!!.readText()
    val icons = text.lines().filter { it.isNotBlank() && !it.startsWith('#') }
      .flatMap { line -> line.split(' ').let { if (it[0] == "default") it.drop(1) else listOf(it[2]) } }.toSet()
    assertTrue(icons.size > 500)
    for (name in icons + listOf("folder-src", "folder-test", "folder-resource", "folder-packages")) {
      val icon = IconLoader.findIcon("/icons/material/$name.svg", MaterialIcons::class.java.classLoader)
      assertNotNull(name, icon)
      assertEquals(name, 16, icon!!.iconWidth)
    }
  }

  fun testKotlinFileGetsTheLogoInTheNavigationBar() {
    val kt = myFixture.configureByText("Listener.kt", "class Listener")
    assertNotNull(KotlinFileNavBarIcon().getIcon(kt))
    val java = myFixture.configureByText("Main.java", "class Main {}")
    assertNull(KotlinFileNavBarIcon().getIcon(java))
    assertNull(KotlinFileNavBarIcon().getIcon("not psi"))
  }
}
