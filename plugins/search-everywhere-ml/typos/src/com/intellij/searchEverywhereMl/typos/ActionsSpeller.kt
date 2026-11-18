// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.typos

import com.intellij.searchEverywhereMl.typos.models.LanguageModelDictionary
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Suggests words of action and setting names for a misspelled query word.
 *
 * Candidates are vocabulary words within an optimal string alignment (Damerau-Levenshtein) distance
 * that grows with the word length, up to [LanguageModelDictionary.MAX_LEVENSHTEIN_DISTANCE].
 * They are ranked by distance first and by frequency in the vocabulary second.
 */
internal class ActionsSpeller(private val dictionary: LanguageModelDictionary) {
  private val vocabulary: Set<String> = dictionary.allWords

  /** Vocabulary words grouped by length, so only words of a close length are compared. */
  private val wordsByLength: Array<Array<String>> = run {
    val maxLength = vocabulary.maxOfOrNull { it.length } ?: 0
    val buckets = Array(maxLength + 1) { ArrayList<String>() }
    for (word in vocabulary) buckets[word.length].add(word)
    Array(buckets.size) { buckets[it].toTypedArray() }
  }

  private val frequencyNorm: Double = ln(1.0 + max(dictionary.maxFrequency, 1))

  /**
   * Returns up to [max] vocabulary words for [word] with confidences in (0, 1], best first.
   * Returns an empty map when the word is in the vocabulary (case-insensitive), is shorter than
   * [MIN_WORD_LENGTH], or has no Latin letter.
   */
  fun suggestAndRank(word: String, max: Int): Map<String, Double> {
    if (max <= 0) return emptyMap()
    val query = word.lowercase()
    if (query.length < MIN_WORD_LENGTH || query.none { it in 'a'..'z' }) return emptyMap()
    if (query in vocabulary) return emptyMap()

    val bound = distanceBound(query.length)
    val buffers = DistanceBuffers(query.length + bound + 1)
    val candidates = ArrayList<Candidate>()
    val minLength = max(1, query.length - bound)
    val maxLength = min(wordsByLength.lastIndex, query.length + bound)
    for (length in minLength..maxLength) {
      for (candidate in wordsByLength[length]) {
        val distance = osaDistance(query, candidate, bound, buffers)
        if (distance <= bound) {
          candidates.add(Candidate(candidate, distance, score(distance, dictionary.getFrequency(candidate) ?: 0)))
        }
      }
    }
    if (candidates.isEmpty()) return emptyMap()

    candidates.sortWith(compareByDescending<Candidate> { it.score }.thenBy { it.word })
    val best = candidates.first()
    val topConfidence = exp(-TOP_DISTANCE_DECAY * best.distance)
    val result = LinkedHashMap<String, Double>()
    for (candidate in candidates.take(max)) {
      result[candidate.word] = candidate.score / best.score * topConfidence
    }
    return result
  }

  private fun score(distance: Int, frequency: Int): Double =
    exp(-DISTANCE_WEIGHT * distance) * (1.0 + ln(1.0 + frequency) / frequencyNorm)

  private class Candidate(val word: String, val distance: Int, val score: Double)

  private class DistanceBuffers(size: Int) {
    var beforePrevious = IntArray(size)
    var previous = IntArray(size)
    var current = IntArray(size)
  }

  companion object {
    const val MIN_WORD_LENGTH: Int = 3

    /** How fast a candidate's score falls with each edit. */
    private const val DISTANCE_WEIGHT = 1.5

    /** How fast the confidence of the best candidate falls with each edit. */
    private const val TOP_DISTANCE_DECAY = 0.15

    private fun distanceBound(length: Int): Int = when {
      length <= 4 -> 1
      length <= 8 -> 2
      else -> LanguageModelDictionary.MAX_LEVENSHTEIN_DISTANCE
    }

    /**
     * Optimal string alignment distance between [a] and [b] (insertions, deletions, substitutions and
     * transpositions of adjacent letters). Returns `bound + 1` as soon as the distance exceeds [bound].
     */
    private fun osaDistance(a: String, b: String, bound: Int, buffers: DistanceBuffers): Int {
      if (abs(a.length - b.length) > bound) return bound + 1
      var beforePrevious = buffers.beforePrevious
      var previous = buffers.previous
      var current = buffers.current
      for (j in 0..b.length) previous[j] = j
      for (i in 1..a.length) {
        current[0] = i
        var rowMin = i
        for (j in 1..b.length) {
          val cost = if (a[i - 1] == b[j - 1]) 0 else 1
          var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
          if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
            value = min(value, beforePrevious[j - 2] + 1)
          }
          current[j] = value
          if (value < rowMin) rowMin = value
        }
        if (rowMin > bound) return bound + 1
        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
      }
      return min(previous[b.length], bound + 1)
    }
  }
}
