package com.intellij.tools.ide.metrics.collector.starter.fus

import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistic.eventLog.LogEvent

fun LogEvent.print(): String {
  return LogEventSerializer.toString(this)
}