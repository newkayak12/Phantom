// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/// Represents a tool which manages IDE updates instead of a built-in update engine.
public enum ExternalUpdateManager {
  /// Never detected (Phantom has no Toolbox App integration); kept for the Android plugin, which compares against it
  TOOLBOX("Toolbox App"),
  SNAP("Snap"),
  FLATPAK("Flatpak"),
  BREW("Homebrew"),
  UNKNOWN(null);

  public final @NlsSafe String toolName;

  ExternalUpdateManager(@Nullable("`null` for unknown (a.k.a. 'other') update manager") String name) {
    if (name != null) {
      toolName = name;
    }
    else {
      // 'ide.no.platform.update' names the tool, or is 'true' when it is not named
      name = System.getProperty("ide.no.platform.update");
      toolName = "true".equalsIgnoreCase(name) ? "(unknown)" : name;
    }
  }

  /// Returns a tool managing this IDE instance, or `null` when no such tool is detected.
  public static final @Nullable ExternalUpdateManager ACTUAL;
  static {
    var home = PathManager.getHomeDir().toString().replace('\\', '/');
    if (OS.CURRENT == OS.Linux && (home.startsWith("/snap/") || home.startsWith("/var/lib/snapd/snap/"))) ACTUAL = SNAP;
    else if (OS.CURRENT == OS.Linux && EnvironmentUtil.getValue("FLATPAK_ID") != null) ACTUAL = FLATPAK;
    else if (OS.CURRENT != OS.Windows && home.contains("/homebrew/")) ACTUAL = BREW;
    else if (System.getProperty("ide.no.platform.update") != null) ACTUAL = UNKNOWN;
    else ACTUAL = null;
    Logger.getInstance(ExternalUpdateManager.class).info("update manager: " + (ACTUAL == null ? "-" : ACTUAL.toolName));
  }

  /// Returns `true` when the update manager takes care of creating XDG desktop entries.
  @ApiStatus.Internal
  public static boolean isCreatingDesktopEntries() {
    return ACTUAL == SNAP || ACTUAL == FLATPAK;
  }
}
