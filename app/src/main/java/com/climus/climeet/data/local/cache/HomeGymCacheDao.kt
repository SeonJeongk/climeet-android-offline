package com.climus.climeet.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeGymCacheDao {
    @Query("SELECT * FROM home_gym_cache_item WHERE accountId = :accountId ORDER BY sortOrder")
    abstract fun observeHomeGyms(accountId: Long): Flow<List<HomeGymEntity>>

    @Query("SELECT * FROM home_gym_cache_metadata WHERE accountId = :accountId")
    abstract fun observeHomeGymMetadata(accountId: Long): Flow<HomeGymCacheMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertHomeGymMetadata(entity: HomeGymCacheMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertHomeGyms(entities: List<HomeGymEntity>)

    @Query("DELETE FROM home_gym_cache_metadata WHERE accountId = :accountId")
    abstract suspend fun clearHomeGyms(accountId: Long)

    @Transaction
    open suspend fun replaceHomeGyms(
        metadata: HomeGymCacheMetadataEntity,
        gyms: List<HomeGymEntity>,
    ) {
        clearHomeGyms(metadata.accountId)
        insertHomeGymMetadata(metadata)
        insertHomeGyms(gyms)
    }
}
