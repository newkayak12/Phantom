// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
// phantom-idea: added import for pluginModulesToPublish below (Kotlin is published as a zip, not bundled)
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
import java.nio.file.Files
import java.nio.file.Path

// phantom-idea: published to the Phantom plugins repository instead of bundled
private const val MAVEN_PLUGIN_MODULE = "intellij.maven.plugin"

/**
 * phantom-idea: plugins built in Phantom-plugins and bundled in place of their IntelliJ originals. They have dev.phantom.* ids
 * and keep the IntelliJ ids as aliases and the IntelliJ content module names, so the plugins and modules depending on them
 * resolve to these. Mermaid is Phantom's own implementation (com.phantom.mermaid, no alias). phantom-build.sh unpacks them
 * into out/phantom-plugins/<dir>; the originals are left out of the bundle.
 */
// phantom-idea: lombok, decompiler, coverage, testng, java-i18n, junit, groovy and gradle-dsl need Java or Gradle, which are not
// bundled: they are installed from the Phantom plugins repository with them
val PHANTOM_PLUGINS_BUNDLED: PersistentList<String> = persistentListOf(
  "toml", "yaml", "sh", "textmate", "markdown", "mermaid", "copyright", "editorconfig", "htmltools", "grammar",
  "terminal", "github", "properties", "repository-search", "json", "groovy-scripting", "groovy-live-templates",
  "images", "grid", "git", "theme", "sync", "navbar", "recentfiles", "serviceview", "endpoints",
)

val MAVEN_ARTIFACTS_ADDITIONAL_MODULES: PersistentList<String> = persistentListOf(
  "intellij.tools.jps.build.standalone",
  "intellij.devkit.jps",
  "intellij.idea.community.build.tasks",
  "intellij.platform.debugger.testFramework",
  "intellij.platform.vcs.testFramework",
  "intellij.platform.externalSystem.testFramework",
  "intellij.platform.uast.testFramework",
  "intellij.maven.testFramework",
  "intellij.tools.reproducibleBuilds.diff",
  "intellij.space.java.jps",
) + JewelMavenArtifacts.STANDALONE.keys

internal suspend fun createCommunityBuildContext(
  options: BuildOptions,
  projectHome: Path = COMMUNITY_ROOT.communityRoot,
): BuildContext {
  return createBuildContext(
    projectHome = projectHome,
    productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
    setupTracer = true,
    options = options,
  )
}

open class IdeaCommunityProperties(private val communityHomeDir: Path) : JetBrainsProductProperties() {
  init {
    configurePropertiesForAllEditionsOfIntelliJIdea(this)
    platformPrefix = "Idea"
    applicationInfoModule = "intellij.idea.community.customization"
    scrambleMainJar = false
    useSplash = true
    buildCrossPlatformDistribution = true
    buildSourcesArchive = true

    imagesDirectoryPath = communityHomeDir.resolve("build/idea-community-images")

    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.idea.community.customization",
    )

