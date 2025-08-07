package com.climus.climeet.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RouteRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routeRecord: RouteRecordData)

    @Update
    suspend fun update(routeRecord: RouteRecordData)

    @Delete
    suspend fun delete(routeRecord: RouteRecordData)

    @Query("DELETE FROM route_record")
    suspend fun deleteAllRoute()

    @Query("DELETE FROM route_record WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM route_record")
    suspend fun getAllRoute(): List<RouteRecordData>

    @Query("SELECT * FROM route_record WHERE id = :id")
    suspend fun getRouteById(id: Int): RouteRecordData

    @Query("SELECT * FROM route_record WHERE sectorId = :sectorId AND routeId = :routeId LIMIT 1")
    suspend fun findExistRoute(
        sectorId: Long,
        routeId: Long,
    ): RouteRecordData?

    @Query("SELECT AVG(difficulty) FROM route_record WHERE isCompleted = 1 AND difficulty BETWEEN 0 AND 12")
    suspend fun getAverageDifficultyOfCompleted(): Double?

    @Query("SELECT * FROM route_record WHERE levelName IN (SELECT DISTINCT levelName FROM route_record)")
    suspend fun getAllLevelRecord(): List<RouteRecordData>

    @Query("SELECT COUNT(*) FROM route_record WHERE levelName = :level AND isCompleted = 1")
    suspend fun getSuccessCount(level: String): Int

    @Query("SELECT COUNT(*) FROM route_record WHERE levelName = :level")
    suspend fun getAttemptCount(level: String): Int
}
