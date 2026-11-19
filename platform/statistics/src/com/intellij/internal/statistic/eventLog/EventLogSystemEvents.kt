// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import org.jetbrains.annotations.ApiStatus

// phantom-idea: Phantom's own definitions; the closed library com.jetbrains.fus.reporting:ap-validation-all used to provide this class.

/**
 * Event ids the event log itself records in any group.
 */
@ApiStatus.Internal
object EventLogSystemEvents {
  const val COLLECTOR_REGISTERED: String = "registered"
  const val STATE_COLLECTOR_INVOKED: String = "invoked"
  const val STATE_COLLECTOR_FAILED: String = "invoke.failed"
  const val TOO_MANY_EVENTS: String = "validation.too_many_events"
  const val TOO_MANY_EVENTS_ALERT: String = "validation.too_many_events.alert"

  @JvmField
  val SYSTEM_EVENTS: Set<String> = setOf(
    COLLECTOR_REGISTERED, STATE_COLLECTOR_INVOKED, STATE_COLLECTOR_FAILED, TOO_MANY_EVENTS, TOO_MANY_EVENTS_ALERT,
  )
}
