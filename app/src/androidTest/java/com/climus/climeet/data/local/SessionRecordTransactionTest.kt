package com.climus.climeet.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerRecordTransactionTest {
    private lateinit var database: ClimbingDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ClimbingDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completedDifficulty_query_includesTenAndExcludesLegacyValues() =
        runBlocking {
            database.RouteRecordDao().insert(route(10, completed = true))
            database.RouteRecordDao().insert(route(12, completed = true))
            database.RouteRecordDao().insert(route(-1, completed = true))
            database.RouteRecordDao().insert(route(1, completed = false))

            assertEquals(11.0, database.RouteRecordDao().getAverageDifficultyOfCompleted() ?: 0.0, 0.0)
        }

    @Test
    fun deleteTransaction_failure_rollsBackBothTables() =
        runBlocking {
            database.ClimbingRecordDao().insert(ClimbingRecordData(id = 1, gymId = 1))
            database.RouteRecordDao().insert(route(3, completed = true))

            runCatching {
                database.withTransaction {
                    database.RouteRecordDao().deleteAllRoute()
                    error("forced rollback")
                }
            }

            assertNotNull(database.ClimbingRecordDao().getRecord(1))
            assertEquals(1, database.RouteRecordDao().getAllRoute().size)
        }

    private fun route(
        difficulty: Int,
        completed: Boolean,
    ) = RouteRecordData(
        routeId = difficulty.toLong(),
        difficulty = difficulty,
        isCompleted = completed,
    )
}
