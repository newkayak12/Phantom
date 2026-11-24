// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Activity signals for plugins that keep heavy state (caches, models, browser panels). The IDE only sends the
 * signals; a plugin decides what to release. Plugins stay loaded: on [idle] a plugin drops references so the
 * garbage collector can reclaim the heap, and rebuilds lazily on [prewarm] or [active].
 *
 * Subscribe declaratively so nothing is loaded before the first signal:
 * ```xml
 * <applicationListeners>
 *   <listener class="my.plugin.MyActivityListener" topic="dev.phantom.ide.activity.PhantomActivityListener"/>
 * </applicationListeners>
 * ```
 * On IDEs without this topic, put the listener into an optional `<depends>` on the module `dev.phantom.modules.activity`.
 *
 * Signals are published on a background thread; listeners must return quickly and move real work to their own
 * coroutine scope. The order is always idle, then optionally prewarm, then active.
 */
@ApiStatus.Experimental
interface PhantomActivityListener {
  /** No user input in the IDE for [PhantomActivitySignals.idleTimeout]. Release what can be rebuilt. */
  fun idle() {}

  /** Likely to be used soon (the IDE window regained focus, or a workspace was selected): start rebuilding in the background. */
  fun prewarm(project: Project?) {}

  /** The first user input after [idle]. */
  fun active() {}

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<PhantomActivityListener> = Topic(PhantomActivityListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
