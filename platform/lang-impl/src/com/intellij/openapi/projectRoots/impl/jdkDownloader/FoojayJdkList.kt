// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// phantom-idea: Phantom's own file, added by the fork (foojay JDK list); not an upstream IntelliJ file
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Path

/**
 * phantom-idea: the JDK list comes from the foojay Disco API (https://api.foojay.io), not from JetBrains' feed.
 *
 * One `/packages` request lists the latest GA build of every major version per vendor, OS and CPU; [toFeed] turns it into
 * the JetBrains feed format, so [JdkListParser], the dialog and the installer read it unchanged. The response has no checksum,
 * so an item's `url` is the package's foojay `/ids/<id>` URL and its `sha256` is empty: [resolveDownload] looks up the
 * vendor's download URL and SHA-256 when the item is installed. The archive's root folder is not listed either:
 * [detectPackageRoot] finds it in the downloaded archive.
 */
@Internal
object FoojayJdkList {
  private const val API = "https://api.foojay.io/disco/v3.0"
  private const val IDS_PREFIX = "$API/ids/"

  /** Vendors whose foojay package info names a SHA-256 (inline or as a checksum file); in the order the dialog lists them. */
  private class Vendor(val vendor: String, val product: String?, val sdkPrefix: String)

  private val VENDORS = linkedMapOf(
    "temurin" to Vendor("Eclipse", "Temurin", "temurin"),
    "zulu" to Vendor("Azul", "Zulu Community™", "azul"),
    "microsoft" to Vendor("Microsoft", "OpenJDK", "ms"),
    "semeru" to Vendor("IBM", "Semeru", "semeru"),
    "graalvm_community" to Vendor("GraalVM", "Community Edition", "graalvm-ce"),
  )

  val FEED_URL: String = "$API/packages?" + (
    VENDORS.keys.map { "distribution=$it" } +
    listOf("operating_system=macos", "operating_system=linux", "operating_system=windows",
           "architecture=x64", "architecture=aarch64", "archive_type=tar.gz", "archive_type=zip",
           "package_type=jdk", "javafx_bundled=false", "release_status=ga", "latest=available", "directly_downloadable=true")
  ).joinToString("&")

  fun isFoojayFeed(feedUrl: String): Boolean = feedUrl.startsWith("$API/")

  fun isFoojayItem(item: JdkItem): Boolean = item.url.startsWith(IDS_PREFIX)

  private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

  private fun os(name: String?) = when (name) {
    "macos" -> "macOS"
    "linux" -> "linux"
    "windows" -> "windows"
    else -> null
  }

  private fun arch(name: String?) = when (name) {
    "x64", "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> null
  }

