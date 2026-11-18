// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules

import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

// phantom-idea: Phantom's own implementation; the closed library com.jetbrains.fus.reporting:ap-validation-all used to provide this class.

/**
 * Base class of rules implemented in code: subclasses implement [doValidate].
 * A rule that repeatedly takes too long is switched off and reports [ValidationResultType.PERFORMANCE_ISSUE],
 * so that a slow rule cannot slow down event logging.
 */
abstract class PerformanceCareRule {
  @Volatile
  private var slowValidations = 0

  fun validate(data: String, context: IEventContext): ValidationResultType {
    if (slowValidations >= MAX_SLOW_VALIDATIONS) {
      return ValidationResultType.PERFORMANCE_ISSUE
    }
    val start = System.nanoTime()
    val result = doValidate(data, context)
    if (System.nanoTime() - start > SLOW_VALIDATION_NANOS) {
      slowValidations++
    }
    return result
  }

  protected abstract fun doValidate(data: String, context: IEventContext): ValidationResultType

  private companion object {
    const val MAX_SLOW_VALIDATIONS = 10
    const val SLOW_VALIDATION_NANOS = 50_000_000L
  }
}
