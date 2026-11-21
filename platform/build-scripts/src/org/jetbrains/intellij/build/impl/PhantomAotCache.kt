// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.Manifest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * phantom-idea: an ahead-of-time cache (JDK 25, JEP 483/515) for the macOS app, `Contents/lib/phantom.aot`. It holds the
 * classes the IDE loads and links while starting and opening a project, so the next starts skip that work: on a 9,878-file
 * project, start to project opened went from 1.88 s with the launcher's dynamic CDS archive to 1.62 s (medians of 5).
 *
 * The cache is made on the build machine: a training run of the built app opens a small generated project
 * (`-XX:AOTMode=record`), and `-XX:AOTMode=create` turns what it recorded into the cache. The JVM accepts the cache when the
 * app sits elsewhere (`/Applications`), as long as the jars are the same. It cannot be used together with the launcher's
 * `-XX:SharedArchiveFile`/`-XX:+AutoCreateSharedArchive`, so an app with the cache gets `-XX:AOTCache` instead of those
 * ([withAotCache]); without one (another OS or architecture than the build machine's, no window server, a failed
 * training run) it keeps them.
 *
 * Set `-Dphantom.aot.cache=false` to skip it.
 */
internal const val PHANTOM_AOT_CACHE: String = "lib/phantom.aot"

internal fun withoutCds(jvmArgs: List<String>): List<String> =
  jvmArgs.filterNot { it.startsWith("-XX:SharedArchiveFile=") || it == "-XX:+AutoCreateSharedArchive" }

internal fun withAotCache(jvmArgs: List<String>): List<String> =
  withoutCds(jvmArgs) + $$"-XX:AOTCache=$APP_PACKAGE/Contents/$$PHANTOM_AOT_CACHE"

/**
 * Trains and creates the cache for the unpacked [appDir] (`<name>.app`) and writes it to [cacheFile]. Returns false, after a
 * warning, when no cache was made. The launcher's CDS arguments cannot go with `-XX:AOTMode=record`, so [appDir] has to carry
 * a product-info.json without them.
 */
internal suspend fun createPhantomAotCache(appDir: Path, cacheFile: Path, arch: JvmArchitecture, workDir: Path, context: BuildContext): Boolean {
  if (!System.getProperty("phantom.aot.cache", "true").toBoolean()) {
    return false
  }
  if (OsFamily.currentOs != OsFamily.MACOS || arch != JvmArchitecture.currentJvmArch) {
    context.messages.warning("No AOT cache for macOS $arch: the build machine cannot run that app")
    return false
  }
  return spanBuilder("create AOT cache").setAttribute("arch", arch.name).use {
    withContext(Dispatchers.IO) {
      try {
        val configuration = workDir.resolve("phantom.aotconf")
        if (!train(appDir, configuration, workDir, context)) {
          return@withContext false
        }
        create(appDir, configuration, cacheFile, arch, context)
        if (Files.size(cacheFile) == 0L) error("empty $cacheFile")
        true
      }
      catch (e: Exception) {
        Files.deleteIfExists(cacheFile)
        context.messages.warning("No AOT cache for macOS $arch, the app keeps the dynamic CDS archive: $e")
        false
      }
    }
  }
}

