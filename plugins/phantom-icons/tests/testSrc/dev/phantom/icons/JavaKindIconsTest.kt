// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.icons

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.DeferredIcon
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CompositeIcon
import dev.phantom.icons.core.MaterialIcons
import dev.phantom.icons.java.JavaKindIcons
import javax.swing.Icon

class JavaKindIconsTest : LightJavaCodeInsightFixtureTestCase() {
  companion object {
    init {
      System.setProperty("idea.load.plugins.id", "dev.phantom.icons,com.intellij.java")
      // as in the IDE: real icons (not 1-px stand-ins) and the icon manager that adds the static/final marks;
      // before any test, so the mark layers Java registers on first use land in the real manager
      IconLoader.activate()
      IconManager.activate(null)
    }

    private val KINDS = listOf("class", "abstract", "interface", "enum", "record", "annotation", "exception").map { "java-$it" }
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

  fun testExceptionInDumbModeIsAPlainClass() {
    val file = myFixture.configureByText("Failure.java", "public class Failure extends RuntimeException {}") as PsiJavaFile
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      assertEquals("java-class", JavaKindIcons.iconName(file.classes.single()))
    }
  }

  fun testMarksAndVisibilityStay() {
    val file = myFixture.configureByText("Outer.java", "class Outer { public static final class Inner {} }") as PsiJavaFile
    val inner = file.classes.single().innerClasses.single()
    val leaves = leaves(inner.getIcon(Iconable.ICON_FLAG_VISIBILITY))
    val icons = IconManager.getInstance()
    assertTrue("kind in $leaves", JavaKindIcons.icon("java-class") in leaves)
    assertTrue("static mark in $leaves", icons.getPlatformIcon(PlatformIcons.StaticMark) in leaves)
    assertTrue("final mark in $leaves", icons.getPlatformIcon(PlatformIcons.FinalMark) in leaves)
    assertTrue("public in $leaves", icons.getPlatformIcon(PlatformIcons.Public) in leaves)
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
    val generic = MaterialIcons.forFile(file.virtualFile)!!
    val leaves = leaves(file.getIcon(0))
    assertTrue("generic java in $leaves", generic in leaves)
  }

  fun testProviderComesRightAfterTheMaterialProvider() {
    val names = IconProvider.EXTENSION_POINT_NAME.extensionList.map { it.javaClass.simpleName }
    val material = names.indexOf("MaterialPsiIconProvider")
    val java = names.indexOf("JavaKindIconProvider")
    assertTrue(names.toString(), material >= 0 && java > material)
  }

  fun testEveryKindIconLoadsAtSixteenPixels() {
    for (name in KINDS) assertEquals(name, 16, JavaKindIcons.icon(name).iconWidth)
  }

  /** The class icon (Project view, structure view) and the file icon (editor tabs) both show the kind. */
  private fun assertKind(expected: String, fileName: String, text: String) {
    val file = myFixture.configureByText(fileName, text) as PsiJavaFile
    val aClass = file.classes.single()
    assertEquals(fileName, expected, JavaKindIcons.iconName(aClass))
    val icon = JavaKindIcons.icon(expected)
    val classLeaves = leaves(aClass.getIcon(0))
    assertTrue("$expected in $classLeaves", icon in classLeaves)
    val fileLeaves = leaves(file.getIcon(0))
    assertTrue("$expected in $fileLeaves", icon in fileLeaves)
  }

  /** The tests run on the EDT, where element icons come deferred (the file icon patcher defers the class icon again). */
  private fun leaves(icon: Icon?): List<Icon> {
    val result = ArrayList<Icon>()
    fun collect(icon: Icon?) {
      when (icon) {
        null -> {}
        is DeferredIcon -> collect(icon.evaluate())
        is RetrievableIcon -> collect(icon.retrieveIcon())
        is CompositeIcon -> for (i in 0 until icon.iconCount) collect(icon.getIcon(i))
        else -> result.add(icon)
      }
    }
    collect(icon)
    return result
  }
}
