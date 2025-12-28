package com.climus.climeet.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.climus.climeet.data.local.cache.RouteCacheDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class RouteCacheControlReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RouteCacheControlEntryPoint {
        fun routeCacheDao(): RouteCacheDao
    }

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
        // 앱이 쓰는 Room 인스턴스로 바꿔야 열려 있는 화면의 Flow가 변경을 받습니다.
        val routeCacheDao =
            EntryPointAccessors
                .fromApplication(context, RouteCacheControlEntryPoint::class.java)
                .routeCacheDao()
        runBlocking(Dispatchers.IO) {
            when (action) {
                CLEAR_ACTION -> routeCacheDao.deleteAllCatalogs()
                EXPIRE_ACTION -> routeCacheDao.expireAllCatalogs(System.currentTimeMillis())
            }
        }
        resultCode = Activity.RESULT_OK
        resultData = action
    }

    private companion object {
        const val CLEAR_ACTION = "com.climus.climeet.benchmark.CLEAR_ROUTE_CACHE"
        const val EXPIRE_ACTION = "com.climus.climeet.benchmark.EXPIRE_ROUTE_CACHE"
    }
}