/** Starts the app on a generated project, waits until the project is open and indexed, and quits it. */
private suspend fun train(appDir: Path, configuration: Path, workDir: Path, context: BuildContext): Boolean {
  val contents = appDir.resolve("Contents")
  val config = workDir.resolve("config").also { it.resolve("options").createDirectories() }
  // no keychain prompt, no exit confirmation
  config.resolve("options/security.xml").writeText(
    """<application><component name="PasswordSafe"><option name="PROVIDER" value="MEMORY_ONLY"/></component></application>""")
  config.resolve("options/ide.general.xml").writeText(
    """<application><component name="GeneralSettings"><option name="confirmExit" value="false"/></component></application>""")
  val project = writeTrainingProject(workDir.resolve("project"))
  val log = workDir.resolve("log")
  val startupReport = workDir.resolve("startup.json")
  val properties = workDir.resolve("idea.properties")
  properties.writeText(
    """
    idea.config.path=$config
    idea.system.path=${workDir.resolve("system")}
    idea.plugins.path=${workDir.resolve("plugins")}
    idea.log.path=$log
    """.trimIndent())
  val vmOptions = workDir.resolve("training.vmoptions")
  vmOptions.writeText(
    listOf(
      "-XX:AOTMode=record",
      "-XX:AOTConfiguration=$configuration",
      "-Didea.log.perf.stats.file=$startupReport",
      "-Didea.trust.all.projects=true",
      "-Dide.trust.all.projects=true",
      "-Djb.consents.confirmation.enabled=false",
      "-Dide.show.tips.on.startup.default.value=false",
      "-Dide.no.platform.update=true",
      "-Dnosplash=true",
    ).joinToString("\n"))

  val envBase = context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)
  val process = ProcessBuilder(contents.resolve("MacOS/${context.productProperties.baseFileName}").toString(), project.toString())
    .redirectErrorStream(true)
    .redirectOutput(workDir.resolve("training.out").toFile())
    .apply {
      environment()["${envBase}_PROPERTIES"] = properties.toString()
      environment()["${envBase}_VM_OPTIONS"] = vmOptions.toString()
    }
    .start()
  try {
    // the startup report is written once the project is open; then give indexing and the post-startup activities time
    var waited = 0.seconds
    while (!startupReport.exists()) {
      if (!process.isAlive) {
        context.messages.warning("No AOT cache: the training run exited with ${process.exitValue()} (see $log)")
        return false
      }
      if (waited > 5.minutes) {
        context.messages.warning("No AOT cache: the training run did not open its project in 5 minutes (see $log)")
        return false
      }
      delay(1.seconds)
      waited += 1.seconds
    }
    delay(30.seconds)
  }
  finally {
    // SIGTERM: the IDE exits normally and the JVM writes the AOT configuration on the way out
    process.destroy()
    if (!process.waitFor(2, TimeUnit.MINUTES)) {
      process.destroyForcibly()
    }
  }
  if (!configuration.exists()) {
    context.messages.warning("No AOT cache: the training run wrote no AOT configuration (see ${workDir.resolve("training.out")})")
    return false
  }
  return true
}

/**
 * `-XX:AOTMode=create` with the classpath and the JVM options the launcher passes, which the cache has to match. Run
 * separately rather than through `-XX:AOTCacheOutput`, whose child JVM aborts when the launcher started the parent.
 */
private suspend fun create(appDir: Path, configuration: Path, cacheFile: Path, arch: JvmArchitecture, context: BuildContext) {
  val contents = appDir.resolve("Contents")
  val vmOptions = contents.resolve("bin/${context.productProperties.baseFileName}.vmoptions").readLines()
    .filter { it.startsWith("-XX:") || it.startsWith("-Xm") }
  val launcherArgs = context.getAdditionalJvmArguments(OsFamily.MACOS, arch)
    .filter { it.startsWith("--add-opens") || it.startsWith("--enable-native-access") || it.startsWith("-Xbootclasspath") }
    .map { it.replace($$"$APP_PACKAGE/Contents", contents.toString()) }
  val classPath = context.bootClassPathJarNames.joinToString(":") { contents.resolve("lib/$it").toString() }
  val java = listOf(contents.resolve("jbr/Contents/Home/bin/java").toString()) + vmOptions + launcherArgs + listOf("-cp", classPath)
  Files.createDirectories(cacheFile.parent)
  context.runProcess(
    args = java + listOf("-XX:AOTMode=create", "-XX:AOTConfiguration=$configuration", "-XX:AOTCache=$cacheFile"),
    timeout = 10.minutes,
  )
  verify(appDir, cacheFile, arch, context)
}

/**
 * Starts the IDE (`--version`) with the cache and `-XX:AOTMode=on` from a copy of [appDir] elsewhere, as the installed app
 * runs from `/Applications`. A cache the JVM rejects (a jar with another size or modification time) is skipped silently, and
 * the IDE then starts with no class data sharing at all, slower than with the CDS archive; a cache that breaks in another
 * location fails while loading its classes. -XX:AOTMode=on turns both into an error here.
 */
