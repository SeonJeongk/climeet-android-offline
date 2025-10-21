package com.climus.climeet.data.repository.route

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NetworkAvailability {
    fun isDefinitelyOffline(): Boolean
}

class AndroidNetworkAvailability @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkAvailability {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isDefinitelyOffline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
