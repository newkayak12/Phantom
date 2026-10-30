// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.customization.base

import com.intellij.platform.ide.customization.FeedbackReporter
import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls

internal class IntelliJIdeaExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  override val basePatchDownloadUrl: Url
    get() = Urls.newFromEncoded("https://download.jetbrains.com/idea/")

  override val productPageUrl: Url
    get() = baseWebSiteUrl.resolve("idea/")

  override val youtrackProjectId: String
    get() = "IDEA"

  override val shortProductNameUsedInForms: String
    get() = "IDEA"

  // Phantom: no JetBrains-only Help menu entries (bug report, support, feedback, YouTube, keymap PDF, What's New, download page)
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

  override val gettingStartedPageUrl: Url
    get() = baseWebSiteUrl.resolve("idea/resources/")

  override val baseWebHelpUrl: Url
    get() = baseWebSiteUrl.resolve("help/idea/")
}