// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.Compressor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/** phantom-idea: the JDK list from foojay's Disco API and how such an item is installed. */
class FoojayJdkListTest : BareTestFixtureTestCase() {
  @Rule @JvmField val tempDir = TempDirectory()

  private fun parse(os: String, arch: String): List<JdkItem> {
    val raw = Files.readString(PathManagerEx.findFileUnderCommunityHome("platform/lang-impl/testData/jdkDownload/foojay-packages.json").toPath())
    return JdkListParser.parseJdkList(FoojayJdkList.toFeed(raw), JdkPredicate(BuildNumber.fromString("263.1")!!, setOf(JdkPlatform(os, arch))))
  }

  @Test
  fun `one newest package per vendor and version, only vendors with a SHA-256`() {
    val items = parse("macOS", "aarch64")
    assertThat(items.map { it.suggestedSdkName + " " + it.jdkVersion }).containsExactly(
      "temurin-25 25.0.4.1", "temurin-21 21.0.12.1",
      "azul-25 25.0.5", "azul-21 21.0.13",
      "graalvm-ce-25 25.0.4.1", "graalvm-ce-21 21.0.2", // not the mislabeled "25.4.4.1"
    )
    // Corretto has no checksum on foojay, JavaFX builds are not plain JDKs
    assertThat(items).noneMatch { it.matchesVendor("corretto") || it.archiveFileName.contains("-fx-") }
  }

  @Test
  fun `an item names its foojay package, a macOS bundle home and a tar_gz`() {
    val temurin = parse("macOS", "aarch64").first { it.suggestedSdkName == "temurin-21" }
    assertThat(temurin.product.packagePresentationText).isEqualTo("Eclipse Temurin")
    assertThat(temurin.detectVariant()).isEqualTo(org.jetbrains.jps.model.java.JdkVersionDetector.Variant.Temurin)
    assertThat(temurin.isDefaultItem).isTrue()
    assertThat(temurin.url).isEqualTo("https://api.foojay.io/disco/v3.0/ids/faa59212208d027d066d1bbe337c5da5")
    assertThat(FoojayJdkList.isFoojayItem(temurin)).isTrue()
    assertThat(temurin.sha256).isEmpty()
    assertThat(temurin.archiveSize).isEqualTo(200073404)
    assertThat(temurin.archiveFileName).isEqualTo("OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz")
    assertThat(temurin.packageType).isEqualTo(JdkPackageType.TAR_GZ)
    assertThat(temurin.packageToBinJavaPrefix).isEqualTo("Contents/Home")
    assertThat(temurin.installFolderName).isEqualTo("temurin-21.0.12.1")
    assertThat(temurin.sharedIndexAliases).contains("temurin-21")
  }

  @Test
  fun `linux lists glibc builds only, windows lists zips`() {
    val linux = parse("linux", "x86_64").filter { it.suggestedSdkName == "temurin-21" }
    assertThat(linux).hasSize(1)
    assertThat(linux.single().archiveFileName).doesNotContain("alpine")
    assertThat(linux.single().packageToBinJavaPrefix).isEmpty()

    val windows = parse("windows", "x86_64")
    assertThat(windows).isNotEmpty().allMatch { it.packageType == JdkPackageType.ZIP }
  }

  @Test
  fun `a broken response is an error, the downloader then lists nothing`() {
    assertThat(runCatching { FoojayJdkList.toFeed("""{"message":"oops"}""") }.isFailure).isTrue()
  }

  @Test
  fun `the package root is the folder above the java home`() {
    val mac = parse("macOS", "aarch64").first { it.suggestedSdkName == "temurin-21" }
    val tar = tempDir.newFile("jdk.tar.gz").toPath()
    Compressor.Tar(tar, Compressor.Tar.Compression.GZIP).use {
      it.addFile("./jdk-21.0.12.1+1/Contents/Info.plist", byteArrayOf())
      it.addFile("./jdk-21.0.12.1+1/Contents/Home/bin/java", byteArrayOf())
      it.addFile("./jdk-21.0.12.1+1/Contents/Home/lib/jre/bin/java", byteArrayOf())
    }
    assertThat(FoojayJdkList.detectPackageRoot(mac, tar)).isEqualTo("jdk-21.0.12.1+1")

    val windows = parse("windows", "x86_64").first()
    val zip = tempDir.newFile("jdk.zip").toPath()
    Compressor.Zip(zip).use { it.addFile("jdk-21/bin/java.exe", byteArrayOf()) }
    assertThat(FoojayJdkList.detectPackageRoot(windows, zip)).isEqualTo("jdk-21")

    val flat = tempDir.newFile("flat.tar.gz").toPath()
    Compressor.Tar(flat, Compressor.Tar.Compression.GZIP).use { it.addFile("bin/java", byteArrayOf()) }
    assertThat(runCatching { FoojayJdkList.detectPackageRoot(mac, flat) }.isFailure).isTrue() // not a macOS bundle
  }

  /**
   * Downloads a real JDK (Temurin 21, or the SDK name in `PHANTOM_FOOJAY_E2E_SDK`) through foojay from the vendor;
   * run with `--test_env=PHANTOM_FOOJAY_E2E=true`.
   */
  @Test
  fun `download and install a JDK end to end`() {
    Assume.assumeTrue(System.getenv("PHANTOM_FOOJAY_E2E") == "true")

    val items = JdkListDownloader.getInstance().downloadModelForJdkInstaller(null, JdkPredicate.forCurrentProcess())
    val item = items.first { it.suggestedSdkName == (System.getenv("PHANTOM_FOOJAY_E2E_SDK") ?: "temurin-21") }
    assertThat(FoojayJdkList.isFoojayItem(item)).isTrue()

    val installDir = tempDir.rootPath.resolve(item.installFolderName)
    val request = JdkInstaller.getInstance().prepareJdkInstallationDirect(item, installDir)
    JdkInstaller.getInstance().installJdk(request, null, null)

    val java = request.javaHome.resolve(if (System.getProperty("os.name").startsWith("Windows")) "bin/java.exe" else "bin/java")
    val output = CapturingProcessHandler(GeneralCommandLine(java.toString(), "-version")).runProcess(60_000)
    println("installed ${item.fullPresentationText} into $installDir\n${output.stderr}")
    assertThat(output.exitCode).isZero()
    assertThat(output.stderr).contains("\"${item.jdkVersion}")
  }
}
