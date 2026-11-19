// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

// phantom-idea: Phantom's own implementation; the closed library com.jetbrains.fus.reporting:ap-validation-all used to provide this interface.

/**
 * A rule referenced from an event field as `{util#<rule id>}`.
 */
interface UtilValidationRule {
  fun acceptRuleId(ruleId: String?): Boolean
}
