# Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
"""phantom-idea: the Gradle API jar (project library "Gradle"), built from Gradle's own distribution.

IntelliJ downloads org.jetbrains.intellij.deps:gradle-api from JetBrains' intellij-dependencies repository, where
JetBrains publishes the output of build/dependencies/gradle-api-deps/build.gradle: the gradleApi() jar of one Gradle
version, cut down to Gradle's own packages. This rule does the same from the Gradle distribution on services.gradle.org,
so Phantom builds it without that repository:
  1. download gradle-<version>-bin.zip, checked against Gradle's published sha256 (distribution_sha256);
  2. run that Gradle (offline, its own user home) on a one-task project that writes the gradleApi() jar with the
     recipe's includes, uncompressed, with fixed timestamps and sorted entries, plus Gradle's LICENSE and NOTICE
     (META-INF/gradle/);
  3. check the result against sha256, so every machine builds the same bytes.
Running Gradle needs a JDK 17 or newer: JAVA_HOME, or on macOS /usr/libexec/java_home -v 17+.
How to move to a new Gradle version: docs/phantom/BUILD.md, "Gradle API jar".
"""

_BUILD_GRADLE = """\
import org.gradle.util.GradleVersion

def gradleVersion = GradleVersion.current().version

configurations {
  gradleApiDeps
}

dependencies {
  gradleApiDeps gradleApi()
}

// The includes and excludes of IntelliJ's build/dependencies/gradle-api-deps/build.gradle (task expandGradleApiJar)
tasks.register('gradleApiJar', Zip) {
  archiveFileName = "gradle-api-${gradleVersion}.jar"
  destinationDirectory = layout.projectDirectory.dir('out')
  preserveFileTimestamps = false
  reproducibleFileOrder = true
  entryCompression = ZipEntryCompression.STORED
  from({ zipTree(configurations.gradleApiDeps.files.find { it.name == "gradle-api-${gradleVersion}.jar" }) }) {
    include 'org/gradle/**'
    include 'net/rubygrapefruit/**'
    include 'META-INF/native/**'
    include 'META-INF/gradle-plugins/**'
    include 'META-INF/services/org.gradle.*'
    include 'META-INF/*.kotlin_module'
    exclude 'org/gradle/internal/impldep/META-INF/versions/**'
  }
  // Gradle's license and the list of the third-party libraries it relocates into org/gradle/internal/impldep. Not
  // META-INF/LICENSE*: the IDE build drops those names from library jars (defaultLibrarySourcesNamesFilter, mergeJars.kt)
  from(gradle.gradleHomeDir) {
    include 'LICENSE', 'NOTICE'
    into 'META-INF/gradle'
  }
}
"""

def _java_home(ctx):
    java_home = ctx.os.environ.get("JAVA_HOME", "")
    if java_home:
        return java_home
    if ctx.os.name.startswith("mac"):
        result = ctx.execute(["/usr/libexec/java_home", "-v", "17+"])
        if result.return_code == 0:
            return result.stdout.strip()
    fail("Building the Gradle API jar runs Gradle, which needs a JDK 17 or newer: set JAVA_HOME")

def _sha256(ctx, path):
    for command in [["shasum", "-a", "256"], ["sha256sum"]]:
        if ctx.which(command[0]):
            result = ctx.execute(command + [str(path)])
            if result.return_code == 0:
                return result.stdout.split(" ")[0].strip()
    fail("Neither shasum nor sha256sum is available to check " + str(path))

def _gradle_api_impl(ctx):
    version = ctx.attr.version
    jar = "gradle-api-%s.jar" % version
    ctx.report_progress("Downloading Gradle %s" % version)
    ctx.download_and_extract(
        url = "https://services.gradle.org/distributions/gradle-%s-bin.zip" % version,
        sha256 = ctx.attr.distribution_sha256,
        stripPrefix = "gradle-" + version,
        output = "dist",
    )
    ctx.file("project/settings.gradle", "rootProject.name = 'gradle-api'\n")
    ctx.file("project/build.gradle", _BUILD_GRADLE)
    # generating the API jar relocates the whole distribution; the default 512 MB heap is not enough
    ctx.file("project/gradle.properties", "org.gradle.jvmargs=-Xmx2g\n")

    ctx.report_progress("Building %s with Gradle %s" % (jar, version))
    result = ctx.execute(
        [
            str(ctx.path("dist/bin/gradle")),
            "--no-daemon",
            "--offline",
            "--quiet",
            "--gradle-user-home",
            str(ctx.path("gradle-home")),
            "--project-dir",
            str(ctx.path("project")),
            "gradleApiJar",
        ],
        environment = {"JAVA_HOME": _java_home(ctx)},
        timeout = 3600,
    )
    if result.return_code != 0:
        fail("Gradle %s could not build %s:\n%s\n%s" % (version, jar, result.stdout, result.stderr))

    built = ctx.path("project/out/" + jar)
    sha256 = _sha256(ctx, built)
    if sha256 != ctx.attr.sha256:
        fail(("%s built from Gradle %s has sha256 %s, expected %s. If the recipe or the version changed on purpose, " +
              "pin the new value (docs/phantom/BUILD.md, \"Gradle API jar\").") % (jar, version, sha256, ctx.attr.sha256))

    result = ctx.execute(["mkdir", "-p", "file"])
    if result.return_code == 0:
        result = ctx.execute(["mv", str(built), str(ctx.path("file/" + jar))])
    if result.return_code != 0:
        fail("Could not move %s: %s" % (jar, result.stderr))
    for directory in ["dist", "project", "gradle-home"]:
        ctx.delete(directory)

    ctx.file("file/BUILD.bazel", 'filegroup(name = "file", srcs = ["%s"], visibility = ["//visibility:public"])\n' % jar)
    ctx.file("BUILD.bazel", "")
    return ctx.repo_metadata(reproducible = True)

gradle_api = repository_rule(
    implementation = _gradle_api_impl,
    attrs = {
        "version": attr.string(mandatory = True, doc = "Gradle version, as in build/dependencies/dependencies.properties (gradleApiVersion)"),
        "distribution_sha256": attr.string(mandatory = True, doc = "https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256"),
        "sha256": attr.string(mandatory = True, doc = "sha256 of the built gradle-api-<version>.jar"),
    },
)
