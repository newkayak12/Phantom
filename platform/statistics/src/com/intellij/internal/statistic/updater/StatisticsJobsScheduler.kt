// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.StatisticsNotificationManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import dev.phantom.statistics.PhantomUsageStatistics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

/**
 * phantom-idea: Phantom has no statistics sender and validates events by the rules declared in code, so there are
 * no send jobs and no validation rules to download from JetBrains' metadata server.
 */
@InternalIgnoreDependencyViolation
internal class StatisticsJobsScheduler : ApplicationActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    serviceAsync<StatisticsValidationUpdatedService>().updatedDeferred.complete(Unit)

    // the consent notification only matters when usage statistics are collected
    if (!PhantomUsageStatistics.enabled) {
      return
    }
    delay(10.seconds)
    serviceAsync<StatisticsNotificationManager>().showNotificationIfNeeded()
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class StatisticsValidationUpdatedService {
  val updatedDeferred: CompletableDeferred<Unit> = CompletableDeferred()
}
