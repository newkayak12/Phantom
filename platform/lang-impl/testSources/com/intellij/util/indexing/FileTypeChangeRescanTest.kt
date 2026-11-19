// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService

/** phantom-idea: a file type event that changes nothing must not invalidate every indexed file. */
class FileTypeChangeRescanTest : BasePlatformTestCase() {
  private val matcher = ExtensionFileNameMatcher("phantomrescantest")
  private var originalIgnored = ""

  private fun token(): Int = service<AppIndexingDependenciesService>().getCurrentTokenInTest().toInt()

  private fun write(action: ThrowableRunnable<Throwable>) = WriteAction.runAndWait(action)

  // what icon plugins do to get their icons repainted
  private fun fireRepaint() = FileTypeManagerEx.getInstanceEx().fireFileTypesChanged()
  private fun emptyChange() = FileTypeManagerEx.getInstanceEx().makeFileTypesChange("nothing changes", EmptyRunnable.getInstance())
  private fun associate() = FileTypeManager.getInstance().associate(PlainTextFileType.INSTANCE, matcher)
  private fun dissociate() = FileTypeManager.getInstance().removeAssociation(PlainTextFileType.INSTANCE, matcher)
  private fun ignoreMore() {
    originalIgnored = FileTypeManager.getInstance().ignoredFilesList
    FileTypeManager.getInstance().ignoredFilesList = "$originalIgnored;phantomRescanTest"
  }
  private fun restoreIgnored() {
    FileTypeManager.getInstance().ignoredFilesList = originalIgnored
  }

  fun testRepaintOnlyEventDoesNotRescan() {
    val before = token()
    write(::fireRepaint)
    write(::fireRepaint)
    assertEquals(before, token())
  }

  /** TextMate and FileTypeOverrider implementations change rules FileTypeManager does not know about. */
  fun testAnnouncedChangeAlwaysRescans() {
    val before = token()
    write(::emptyChange)
    assertTrue(token() > before)
    // and the next bare repaint event is judged against the state after it
    val after = token()
    write(::fireRepaint)
    assertEquals(after, token())
  }

  fun testAssociationChangeStillRescans() {
    val before = token()
    write(::associate)
    try {
      assertTrue("an added association must rescan", token() > before)
    }
    finally {
      write(::dissociate)
    }
  }

  fun testIgnoredFilesChangeStillRescans() {
    val before = token()
    write(::ignoreMore)
    try {
      assertTrue("a changed ignore list must rescan", token() > before)
    }
    finally {
      write(::restoreIgnored)
    }
  }
}
