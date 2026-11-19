// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.statistics

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

/**
 * Phantom collects no usage statistics unless `-Dphantom.usage.statistics=true` is set.
 *
 * When the flag is off, no event logger provider is active: events are not validated, logged or written.
 * When it is on, events are validated by the rules declared in code and written to the local event log only;
 * Phantom has no sender, so nothing leaves the machine.
 *
 * Unit tests behave as if the flag were on, so that the collectors' own tests keep working.
 */
@ApiStatus.Internal
object PhantomUsageStatistics {
  const val PROPERTY: String = "phantom.usage.statistics"

  @JvmStatic
  val enabled: Boolean
    get() = java.lang.Boolean.getBoolean(PROPERTY) || ApplicationManager.getApplication()?.isUnitTestMode == true
}
