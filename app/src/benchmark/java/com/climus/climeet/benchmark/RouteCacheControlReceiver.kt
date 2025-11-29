package com.climus.climeet.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.climus.climeet.data.local.routecache.RouteCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class RouteCacheControlReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != CLEAR_ACTION && action != EXPIRE_ACTION) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "unsupported-action"
            return
        }
        runBlocking(Dispatchers.IO) {
            val database = Room.databaseBuilder(context, RouteCacheDatabase::class.java, DATABASE_NAME).build()
            try {
                when (action) {
                    CLEAR_ACTION -> database.routeCacheDao().deleteAllCatalogs()
                    EXPIRE_ACTION -> database.routeCacheDao().expireAllCatalogs(System.currentTimeMillis())
                }
            } finally {
                database.close()
            }
        }
        resultCode = Activity.RESULT_OK
        resultData = action
    }

    private companion object {
        const val DATABASE_NAME = "route_catalog_cache.db"
        const val CLEAR_ACTION = "com.climus.climeet.benchmark.CLEAR_ROUTE_CACHE"
        const val EXPIRE_ACTION = "com.climus.climeet.benchmark.EXPIRE_ROUTE_CACHE"
    }
}
