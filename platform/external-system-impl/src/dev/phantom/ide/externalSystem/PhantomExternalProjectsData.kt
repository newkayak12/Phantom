// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.externalSystem

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.createCacheReadConfiguration
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.project.getProjectDataPathRoot
import com.intellij.serialization.VersionedFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<PhantomExternalProjectsData>()

/** Read-only access to the external projects data another checkout stored (its `project.dat`). */
@ApiStatus.Internal
object PhantomExternalProjectsData {
  /** The external configuration dir of the project opened at [projectRoot], as [com.intellij.openapi.project.getExternalConfigurationDir] names it. */
  fun configurationDir(projectRoot: Path): Path = getProjectDataPathRoot(projectRoot).resolve("external_build_system")

  fun dataFile(configurationDir: Path): Path = configurationDir.resolve("project.dat")

  /**
   * The projects stored in [configurationDir], or null when there is no usable file: absent, older than the
   * invalidation marker, or of another storage version. The file is never renamed or deleted.
   */
  fun read(configurationDir: Path): List<InternalExternalProjectInfo>? {
    val file = dataFile(configurationDir)
    return try {
      if (!Files.isRegularFile(file)) return null
      val lastModified = Files.getLastModifiedTime(file).toMillis()
      val brokenMarker = PathManager.getSystemDir().resolve("external_build_system").resolve(".broken")
      if (lastModified == 0L || Files.exists(brokenMarker) && lastModified < Files.getLastModifiedTime(brokenMarker).toMillis()) return null
      VersionedFile(file, ExternalProjectsDataStorage.STORAGE_VERSION)
        .readList(InternalExternalProjectInfo::class.java, createCacheReadConfiguration(LOG), false)
    }
    catch (e: Exception) {
      LOG.info("Cannot read $file: $e")
      null
    }
  }
}