    // phantom-idea: no feature trainer ("Learn IDE Features" courses, Learn tool window), and no AI agent plumbing
    // (MCP server for external agents, Agent Client Protocol); nothing bundled depends on either.
    // No performanceTesting either: JetBrains' scripted performance-test runner, nothing for users.
    // No DevKit runtime (internal PSI viewer and usage-stats tools), no Eclipse project import, no JavaFX
    // (its Scene Builder tab downloads a kit from JetBrains).
    // Bytecode Viewer, WebP and Modal Commit Interface are Phantom-plugins now (bytecode, webp, modal-commit).
    // No GitLab integration (GitHub only); no Eclipse, Visual Studio or NetBeans keymaps (installable from Marketplace).
    // No configurationScript: intellij.yaml project-configuration files; nothing bundled depends on it.
    productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS
      .removing("intellij.featuresTrainer")
      .removing("intellij.mcpserver")
      .removing("intellij.performanceTesting")
      .removing("intellij.platform.acp.plugin")
      .removing("intellij.dev")
      .removing("intellij.eclipse")
      .removing("intellij.java.byteCodeViewer")
      .removing("intellij.webp")
      .removing("intellij.vcs.git.commit.modal")
      .removing("intellij.vcs.gitlab")
      .removing("intellij.keymap.eclipse")
      .removing("intellij.keymap.visualStudio")
      .removing("intellij.keymap.netbeans")
      .removing("intellij.configurationScript")
      // built in Phantom-plugins instead, see PHANTOM_PLUGINS_BUNDLED
      .removing("intellij.toml")
      .removing("intellij.yaml")
      .removing("intellij.sh.plugin")
      .removing("intellij.java.decompiler")
      .removing("intellij.java.coverage")
      .removing("intellij.textmate.plugin")
      .removing("intellij.markdown")
      .removing("intellij.mermaid")
      .removing("intellij.copyright")
      .removing("intellij.editorconfig.plugin")
      .removing("intellij.html.tools")
      .removing("intellij.testng")
      .removing("intellij.java.i18n")
      .removing("intellij.grazie")
      .removing("intellij.terminal")
      .removing("intellij.vcs.github")
      .removing("intellij.junit")
      .removing("intellij.properties")
      .removing("intellij.repository.search")
      .removing("intellij.json")
      .removing("intellij.android.gradle.dsl")
      .removing("intellij.groovy.scripting")
      .removing("intellij.groovy")
      .removing("intellij.groovy.live.templates")
      .removing("intellij.platform.images")
      .removing("intellij.grid.core.plugin")
      .removing("intellij.vcs.git")
      .removing("intellij.platform.navbar.plugin")
      .removing("intellij.platform.recentFiles.plugin")
      .removing("intellij.platform.execution.serviceView.plugin")
      .removing(KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE)
      .removing(MAVEN_PLUGIN_MODULE)
      .removing(JavaPluginLayout.MAIN_MODULE_NAME)
      // Java IDE Customization (com.intellij.java.ide) only adjusts the IDE for Java and requires the Java plugin
      .removing("intellij.java.ide.customization")
      // Gradle: "Support for Gradle" (gradle-core) and "Support for Gradle (Java)" (gradle-java) in Phantom-plugins,
      // installed from its marketplace for Gradle projects
      .removing("intellij.gradle.plugin")
      .removing("intellij.gradle.java.plugin")
    // phantom-idea: Kotlin is not bundled (381 MB, and Java-only projects never use it). The build zips it into
    // artifacts/<product code>-plugins for the Phantom plugins repository, which suggests it when a project uses Kotlin
    // or Gradle Kotlin DSL scripts; phantom-release.sh publishes it with each release.
    // Maven likewise (51 MB): suggested when a project has a pom.xml. Its optional modules elsewhere (Gradle's Maven
    // import, Kotlin's Maven support, Repository Search's common part) switch off without it.
    // Java likewise ("Support for Java", id dev.phantom.java with com.intellij.java as an alias): the IDE itself is
    // language-neutral, and the plugins repository suggests Java when a project has Java sources or build files.
    // Only //build:i_build_target_published (-Dphantom.publish.plugins=true, phantom-build.sh with PHANTOM_PUBLISH_PLUGINS=1)
    // builds them: they are about 190 of the build's 980 targets, and a build that doesn't release them skips those.
    productLayout.pluginModulesToPublish =
      if (System.getProperty("phantom.publish.plugins").toBoolean()) persistentSetOf(KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE, MAVEN_PLUGIN_MODULE, JavaPluginLayout.MAIN_MODULE_NAME)
      else persistentSetOf()

    // phantom-idea: class data sharing. The JVM archives the loaded classes into $IDE_CACHE_DIR/<base><build>.jsa on the
    // first exit after an install or update (about 25 s, about 370 MB) and maps it on later starts: "app initialization"
    // 0.8-2.9 s instead of 2.3-3.9 s and the first smart mode 5-9 s instead of 14-19 s (phantom-bench.sh, Kotlin fixture).
    // It replaces -Djava.system.class.loader=PathClassLoader, which the dynamic archive does not support.
    enableCds = true

