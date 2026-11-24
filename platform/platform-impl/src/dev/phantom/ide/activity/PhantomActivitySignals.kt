// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.activity

import com.intellij.ide.IdleTracker
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<PhantomActivitySignals>()

/**
 * Publishes [PhantomActivityListener] signals. Idle starts after [idleTimeout] without user input
 * (system property `phantom.activity.idle.minutes`, default 10; 0 turns the signals off).
 */
@ApiStatus.Experimental
@Service(Service.Level.APP)
class PhantomActivitySignals(private val scope: CoroutineScope) {
  private val started = AtomicBoolean()
  private val state = ActivityState()

  val idleTimeout: Duration = (System.getProperty("phantom.activity.idle.minutes")?.toLongOrNull() ?: 10L).minutes

  val isIdle: Boolean get() = state.isIdle

  /** Asks plugins to warm up for [project], e.g. when a workspace is selected. Does nothing unless idle. */
  fun prewarm(project: Project?) {
    if (state.prewarm()) publish { it.prewarm(project) }
  }

  /** Marks [project] dormant (open but hidden); plugins release its heavy state. Repeated calls are ignored. */
  fun dormant(project: Project) {
    if (project.isDisposed || !dormantProjects.add(project)) return
    PhantomLazyActivations.getInstance(project).reset()
    publishProject(project) { it.dormant() }
  }

  /** Ends a [dormant] period of [project]. */
  fun awake(project: Project) {
    if (!dormantProjects.remove(project) || project.isDisposed) return
    publishProject(project) { it.awake() }
  }

  fun isDormant(project: Project): Boolean = project in dormantProjects

  private val dormantProjects: MutableSet<Project> = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.WeakHashMap()))

  private fun publishProject(project: Project, signal: (PhantomProjectActivityListener) -> Unit) {
    scope.launch {
      try {
        if (!project.isDisposed) signal(project.messageBus.syncPublisher(PhantomProjectActivityListener.TOPIC))
      }
      catch (e: Exception) {
        LOG.warn("Project activity signal failed", e)
      }
    }
  }

  internal fun start() {
    if (idleTimeout <= Duration.ZERO || !started.compareAndSet(false, true)) return
    scope.launch {
      IdleTracker.getInstance().events.collectLatest {
        if (state.input()) publish { it.active() }
        delay(idleTimeout)
        if (state.timeout()) publish { it.idle() }
      }
    }
    ApplicationManager.getApplication().messageBus.connect(scope).subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
      override fun applicationActivated(ideFrame: IdeFrame) = prewarm(ideFrame.project)
    })
  }

  private fun publish(signal: (PhantomActivityListener) -> Unit) {
    scope.launch {
      try {
        signal(ApplicationManager.getApplication().messageBus.syncPublisher(PhantomActivityListener.TOPIC))
      }
      catch (e: Exception) {
        LOG.warn("Activity signal failed", e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): PhantomActivitySignals = service()
  }
}

/** Active -> idle on timeout; idle -> prewarmed at most once; idle or prewarmed -> active on input. */
@ApiStatus.Internal
class ActivityState {
  private enum class Phase { ACTIVE, IDLE, PREWARMED }

  @Volatile
  private var phase = Phase.ACTIVE

  val isIdle: Boolean get() = phase != Phase.ACTIVE

  /** @return true when this input ends an idle period */
  @Synchronized
  fun input(): Boolean = (phase != Phase.ACTIVE).also { phase = Phase.ACTIVE }

  /** @return true when the IDE just became idle */
  @Synchronized
  fun timeout(): Boolean = (phase == Phase.ACTIVE).also { if (it) phase = Phase.IDLE }

  /** @return true for the first prewarm of an idle period */
  @Synchronized
  fun prewarm(): Boolean = (phase == Phase.IDLE).also { if (it) phase = Phase.PREWARMED }
}

/** Starts the signals after the first project opens, off the startup path. */
internal class PhantomActivitySignalsStarter : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!ApplicationManager.getApplication().isUnitTestMode) PhantomActivitySignals.getInstance().start()
  }
}
