// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Per-project counterpart of [PhantomActivityListener]: a project kept open but out of sight (a background workspace
 * tab) goes [dormant], and [awake] when it is shown again. Fired by whoever hides projects, through
 * [PhantomActivitySignals.dormant] and [PhantomActivitySignals.awake]; on dormant a plugin releases per-project heavy
 * state, and [PhantomLazyActivation.activate] runs again on the next trigger. Published on a background thread.
 * Subscribe with `<projectListeners>`.
 */
@ApiStatus.Experimental
interface PhantomProjectActivityListener {
  fun dormant() {}
  fun awake() {}

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<PhantomProjectActivityListener> = Topic(PhantomProjectActivityListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
