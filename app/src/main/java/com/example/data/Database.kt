package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val folderPath: String,
    val coverColorHex: String = "#FF6200EE", // Procedural visual colors
    val skipIntroSeconds: Int = -1, // -1 means "use global setting", 0 means "no skip", >0 means skip seconds
    val skipOutroSeconds: Int = -1, // -1 means "use global setting", 0 means "no skip", >0 means skip seconds
    val currentTrackIndex: Int = 0,
    val lastPlayedPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val trackNumber: Int // Sorted naturally
)

@Entity(tableName = "global_settings")
data class GlobalSettings(
    @PrimaryKey val id: Int = 1,
    val defaultSkipIntroSeconds: Int = 15,
    val defaultSkipOutroSeconds: Int = 10
)

// Relation class for combined queries
data class BookWithTracks(
    @Embedded val book: Book,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val tracks: List<Track>
) {
    // Return tracks ordered naturally by track number
    val sortedTracks: List<Track> get() = tracks.sortedBy { it.trackNumber }
}

// 2. DAOs

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM books ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getAllBooksFlow(): Flow<List<Book>>

    @Transaction
    @Query("SELECT * FROM books ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getAllBooksWithTracksFlow(): Flow<List<BookWithTracks>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun getBookWithTracksFlow(bookId: Long): Flow<BookWithTracks?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: Long): Book?

    @Query("SELECT * FROM books WHERE folderPath = :folderPath LIMIT 1")
    suspend fun getBookByFolderPath(folderPath: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Query("UPDATE books SET currentTrackIndex = :trackIndex, lastPlayedPositionMs = :positionMs, lastPlayedTimestamp = :timestamp WHERE id = :bookId")
    suspend fun updatePlaybackProgress(bookId: Long, trackIndex: Int, positionMs: Long, timestamp: Long)

    @Query("UPDATE books SET skipIntroSeconds = :seconds WHERE id = :bookId")
    suspend fun updateBookSkipIntro(bookId: Long, seconds: Int)

    @Query("UPDATE books SET skipOutroSeconds = :seconds WHERE id = :bookId")
    suspend fun updateBookSkipOutro(bookId: Long, seconds: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: Long)

    @Query("DELETE FROM tracks WHERE bookId = :bookId")
    suspend fun deleteTracksForBook(bookId: Long)

    // Global settings queries
    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    fun getGlobalSettingsFlow(): Flow<GlobalSettings?>

    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    suspend fun getGlobalSettingsSync(): GlobalSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalSettings(settings: GlobalSettings)
}

// 3. Database

@Database(entities = [Book::class, Track::class, GlobalSettings::class], version = 1, exportSchema = false)
abstract class AudiobookDatabase : RoomDatabase() {
    abstract val dao: AudiobookDao

    companion object {
        @Volatile
        private var INSTANCE: AudiobookDatabase? = null

        fun getInstance(context: android.content.Context): AudiobookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudiobookDatabase::class.java,
                    "audiobook_player.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