    // phantom-idea: JetBrains' user agreements, privacy policies and consent lists are not Phantom's and are not shipped. The code
    // reads them only for vendor JetBrains (EndUserAgreement, ConsentOptions) or tolerates their absence (LocalConsentOptions);
    // eua.html stays in the source tree because the SBOM generator reads it.
    productLayout.addPlatformSpec { layout, _ ->
      layout.excludeFromModule("intellij.platform.resources", listOf(
        "eua.html", "euaCommunity.html", "PrivacyPolicy.html", "privacyEap.html", "consents.json", "localConsents.json",
      ))
      // phantom-idea: JetBrains' usage statistics schemes and dictionaries (FUS and other recorders, 11 MB) only serve the
      // statistics Phantom does not collect
      layout.excludeFromModule("intellij.idea.community.customization", "event-log-metadata/**")
    }

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false // phantom-idea: no separate plugin repository build
    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + persistentListOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin(),
      // phantom-idea: Android plugin removed from the default bundle
    )

    productLayout.skipUnresolvedContentModules = true

    // phantom-idea: no Maven artifacts (forIdeModules, additionalModules = MAVEN_ARTIFACTS_ADDITIONAL_MODULES, squashedModules
    // stay empty, so the build skips the step). Phantom publishes none, phantom-build.sh deleted the output, and the step
    // compiled test frameworks whose dependencies include JetBrains-owned libraries (patronus codeowners).
    mavenArtifacts.validateForMavenCentralPublication = { module ->
      JewelMavenArtifacts.isPublishedJewelModule(module) || JewelMavenArtifacts.isPublishedPlatformDependency(module)
    }
    mavenArtifacts.patchCoordinates = { module, coordinates ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.patchCoordinates(module, coordinates)
        else -> coordinates
      }
    }
    mavenArtifacts.patchDependencies = { module, dependencies ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.patchDependencies(module, dependencies)
        else -> dependencies
      }
    }
    mavenArtifacts.addPomMetadata = { module, model ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.addPomMetadata(module, model)
        JewelMavenArtifacts.isPublishedPlatformDependency(module) -> JewelMavenArtifacts.addPlatformPomMetadata(module, model)
      }
    }
    mavenArtifacts.isJavadocJarRequired = {
      JewelMavenArtifacts.isPublishedPlatformDependency(it) ||
      (JewelMavenArtifacts.isPublishedJewelModule(it) && it.name != "intellij.platform.jewel.intUi.decoratedWindow")
    }
    mavenArtifacts.validate = { context, artifacts ->
      JewelMavenArtifacts.validate(context, artifacts)
    }

    // Maven is published, not bundled, so its runtime jars are not in the distribution to check
    versionCheckerConfig = CE_CLASS_VERSIONS.filterKeys { !it.startsWith("plugins/maven-plugin/") }
    baseDownloadUrl = "https://download.jetbrains.com/idea/"
    buildDocAuthoringAssets = true

    @Suppress("SpellCheckingInspection")
    qodanaProductProperties = QodanaProductProperties("QDJVMC", "Qodana Community for JVM")
    additionalVmOptions = persistentListOf("-Dllm.show.ai.promotion.window.on.start=false")
  }

  override val baseFileName: String
    get() = "phantom"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment(platformPrefix))
  }

  override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    super.copyAdditionalFiles(targetDir, context)

    copyFileToDir(context.paths.communityHomeDir.resolve("LICENSE.txt"), targetDir)
    copyFileToDir(context.paths.communityHomeDir.resolve("NOTICE.txt"), targetDir)

    copyDir(
      sourceDir = context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"),
      targetDir = targetDir.resolve("bin"),
    )

    bundleExternalPlugins(context, targetDir)
  }

  protected open suspend fun bundleExternalPlugins(context: BuildContext, targetDirectory: Path) {}

  // phantom-idea: a build without them would ship without YAML, Markdown and the rest, so a missing one fails the build
  override suspend fun getAdditionalPluginPaths(context: BuildContext): List<Path> {
    val root = context.paths.communityHomeDir.resolve("out/phantom-plugins")
    val dirs = PHANTOM_PLUGINS_BUNDLED.map { root.resolve(it) }
    val missing = dirs.filterNot { Files.isDirectory(it.resolve("lib")) }
    check(missing.isEmpty()) { "Phantom-plugins builds missing (phantom-build.sh puts them in $root): $missing" }
    return dirs
  }

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer = ideaCommunityWindowsCustomizer(communityHomeDir)

  override fun createLinuxCustomizer(projectHome: Path): LinuxDistributionCustomizer = ideaCommunityLinuxCustomizer(communityHomeDir)

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer = ideaCommunityMacCustomizer(communityHomeDir)

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "PhantomIDEA" // phantom-idea: fixed so settings survive date-based version bumps
  }

  // Phantom's own build number when phantom-build.sh sets it (PH-261018.2 -> phantom-261018.2), else 262.261016 -> phantom-261016
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "phantom-" + (System.getProperty("phantom.build.number")?.takeIf { it.isNotBlank() }?.substringAfter('-') ?: buildNumber.substringAfter('.'))

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "phantom-idea"
}

@Suppress("unused")
open class AndroidStudioProperties(communityHomeDir: Path) : IdeaCommunityProperties(communityHomeDir) {
  init {
    platformPrefix = "AndroidStudio"
    applicationInfoModule = "intellij.idea.android.customization"

    productLayout.productImplementationModules += "intellij.idea.android.customization"

    val defaultBundledPlugins = IDEA_BUNDLED_PLUGINS
      .removing("intellij.mcpserver")
      .removing("intellij.featuresTrainer")

    productLayout.bundledPluginModules = defaultBundledPlugins + persistentListOf(
      "intellij.android.compose-ide-plugin",
      "intellij.android.design-plugin.descriptor",
      "intellij.android.plugin.descriptor",
      "intellij.android.smali",
    )
  }

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment(platformPrefix))
    // no community extensions
  }
}

