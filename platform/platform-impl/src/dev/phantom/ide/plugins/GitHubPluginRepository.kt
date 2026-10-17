// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.plugins

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelBuilderFactory
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.intellij.ide.plugins.marketplace.setHeadersViaTuner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream

/**
 * A plugin repository kept in a GitHub repository, entered as `owner/repo` (optionally `owner/repo@branch`,
 * or the github.com URL) in Settings | Plugins | Manage Plugin Repositories.
 *
 * The repository describes its plugins in `.phantom/marketplace.json` on that branch:
 * ```
 * { "name": "phantom-plugins",
 *   "plugins": [ { "id": "dev.phantom.db", "name": "Phantom DB", "version": "0.1.0", "source": "db",
 *                  "description": "...", "vendor": "Phantom", "category": "Database",
 *                  "since": "262", "until": "262.*", "depends": ["com.intellij.modules.platform"],
 *                  "download": "db-0.1.0/phantom-db-0.1.0.zip",
 *                  "detect": ["org.postgresql:postgresql"] } ] }
 * ```
 * `download` is either an https URL or `<release tag>/<asset name>` of the same repository's GitHub Releases.
 * `detect` lists library coordinates whose presence in a project makes the IDE suggest the plugin.
 */
@ApiStatus.Internal
object GitHubPluginRepository {
  /** The Phantom plugins every Phantom IDE knows about; its plugins skip the third-party plugins notice. */
  const val OFFICIAL: String = "newkayak12/Phantom-plugins"

  const val METADATA_PATH: String = ".phantom/marketplace.json"

  /** GitHub API requests for a repository start with this; only they carry a token (see [GitHubPluginRepositoryAuthProvider]). */
  const val API_REPOS: String = "https://api.github.com/repos/"

  private val LOG = logger<GitHubPluginRepository>()

  private val SHORT = Regex("""^(?:github:)?([A-Za-z0-9-]+)/([\w.-]+?)(?:\.git)?(?:@([\w./-]+))?/?$""")
  private val WEB = Regex("""^https?://(?:www\.)?github\.com/([A-Za-z0-9-]+)/([\w.-]+?)(?:\.git)?(?:/tree/([\w./-]+))?/?$""")

  class Ref(val owner: String, val repo: String, val branch: String?) {
    val host: String get() = "$owner/$repo" + (branch?.let { "@$it" } ?: "")
    val metadataUrl: String get() = "https://raw.githubusercontent.com/$owner/$repo/${branch ?: "HEAD"}/$METADATA_PATH"
    /** The same file through the API, which can read private repositories with a token. */
    val apiMetadataUrl: String get() = "$API_REPOS$owner/$repo/contents/$METADATA_PATH" + (branch?.let { "?ref=$it" } ?: "")
    val apiReleasesUrl: String get() = "$API_REPOS$owner/$repo/releases?per_page=100"
    fun releaseAsset(path: String): String = "https://github.com/$owner/$repo/releases/download/$path"
  }

  @JvmStatic
  fun parse(host: String?): Ref? {
    val text = host?.trim() ?: return null
    val match = WEB.matchEntire(text) ?: SHORT.matchEntire(text) ?: return null
    val (owner, repo, branch) = match.destructured
    return Ref(owner, repo, branch.ifEmpty { null })
  }

  /** `owner/repo` for any accepted spelling of a GitHub repository, null for other repository URLs. */
  @JvmStatic
  fun normalize(host: String): String? = parse(host)?.host

  @JvmStatic
  fun isOfficial(repositoryName: String?): Boolean = parse(repositoryName)?.host.equals(OFFICIAL, ignoreCase = true)  // GitHub names ignore case

  /**
   * Lists the plugins of a GitHub repository. With a token ([GitHubTokens]) it reads through the GitHub API, which also
   * works for private repositories, and points downloads at the API's release assets; without one, or when the token
   * has no access, it reads the public raw file.
   */
  @JvmStatic
  fun load(ref: Ref, factory: PluginUiModelBuilderFactory, indicator: ProgressIndicator?): List<PluginUiModel> {
    if (GitHubTokens.token() != null) {
      try {
        val metadata = request(ref.apiMetadataUrl, indicator)
        val assets = releaseAssets(request(ref.apiReleasesUrl, indicator))
        return parseMetadata(metadata.byteInputStream(), ref, factory) { assets[it] }
      }
      catch (e: HttpRequests.HttpStatusException) {
        LOG.info("${ref.host}: the GitHub API answered ${e.statusCode} with a token; trying the public file")
      }
    }
    return parseMetadata(request(ref.metadataUrl, indicator).byteInputStream(), ref, factory)
  }

