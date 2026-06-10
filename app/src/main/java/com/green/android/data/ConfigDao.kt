package com.green.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Config>>

    @Query("SELECT * FROM configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Config?

    @Insert
    suspend fun insert(config: Config): Long

    @Delete
    suspend fun delete(config: Config)

    @Update
    suspend fun update(config: Config)

    @Query("SELECT * FROM configs WHERE subscriptionId = :subId")
    suspend fun getBySubscriptionId(subId: Int): List<Config>

    @Query("DELETE FROM configs WHERE subscriptionId = :subId")
    suspend fun deleteBySubscriptionId(subId: Int)
}
