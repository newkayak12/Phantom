#!/usr/bin/env python3
"""Writes build/phantom_dist_targets.bzl: the Bazel targets the Phantom distribution build (//build:i_build_target) compiles.

Upstream compiles every production and test module of the repository (ALL_COMMUNITY_TARGETS, about 4,100 targets: Python,
Android, plugins Phantom doesn't ship, and all tests). Phantom's build needs the modules that end up in the distribution,
the modules of the standalone JPS build (Phantom builds no Maven artifacts), and their module dependencies other than test ones: the build
looks up the output jar of each of them in its runfiles, and likewise the jars of their libraries (not all 1,200 library
targets: those would fetch about 110 more JetBrains-hosted files no part of Phantom uses). Only production targets are
built, no tests. Test dependencies would bring in modules only tests use, Grazie's 54 JetBrains-owned libraries among
them. The list of
distribution modules comes from the content report of a finished build:
  tools/phantom-dist-targets.py out/phantom-idea/artifacts/content-report.zip [bazel-targets.json]
bazel-targets.json defaults to bazel-bin/build/bazel-targets.json. Run it after a change to the bundled plugins or
modules; a build that misses a module fails with "Unable to find dependency '<target>'".

The plugins Phantom publishes instead of bundling (Kotlin, Maven: pluginModulesToPublish) are built only by
//build:i_build_target_published (phantom-build.sh with PHANTOM_PUBLISH_PLUGINS=1, which phantom-release.sh sets). Their
extra targets go to PHANTOM_PUBLISHED_TARGETS. A report of such a build lists them in non-bundled-plugins.yaml and
refreshes build/phantom-published-modules.txt (their modules and libraries); a report of a normal build has none, and the file is reused.
"""
import json, re, sys, zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
report = Path(sys.argv[1])
targets_json = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "bazel-bin/build/bazel-targets.json"
build_src = root / "build/src/org/jetbrains/intellij/build"

seeds, published_seeds = set(), set()
with zipfile.ZipFile(report) as z:
  for name in z.namelist():
    if name.endswith(".yaml"):
      text = z.read(name).decode()
      names = re.findall(r"^\s*- name: ([A-Za-z][\w.\-]*)\s*$", text, re.M)
      # a library packed on its own names its library and the module that holds it (withModuleLibrary)
      names += re.findall(r"^\s*(?:module|library): ([A-Za-z][\w.\-]*)\s*$", text, re.M)
      (published_seeds if name == "non-bundled-plugins.yaml" else seeds).update(names)
# The standalone JPS build
jps_builder = (build_src / "CommunityStandaloneJpsBuilder.kt").read_text()
seeds.update(re.findall(r'withModule\("([\w.]+)"', jps_builder))
# and the modules of its jps-build-test jar
for names in re.findall(r"moduleNames = listOf\((.*?)\)", jps_builder, re.S):
  seeds.update(re.findall(r'"([\w.]+)"', names))

imls = {Path(p).stem: root / p for p in re.findall(r'filepath="\$PROJECT_DIR\$/([^"]+\.iml)"', (root / ".idea/modules.xml").read_text())}
# Names in the report that are not modules are project libraries, added with their jars below
def closure(start, test_dependencies, skip=None):
  modules, todo = set(), [m for m in start if m in imls]
  while todo:
    m = todo.pop()
    if m in modules or m not in imls or (skip and m.startswith(skip)):
      continue
    modules.add(m)
    for name, attrs in re.findall(r'<orderEntry type="module" module-name="([^"]+)"([^>]*)>', imls[m].read_text()):
      if test_dependencies or 'scope="TEST"' not in attrs:
        todo.append(name)
  return modules

published_file = root / "build/phantom-published-modules.txt"
if published_seeds:
  published_file.write_text("".join(f"{m}\n" for m in sorted(published_seeds)))
else:
  published_seeds = set(published_file.read_text().split())

modules = closure(seeds, False)
# The published plugins' layout resolves plugin.xml includes through test dependencies too, so their list follows them,
# except into JetBrains' code owner modules and the IDE metrics benchmark that uses them (test tooling without descriptors;
# they need the unlicensed patronus libraries). Only modules
# reached without test dependencies get their libraries.
published = closure(published_seeds, True, skip=("intellij.codeowners", "intellij.tools.ide.metrics.benchmark")) - modules
published_with_libraries = closure(published_seeds, False) - modules

targets_data = json.loads(targets_json.read_text())
known, project_libraries = targets_data["modules"], targets_data["projectLibraries"]
def targets_of(ms, test_libraries):
  targets = set()
  for m in ms:
    if m not in known:
      continue
    targets.update(known[m]["productionTargets"])
    # The jars of its libraries (test ones only for the published plugins: Kotlin packs kotlin-test-junit): the build looks each of them up in its
    # runfiles ("Unable to determine dependency path '@lib//:...jar'")
    for attrs, name in re.findall(r'<orderEntry type="library"([^>]*) name="([^"]+)" level="project"', imls[m].read_text()):
      if test_libraries or 'scope="TEST"' not in attrs:
        targets.update(project_libraries[name]["jarTargets"])
    for library in known[m]["moduleLibraries"].values():
      targets.update(library["jarTargets"])
  return sorted(targets)
targets = targets_of(modules, False)
# libraries the layout adds by name (withProjectLibrary), not through a module
targets = sorted(set(targets).union(*(project_libraries[n]["jarTargets"] for n in seeds if n not in imls and n in project_libraries)))
# the Kotlin compiler distribution the Kotlin plugin unpacks into kotlinc/ (kotlin/KotlinPluginBuilder.kt), looked up by name
published_libraries = {t for n in published_seeds | {"kotlinc.kotlin-dist"} if n not in imls and n in project_libraries
                       for t in project_libraries[n]["jarTargets"]}
published_modules_only = {t for m in published - published_with_libraries if m in known for t in known[m]["productionTargets"]}
published_targets = sorted((set(targets_of(published_with_libraries, True)) | published_modules_only | published_libraries) - set(targets))

def bzl_list(name, items):
  return f"{name} = [\n" + "".join(f'    "{t}",\n' for t in items) + "]\n"
out = root / "build/phantom_dist_targets.bzl"
out.write_text(
  "# Generated by tools/phantom-dist-targets.py from a build's content report; do not edit.\n"
  "# The production targets of the modules the Phantom distribution build needs (see the script).\n"
  + bzl_list("PHANTOM_DIST_TARGETS", targets)
  + "\n# The further targets of the plugins Phantom publishes instead of bundling (//build:i_build_target_published).\n"
  + bzl_list("PHANTOM_PUBLISHED_TARGETS", published_targets)
)
print(f"{out.relative_to(root)}: {len(targets)} targets for {len(modules)} modules and their libraries,"
      f" {len(published_targets)} more for {len(published)} published modules")
