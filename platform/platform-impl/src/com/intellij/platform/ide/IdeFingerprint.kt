// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.yieldAllDescriptors
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.util.indexing.FileBasedIndexExtension
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

private val fingerprint = lazy { computeIdeFingerprint(debugHelperToken = 0) }

@Internal
fun ideFingerprint(debugHelperToken: Int = 0): IdeFingerprint {
  return if (debugHelperToken == 0) fingerprint.value else computeIdeFingerprint(debugHelperToken = debugHelperToken)
}

/**
 * Fingerprint (=hash) of the current IDE installation.
 * It used to skip the scanning/indexing process altogether if nothing has changed from the previous IDE session.
 *
 * The defining property of the fingerprint is:
 * 'if (fingerprint is unchanged) AND (files on disk are not changed) => it should be no reason to update indexes'.
 * I.e. fingerprint should include all the reasons to update indexes _other_ than file(s) content change.
 *
 * Current algorithm to define the fingerprint (i.e. params to include/not include in the fingerprint) is not
 * fundamental, it is just a heuristical attempt to list all the things that could affect the indexing process.
 * The current list contains plugins versions, app build version, [FileSizeLimit]s used, and so on. The list may
 * need to be extended in the future.
 */
@Internal
@JvmInline
value class IdeFingerprint(private val value: Long) {
  @Throws(NumberFormatException::class)
  constructor(value: String) : this(java.lang.Long.parseUnsignedLong(value, Character.MAX_RADIX))

  fun asString(): String = java.lang.Long.toUnsignedString(value, Character.MAX_RADIX)

  fun asLong(): Long = value

  override fun toString(): String = asString()
}

@Internal
private fun computeIdeFingerprint(debugHelperToken: Int): IdeFingerprint {
  val startTime = System.currentTimeMillis()

  val hasher = Hashing.xxh3_64().hashStream()

  val appInfo = ApplicationInfoImpl.getShadowInstance()
  if (AppMode.isRunningFromDevBuild()) {
    hasher.putBytes(Files.readAllBytes(PathManager.getHomeDir().resolve("fingerprint.txt")))
  }
  else {
    hasher.putLong(appInfo.buildTime.toEpochSecond())
  }
  hasher.putString(appInfo.build.asString())

  // the loadedPlugins list is sorted
  val loadedPlugins = PluginManagerCore.loadedPlugins
  // Phantom: only plugins that can change what or how files are indexed count, so updating, adding or removing any other
  // plugin keeps the fingerprint and the next project open skips the full scan of every file
  val indexingPlugins = loadedPlugins.filter { it.isBundled || affectsIndexing(it) }
  hasher.putInt(indexingPlugins.size)
  // Classpath is too huge to calculate its fingerprint. Dev Mode is a preferred way to run IDE from sources.
  if (PluginManagerCore.isRunningFromSources()) {
    hasher.putLong(System.currentTimeMillis())
  }
  else {
    for (plugin in indexingPlugins) {
      // no need to check bundled plugins - handled by taking build time and version into account
      if (!plugin.isBundled) {
        addPluginFingerprint(plugin = plugin, hasher = hasher)
      }
    }
  }

  //RC: we include IDE/plugins versions AND explicit indexes versions into the fingerprint -- because index.version
  //    could change without code change, by sys-properties or other env conditions change.
  //    E.g., sharding: # shards could be changed with system properties and could change automatically if #CPU changed.
  //MAYBE RC: use com.intellij.util.indexing.FbiSnapshot -- it is an 'index version hash' thing developed for other (but similar) purposes?
  FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList.forEach {
    hasher.putInt(it.version)
  }

  hasher.putInt(debugHelperToken)

  hasher.putInt(FileSizeLimit.getFingerprint())

  val fingerprint = IdeFingerprint(hasher.asLong)

  val durationMs = System.currentTimeMillis() - startTime
  Logger.getInstance("com.intellij.platform.ide.IdeFingerprint")
    .info("Calculated dependencies fingerprint in $durationMs ms " +
          "(hash=${fingerprint.asString()}, buildTime=${appInfo.buildTime.toEpochSecond()}, appVersion=${appInfo.build.asString()}, " +
          "indexing plugins=${indexingPlugins.filter { !it.isBundled }.joinToString { it.pluginId.idString }})")
  return fingerprint
}

/**
 * Extension point name parts that can change which files are indexed or what their indexed data is: indexes and stubs,
 * file types, languages (parsers, word scanners), decompilers, roots and excludes, file property pushers.
 * Deliberately broad: a plugin wrongly counted only costs one full scan after its update, a plugin wrongly left out
 * keeps stale index data.
 */
private val INDEXING_EXTENSION_POINT_PARTS = listOf("index", "stub", "filetype", "parserdefinition", "findusagesprovider",
                                                    "syntaxhighlighter", "ast.factory", "fileviewprovider", "substitutor", "decompiler",
                                                    "roots", "exclude", "pusher", "orderenumeration", "workspacemodel", "gist")

private fun affectsIndexing(plugin: IdeaPluginDescriptor): Boolean {
  if (plugin !is PluginMainDescriptor) return true
  val descriptors = sequence { yieldAllDescriptors(plugin) }
  return descriptors.any { descriptor ->
    descriptor.extensions.keys.any { epName ->
      val name = epName.lowercase()
      INDEXING_EXTENSION_POINT_PARTS.any { name.contains(it) }
    }
  }
}

private fun addPluginFingerprint(plugin: IdeaPluginDescriptor, hasher: HashStream64) {
  hasher.putString(plugin.pluginId.idString)
  hasher.putString(plugin.version)
  if (plugin.version.contains("SNAPSHOT", ignoreCase = true)) {
    hashByFileContent(plugin as IdeaPluginDescriptorImpl, hasher)
  }
}

@OptIn(ExperimentalPathApi::class)
private fun hashByFileContent(descriptor: IdeaPluginDescriptorImpl, hasher: HashStream64) {
  if (descriptor.useCoreClassLoader) {
    hasher.putLong(0)
    return
  }

  ProgressManager.checkCanceled()

  val files = descriptor.ownClassPath!!
  hasher.putInt(files.size)
  for (file in files) {
    // if the path is not a directory, only "this" file will be visited
    // if the path is a directory, all the regular files will be visited
    //  note that symlinks are not followed
    file.walk().sorted().forEach { f ->
      // /tmp/byteBuddyAgent12978532094762450051.jar:1261:2023-09-21T12:46:17.196727952Z <= this file is always different :(
      // see also https://youtrack.jetbrains.com/issue/IJPL-166
      val absolutePathString = f.toAbsolutePath().toString()
      if (!absolutePathString.startsWith("/tmp/byteBuddyAgent")) {
        hashFile(file = f, hasher = hasher, path = absolutePathString)
      }
    }
  }
}

private fun hashFile(file: Path, hasher: HashStream64, path: String) {
  val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
  hasher.putString(path)
  hasher.putLong(attributes.size())
  hasher.putLong(attributes.lastModifiedTime().toMillis())
  //println(DateTimeFormatter.ISO_LOCAL_TIME.format(attributes.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())))
}
