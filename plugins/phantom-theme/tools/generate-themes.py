#!/usr/bin/env python3
# Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Regenerates resources/themes/*.theme.json from the Islands themes and the token overrides below:

    python3 -I plugins/phantom-theme/tools/generate-themes.py

A child theme cannot just redefine a named color: the parent's `ui` values are resolved to colors before the child is
merged. So every `ui` key of the parent whose color chain passes through an overridden token is written out with the new
value. The `alt.` keys (Settings | Appearance | "Different tool window background") get the same values, so the theme
looks the same with that option on or off.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = pathlib.Path(__file__).resolve().parent.parent / "resources" / "themes"

# Softer, rounder shapes than Islands: bigger panel corners and controls
SHAPE = {
  "Island.arc": 24,
  "Island.arc.compact": 18,
  "Button.arc": 12,
  "Component.arc": 10,
  "Component.arc.compact": 8,
  "Banner.arc": 14,
}

THEMES = {
  "PhantomDark": {
    "name": "Phantom Dark", "dark": True, "parent": "Islands Dark", "scheme": "Islands Dark",
    "source": "platform/platform-resources/src/themes/islands/ManyIslandsDark.theme.json",
    "tokens": {
      # The frame sits just above the panels instead of standing out as a lighter band
      "main-window-bg": "#1D1E21",
      "main-window-bg-alt": "#1D1E21",
      "tool-window-bg-alt": "#191A1C",
      # Borders fade into the panels
      "layer-0-border": "#202125",
      "editor-border": "#202125",
      "editor-border-alt": "#202125",
      "tool-window-border": "#202125",
      "popup-border": "#2C2E33",
      "control-border": "#36383E",
      "tree-indent-guide-border": "#222327",
      # Quiet selections and tabs
      "selection-bg-active": "#2A3142",
      "selection-bg-active-muted": "#242936",
      "selection-bg-inactive": "#26282D",
      "tab-selected-bg-active": "#262930",
      "tab-selected-bg-inactive": "#212226",
      "tab-selected-border-active": "#262930",
      "tab-selected-border-inactive": "#212226",
      # Amber accent from the app icon's eyes: buttons, focus rings, selected toolbar items, completion matches
      "accent-brand-bg": "#C17F35",
      "accent-brand-border": "#C17F35",
      "accent-brand-bg-secondary": "#3A2C1C",
      "accent-brand-border-secondary": "#6B4A26",
      "toolbar-selected-bg": "#4A3720",
      "toolbar-selected-bg-hovered": "#5A4226",
      "popup-completion-match-text": "#E2A65A",
    },
    "ui": SHAPE,
  },
  "PhantomLight": {
    "name": "Phantom Light", "dark": False, "parent": "Islands Light", "scheme": "Light",
    "source": "platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json",
    "tokens": {
      "main-window-bg": "#EDEEF1",
      "main-window-bg-alt": "#EDEEF1",
      "tool-window-bg-alt": "#FFFFFF",
      "layer-2-border": "#ECEDF0",
      "editor-border": "#ECEDF0",
      "editor-border-alt": "#ECEDF0",
      "tool-window-border": "#ECEDF0",
      "popup-border": "#E3E4E8",
      "tree-indent-guide-border": "#ECEDF0",
      "selection-bg-active": "#DDE6FA",
      "selection-bg-active-muted": "#E8EEFB",
      "selection-bg-inactive": "#E9EAEE",
      "tab-selected-bg-active": "#E4EAF8",
      "tab-selected-bg-inactive": "#EFF0F3",
      "tab-selected-border-active": "#E4EAF8",
      "tab-selected-border-inactive": "#EFF0F3",
      "accent-brand-bg": "#B5711F",
      "accent-brand-border": "#B5711F",
      "accent-brand-bg-secondary": "#FBF1E3",
      "accent-brand-border-secondary": "#E8C9A0",
      "toolbar-selected-bg": "#F6E3C8",
      "toolbar-selected-bg-hovered": "#F1D6B2",
      "popup-completion-match-text": "#A9651A",
    },
    "ui": SHAPE,
  },
}


def flatten(d: dict, prefix: str = ""):
  for key, value in d.items():
    if isinstance(value, dict):
      yield from flatten(value, prefix + key + ".")
    else:
      yield prefix + key, value


def resolve(value, colors: dict, tokens: dict):
  """The overridden color this value ends up at, or None when its chain touches no overridden token."""
  seen = set()
  while isinstance(value, str) and value in colors and value not in seen:
    if value in tokens:
      return tokens[value]
    seen.add(value)
    value = colors[value]
  return None


def main() -> None:
  for file_name, spec in THEMES.items():
    parent = json.loads((ROOT / spec["source"]).read_text(encoding="utf-8"))
    colors, tokens = parent["colors"], spec["tokens"]
    ui = {}
    for key, value in flatten(parent["ui"]):
      color = resolve(value, colors, tokens)
      if color is not None:
        ui[key] = color
    for key in list(ui):
      if key.startswith("alt."):
        ui.setdefault(key[4:], ui[key])
      elif "alt." + key not in ui and any(k.startswith("alt.") and k[4:] == key for k, _ in flatten(parent["ui"])):
        ui["alt." + key] = ui[key]
    theme = {
      "name": spec["name"],
      "dark": spec["dark"],
      "author": "Phantom",
      "parentTheme": spec["parent"],
      "editorScheme": spec["scheme"],
      "colors": tokens,
      "ui": dict(sorted({**ui, **spec["ui"]}.items())),
    }
    (OUT / f"{file_name}.theme.json").write_text(json.dumps(theme, indent=2) + "\n", encoding="utf-8")
    print(f"{spec['name']}: {len(ui)} ui keys")


if __name__ == "__main__":
  main()
