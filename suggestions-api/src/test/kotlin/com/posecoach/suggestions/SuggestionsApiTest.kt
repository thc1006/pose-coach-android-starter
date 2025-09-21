package com.posecoach.suggestions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SuggestionsApiTest {
    @Test fun fake_returns_three_items() = runBlocking {
        val client = FakeSuggestionClient()
        val out = client.getPoseSuggestions(emptyList())
        assertThat(out.suggestions).hasSize(3)
        assertThat(out.suggestions[0].title).isNotEmpty()
    }
}
