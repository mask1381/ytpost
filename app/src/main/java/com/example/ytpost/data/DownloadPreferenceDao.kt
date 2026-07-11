package com.example.ytpost.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DownloadPreferenceDao {
    @Query("SELECT * FROM download_preferences")
    suspend fun getAll(): List<DownloadPreferenceProfile>

    @Query("SELECT * FROM download_preferences WHERE sourceType = :sourceType AND sourceIdentifier = :sourceIdentifier LIMIT 1")
    suspend fun getPreference(sourceType: String, sourceIdentifier: String?): DownloadPreferenceProfile?

    @Query("SELECT * FROM download_preferences WHERE sourceType = 'manual' AND sourceIdentifier IS NULL LIMIT 1")
    suspend fun getManualDefault(): DownloadPreferenceProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: DownloadPreferenceProfile)

    @Update
    suspend fun update(preference: DownloadPreferenceProfile)

    @Delete
    suspend fun delete(preference: DownloadPreferenceProfile)

    @Query("DELETE FROM download_preferences WHERE sourceType = 'rss' AND sourceIdentifier = :identifier")
    suspend fun deleteByIdentifier(identifier: String)
}
