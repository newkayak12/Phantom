// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import org.jetbrains.annotations.ApiStatus

// phantom-idea: Phantom's own event model. It replaces the classes the closed library
// com.jetbrains.fus.reporting:ap-validation-all provided (no sources, no license); written from how this repository uses them.

/**
 * One recorded usage event: one line of the local event log.
 * [escape] and [escapeExceptData] change the event in place and return it.
 */
@ApiStatus.Internal
data class LogEvent(
  var session: String,
  var build: String,
  var bucket: String,
  val time: Long,
  val group: LogEventGroup,
  var recorderVersion: String,
  val event: LogEventAction,
)

@ApiStatus.Internal
data class LogEventGroup(var id: String, var version: String)

/**
 * [count] is the number of merged successive events; a state event has no count.
 */
@ApiStatus.Internal
data class LogEventAction @JvmOverloads constructor(
  var id: String,
  val state: Boolean = false,
  val data: MutableMap<String, Any> = HashMap(),
  var count: Int = 1,
) {
  fun increment() {
    count++
  }

  fun isEventGroup(): Boolean = count > 1

  fun addData(key: String, value: Any) {
    data[key] = value
  }
}

@ApiStatus.Internal
fun newLogEvent(
  session: String,
  build: String,
  bucket: String,
  time: Long,
  groupId: String,
  groupVersion: String,
  recorderVersion: String,
  eventId: String,
  isState: Boolean = false,
  data: Map<String, Any> = emptyMap(),
  count: Int = 1,
): LogEvent = LogEvent(session, build, bucket, time, LogEventGroup(groupId, groupVersion), recorderVersion,
                       LogEventAction(eventId, isState, HashMap(data), count))

/** Escapes ids, system fields and event data so that every value stays on one line, ASCII and without quotes. */
@ApiStatus.Internal
fun LogEvent.escape(): LogEvent {
  escapeExceptData()
  val escaped = StatisticsEventEscaper.escapeData(event.data)
  event.data.clear()
  event.data.putAll(escaped)
  return this
}

/** Escapes everything but the event data, for recorders whose data is escaped where it is produced. */
@ApiStatus.Internal
fun LogEvent.escapeExceptData(): LogEvent {
  session = StatisticsEventEscaper.escapeEventIdOrFieldValue(session)
  build = StatisticsEventEscaper.escapeEventIdOrFieldValue(build)
  bucket = StatisticsEventEscaper.escapeEventIdOrFieldValue(bucket)
  recorderVersion = StatisticsEventEscaper.escapeEventIdOrFieldValue(recorderVersion)
  group.id = StatisticsEventEscaper.escape(group.id)
  group.version = StatisticsEventEscaper.escapeEventIdOrFieldValue(group.version)
  event.id = StatisticsEventEscaper.escapeEventIdOrFieldValue(event.id)
  return this
}

/**
 * Escaping rules of the event log:
 * - quotes are removed;
 * - non-ASCII characters become `?`;
 * - [escape] (group ids, single words): whitespace becomes `_`;
 * - [escapeEventIdOrFieldValue] (event ids and values): tabs and line breaks become spaces;
 * - in field names, whitespace and `.`, `,`, `:`, `;` become `_`.
 */
@ApiStatus.Internal
object StatisticsEventEscaper {
  private const val FIELD_NAME_SEPARATORS = ".,:;"

  @JvmStatic
  fun escape(str: String): String = transform(str) { if (it.isWhitespace()) '_' else it }

  @JvmStatic
  fun escapeEventIdOrFieldValue(str: String): String = transform(str) { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }

  @JvmStatic
  fun escapeFieldName(str: String): String =
    transform(str) { if (it.isWhitespace() || FIELD_NAME_SEPARATORS.indexOf(it) >= 0) '_' else it }

  /** Escapes field names and string values, also inside lists and nested objects. */
  @JvmStatic
  fun escapeData(data: Map<*, *>): MutableMap<String, Any> {
    val result = LinkedHashMap<String, Any>()
    for ((key, value) in data) {
      if (key == null || value == null) continue
      result[escapeFieldName(key.toString())] = escapeValue(value)
    }
    return result
  }

  private fun escapeValue(value: Any): Any = when (value) {
    is String -> escapeEventIdOrFieldValue(value)
    is Map<*, *> -> escapeData(value)
    is Collection<*> -> value.filterNotNull().map { escapeValue(it) }
    else -> value
  }

  private inline fun transform(str: String, replace: (Char) -> Char): String {
    var builder: StringBuilder? = null
    for ((index, char) in str.withIndex()) {
      val replacement: Char? = when {
        char == '"' || char == '\'' -> null
        char.code > 127 -> '?'
        else -> replace(char)
      }
      if (replacement == char) {
        builder?.append(char)
        continue
      }
      if (builder == null) {
        builder = StringBuilder(str.length).append(str, 0, index)
      }
      if (replacement != null) {
        builder.append(replacement)
      }
    }
    return builder?.toString() ?: str
  }
}
