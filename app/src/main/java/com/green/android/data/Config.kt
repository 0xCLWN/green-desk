package com.green.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "configs")
data class Config(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val vlessLink: String? = null,   // set when added via vless:// key; config regenerated at connect time
    val configJson: String? = null,  // set when added via raw JSON; used as-is
    val subscriptionId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
