// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.PayloadKey;
import com.jetbrains.fus.reporting.api.ValidationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// phantom-idea: Phantom's own implementation; the closed library com.jetbrains.fus.reporting:ap-validation-all used to provide this class.

/**
 * The event being validated: its id, its data, and values rules share while validating it.
 */
public final class EventContext implements IEventContext {
  public final @NotNull String eventId;
  public final @NotNull Map<String, Object> eventData;
  private final List<ValidationError> myErrors = new ArrayList<>();
  private final Map<String, Object> myPayload = new HashMap<>();

  private EventContext(@NotNull String eventId, @NotNull Map<String, Object> eventData) {
    this.eventId = eventId;
    this.eventData = eventData;
  }

  public static @NotNull EventContext create(@NotNull String eventId, @NotNull Map<String, Object> eventData) {
    return new EventContext(eventId, eventData);
  }

  @Override
  public @NotNull String getEventId() {
    return eventId;
  }

  @Override
  public @NotNull Map<String, Object> getEventData() {
    return eventData;
  }

  @Override
  public synchronized void addError(@NotNull ValidationError error) {
    myErrors.add(error);
  }

  @Override
  public synchronized @Nullable List<ValidationError> getErrors() {
    return myErrors.isEmpty() ? null : new ArrayList<>(myErrors);
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T> @Nullable T getPayload(@NotNull PayloadKey<T> key) {
    return (T)myPayload.get(key.getKey());
  }

  @Override
  public synchronized <T> void setPayload(@NotNull PayloadKey<T> key, T value) {
    myPayload.put(key.getKey(), value);
  }
}
