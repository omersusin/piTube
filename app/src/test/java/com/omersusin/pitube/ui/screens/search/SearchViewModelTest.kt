package com.omersusin.pitube.ui.screens.search

import com.google.common.truth.Truth.assertThat
import com.omersusin.pitube.data.local.ContentType
import com.omersusin.pitube.data.local.SearchFilter
import com.omersusin.pitube.data.repository.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: YouTubeRepository = mockk(relaxed = true)
    private val playlistRepository: com.omersusin.pitube.data.repository.PlaylistRepository =
        mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial ui state has empty query and null filters`() {
        val viewModel = SearchViewModel(repository, playlistRepository)
        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.filters).isNull()
    }

    @Test
    fun `search with valid query updates uiState`() {
        val viewModel = SearchViewModel(repository, playlistRepository)
        viewModel.search("Kotlin Compose")

        val uiState = viewModel.uiState.value
        assertThat(uiState.query).isEqualTo("Kotlin Compose")
    }

    @Test
    fun `search with empty query resets uiState`() {
        val viewModel = SearchViewModel(repository, playlistRepository)
        viewModel.search("Kotlin")
        viewModel.search("")

        val uiState = viewModel.uiState.value
        assertThat(uiState.query).isEmpty()
        assertThat(uiState.filters).isNull()
    }

    @Test
    fun `updateFilters updates filters in uiState when query is active`() {
        val viewModel = SearchViewModel(repository, playlistRepository)
        viewModel.search("Music")

        val filter = SearchFilter(contentType = ContentType.VIDEOS)
        viewModel.updateFilters(filter)

        assertThat(viewModel.uiState.value.filters).isEqualTo(filter)
    }

    @Test
    fun `clearSearch resets search query and filters`() {
        val viewModel = SearchViewModel(repository, playlistRepository)
        viewModel.search("Android", SearchFilter(contentType = ContentType.PLAYLISTS))

        viewModel.clearSearch()

        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.filters).isNull()
    }

    @Test
    fun `getSearchSuggestions calls YouTubeRepository for valid query`() =
        runTest {
            val suggestions = listOf("kotlin tutorial", "kotlin android")
            coEvery { repository.getSearchSuggestions("kotlin") } returns suggestions

            val viewModel = SearchViewModel(repository, playlistRepository)
            val result = viewModel.getSearchSuggestions("kotlin")

            assertThat(result).isEqualTo(suggestions)
            coVerify(exactly = 1) { repository.getSearchSuggestions("kotlin") }
        }
}
