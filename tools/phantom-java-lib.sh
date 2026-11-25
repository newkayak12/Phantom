#!/bin/bash
# Publishes the Java plugin built by Phantom ("Support for Java", id dev.phantom.java) as a release of
# newkayak12/phantom-libs, for Phantom-plugins to package and list: Phantom keeps the Java sources and builds them,
# Phantom-plugins ships the plugin. The zip keeps the IDE's plugin layout (java/lib/*.jar, java/lib/modules/*.jar) and
# gets in java/ what Apache 2.0 section 4 and the bundled third-party libraries ask: LICENSE.txt, a NOTICE, and in
# java/license the build's third-party list with the license texts it names.
#   tools/phantom-java-lib.sh <java-*.zip from artifacts/<product code>-plugins> <dir with the build's
#                             third-party-libraries.json and .html> [tag]
# The zip comes from PHANTOM_PUBLISH_PLUGINS=1 ./phantom-build.sh or the Phantom Java lib workflow's artifact. The tag
# defaults to java-<plugin version>. Needs gh logged in with write access to phantom-libs (or GH_TOKEN).
set -euo pipefail
root="$(cd "$(dirname "$0")/.."; pwd)"
src="$(cd "$(dirname "$1")"; pwd)/$(basename "$1")"
unzip -Z1 "$src" java/lib/java-impl.jar >/dev/null || { echo "$1 is not the Java plugin zip"; exit 1; }
jar="$(mktemp)"; unzip -p "$src" java/lib/java-impl.jar > "$jar"
version="$(unzip -p "$jar" META-INF/plugin.xml | awk -F'[<>]' '$2 == "version" && !v { v = $3 } END { print v }')"; rm -f "$jar"
[[ -n "$version" ]] || { echo "no version in the Java plugin.xml"; exit 1; }
list="$(cd "$2"; pwd)"
[[ -f "$list/third-party-libraries.json" && -f "$list/third-party-libraries.html" ]] || { echo "no third-party-libraries.json/.html in $2"; exit 1; }
tag="${3:-java-$version}"
stage="$(mktemp -d)"; mkdir -p "$stage/java/license"
cp "$root/LICENSE.txt" "$stage/java/"
cp "$root"/license/*.txt "$list"/third-party-libraries.* "$stage/java/license/"
cat > "$stage/java/NOTICE.txt" <<NOTICE
Phantom Java plugin ("Support for Java")
Copyright 2026 Phantom contributors

This is the Java plugin of IntelliJ IDEA Community Edition (https://github.com/JetBrains/intellij-community),
built by Phantom from its sources as modified by Phantom contributors (changed files are marked with "phantom-idea:"
comments; https://github.com/newkayak12/Phantom). It is not published or endorsed by JetBrains.
This software includes code from IntelliJ IDEA
Copyright (C) JetBrains s.r.o.
https://www.jetbrains.com/idea/
Licensed under the Apache License, Version 2.0 (LICENSE.txt).

Third-party libraries in this plugin (among them ecj, jb-jdi, kotlin-metadata, netty-codec-protobuf and the debugger
agents) are listed, with their licenses and where to get their sources, in license/third-party-libraries.html (and
.json), the list of the whole Phantom build this plugin was built with. The license/ folder holds the full texts of
the licenses named there other than Apache 2.0.
NOTICE
out="$stage/java-$version.zip"
cp "$src" "$out"
(cd "$stage" && zip -qr "$out" java)
(cd "$stage" && shasum -a 256 "java-$version.zip" > "java-$version.zip.sha256")
gh release create "$tag" -R newkayak12/phantom-libs --title "$tag" "$out" "$out.sha256" --notes \
  "Java plugin (Support for Java, dev.phantom.java, alias com.intellij.java) built by Phantom $(git -C "$root" rev-parse --short=10 HEAD) in the IDE's plugin layout, for Phantom-plugins to package."
echo "published $tag: $(cat "$out.sha256")"
