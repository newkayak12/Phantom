// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.icons.CompositeIcon
import dev.phantom.icons.core.MaterialIcons
import dev.phantom.icons.java.JavaKindIcons
import javax.swing.Icon

class JavaKindIconsTest : LightJavaCodeInsightFixtureTestCase() {
  companion object {
    init {
      System.setProperty("idea.load.plugins.id", "dev.phantom.icons,com.intellij.java")
    }
  }

  fun testEachKindGetsItsIcon() {
    assertKind("java-class", "Main.java", "public class Main {}")
    assertKind("java-abstract", "Shape.java", "public abstract class Shape {}")
    assertKind("java-interface", "Repo.java", "public interface Repo {}")
    assertKind("java-enum", "Color.java", "public enum Color { RED }")
    assertKind("java-record", "Point.java", "public record Point(int x, int y) {}")
    assertKind("java-annotation", "Marker.java", "public @interface Marker {}")
    assertKind("java-exception", "Oops.java", "public class Oops extends RuntimeException {}")
  }

  fun testMembersAndOtherFilesKeepTheirIcons() {
    val file = myFixture.configureByText("Main.java", "class Main { int count; void run() {} }") as PsiJavaFile
    val aClass = file.classes.single()
    assertNull(JavaKindIcons.iconName(aClass.methods.single()))
    assertNull(JavaKindIcons.iconName(aClass.fields.single()))
    assertNull(JavaKindIcons.iconName(file))
    assertNull(JavaKindIcons.iconName(myFixture.configureByText("notes.txt", "class Main {}")))
    assertEquals("gradle", MaterialIcons.iconName("build.gradle.kts"))
  }

  fun testFileWithoutAClassGetsTheGenericJavaIcon() {
    val file = myFixture.configureByText("package-info.java", "package demo;") as PsiJavaFile
    assertEmpty(file.classes)
    assertContainsIcon("java", file.getIcon(0))
  }

  fun testProviderIsRegistered() {
    assertTrue(IconProvider.EXTENSION_POINT_NAME.extensionList.any { it.javaClass.simpleName == "JavaKindIconProvider" })
  }

  /** The class icon (Project view, structure view) and the file icon (editor tabs) both show the kind. */
  private fun assertKind(expected: String, fileName: String, text: String) {
    val file = myFixture.configureByText(fileName, text) as PsiJavaFile
    val aClass = file.classes.single()
    assertEquals(fileName, expected, JavaKindIcons.iconName(aClass))
    assertContainsIcon(expected, aClass.getIcon(0))
    assertContainsIcon(expected, file.getIcon(0))
  }

  private fun assertContainsIcon(name: String, icon: Icon?) {
    val leaves = ArrayList<Icon>()
    fun collect(icon: Icon?) {
      when (icon) {
        null -> {}
        is CompositeIcon -> for (i in 0 until icon.iconCount) collect(icon.getIcon(i))
        else -> leaves.add(icon)
      }
    }
    collect(icon)
    assertTrue("$name in $leaves", MaterialIcons.icon(name) in leaves)
  }
}
