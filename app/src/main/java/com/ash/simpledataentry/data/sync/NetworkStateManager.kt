package com.ash.simpledataentry.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkState(
    val isConnected: Boolean = false,
    val hasInternet: Boolean = false,
    val isMetered: Boolean = false,
    val networkType: NetworkType = NetworkType.NONE
)

enum class NetworkType {
    NONE, WIFI, CELLULAR, ETHERNET, BLUETOOTH, VPN
}

@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
    
    private val _networkState = MutableStateFlow(getCurrentNetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkState.value = getCurrentNetworkState()
        }
        
        override fun onLost(network: Network) {
            _networkState.value = getCurrentNetworkState()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkState.value = getCurrentNetworkState()
        }
    }
    
    init {
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            // Handle registration failure gracefully
        }
    }
    
    private fun getCurrentNetworkState(): NetworkState {
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager?.getNetworkCapabilities(it) }
        
        val isConnected = activeNetwork != null && capabilities != null
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                         capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        
        val networkType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.CELLULAR
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetworkType.ETHERNET
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> NetworkType.BLUETOOTH
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> NetworkType.VPN
            else -> NetworkType.NONE
        }
        
        return NetworkState(
            isConnected = isConnected,
            hasInternet = hasInternet,
            isMetered = isMetered,
            networkType = networkType
        )
    }
    
    fun isOnline(): Boolean = networkState.value.hasInternet
    
    fun isWifiConnected(): Boolean = networkState.value.networkType == NetworkType.WIFI
    
    fun unregisterCallback() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Handle unregister failure gracefully
        }
    }
}