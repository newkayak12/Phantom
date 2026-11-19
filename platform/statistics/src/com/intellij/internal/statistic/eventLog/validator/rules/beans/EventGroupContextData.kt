// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.beans

import com.jetbrains.fus.reporting.api.FUSRule
import com.jetbrains.fus.reporting.api.ValidationResultType
import java.util.regex.Pattern

// phantom-idea: Phantom's own implementation; the closed library com.jetbrains.fus.reporting:ap-validation-all used to provide this class.
// The named enums and regexps were downloaded from JetBrains' metadata server; Phantom defines the ones its code refers to.

/**
 * Named enums (`{enum#name}`) and regexps (`{regexp#name}`) that event fields and custom rules refer to.
 * An unknown name gives a rule that reports [ValidationResultType.UNDEFINED_RULE].
 */
class EventGroupContextData @JvmOverloads constructor(
  private val enums: Map<String, Set<String>> = BUILT_IN_ENUMS,
  regexps: Map<String, String> = BUILT_IN_REGEXPS,
) {
  private val patterns: Map<String, Pattern> = regexps.mapValues { Pattern.compile(it.value) }

  fun getEnumValidationRule(name: String): FUSRule {
    val values = enums[name] ?: return UNDEFINED
    return FUSRule { data, _ -> if (data in values) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED }
  }

  fun getRegexpValidationRule(name: String): FUSRule {
    val pattern = patterns[name] ?: return UNDEFINED
    return FUSRule { data, _ -> if (pattern.matcher(data).matches()) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED }
  }

  companion object {
    private val UNDEFINED = FUSRule { _, _ -> ValidationResultType.UNDEFINED_RULE }

    val BUILT_IN_ENUMS: Map<String, Set<String>> = mapOf(
      "boolean" to setOf("true", "false", "TRUE", "FALSE", "True", "False"),
    )

    val BUILT_IN_REGEXPS: Map<String, String> = mapOf(
      "integer" to "-?\\d+(\\+)?",
      "float" to "-?\\d+(\\.\\d+)?([eE]-?\\d+)?",
      "version" to "\\d+(\\.\\d+){0,5}",
      "hash" to "[0-9a-fA-F]{8,128}",
      "short_hash" to "[0-9a-fA-F]{4,16}",
      "date_short_hash" to "\\d{2}[01]\\d[0-3]\\d-[0-9a-fA-F]{4,16}",
    )
  }
}
