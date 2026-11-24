// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivityStateTest {
  @Test
  fun inputWhileActiveSendsNothing() {
    val state = ActivityState()
    assertFalse(state.input())
    assertFalse(state.isIdle)
  }

  @Test
  fun timeoutMakesItIdleOnce() {
    val state = ActivityState()
    assertTrue(state.timeout())
    assertFalse(state.timeout())
    assertTrue(state.isIdle)
  }

  @Test
  fun prewarmOnlyOncePerIdlePeriod() {
    val state = ActivityState()
    assertFalse(state.prewarm())
    state.timeout()
    assertTrue(state.prewarm())
    assertFalse(state.prewarm())
    assertTrue(state.isIdle)
  }

  @Test
  fun inputAfterIdleOrPrewarmBecomesActive() {
    val state = ActivityState()
    state.timeout()
    assertTrue(state.input())
    state.timeout()
    state.prewarm()
    assertTrue(state.input())
    assertFalse(state.isIdle)
  }
}
