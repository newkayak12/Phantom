// Copyright 2026 Phantom contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.typos

import com.intellij.searchEverywhereMl.typos.models.LanguageModelDictionary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ActionsSpellerTest {
  private val speller = speller(
    "show" to 40, "color" to 12, "picker" to 5, "settings" to 30, "setting" to 3, "format" to 100, "forma" to 1,
    "code" to 50, "close" to 20, "project" to 25, "demo" to 10,
  )

  @Test
  fun transposition() {
    assertTrue("color" in speller.suggestAndRank("colr", 5))
    assertEquals("picker", speller.suggestAndRank("pikcer", 5).keys.first())
  }

  @Test
  fun `missing and extra letter`() {
    assertEquals("show", speller.suggestAndRank("shw", 5).keys.first())
    assertEquals("settings", speller.suggestAndRank("settigns", 5).keys.first())
    assertEquals("project", speller.suggestAndRank("projectt", 5).keys.first())
  }

  @Test
  fun `frequency breaks ties`() {
    assertEquals("format", speller.suggestAndRank("formt", 5).keys.first())
  }

  @Test
  fun `confidence is best first and a one edit fix passes the default threshold`() {
    val suggestions = speller.suggestAndRank("formt", 5)
    val confidences = suggestions.values.toList()
    assertEquals(confidences.sortedDescending(), confidences)
    assertTrue(confidences.first() > 0.7 && confidences.first() <= 1.0)
    assertTrue(confidences.all { it > 0.0 })
  }

  @Test
  fun `mixed case is matched case insensitively`() {
    assertEquals("picker", speller.suggestAndRank("PikCer", 5).keys.first())
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("Show", 5))
  }

  @Test
  fun `max limits the suggestions`() {
    assertEquals(1, speller.suggestAndRank("formt", 1).size)
  }

  @Test
  fun `only the closest candidates are returned`() {
    assertEquals(listOf("settings"), speller.suggestAndRank("settigns", 5).keys.toList())
  }

  @Test
  fun `two edits that change the first letter are not suggested`() {
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("lemon", 5))
  }

  @Test
  fun `known word untouched`() {
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("show", 5))
  }

  @Test
  fun `short word untouched`() {
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("gt", 5))
  }

  @Test
  fun `non latin untouched`() {
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("설정", 5))
    assertEquals(emptyMap<String, Double>(), speller.suggestAndRank("설정하기", 5))
  }

  @Test
  fun `empty vocabulary gives no suggestions`() {
    assertEquals(emptyMap<String, Double>(), speller().suggestAndRank("colr", 5))
  }

  private fun speller(vararg frequencies: Pair<String, Int>): ActionsSpeller {
    val map = frequencies.toMap()
    return ActionsSpeller(object : LanguageModelDictionary {
      override val totalFrequency: Int = map.values.sum()
      override val allWords: Set<String> = map.keys
      override val maxFrequency: Int = map.values.maxOrNull() ?: 0
      override fun getFrequency(word: String): Int? = map[word]
    })
  }
}
