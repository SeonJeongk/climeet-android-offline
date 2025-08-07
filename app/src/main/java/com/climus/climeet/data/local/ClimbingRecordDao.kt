package com.climus.climeet.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ClimbingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(climbingRecord: ClimbingRecordData)

    @Update
    suspend fun update(climbingRecord: ClimbingRecordData)

    @Delete
    suspend fun delete(climbingRecord: ClimbingRecordData)

    @Query("DELETE FROM climbing_record")
    suspend fun deleteAllRecord()

    @Query("SELECT * FROM climbing_record")
    suspend fun getAllRecord(): List<ClimbingRecordData>

    @Query("SELECT * FROM climbing_record WHERE id = :id")
    suspend fun getRecord(id: Int): ClimbingRecordData?
}
