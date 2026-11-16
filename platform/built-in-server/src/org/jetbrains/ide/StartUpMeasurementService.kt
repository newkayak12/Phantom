// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.diagnostic.StartUpPerformanceService
// phantom-idea: import for the jetBrainsServicesEnabled gate below
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.util.io.origin
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.io.response

internal class StartUpMeasurementService : RestService() {
  override fun getServiceName() = "startUpMeasurement"

  override fun isOriginAllowed(request: HttpRequest): OriginCheckResult {
    // phantom-idea: JetBrains' ij-perf pages may read the local start-up report only with -Dphantom.jetbrains.services=true;
    // otherwise only the default origin check applies (local origins)
    if (!MarketplaceRequests.jetBrainsServicesEnabled) return super.isOriginAllowed(request)
    return when (request.origin) {
      "https://ij-perf.jetbrains.com", "https://ij-perf.labs.jb.gg" -> OriginCheckResult.ALLOW
      else -> super.isOriginAllowed(request)
    }
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val reporter = StartUpPerformanceService.getInstance()
    val lastReport = reporter.getLastReport() ?: return """{"error": "Report is not ready yet, start-up in progress"}"""
    val response = response("application/json", Unpooled.wrappedBuffer(lastReport))
    sendResponse(request, context, response)
    return null
  }
}