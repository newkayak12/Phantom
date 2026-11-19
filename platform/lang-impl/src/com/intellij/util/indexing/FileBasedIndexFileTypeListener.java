// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class FileBasedIndexFileTypeListener implements FileTypeListener {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexFileTypeListener.class);

  // phantom-idea: what file types looked like after the last change. A plugin that only wants its icons repainted
  // (Atom Material Icons) calls the deprecated FileTypeManagerEx.fireFileTypesChanged() with nothing added or removed;
  // rescanning ~400k files for that pushed indexing ~2 s later on every start. Such a bare event now rescans only if
  // file types, associations or ignored files differ from this snapshot. A change made through makeFileTypesChange()
  // (TextMate mappings, FileTypeOverrider conditions) is announced by beforeFileTypesChanged and always rescans,
  // since what it changed may live outside FileTypeManager.
  private @Nullable FileTypesSnapshot last = FileTypesSnapshot.take();
  private boolean announced;

  @Override
  public void beforeFileTypesChanged(@NotNull FileTypeEvent event) {
    announced = true;
  }

  @Override
  public void fileTypesChanged(final @NotNull FileTypeEvent event) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    FileTypesSnapshot now = FileTypesSnapshot.take();
    FileTypesSnapshot previous = last;
    last = now;
    boolean wasAnnounced = announced;
    announced = false;
    if (fileBasedIndex.getRegisteredIndexes() == null) {
      return;
    }

    String message = "File type change: " + "added - " + event.getAddedFileType() + ", removed - " + event.getRemovedFileType();
    if (!wasAnnounced && event.getAddedFileType() == null && event.getRemovedFileType() == null &&
        now != null && now.equals(previous)) {
      LOG.info(message + ": file types, associations and ignored files are unchanged, no rescan");
      return;
    }
    fileBasedIndex.scheduleFullIndexesRescan(message);
  }

  /** Registered file types, their name patterns (extensions, names, wildcards, hashbangs) and the ignored-files list. */
  private record FileTypesSnapshot(@NotNull List<String> types, @NotNull Object associations, @NotNull String ignored) {
    static @Nullable FileTypesSnapshot take() {
      try {
        FileTypeManager manager = FileTypeManager.getInstance();
        if (!(manager instanceof FileTypeManagerImpl impl)) return null;
        List<String> types = Arrays.stream(impl.getRegisteredFileTypes())
          .map(FileTypesSnapshot::describe)
          .sorted()
          .toList();
        return new FileTypesSnapshot(types, impl.getExtensionMap().copy(), impl.getIgnoredFilesList());
      }
      catch (Throwable e) {
        // without a snapshot every event rescans, as before
        LOG.debug(e);
        return null;
      }
    }

    private static String describe(FileType type) {
      return type.getName() + "/" + type.getClass().getName() + "/" + Objects.hashCode(type.getDefaultExtension());
    }
  }
}
