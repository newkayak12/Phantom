// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.icons.java

import com.intellij.ide.IconProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.ui.IconManager
import com.intellij.util.BitUtil
import dev.phantom.icons.core.MaterialIcons
import javax.swing.Icon

/** Which Material icon a Java class gets: by the same kinds the platform tells apart with its C/I/E/@/R icons. */
object JavaKindIcons {
  /** The icon id for a Java class, or null to keep the platform's icon (anything else, anonymous classes, type parameters). */
  fun iconName(element: PsiElement): String? {
    val aClass = element as? PsiClass ?: return null
    if (aClass is PsiTypeParameter || aClass is PsiAnonymousClass || aClass.language != JavaLanguage.INSTANCE) return null
    return when {
      aClass.isAnnotationType -> "java-annotation"
      aClass.isEnum -> "java-enum"
      aClass.isRecord -> "java-record"
      aClass.isInterface -> "java-interface"
      // cached by the platform; tells exceptions apart only outside dumb mode, as the platform's own icon does
      ElementPresentationUtil.getClassKind(aClass) == ElementPresentationUtil.CLASS_KIND_EXCEPTION -> "java-exception"
      // explicit modifier only, like the platform: hasModifierProperty could run augmenters
      aClass.modifierList?.hasExplicitModifier(PsiModifier.ABSTRACT) == true -> "java-abstract"
      else -> "java-class"
    }
  }
}

/**
 * Java classes in the Project view, structure view, navigation bar and editor tabs (a `.java` file takes its class's icon
 * through the Java plugin's file icon patcher). Builds the icon as the platform's class icon does, with only the base
 * swapped, so the static/final/test/runnable marks, the lock and the visibility column stay. Runs where the platform's
 * deferred class icon is evaluated; members keep their platform icons.
 */
internal class JavaKindIconProvider : IconProvider(), DumbAware {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    val name = JavaKindIcons.iconName(element) ?: return null
    val aClass = element as PsiClass
    val locked = BitUtil.isSet(flags, Iconable.ICON_FLAG_READ_STATUS) && !aClass.isWritable
    val row = IconManager.getInstance().createLayeredIcon(aClass, MaterialIcons.icon(name), ElementPresentationUtil.getFlags(aClass, locked))
    return ElementPresentationUtil.addVisibilityIcon(aClass, flags, row)
  }
}
