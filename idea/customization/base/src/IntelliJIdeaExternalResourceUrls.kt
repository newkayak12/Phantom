// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.customization.base

// phantom-idea: SystemInfoRt import replaced by FeedbackReporter for the Help menu overrides below
import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls

internal class IntelliJIdeaExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  // phantom-idea: no JetBrains download or product pages; patches only apply with -Didea.updates.url, which also needs -Didea.patches.url
  override val basePatchDownloadUrl: Url
    get() = Urls.newFromEncoded("https://github.com/newkayak12/Phantom/releases/")

  override val productPageUrl: Url
    get() = Urls.newFromEncoded("https://github.com/newkayak12/Phantom")

  override val youtrackProjectId: String
    get() = "IDEA"

  override val shortProductNameUsedInForms: String
    get() = "IDEA"

  // phantom-idea: no JetBrains-only Help menu entries (bug report, support, feedback, YouTube, keymap PDF, What's New, download page)
  override val bugReportUrl: ((String) -> Url)?
    get() = null

  override val technicalSupportUrl: ((description: String) -> Url)?
    get() = null

  override val feedbackReporter: FeedbackReporter?
    get() = null

  override val downloadPageUrl: Url?
    get() = null

  override val whatIsNewPageUrl: Url?
    get() = null

  override val youTubeChannelUrl: Url?
    get() = null

  override val keyboardShortcutsPdfUrl: Url?
    get() = null

  // phantom-idea: JetBrains' IntelliJ IDEA documentation does not describe Phantom; no Getting Started action and no web help
  override val gettingStartedPageUrl: Url?
    get() = null

  override val baseWebHelpUrl: Url?
    get() = null
}