/**
 * Base IntelliJ Community content fragment.
 * This fragment is composable - subclasses can include this and optionally add community extensions.
 */
fun intellijCommunityBaseFragment(platformPrefix: String? = null): ProductModulesContentSpec = productModules {
  if (platformPrefix == "AndroidStudio") {
    alias("com.intellij.modules.androidstudio")
  }
  else {
    alias("com.intellij.modules.idea")
    alias("com.intellij.modules.idea.community")
  }

  // phantom-idea: com.intellij.modules.java-capable is declared by the Java plugin, which is not bundled
  // phantom-idea: lets plugins depend optionally on the PhantomActivityListener topic and still load on stock IntelliJ
  alias("dev.phantom.modules.activity")
  // phantom-idea: lets plugins depend optionally on PhantomCheckoutLink (base / worktree record)
  alias("dev.phantom.modules.checkout")
  alias("com.intellij.modules.python-core-capable")
  alias("com.intellij.modules.python-in-non-pycharm-ide-capable")

  if (platformPrefix != "AndroidStudio") {
    alias("com.intellij.platform.ide.provisioner")
  }

  // phantom-idea: javaIdeBaseFragment() without JavaIdePlugin.xml (intellij.java.ide.resources): Java is not bundled, and that
  // file only adds JetBrains UTM parameters to web links, the IDEA new UI onboarding help name and a Java completion tip
  deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
  alias("com.intellij.modules.all")
  alias("com.intellij.modules.jsp.base")
  embeddedModule("intellij.platform.remoteServers.impl")
  deprecatedInclude("intellij.uiDesigner", "META-INF/DesignerCorePlugin.xml", optional = true)
  // phantom-idea: no tips-intellij-idea-community.xml: Phantom ships no Tips of the Day content (the JetBrains tips-idea-ce library is not a dependency)

  module("intellij.platform.coverage")
  module("intellij.platform.coverage.agent")
  module("intellij.xml.xmlbeans")
  // phantom-idea: no onboarding tours, no settings import from other IDEs (intellij.ide.startup.importSettings)
  module("intellij.platform.customization.min")
  module("intellij.idea.customization.base")
  module("intellij.idea.customization.backend")
  module("intellij.platform.tips")

  if (System.getProperty("idea.platform.prefix") == "AndroidStudio") {
    module("intellij.idea.android.customization")
  }

  moduleSet(CommunityModuleSets.ideCommon())
  moduleSet(CommunityModuleSets.rdCommon())

  deprecatedInclude("intellij.idea.community.customization", "META-INF/community-customization.xml")
}

inline fun ideaCommunityWindowsCustomizer(
  projectHome: Path,
  configure: WindowsCustomizerBuilder.() -> Unit = {}
): WindowsDistributionCustomizer = windowsCustomizer(projectHome) {
  // phantom-idea: no file associations: Java, Gradle, Groovy, Kotlin and Maven are not bundled, so their files would open as text
  fileAssociations = emptyList()

  // phantom-idea: Windows product and install folder name Phantom (rebrand); the JetBrains uninstall feedback page (uninstallFeedbackUrl) is removed
  fullName { "Phantom" }
  installDirNameHandler { "Phantom" }

  configure()
}

inline fun ideaCommunityMacCustomizer(
  projectHome: Path,
  configure: MacCustomizerBuilder.() -> Unit = {}
): MacDistributionCustomizer = macCustomizer(projectHome) {
  // phantom-idea: macOS URL scheme phantom, bundle id dev.phantom.idea and "Phantom.app" instead of IntelliJ IDEA OSS (rebrand)
  urlSchemes = listOf("phantom")
  associateIpr = true
  // phantom-idea: no file associations: Java, Groovy and Kotlin are not bundled, so their files would open as text
  fileAssociations = emptyList()
  bundleIdentifier = "dev.phantom.idea"

  rootDirectoryName { _, _ -> "Phantom.app" }

  executableFilePatterns { base, _, _, _ ->
    val kotlinExecutables = KotlinBinaries.kotlinCompilerExecutables
    (base + kotlinExecutables).filterNot { it == "plugins/**/*.sh" }
  }

  configure()
}

inline fun ideaCommunityLinuxCustomizer(
  projectHome: Path,
  configure: LinuxCustomizerBuilder.() -> Unit = {}
): LinuxDistributionCustomizer = linuxCustomizer(projectHome) {

  // phantom-idea: Linux root folder phantom instead of idea-oss (rebrand)
  rootDirectoryName { _, _ -> "phantom" }

  executableFilePatterns { base, _, _, _, _ ->
    base.plus(KotlinBinaries.kotlinCompilerExecutables).filterNot { it == "plugins/**/*.sh" }
  }

  configure()
}
