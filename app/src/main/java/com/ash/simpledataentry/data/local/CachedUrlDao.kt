package com.ash.simpledataentry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedUrlDao {
    
    @Query("SELECT * FROM cached_urls WHERE isValid = 1 ORDER BY frequency DESC, lastUsed DESC LIMIT 10")
    suspend fun getCachedUrls(): List<CachedUrlEntity>
    
    @Query("SELECT * FROM cached_urls WHERE isValid = 1 ORDER BY frequency DESC, lastUsed DESC")
    suspend fun getAllCachedUrls(): List<CachedUrlEntity>
    
    @Query("SELECT * FROM cached_urls WHERE url = :url")
    suspend fun getCachedUrl(url: String): CachedUrlEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedUrl(cachedUrl: CachedUrlEntity)
    
    @Query("UPDATE cached_urls SET frequency = frequency + 1, lastUsed = :timestamp WHERE url = :url")
    suspend fun incrementUrlUsage(url: String, timestamp: Long)
    
    @Query("DELETE FROM cached_urls WHERE url = :url")
    suspend fun deleteCachedUrl(url: String)
    
    @Query("DELETE FROM cached_urls WHERE isValid = 0")
    suspend fun deleteInvalidUrls()
    
    @Query("DELETE FROM cached_urls WHERE url NOT IN (SELECT url FROM cached_urls ORDER BY frequency DESC, lastUsed DESC LIMIT 20)")
    suspend fun cleanupOldUrls()
}
