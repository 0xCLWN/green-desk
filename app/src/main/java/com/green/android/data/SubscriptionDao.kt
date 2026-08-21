package com.green.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    fun getAllFlow(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    suspend fun getAll(): List<Subscription>

    @Insert
    suspend fun insert(sub: Subscription): Long

    @Delete
    suspend fun delete(sub: Subscription)
}
