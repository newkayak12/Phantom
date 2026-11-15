// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.platform.feedback.impl.FeedbackRequestData
import com.intellij.platform.feedback.impl.FeedbackRequestType
import com.intellij.platform.feedback.impl.submitFeedback
import com.intellij.testFramework.junit5.TestApplication
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val GATE_PROPERTY = "phantom.jetbrains.services"
private const val URL_OVERRIDE_PROPERTY = "platform.feedback.test.submission.url"

/**
 * Phantom: feedback forms (this plugin's onboarding form among them) must not reach JetBrains' feedback service
 * unless `-Dphantom.jetbrains.services=true`. The submission URL is pointed at a local server, so a broken gate
 * shows up as a request on that server or as a callback, instead of a call to forms-service.jetbrains.com.
 * The platform feedback module has no test root of its own; this module depends on it directly.
 * Limit: without the gate the request is preceded by a region-mapping fetch that can be slow, so the wait for the
 * request is long; a mapping fetch slower than that would still let a broken gate pass.
 */
@TestApplication
class FeedbackSubmissionGateTest {
  private lateinit var server: HttpServer
  private val requestReceived = CountDownLatch(1)
  private val savedProperties = HashMap<String, String?>()

  @BeforeEach
  fun setUp() {
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      requestReceived.countDown()
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    server.start()
    overrideProperty(GATE_PROPERTY, null)
    overrideProperty(URL_OVERRIDE_PROPERTY, "http://127.0.0.1:${server.address.port}/feedback")
  }

  @AfterEach
  fun tearDown() {
    server.stop(0)
    for ((name, value) in savedProperties) {
      if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
  }

  @Test
  fun `feedback is not submitted while JetBrains services are off`() {
    for (type in listOf(FeedbackRequestType.TEST_REQUEST, FeedbackRequestType.PRODUCTION_REQUEST)) {
      val callbackRan = CountDownLatch(1)
      submitFeedback(FeedbackRequestData("phantom_gate_test", buildJsonObject { put("request_type", type.name) }),
                     onDone = { callbackRan.countDown() },
                     onError = { callbackRan.countDown() },
                     feedbackRequestType = type)
      assertFalse(callbackRan.await(2, TimeUnit.SECONDS), "a submission callback ran for $type, so the request was attempted")
    }
    assertFalse(requestReceived.await(15, TimeUnit.SECONDS), "the feedback request reached the server")
  }

  private fun overrideProperty(name: String, value: String?) {
    savedProperties[name] = System.getProperty(name)
    if (value == null) System.clearProperty(name) else System.setProperty(name, value)
  }
}
