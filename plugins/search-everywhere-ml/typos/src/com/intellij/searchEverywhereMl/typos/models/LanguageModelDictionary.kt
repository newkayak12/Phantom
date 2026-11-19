package com.intellij.searchEverywhereMl.typos.models

// phantom-idea: no longer extends the closed Grazie WordList/FrequencyMetadata; ActionsSpeller reads it directly
internal interface LanguageModelDictionary {
  val totalFrequency: Int
  val allWords: Set<String>
  val maxFrequency: Int

  fun getFrequency(word: String): Int?
  
  companion object {
    const val MAX_LEVENSHTEIN_DISTANCE = 3
  }
}
