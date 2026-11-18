// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.fus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher

/**
 * phantom-idea: Phantom does not bundle JetBrains' feature usage statistics metadata. Upstream downloaded it here with the
 * JetBrains library (no published sources) com.jetbrains.fus.reporting:ap-validation-all; Phantom validates events by the rules declared in code.
 */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
internal fun CoroutineScope.createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher: ModuleOutputPatcher,
                                                                                context: BuildContext): Job? = null
