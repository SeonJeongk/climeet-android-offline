package com.climus.climeet.data.local.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "home_gym_cache_metadata",
    primaryKeys = ["accountId"],
)
data class HomeGymCacheMetadataEntity(
    val accountId: Long,
    val receivedAtEpochMs: Long,
)

@Entity(
    tableName = "home_gym_cache_item",
    primaryKeys = ["accountId", "gymId"],
    foreignKeys = [
        ForeignKey(
            entity = HomeGymCacheMetadataEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId"])],
)
data class HomeGymEntity(
    val accountId: Long,
    val gymId: Long,
    val sortOrder: Int,
    val gymProfileUrl: String,
    val gymName: String,
    val followerCount: Int,
)