  /** The metadata text, the way [load] reads it. */
  @JvmStatic
  fun loadMetadata(ref: Ref): String {
    if (GitHubTokens.token() != null) {
      try {
        return request(ref.apiMetadataUrl, null)
      }
      catch (e: HttpRequests.HttpStatusException) {
        LOG.info("${ref.host}: the GitHub API answered ${e.statusCode} with a token; trying the public file")
      }
    }
    return request(ref.metadataUrl, null)
  }

  private fun request(url: String, indicator: ProgressIndicator?): String =
    HttpRequests.request(url).productNameAsUserAgent().setHeadersViaTuner().readString(indicator)

  /** `<tag>/<asset name>` to the asset's API URL, from the GitHub releases list. */
  @JvmStatic
  fun releaseAssets(releasesJson: String): Map<String, String> {
    val releases = runCatching { Json.parseToJsonElement(releasesJson) as? JsonArray }.getOrNull() ?: return emptyMap()
    val result = HashMap<String, String>()
    for (release in releases) {
      val tag = (release as? JsonObject)?.string("tag_name") ?: continue
      for (asset in release["assets"] as? JsonArray ?: continue) {
        val name = (asset as? JsonObject)?.string("name") ?: continue
        asset.string("url")?.let { result["$tag/$name"] = it }
      }
    }
    return result
  }

  @JvmStatic
  @JvmOverloads
  fun parseMetadata(
    input: InputStream,
    ref: Ref,
    factory: PluginUiModelBuilderFactory,
    resolveAsset: (String) -> String? = { null },
  ): List<PluginUiModel> {
    val root = try {
      Json.parseToJsonElement(input.reader().readText()) as? JsonObject
    }
    catch (e: IllegalArgumentException) {
      throw IOException("${ref.host}: $METADATA_PATH is not valid JSON", e)
    } ?: throw IOException("${ref.host}: $METADATA_PATH must be a JSON object")
    val plugins = root["plugins"] as? JsonArray ?: throw IOException("${ref.host}: $METADATA_PATH has no \"plugins\" array")
    return plugins.mapNotNull { (it as? JsonObject)?.let { plugin -> toModel(plugin, ref, factory, resolveAsset) } }
  }

  /** A plugin that asks to be suggested for projects using one of [detect] (`group:artifact`). */
  class Detect(val id: String, val name: String, val detect: List<String>)

  @JvmStatic
  fun parseDetect(metadata: String): List<Detect> {
    val root = runCatching { Json.parseToJsonElement(metadata) as? JsonObject }.getOrNull() ?: return emptyList()
    val plugins = root["plugins"] as? JsonArray ?: return emptyList()
    return plugins.mapNotNull { element ->
      val plugin = element as? JsonObject ?: return@mapNotNull null
      val id = plugin.string("id") ?: return@mapNotNull null
      Detect(id, plugin.string("name") ?: id, plugin.strings("detect")).takeIf { it.detect.isNotEmpty() }
    }
  }

  private fun toModel(plugin: JsonObject, ref: Ref, factory: PluginUiModelBuilderFactory, resolveAsset: (String) -> String?): PluginUiModel? {
    val id = plugin.string("id") ?: return null
    val download = plugin.string("download") ?: return null
    val builder = factory.createBuilder(PluginId.getId(id))
      .setName(plugin.string("name") ?: id)
      .setVersion(plugin.string("version"))
      .setDescription(plugin.string("description"))
      .setVendor(plugin.string("vendor") ?: ref.owner)
      .setCategory(plugin.string("category"))
      .setChangeNotes(plugin.string("changeNotes"))
      .setSinceBuild(plugin.string("since"))
      .setUntilBuild(plugin.string("until"))
      .setUrl("https://github.com/${ref.owner}/${ref.repo}" + (plugin.string("source")?.let { "/tree/HEAD/${it.trim('/', '.')}" } ?: ""))
      .setDownloadUrl(if (download.startsWith("https://")) download
                      else download.trimStart('/').let { resolveAsset(it) ?: ref.releaseAsset(it) })
      .setIncomplete(false)
    plugin.strings("depends").forEach { builder.addDependency(it, false) }
    plugin.strings("tags").forEach { builder.addTag(it) }
    return builder.build()
  }

  private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

  private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
}
