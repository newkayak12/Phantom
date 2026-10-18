// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext

/**
 * phantom-idea: the build number users see (`PH-<YYMMDD>.<N>`, set by phantom-build.sh as `-Dphantom.build.number`).
 * The platform build number ([BuildContext.buildNumber], `262.<YYMMDD>`) stays what the build tooling and plugin
 * compatibility use: bundled plugins get `since/until-build` from it, and the IDE declares it as its `apiVersion`.
 */
const val PHANTOM_BUILD_NUMBER_PROPERTY: String = "phantom.build.number"

fun phantomBuildNumber(context: BuildContext): String =
  System.getProperty(PHANTOM_BUILD_NUMBER_PROPERTY)?.takeIf { it.isNotBlank() } ?: context.fullBuildNumber
