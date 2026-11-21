package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val streamUrl: String,
    val artworkUri: String?,
    val localFilePath: String,
    val qualityName: String,
    val statusName: String,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val sha256Hex: String,
    val licenseName: String,
    val errorMessage: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey val name: String,
    val bandLevelsCsv: String,
    val bassBoost: Int,
    val virtualizer: Int,
    val trebleBoost: Int
)

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val trackKey: String,
    val rawLrc: String,
    val languageTag: String = "en",
    val offsetMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownload(trackId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteById(trackId: String)
}

@Dao
interface EqPresetDao {
    @Query("SELECT * FROM eq_presets ORDER BY name ASC")
    fun observePresets(): Flow<List<EqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface CachedLyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getLyrics(trackKey: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedLyricsEntity)
}

@Database(
    entities = [DownloadEntity::class, EqPresetEntity::class, CachedLyricsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MusicVerseDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun eqPresetDao(): EqPresetDao
    abstract fun cachedLyricsDao(): CachedLyricsDao

    companion object {
        @Volatile
        private var INSTANCE: MusicVerseDatabase? = null

        fun getInstance(context: Context): MusicVerseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicVerseDatabase::class.java,
                    "musicverse_local_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
