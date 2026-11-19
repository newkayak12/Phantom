// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRuleFactory
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.fus.reporting.api.FUSRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * The data from all collectors is validated before it's recorded locally.
 * It's necessary to make sure that the data is correct and it doesn't contain personal or proprietary information.
 *
 * phantom-idea: Phantom validates events only by the rules declared in code, that is by the `validationRule` of the
 * [EventField]s each event is registered with in its [EventLogGroup]. Upstream validated by rules downloaded from
 * JetBrains' metadata server through the JetBrains library (no published sources) com.jetbrains.fus.reporting:ap-validation-all, which Phantom
 * does not ship. A value that no rule accepts is replaced with the [ValidationResultType] description; an event whose
 * id is not registered in its group is not recorded.
 *
 * There are 3 types of rules:
 * 1. **Enum**: a list of possible values, e.g.
 *    `{enum:started|finished}` checks that the value is equal to 'started' or 'finished'.
 * 2. **Regexp**: e.g. `{regexp#integer}` checks that the value is integer, `{regexp:\d+}` uses the given regexp.
 * 3. **Custom rule**: class which inherits [CustomValidationRule] and validates dynamic data like action id or file type, e.g.
 *    `{util#class_name}` checks that the value is a class name from platform, JetBrains plugin or a plugin from JetBrains Marketplace.
 *    See: [com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator]
 *
 * Named enums and regexps (`{enum#boolean}`, `{regexp#integer}`) are defined in [EventGroupContextData].
 */
open class IntellijSensitiveDataValidator protected constructor(private val recorderId: String) {
  companion object {
    private val instances = ConcurrentHashMap<String, IntellijSensitiveDataValidator>()

    private val PLUGIN_INFO_FIELDS = setOf("plugin", "plugin_type", "plugin_version")
    private val CONTEXT_DATA = EventGroupContextData()

    private val extensionListeners = lazy {
      CustomValidationRule.EP_NAME.addChangeListener({ instances.clear() }, null)
      CustomValidationRuleFactory.EP_NAME.addChangeListener({ instances.clear() }, null)
    }

    @JvmStatic
    fun clearInstances() {
      instances.clear()
    }

    @JvmStatic
    fun getInstance(recorderId: String): IntellijSensitiveDataValidator {
      extensionListeners.value
      return instances.computeIfAbsent(recorderId) { id ->
        if (ApplicationManager.getApplication().isUnitTestMode) BlindSensitiveDataValidator(id) else IntellijSensitiveDataValidator(id)
      }
    }

    @JvmStatic
    fun getIfInitialized(recorderId: String): IntellijSensitiveDataValidator? = instances[recorderId]
  }

  private val customRules = ConcurrentHashMap<String, Array<FUSRule>>()
  private val patterns = ConcurrentHashMap<String, Pattern>()

  open suspend fun isGroupAllowed(group: EventLogGroup): Boolean {
    return StatisticsRecorderUtil.isTestModeEnabled(recorderId) || group.events.isNotEmpty()
  }

  /**
   * @return the event with its data validated, or null if the event must not be recorded
   */
  open fun validateEvent(group: EventLogGroup, event: LogEvent): LogEvent? {
    if (StatisticsRecorderUtil.isTestModeEnabled(recorderId)) {
      return event
    }
    val eventId = event.event.id
    val fields = group.events.firstOrNull { it.eventId == eventId }?.getFields()
                 ?: if (eventId in EventLogSystemEvents.SYSTEM_EVENTS) group.groupData.map { it.first } else return null
    val context = EventContext.create(eventId, event.event.data)
    val validated = validateObject(event.event.data, fields, context)
    val pluginInfo = context.getPayload(CustomValidationRule.PLUGIN_INFO)
    if (pluginInfo != null && PLUGIN_INFO_FIELDS.none { validated.containsKey(it) }) {
      StatisticsUtil.addPluginInfoTo(pluginInfo, validated)
    }
    event.event.data.clear()
    event.event.data.putAll(validated)
    return event
  }

  private fun validateObject(data: Map<*, *>, fields: List<EventField<*>>, context: IEventContext): MutableMap<String, Any> {
    val result = HashMap<String, Any>()
    for ((key, value) in data) {
      if (key == null || value == null) continue
      val name = key.toString()
      val field = fields.firstOrNull { it.name == name }
      result[name] = when {
        name in PLUGIN_INFO_FIELDS -> value
        field == null -> ValidationResultType.UNDEFINED_RULE.description
        else -> validateValue(value, field, context)
      }
    }
    return result
  }