private suspend fun verify(appDir: Path, cacheFile: Path, arch: JvmArchitecture, context: BuildContext) {
  val copy = appDir.parent.resolveSibling("relocated").resolve(appDir.fileName)
  Files.createDirectories(copy.parent)
  context.runProcess(args = listOf("cp", "-cR", appDir.toString(), copy.toString()))  // an APFS clone, no extra space
  try {
    val contents = copy.resolve("Contents")
    val vmOptions = contents.resolve("bin/${context.productProperties.baseFileName}.vmoptions").readLines()
      .filter { it.isNotBlank() && !it.startsWith("#") }
    val launcherArgs = withoutCds(context.getAdditionalJvmArguments(OsFamily.MACOS, arch))
      .map { it.replace($$"$APP_PACKAGE/Contents", contents.toString()).replace($$"$APP_PACKAGE", copy.toString()) }
    val classPath = context.bootClassPathJarNames.joinToString(":") { contents.resolve("lib/$it").toString() }
    context.runProcess(
      args = listOf(contents.resolve("jbr/Contents/Home/bin/java").toString()) + vmOptions + launcherArgs +
             listOf("-XX:AOTMode=on", "-XX:AOTCache=$cacheFile", "-cp", classPath, context.ideMainClassName, "--version"),
      timeout = 2.minutes,
    )
  }
  finally {
    context.runProcess(args = listOf("rm", "-rf", copy.parent.toString()))
  }
}

/**
 * The JVM fails to load a cache whose classes come from a sealed package (`Sealed: true`) once the app has moved: "sealing
 * violation: can't seal package com.jetbrains: already defined". Sealing only keeps other jars out of those packages, so the
 * boot classpath jars drop it (intellij.libraries.jbr.jar, intellij.libraries.xz.jar).
 */
internal fun unsealJars(jars: List<Path>) {
  for (jar in jars) {
    if (!Files.isRegularFile(jar)) continue
    FileSystems.newFileSystem(jar).use { zip ->
      val manifestPath = zip.getPath("META-INF/MANIFEST.MF")
      if (!Files.exists(manifestPath)) return@use
      val manifest = Files.newInputStream(manifestPath).use { Manifest(it) }
      val sealed = manifest.mainAttributes.remove(Attributes.Name.SEALED) != null
      var entrySealed = false
      for (attributes in manifest.entries.values) {
        entrySealed = attributes.remove(Attributes.Name.SEALED) != null || entrySealed
      }
      if (sealed || entrySealed) {
        Files.newOutputStream(manifestPath).use { manifest.write(it) }
      }
    }
  }
}

/** A plain Java module with enough sources for indexing, highlighting and the editor to load their classes. */
private fun writeTrainingProject(dir: Path): Path {
  val src = dir.resolve("src/training").createDirectories()
  dir.resolve(".idea").createDirectories()
  dir.resolve(".idea/modules.xml").writeText(
    $$"""<?xml version="1.0" encoding="UTF-8"?>
<project version="4"><component name="ProjectModuleManager"><modules><module fileurl="file://$PROJECT_DIR$/training.iml" filepath="$PROJECT_DIR$/training.iml" /></modules></component></project>
""")
  dir.resolve("training.iml").writeText(
    $$"""<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4"><component name="NewModuleRootManager"><content url="file://$MODULE_DIR$"><sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" /></content><orderEntry type="sourceFolder" forTests="false" /></component></module>
""")
  for (i in 1..200) {
    src.resolve("Service$i.java").writeText(
      """
      package training;

      import java.util.ArrayList;
      import java.util.List;
      import java.util.Map;
      import java.util.stream.Collectors;

      /** Generated for the AOT training run. */
      public class Service$i {
        private final List<String> names = new ArrayList<>();

        public void add(String name) {
          if (name != null && !name.isBlank()) names.add(name.trim());
        }

        public Map<Integer, List<String>> byLength() {
          return names.stream().collect(Collectors.groupingBy(String::length));
        }

        @Override
        public String toString() {
          return "Service$i" + names;
        }
      }
      """.trimIndent())
  }
  dir.resolve("README.md").writeText("# Training project\n\nOpened by the build to record the AOT cache.\n")
  return dir
}
