package com.phantom.tube.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class PhantomDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val tableChangeEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)

    private fun notifyTableChanged(tableName: String) {
        tableChangeEvents.tryEmit(tableName)
    }

    private fun <T> observeTable(tableName: String, queryBlock: () -> T): Flow<T> = flow {
        emit(queryBlock())
        tableChangeEvents.collect { changedTable ->
            if (changedTable == tableName) {
                emit(queryBlock())
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive migration: ensure all expected tables and indexes exist without wiping user data
        createTables(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Preserve user data safely on downgrade
        createTables(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_history (
                videoId TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                channelTitle TEXT NOT NULL,
                channelId TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                durationText TEXT NOT NULL,
                lastPositionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                watchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt ON watch_history(watchedAt)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorites (
                videoId TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                channelTitle TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                durationText TEXT NOT NULL,
                savedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_savedAt ON favorites(savedAt)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                `query` TEXT NOT NULL PRIMARY KEY,
                searchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_searchedAt ON search_history(searchedAt)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subscriptions (
                channelId TEXT NOT NULL PRIMARY KEY,
                channelTitle TEXT NOT NULL,
                channelHandle TEXT NOT NULL,
                channelAvatarUrl TEXT NOT NULL,
                subscriberCountText TEXT NOT NULL,
                subscribedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloads (
                id TEXT NOT NULL PRIMARY KEY,
                videoId TEXT NOT NULL,
                title TEXT NOT NULL,
                channelTitle TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                format TEXT NOT NULL,
                qualityLabel TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileUri TEXT NOT NULL,
                filePath TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                downloadedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_downloadedAt ON downloads(downloadedAt)")
    }

    private val watchHistoryDaoInstance = object : WatchHistoryDao {
        override suspend fun insertOrUpdate(entry: WatchHistoryEntity) = withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put("videoId", entry.videoId)
                put("title", entry.title)
                put("channelTitle", entry.channelTitle)
                put("channelId", entry.channelId)
                put("thumbnailUrl", entry.thumbnailUrl)
                put("durationText", entry.durationText)
                put("lastPositionMs", entry.lastPositionMs)
                put("durationMs", entry.durationMs)
                put("watchedAt", entry.watchedAt)
            }
            writableDatabase.insertWithOnConflict("watch_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("watch_history")
        }

        override fun getAllHistory(): Flow<List<WatchHistoryEntity>> =
            observeTable("watch_history") { queryHistory(200) }

        override suspend fun getRecentWatched(limit: Int): List<WatchHistoryEntity> =
            withContext(Dispatchers.IO) { queryHistory(limit) }

        override suspend fun getEntry(videoId: String): WatchHistoryEntity? = withContext(Dispatchers.IO) {
            readableDatabase.rawQuery(
                "SELECT videoId, title, channelTitle, channelId, thumbnailUrl, durationText, lastPositionMs, durationMs, watchedAt FROM watch_history WHERE videoId = ? LIMIT 1",
                arrayOf(videoId)
            ).use { c ->
                if (c.moveToFirst()) {
                    WatchHistoryEntity(
                        videoId = c.getString(0),
                        title = c.getString(1),
                        channelTitle = c.getString(2),
                        channelId = c.getString(3),
                        thumbnailUrl = c.getString(4),
                        durationText = c.getString(5),
                        lastPositionMs = c.getLong(6),
                        durationMs = c.getLong(7),
                        watchedAt = c.getLong(8)
                    )
                } else null
            }
        }

        override suspend fun getLastPosition(videoId: String): Long? = withContext(Dispatchers.IO) {
            readableDatabase.rawQuery(
                "SELECT lastPositionMs FROM watch_history WHERE videoId = ? LIMIT 1",
                arrayOf(videoId)
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }

        override suspend fun delete(videoId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("watch_history", "videoId = ?", arrayOf(videoId))
            notifyTableChanged("watch_history")
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete("watch_history", null, null)
            notifyTableChanged("watch_history")
        }

        private fun queryHistory(limit: Int?): List<WatchHistoryEntity> {
            val sql = buildString {
                append("SELECT videoId, title, channelTitle, channelId, thumbnailUrl, durationText, lastPositionMs, durationMs, watchedAt FROM watch_history ORDER BY watchedAt DESC")
                if (limit != null) append(" LIMIT $limit")
            }
            val list = ArrayList<WatchHistoryEntity>()
            readableDatabase.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        WatchHistoryEntity(
                            videoId = c.getString(0),
                            title = c.getString(1),
                            channelTitle = c.getString(2),
                            channelId = c.getString(3),
                            thumbnailUrl = c.getString(4),
                            durationText = c.getString(5),
                            lastPositionMs = c.getLong(6),
                            durationMs = c.getLong(7),
                            watchedAt = c.getLong(8)
                        )
                    )
                }
            }
            return list
        }
    }

    private val favoriteDaoInstance = object : FavoriteDao {
        override suspend fun insert(favorite: FavoriteEntity) = withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put("videoId", favorite.videoId)
                put("title", favorite.title)
                put("channelTitle", favorite.channelTitle)
                put("thumbnailUrl", favorite.thumbnailUrl)
                put("durationText", favorite.durationText)
                put("savedAt", favorite.savedAt)
            }
            writableDatabase.insertWithOnConflict("favorites", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("favorites")
        }

        override fun getAllFavorites(): Flow<List<FavoriteEntity>> = observeTable("favorites") {
            val list = ArrayList<FavoriteEntity>()
            readableDatabase.rawQuery(
                "SELECT videoId, title, channelTitle, thumbnailUrl, durationText, savedAt FROM favorites ORDER BY savedAt DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        FavoriteEntity(
                            videoId = c.getString(0),
                            title = c.getString(1),
                            channelTitle = c.getString(2),
                            thumbnailUrl = c.getString(3),
                            durationText = c.getString(4),
                            savedAt = c.getLong(5)
                        )
                    )
                }
            }
            list
        }

        override fun isFavorite(videoId: String): Flow<Boolean> = observeTable("favorites") {
            readableDatabase.rawQuery(
                "SELECT 1 FROM favorites WHERE videoId = ? LIMIT 1",
                arrayOf(videoId)
            ).use { it.moveToFirst() }
        }

        override suspend fun delete(videoId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("favorites", "videoId = ?", arrayOf(videoId))
            notifyTableChanged("favorites")
        }
    }

    private val searchHistoryDaoInstance = object : SearchHistoryDao {
        override suspend fun insertOrUpdate(entry: SearchHistoryEntity) = withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put("query", entry.query)
                put("searchedAt", entry.searchedAt)
            }
            writableDatabase.insertWithOnConflict("search_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("search_history")
        }

        override fun getRecentHistory(limit: Int): Flow<List<SearchHistoryEntity>> = observeTable("search_history") {
            val list = ArrayList<SearchHistoryEntity>()
            readableDatabase.rawQuery(
                "SELECT `query`, searchedAt FROM search_history ORDER BY searchedAt DESC LIMIT $limit",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(SearchHistoryEntity(query = c.getString(0), searchedAt = c.getLong(1)))
                }
            }
            list
        }

        override suspend fun delete(query: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("search_history", "`query` = ?", arrayOf(query))
            notifyTableChanged("search_history")
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete("search_history", null, null)
            notifyTableChanged("search_history")
        }
    }

    private val subscriptionDaoInstance = object : SubscriptionDao {
        override suspend fun insert(subscription: SubscriptionEntity) = withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put("channelId", subscription.channelId)
                put("channelTitle", subscription.channelTitle)
                put("channelHandle", subscription.channelHandle)
                put("channelAvatarUrl", subscription.channelAvatarUrl)
                put("subscriberCountText", subscription.subscriberCountText)
                put("subscribedAt", subscription.subscribedAt)
            }
            writableDatabase.insertWithOnConflict("subscriptions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("subscriptions")
        }

        override fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> =
            observeTable("subscriptions") { querySubscriptions(null) }

        override suspend fun getRecentSubscriptions(limit: Int): List<SubscriptionEntity> =
            withContext(Dispatchers.IO) { querySubscriptions(limit) }

        override fun isSubscribed(channelId: String): Flow<Boolean> = observeTable("subscriptions") {
            readableDatabase.rawQuery(
                "SELECT 1 FROM subscriptions WHERE channelId = ? LIMIT 1",
                arrayOf(channelId)
            ).use { it.moveToFirst() }
        }

        override suspend fun delete(channelId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("subscriptions", "channelId = ?", arrayOf(channelId))
            notifyTableChanged("subscriptions")
        }

        override fun getSubscriptionCount(): Flow<Int> = observeTable("subscriptions") {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM subscriptions", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }

        private fun querySubscriptions(limit: Int?): List<SubscriptionEntity> {
            val sql = buildString {
                append("SELECT channelId, channelTitle, channelHandle, channelAvatarUrl, subscriberCountText, subscribedAt FROM subscriptions ORDER BY subscribedAt DESC")
                if (limit != null) append(" LIMIT $limit")
            }
            val list = ArrayList<SubscriptionEntity>()
            readableDatabase.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        SubscriptionEntity(
                            channelId = c.getString(0),
                            channelTitle = c.getString(1),
                            channelHandle = c.getString(2),
                            channelAvatarUrl = c.getString(3),
                            subscriberCountText = c.getString(4),
                            subscribedAt = c.getLong(5)
                        )
                    )
                }
            }
            return list
        }
    }

    private val downloadDaoInstance = object : DownloadDao {
        override suspend fun insert(download: DownloadEntity) = withContext(Dispatchers.IO) {
            val cv = ContentValues().apply {
                put("id", download.id)
                put("videoId", download.videoId)
                put("title", download.title)
                put("channelTitle", download.channelTitle)
                put("thumbnailUrl", download.thumbnailUrl)
                put("format", download.format)
                put("qualityLabel", download.qualityLabel)
                put("fileName", download.fileName)
                put("fileUri", download.fileUri)
                put("filePath", download.filePath)
                put("fileSize", download.fileSize)
                put("downloadedAt", download.downloadedAt)
            }
            writableDatabase.insertWithOnConflict("downloads", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("downloads")
        }

        override fun getAllDownloads(): Flow<List<DownloadEntity>> = observeTable("downloads") {
            val list = ArrayList<DownloadEntity>()
            readableDatabase.rawQuery(
                "SELECT id, videoId, title, channelTitle, thumbnailUrl, format, qualityLabel, fileName, fileUri, filePath, fileSize, downloadedAt FROM downloads ORDER BY downloadedAt DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        DownloadEntity(
                            id = c.getString(0),
                            videoId = c.getString(1),
                            title = c.getString(2),
                            channelTitle = c.getString(3),
                            thumbnailUrl = c.getString(4),
                            format = c.getString(5),
                            qualityLabel = c.getString(6),
                            fileName = c.getString(7),
                            fileUri = c.getString(8),
                            filePath = c.getString(9),
                            fileSize = c.getLong(10),
                            downloadedAt = c.getLong(11)
                        )
                    )
                }
            }
            list
        }

        override fun isDownloaded(videoId: String): Flow<Boolean> = observeTable("downloads") {
            readableDatabase.rawQuery(
                "SELECT 1 FROM downloads WHERE videoId = ? LIMIT 1",
                arrayOf(videoId)
            ).use { it.moveToFirst() }
        }

        override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("downloads", "id = ?", arrayOf(id))
            notifyTableChanged("downloads")
        }

        override suspend fun deleteByVideoId(videoId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("downloads", "videoId = ?", arrayOf(videoId))
            notifyTableChanged("downloads")
        }

        override suspend fun clearAll() = withContext(Dispatchers.IO) {
            writableDatabase.delete("downloads", null, null)
            notifyTableChanged("downloads")
        }
    }

    fun watchHistoryDao(): WatchHistoryDao = watchHistoryDaoInstance
    fun favoriteDao(): FavoriteDao = favoriteDaoInstance
    fun searchHistoryDao(): SearchHistoryDao = searchHistoryDaoInstance
    fun subscriptionDao(): SubscriptionDao = subscriptionDaoInstance
    fun downloadDao(): DownloadDao = downloadDaoInstance

    companion object {
        private const val DATABASE_NAME = "phantom_database"
        private const val DATABASE_VERSION = 5

        @Volatile
        private var INSTANCE: PhantomDatabase? = null

        fun getInstance(context: Context): PhantomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhantomDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
