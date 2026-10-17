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
  const val OFFICIAL: String = "newkayak12/phantom-plugins"

  const val METADATA_PATH: String = ".phantom/marketplace.json"

  private val SHORT = Regex("""^(?:github:)?([A-Za-z0-9-]+)/([\w.-]+?)(?:\.git)?(?:@([\w./-]+))?/?$""")
  private val WEB = Regex("""^https?://(?:www\.)?github\.com/([A-Za-z0-9-]+)/([\w.-]+?)(?:\.git)?(?:/tree/([\w./-]+))?/?$""")

  class Ref(val owner: String, val repo: String, val branch: String?) {
    val host: String get() = "$owner/$repo" + (branch?.let { "@$it" } ?: "")
    val metadataUrl: String get() = "https://raw.githubusercontent.com/$owner/$repo/${branch ?: "HEAD"}/$METADATA_PATH"
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
  fun isOfficial(repositoryName: String?): Boolean = parse(repositoryName)?.host == OFFICIAL

  @JvmStatic
  fun parseMetadata(input: InputStream, ref: Ref, factory: PluginUiModelBuilderFactory): List<PluginUiModel> {
    val root = try {
      Json.parseToJsonElement(input.reader().readText()) as? JsonObject
    }
    catch (e: IllegalArgumentException) {
      throw IOException("${ref.host}: $METADATA_PATH is not valid JSON", e)
    } ?: throw IOException("${ref.host}: $METADATA_PATH must be a JSON object")
    val plugins = root["plugins"] as? JsonArray ?: throw IOException("${ref.host}: $METADATA_PATH has no \"plugins\" array")
    return plugins.mapNotNull { (it as? JsonObject)?.let { plugin -> toModel(plugin, ref, factory) } }
  }

  /** Library coordinates (`group:artifact`) a plugin asks to be suggested for, by plugin id. */
  @JvmStatic
  fun parseDetect(input: InputStream): Map<String, List<String>> {
    val root = runCatching { Json.parseToJsonElement(input.reader().readText()) as? JsonObject }.getOrNull() ?: return emptyMap()
    val plugins = root["plugins"] as? JsonArray ?: return emptyMap()
    return plugins.mapNotNull { element ->
      val plugin = element as? JsonObject ?: return@mapNotNull null
      val id = plugin.string("id") ?: return@mapNotNull null
      id to plugin.strings("detect")
    }.filter { it.second.isNotEmpty() }.toMap()
  }

  private fun toModel(plugin: JsonObject, ref: Ref, factory: PluginUiModelBuilderFactory): PluginUiModel? {
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
      .setDownloadUrl(if (download.startsWith("https://")) download else ref.releaseAsset(download.trimStart('/')))
      .setIncomplete(false)
    plugin.strings("depends").forEach { builder.addDependency(it, false) }
    plugin.strings("tags").forEach { builder.addTag(it) }
    return builder.build()
  }

  private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

  private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
}
