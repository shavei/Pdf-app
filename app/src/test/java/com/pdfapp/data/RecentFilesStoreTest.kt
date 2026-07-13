package com.pdfapp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentFilesStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val storeScope = CoroutineScope(dispatcher + SupervisorJob())

    private fun newStore(): RecentFilesStore =
        RecentFilesStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                folder.newFile("recents.preferences_pb")
            },
        )

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun entry(
        uri: String,
        lastPage: Int = 0,
    ) = RecentFile(
        uri = uri,
        displayName = "doc-$uri.pdf",
        pageCount = 10,
        lastPageIndex = lastPage,
        lastOpenedEpochMillis = 1_000L,
    )

    @Test
    fun `recordOpen puts newest first, dedupes by uri and caps the list`() =
        testScope.runTest {
            val store = newStore()

            (1..12).forEach { store.recordOpen(entry(uri = "file-$it")) }
            store.recordOpen(entry(uri = "file-5"))

            val recents = store.recents.first()
            assertThat(recents).hasSize(10)
            assertThat(recents.first().uri).isEqualTo("file-5")
            assertThat(recents.count { it.uri == "file-5" }).isEqualTo(1)
        }

    @Test
    fun `updateLastPage rewrites only the matching entry`() =
        testScope.runTest {
            val store = newStore()
            store.recordOpen(entry(uri = "a"))
            store.recordOpen(entry(uri = "b"))

            store.updateLastPage("a", pageIndex = 7)

            val recents = store.recents.first()
            assertThat(recents.first { it.uri == "a" }.lastPageIndex).isEqualTo(7)
            assertThat(recents.first { it.uri == "b" }.lastPageIndex).isEqualTo(0)
        }

    @Test
    fun `remove drops the entry`() =
        testScope.runTest {
            val store = newStore()
            store.recordOpen(entry(uri = "a"))
            store.recordOpen(entry(uri = "b"))

            store.remove("a")

            assertThat(store.recents.first().map { it.uri }).containsExactly("b")
        }

    @Test
    fun `codec round-trips and survives malformed json`() {
        val files = listOf(entry("a", lastPage = 3), entry("b"))

        assertThat(RecentFilesCodec.decode(RecentFilesCodec.encode(files))).isEqualTo(files)
        assertThat(RecentFilesCodec.decode("not json")).isEmpty()
    }
}