  /** Converts a foojay `/packages` response into the JetBrains feed format: one feed item per package. */
  fun toFeed(packagesJson: String): JsonObject {
    val packages = JdkListParser.readTree(packagesJson)["result"]?.jsonArray ?: error("`result` element is missing")

    class Pkg(val distribution: String, val major: Int, val os: String, val arch: String, val version: String, val json: JsonObject)

    val selected = packages
      .filterIsInstance<JsonObject>()
      .mapNotNull { p ->
        val distribution = p.str("distribution")?.takeIf { it in VENDORS } ?: return@mapNotNull null
        val os = os(p.str("operating_system")) ?: return@mapNotNull null
        // one archive type per OS, as the JetBrains feed has; musl builds do not run on a usual Linux
        if (p.str("archive_type") != if (os == "windows") "zip" else "tar.gz") return@mapNotNull null
        if (os == "linux" && p.str("lib_c_type") != "glibc") return@mapNotNull null
        if (p.str("package_type") != "jdk" || p.str("release_status") != "ga") return@mapNotNull null
        if ((p["javafx_bundled"] as? JsonPrimitive)?.contentOrNull == "true") return@mapNotNull null
        if ((p["feature"] as? JsonArray)?.isNotEmpty() == true) return@mapNotNull null
        Pkg(distribution = distribution,
            major = (p["major_version"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 8 } ?: return@mapNotNull null,
            os = os,
            arch = arch(p.str("architecture")) ?: return@mapNotNull null,
            // a GA version is <major>.0.<update>...; foojay mislabels some builds (GraalVM's "25.4.4.1")
            version = p.str("java_version")?.substringBefore('+')?.takeIf { it.split('.').getOrNull(1).let { i -> i == null || i == "0" } }
                      ?: return@mapNotNull null,
            json = p)
      }
      // foojay may list several builds of a major version; keep the newest one per vendor, version, OS and CPU
      .groupBy { listOf(it.distribution, it.major, it.os, it.arch) }
      .values
      .map { group -> group.maxWith { a, b -> VersionComparatorUtil.compare(a.version, b.version) } }
      .sortedWith(compareBy<Pkg> { VENDORS.keys.indexOf(it.distribution) }.thenByDescending { it.major })

    return buildJsonObject {
      putJsonArray("jdks") {
        for (p in selected) {
          val vendor = VENDORS.getValue(p.distribution)
          val id = p.json.str("id") ?: continue
          val size = (p.json["size"] as? JsonPrimitive)?.longOrNull ?: continue
          val fileName = p.json.str("filename") ?: continue
          add(buildJsonObject {
            put("vendor", vendor.vendor)
            vendor.product?.let { put("product", it) }
            put("jdk_version_major", p.major)
            put("jdk_version", p.version)
            put("suggested_sdk_name", "${vendor.sdkPrefix}-${p.major}")
            put("default", p.distribution == "temurin")
            put("shared_index_aliases", buildJsonArray {
              add(JsonPrimitive("${vendor.sdkPrefix}-${p.version}"))
              add(JsonPrimitive("${vendor.sdkPrefix}-${p.major}"))
              add(JsonPrimitive(p.version))
              add(JsonPrimitive(p.major.toString()))
            })
            putJsonArray("packages") {
              add(buildJsonObject {
                put("os", p.os)
                put("arch", p.arch)
                put("package_type", if (p.os == "windows") JdkPackageType.ZIP.type else JdkPackageType.TAR_GZ.type)
                put("url", IDS_PREFIX + id)
                put("sha256", "")
                put("archive_size", size)
                put("archive_file_name", fileName)
                put("package_root_prefix", "")
                put("package_to_java_home_prefix", if (p.os == "macOS") "Contents/Home" else "")
                put("unpacked_size", 0)
                put("install_folder_name", "${vendor.sdkPrefix}-${p.version}")
              })
            }
          })
        }
      }
    }
  }

  /** Looks up the vendor's download URL and the SHA-256 of a foojay item. */
  @Throws(IOException::class)
  fun resolveDownload(item: JdkItem): Pair<String, String> {
    val info = try {
      JdkListParser.readTree(HttpRequests.request(item.url).productNameAsUserAgent().readString())["result"]!!.jsonArray[0].jsonObject
    }
    catch (e: IOException) {
      throw e
    }
    catch (t: Throwable) {
      throw IOException("Unexpected package info for ${item.fullPresentationText} from ${item.url}", t)
    }

    val url = info.str("direct_download_uri")?.takeIf { it.isNotBlank() }
              ?: throw IOException("No download URL for ${item.fullPresentationText} from ${item.url}")
    val sha256Pattern = Regex("\\b[0-9a-fA-F]{64}\\b")
    val sha256 = info.str("checksum")?.takeIf { info.str("checksum_type") == "sha256" && sha256Pattern.matches(it) }
                 ?: info.str("checksum_uri")?.takeIf { it.isNotBlank() }?.let { checksumUrl ->
                   sha256Pattern.find(HttpRequests.request(checksumUrl).productNameAsUserAgent().readString())?.value
                 }
                 ?: throw IOException("No SHA-256 checksum is published for ${item.fullPresentationText} ($url)")
    return url to sha256
  }

  /**
   * Finds the folder in [archive] to unpack as the install folder: the folder that holds `bin/java`, less the item's
   * `package_to_java_home_prefix` (`Contents/Home` in a macOS bundle).
   */
  @Throws(IOException::class)
  fun detectPackageRoot(item: JdkItem, archive: Path): String {
    val javaHomes = mutableListOf<List<String>>()
    item.packageType.openDecompressor(archive)
      .entryFilter { entry ->
        val path = entry.name.split('/').filter { it.isNotEmpty() && it != "." }
        if (path.size >= 2 && path[path.size - 2] == "bin" && (path.last() == "java" || path.last() == "java.exe")) {
          javaHomes += path.dropLast(2)
        }
        false // only list the entries, unpack nothing
      }
      .extract(archive.parent)

    val javaHome = javaHomes.minByOrNull { it.size } ?: throw IOException("No bin/java in ${item.archiveFileName}")
    val toJavaHome = item.packageToBinJavaPrefix.split('/').filter { it.isNotEmpty() }
    if (javaHome.takeLast(toJavaHome.size) != toJavaHome) {
      throw IOException("Unexpected layout of ${item.archiveFileName}: Java home ${javaHome.joinToString("/")} is not under ${item.packageToBinJavaPrefix}")
    }
    return javaHome.dropLast(toJavaHome.size).joinToString("/")
  }
}
