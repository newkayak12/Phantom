// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path

/**
 * phantom-idea: the bundled vmoptions enable `-XX:+UseCompactObjectHeaders` ([VmOptionsGenerator]), and with that flag the JVM
 * only maps the base CDS archive `lib/server/classes_coh.jsa`. The JBR builds Phantom bundles ship `classes.jsa` and
 * `classes_nocoops.jsa` but no `classes_coh.jsa`, so the IDE ran without class data sharing, and the launcher's
 * `-XX:+AutoCreateSharedArchive` (dynamic AppCDS, [BuildContextImpl]) never created its archive, since that needs a base one.
 * Creates the archive with the same `-Xshare:dump` the JDK build uses for its default archives. The build has to run the
 * bundled `java`, so this only happens when the target is the build machine's own OS and architecture.
 */
internal suspend fun createCompactHeadersCdsArchive(runtimeDir: Path, arch: JvmArchitecture, context: BuildContext) {
  if (OsFamily.currentOs != OsFamily.MACOS || arch != JvmArchitecture.currentJvmArch) {
    context.messages.warning("No classes_coh.jsa for macOS $arch: the build machine cannot run that runtime")
    return
  }
  val home = runtimeDir.resolve("jbr/Contents/Home")
  if (Files.exists(home.resolve("lib/server/classes_coh.jsa"))) {
    return
  }
  spanBuilder("create compact object headers CDS archive").setAttribute("arch", arch.name).use {
    context.runProcess(args = listOf(home.resolve("bin/java").toString(), "-XX:+UseCompactObjectHeaders", "-Xshare:dump"))
  }
  check(Files.exists(home.resolve("lib/server/classes_coh.jsa"))) { "-Xshare:dump did not create classes_coh.jsa in $home" }
}
