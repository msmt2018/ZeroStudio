package android.zero.studio.shell.common.data.repository

import android.zero.studio.core.domain.model.SortType
import android.zero.studio.shell.common.data.database.BookmarkDao
import android.zero.studio.shell.common.data.model.BookmarkEntity
import android.zero.studio.shell.common.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao
) : BookmarkRepository {

    override suspend fun addBookmark(command: String) {
        dao.addBookmark(BookmarkEntity(command = command))
    }

    override suspend fun deleteBookmarkByCommand(command: String) {
        dao.deleteBookmarkByCommand(command)
    }

    override suspend fun deleteAllBookmarks() {
        dao.deleteAllBookmarks()
    }

    override suspend fun insertAllBookmarks(bookmarks: List<BookmarkEntity>) {
        dao.insertAllBookmarks(bookmarks)
    }

    override fun isBookmarked(command: String): Flow<Boolean> {
        return dao.isBookmarked(command)
    }

    override fun getBookmarkCount(): Flow<Int> {
        return dao.getBookmarkCount()
    }

    override suspend fun getBookmarksSorted(sortType: Int): List<BookmarkEntity> {
        return when (sortType) {
            SortType.AZ -> dao.getBookmarksSortedAZ()
            SortType.ZA -> dao.getBookmarksSortedZA()
            SortType.NEWEST -> dao.getBookmarksSortedNewest()
            SortType.OLDEST -> dao.getBookmarksSortedOldest()
            else -> dao.getBookmarksSortedAZ()
        }
    }

    override fun getSortedBookmarksFlow(sortType: Int): Flow<List<BookmarkEntity>> {
        return when (sortType) {
            SortType.AZ -> dao.getFlowBookmarksSortedAZ()
            SortType.ZA -> dao.getFlowBookmarksSortedZA()
            SortType.NEWEST -> dao.getFlowBookmarksSortedNewest()
            SortType.OLDEST -> dao.getFlowBookmarksSortedOldest()
            else -> dao.getFlowBookmarksSortedAZ()
        }
    }
}