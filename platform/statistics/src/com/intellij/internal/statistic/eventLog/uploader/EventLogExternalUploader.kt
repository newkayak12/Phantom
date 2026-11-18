// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.uploader

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.openapi.diagnostic.Logger

/**
 * phantom-idea: Phantom has no statistics sender. Upstream started a separate uploader process here that sent the event
 * log to JetBrains; that process needed the closed library com.jetbrains.fus.reporting:ap-validation-all, which Phantom
 * does not ship. Both entry points do nothing.
 */
object EventLogExternalUploader {
  private val LOG = Logger.getInstance(EventLogExternalUploader.javaClass)

  @Suppress("UNUSED_PARAMETER")
  fun logPreviousExternalUploadResult(providers: List<StatisticsEventLoggerProvider>) {
  }

  @Suppress("UNUSED_PARAMETER")
  fun startExternalUpload(recordersProviders: List<StatisticsEventLoggerProvider>, isTestConfig: Boolean, isTestSendEndpoint: Boolean) {
    LOG.info("Statistics. Phantom does not send usage statistics")
  }
}