  private fun validateValue(value: Any, field: EventField<*>, context: IEventContext): Any {
    return when (field) {
      is ObjectEventField -> (value as? Map<*, *>)?.let { validateObject(it, field.fields.asList(), context) }
                             ?: ValidationResultType.INCORRECT_RULE.description
      is ObjectListEventField -> (value as? Collection<*>)?.map { item ->
        (item as? Map<*, *>)?.let { validateObject(it, field.fields.asList(), context) } ?: ValidationResultType.INCORRECT_RULE.description
      } ?: ValidationResultType.INCORRECT_RULE.description
      is ListEventField<*> -> (value as? Collection<*>)?.map { item -> validateString(item.toString(), field.validationRule, context) }
                              ?: validateString(value.toString(), field.validationRule, context)
      is PrimitiveEventField<*> -> validateString(value, field.validationRule, context)
      else -> ValidationResultType.UNDEFINED_RULE.description
    }
  }

  /**
   * @return [value] if a rule accepts it, the description of the validation result otherwise
   */
  private fun validateString(value: Any, rules: List<String>, context: IEventContext): Any {
    val data = value.toString()
    var result = ValidationResultType.UNDEFINED_RULE
    for (rule in rules) {
      val ruleResult = validateByRule(rule, data, context)
      if (ruleResult == ValidationResultType.ACCEPTED) return value
      if (ruleResult.isFinal) return ruleResult.description
      if (ruleResult == ValidationResultType.THIRD_PARTY || result == ValidationResultType.UNDEFINED_RULE) result = ruleResult
    }
    return result.description
  }

  private fun validateByRule(rule: String, data: String, context: IEventContext): ValidationResultType {
    if (!rule.startsWith("{") || !rule.endsWith("}")) {
      return ValidationResultType.UNDEFINED_RULE
    }
    val body = rule.substring(1, rule.length - 1)
    return when {
      body.startsWith("enum:") -> accepted(data in body.removePrefix("enum:").split('|'))
      body.startsWith("enum#") -> CONTEXT_DATA.getEnumValidationRule(body.removePrefix("enum#")).validate(data, context)
      body.startsWith("regexp:") -> accepted(pattern(body.removePrefix("regexp:"))?.matcher(data)?.matches() ?: false)
      body.startsWith("regexp#") -> CONTEXT_DATA.getRegexpValidationRule(body.removePrefix("regexp#")).validate(data, context)
      body.startsWith("util#") -> validateByCustomRule(body.removePrefix("util#"), data, context)
      else -> ValidationResultType.UNDEFINED_RULE
    }
  }

  private fun validateByCustomRule(ruleId: String, data: String, context: IEventContext): ValidationResultType {
    val rules = customRules.computeIfAbsent(ruleId, ::findCustomRules)
    var result = ValidationResultType.UNDEFINED_RULE
    for (rule in rules) {
      val ruleResult = rule.validate(data, context)
      if (ruleResult == ValidationResultType.ACCEPTED || ruleResult.isFinal) return ruleResult
      result = ruleResult
    }
    return result
  }

  private fun findCustomRules(ruleId: String): Array<FUSRule> {
    val testMode = StatisticsRecorderUtil.isTestModeEnabled(recorderId)
    val rules = ArrayList<FUSRule>()
    for (rule in CustomValidationRule.EP_NAME.extensionList) {
      if ((testMode || rule !is TestModeValidationRule) && isDevelopedByJetBrains(rule.javaClass) && rule.acceptRuleId(ruleId)) {
        rules.add(rule)
      }
    }
    for (factory in CustomValidationRuleFactory.EP_NAME.extensionList) {
      if (isDevelopedByJetBrains(factory.javaClass) && factory.ruleId == ruleId) {
        rules.add(factory.createValidator(CONTEXT_DATA))
      }
    }
    return rules.toTypedArray()
  }

  private fun isDevelopedByJetBrains(clazz: Class<*>): Boolean =
    ApplicationManager.getApplication().isUnitTestMode || getPluginInfo(clazz).isDevelopedByJetBrains()

  private fun pattern(regexp: String): Pattern? =
    try {
      patterns.computeIfAbsent(regexp) { Pattern.compile(it) }
    }
    catch (_: IllegalArgumentException) {
      null
    }

  private fun accepted(accepted: Boolean): ValidationResultType =
    if (accepted) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}

private class BlindSensitiveDataValidator(recorderId: String) : IntellijSensitiveDataValidator(recorderId) {
  override fun validateEvent(group: EventLogGroup, event: LogEvent): LogEvent = event

  override suspend fun isGroupAllowed(group: EventLogGroup): Boolean = true
}
