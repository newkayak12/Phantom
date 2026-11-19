// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.typos

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel
import com.intellij.searchEverywhereMl.typos.models.ActionsTypoPrefixMatcher
import com.intellij.searchEverywhereMl.typos.models.NGramModelProvider
import com.intellij.searchEverywhereMl.typos.models.tokenizeTextForTypoLookup
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

class SearchEverywhereSpellerImpl : SearchEverywhereSpellingCorrector {
  private val actionsLanguageModel: ActionsLanguageModel? = ActionsLanguageModel.getInstance()

  // phantom-idea: own ActionsSpeller over the action vocabulary instead of the closed Grazie speller and its Hunspell resources
  private val deferredSpeller: Deferred<ActionsSpeller>? = actionsLanguageModel
    ?.let { model ->
      model.coroutineScope.async {
        ActionsSpeller(model.deferredDictionary.await())
      }
    }

  init {
    actionsLanguageModel?.ensurePrefixMatcherBuilding()
  }

  /**
   * Returns all possible spelling corrections for the given query if the speller is ready.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getAllCorrections(query: String, maxCorrections: Int): List<SearchEverywhereSpellCheckResult.Correction> {
    // If the query is blank or the speller hasn't completed initialization, return an empty list.
    if (query.isBlank() || maxCorrections <= 0) return emptyList()
    if (shouldSkipTypoCorrection(query)) return emptyList()
    if (deferredSpeller == null || !deferredSpeller.isCompleted) return emptyList()

    val speller = deferredSpeller.getCompleted()
    val suggestionsGenerator = SpellerSuggestionsGenerator(speller, nGramModelProvider = NGramModelProvider(actionsLanguageModel))

    // Generate combined corrections from the speller.
    return selectCorrections(query,
                             suggestionsGenerator.combinedCorrections(query),
                             maxCorrections,
                             Registry.doubleValue(MIN_CONFIDENCE_REGISTRY_KEY))
  }

  /**
   * Checks the spelling for a single query string and returns the first correction if any exist.
   */
  override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
    val corrections = getAllCorrections(query, 1)
    return if (corrections.isEmpty()) {
      SearchEverywhereSpellCheckResult.NoCorrection
    } else {
      corrections.first()
    }
  }

  override fun isAvailableInTab(tabId: String): Boolean =
    tabId == ActionSearchEverywhereContributor::class.java.simpleName && isTypoFixingEnabled

  private fun shouldSkipTypoCorrection(query: String): Boolean {
    val model = actionsLanguageModel ?: return false
    val prefixMatcher = model.getReadyPrefixMatcherOrNull()
    if (prefixMatcher == null) {
      model.ensurePrefixMatcherBuilding()
    }

    return shouldSkipTypoCorrection(query, prefixMatcher)
  }
}

internal const val MIN_CONFIDENCE_REGISTRY_KEY: String = "search.everywhere.ml.typos.min.confidence"

internal fun selectCorrections(query: String,
                               corrections: List<SearchEverywhereSpellCheckResult.Correction>,
                               maxCorrections: Int,
                               minConfidence: Double): List<SearchEverywhereSpellCheckResult.Correction> {
  if (maxCorrections <= 0) return emptyList()

  return corrections.asSequence()
    .filter { it.correction != query }
    .filter { it.confidence >= minConfidence }
    .distinctBy { it.correction }
    .take(maxCorrections)
    .toList()
}

internal fun shouldSkipTypoCorrection(query: String, prefixMatcher: ActionsTypoPrefixMatcher?): Boolean {
  if (tokenizeTextForTypoLookup(query).isEmpty()) {
    return false
  }
  return prefixMatcher == null || prefixMatcher.hasPrefix(query)
}

internal class GrazieSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(): Boolean {
    // Some logic to verify if typo-fixing is enabled.
    return isTypoFixingEnabled
  }

  override fun create(): SearchEverywhereSpellingCorrector = SearchEverywhereSpellerImpl()
}
