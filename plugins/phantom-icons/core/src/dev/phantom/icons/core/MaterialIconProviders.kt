// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.icons.core

import com.intellij.ide.FileIconProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/** Editor tabs, Recent Files, Search Everywhere and other places that ask for a [VirtualFile]'s icon. */
internal class MaterialFileIconProvider : FileIconProvider, DumbAware {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? =
    if (file.isDirectory) MaterialIcons.forFolder(file, project) else MaterialIcons.forFile(file)
}

/** The Project view, which asks for PSI icons; runs before the language providers that would show plain folders. */
internal class MaterialPsiIconProvider : IconProvider(), DumbAware {
  override fun getIcon(element: PsiElement, flags: Int): Icon? = when (element) {
    is PsiDirectory -> MaterialIcons.forFolder(element.virtualFile, element.project)
    is PsiFile -> element.virtualFile?.let(MaterialIcons::forFile)
    else -> null
  }
}
