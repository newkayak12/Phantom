// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventAction
import com.intellij.internal.statistic.eventLog.LogEventGroup
import com.intellij.internal.statistic.eventLog.escape
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.jetbrains.fus.reporting.api.ValidationResultType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// phantom-idea: events are validated by the rules of the fields they are registered with, not by downloaded metadata
class LocalRulesValidatorTest {
  private class TestValidator : IntellijSensitiveDataValidator("TEST")

  private enum class Kind { FIRST, SECOND }

  private val group = EventLogGroup("test.group", 1, "TEST")
  private val event = group.registerVarargEvent(
    "test.event",
    EventFields.Int("count"),
    EventFields.Boolean("flag"),
    EventFields.Enum<Kind>("kind"),
    EventFields.String("mode", listOf("fast", "slow")),
    EventFields.StringValidatedByInlineRegexp("code", "[a-z]{3}"),
  )

  private fun logEvent(eventId: String, data: Map<String, Any>): LogEvent =
    LogEvent("session", "build", "bucket", 1L, LogEventGroup(group.id, "1"), "1", LogEventAction(eventId, false, HashMap(data)))

  @Test
  fun `accepted values are kept`() {
    val data = mapOf("count" to 42, "flag" to true, "kind" to "SECOND", "mode" to "fast", "code" to "abc")
    val validated = TestValidator().validateEvent(group, logEvent(event.eventId, data))
    assertEquals(data, validated!!.event.data)
  }

  @Test
  fun `rejected values are replaced`() {
    val data = mapOf("count" to "many", "flag" to "maybe", "kind" to "THIRD", "mode" to "/home/user", "code" to "abcd")
    val validated = TestValidator().validateEvent(group, logEvent(event.eventId, data))!!.event.data
    for (key in data.keys) {
      assertEquals(ValidationResultType.REJECTED.description, validated[key], key)
    }
  }

  @Test
  fun `fields without a rule are replaced`() {
    val validated = TestValidator().validateEvent(group, logEvent(event.eventId, mapOf("file_path" to "/home/user/secret.txt")))
    assertEquals(ValidationResultType.UNDEFINED_RULE.description, validated!!.event.data["file_path"])
  }

  @Test
  fun `plugin info is kept`() {
    val data = mapOf("plugin_type" to "PLATFORM", "plugin" to "com.example", "plugin_version" to "1.0")
    val validated = TestValidator().validateEvent(group, logEvent(event.eventId, data))
    assertEquals(data, validated!!.event.data)
  }

  @Test
  fun `events not registered in the group are not recorded`() {
    assertNull(TestValidator().validateEvent(group, logEvent("unknown.event", mapOf("count" to 1))))
  }

  @Test
  fun `groups without registered events are not allowed`() {
    runBlocking {
      assertTrue(TestValidator().isGroupAllowed(group))
      assertFalse(TestValidator().isGroupAllowed(EventLogGroup("empty.group", 1, "TEST")))
    }
  }

  @Test
  fun `escaping keeps one line, ASCII and no quotes`() {
    val escaped = LogEvent("s", "b", "1", 1L, LogEventGroup("test group窗", "1"), "1",
                           LogEventAction("e\"v\ne'nt", false, hashMapOf("my.key:" to "va\tl\"ueļ", "list" to listOf("aļ"))))
      .escape()
    assertEquals("test_group?", escaped.group.id)
    assertEquals("ev ent", escaped.event.id)
    assertEquals(mapOf("my_key_" to "va lue?", "list" to listOf("a?")), escaped.event.data)
  }
}
