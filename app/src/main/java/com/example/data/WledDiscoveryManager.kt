package com.example.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress

class WledDiscoveryManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val mutex = Mutex()
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoveryListenerHSL: NsdManager.DiscoveryListener? = null

    data class DiscoveredDevice(
        val name: String,
        val ipAddress: String,
        val port: Int,
        val serviceType: String
    )

    fun startDiscovery() {
        val manager = nsdManager
        if (manager == null) {
            Log.e("WledDiscovery", "NsdManager in system is NULL/unsupported - service discovery disabled")
            return
        }
        stopDiscovery()
        _discoveredDevices.value = emptyList()

        Log.i("WledDiscovery", "Starting Network Service Discovery (NSD)...")

        // Discover HSL services only
        discoveryListenerHSL = createDiscoveryListener("_hsl._tcp")
        try {
            manager.discoverServices("_hsl._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListenerHSL)
            Log.i("WledDiscovery", "Registered discovery listener for _hsl._tcp")
        } catch (e: Exception) {
            Log.e("WledDiscovery", "Failed to start HSL service discovery", e)
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        Log.i("WledDiscovery", "Stopping Network Service Discovery (NSD)...")
        
        discoveryListenerHSL?.let {
            try {
                manager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("WledDiscovery", "Failed to stop HSL discovery", e)
            }
        }
        discoveryListenerHSL = null
    }

    private fun createDiscoveryListener(targetServiceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("WledDiscovery", "Discovery failed to start for $serviceType: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("WledDiscovery", "Discovery failed to stop for $serviceType: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d("WledDiscovery", "Discovery started for service type: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d("WledDiscovery", "Discovery stopped for service type: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                Log.i("WledDiscovery", "Service found! Name: ${serviceInfo.serviceName}, Type: ${serviceInfo.serviceType}")
                
                discoveryScope.launch {
                    resolveServiceWithRetry(serviceInfo, targetServiceType)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                Log.i("WledDiscovery", "Service lost! Name: ${serviceInfo.serviceName}")
                val nameToRemove = serviceInfo.serviceName
                _discoveredDevices.value = _discoveredDevices.value.filter { it.name != nameToRemove }
            }
        }
    }

    /**
     * Resolves NsdServiceInfo sequentially using a Mutex to avoid concurrent resolve errors on Android.
     */
    private suspend fun resolveServiceWithRetry(serviceInfo: NsdServiceInfo, targetServiceType: String) {
        mutex.withLock {
            val completer = CompletableDeferred<DiscoveredDevice?>()
            
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e("WledDiscovery", "Resolve failed for ${serviceInfo?.serviceName}: Error $errorCode")
                    completer.complete(null)
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                    try {
                        if (resolvedServiceInfo == null) {
                            completer.complete(null)
                            return
                        }
                        val host: InetAddress? = resolvedServiceInfo.host
                        if (host == null) {
                            Log.e("WledDiscovery", "Resolved service has null host")
                            completer.complete(null)
                            return
                        }
                        var ip = host.hostAddress ?: ""
                        if (ip.startsWith("/")) {
                            ip = ip.substring(1)
                        }

                        // Avoid link-local or raw IPv6 representation if we can clean it, but keep Valid IPv4
                        if (ip.contains("%") || ip.contains(":")) {
                            Log.d("WledDiscovery", "Ignoring IPv6 or link-local address for simplicity: $ip")
                            completer.complete(null)
                            return
                        }

                        val name = resolvedServiceInfo.serviceName ?: "HSL Device"
                        val port = resolvedServiceInfo.port
                        val device = DiscoveredDevice(
                            name = name,
                            ipAddress = ip,
                            port = port,
                            serviceType = targetServiceType
                        )
                        Log.i("WledDiscovery", "Successfully resolved: $name. IP address: $ip, Port: $port")
                        completer.complete(device)
                    } catch (e: Exception) {
                        Log.e("WledDiscovery", "Exception in onServiceResolved callback", e)
                        completer.complete(null)
                    }
                }
            }

            try {
                Log.d("WledDiscovery", "Requesting NsdManager resolve for service: ${serviceInfo.serviceName}")
                nsdManager?.resolveService(serviceInfo, resolveListener)
                
                // Wait for the asynchronous resolution with a timeout
                val resolvedDevice = withTimeoutOrNull(4000) { completer.await() }
                if (resolvedDevice != null && resolvedDevice.ipAddress.isNotEmpty()) {
                    val currentList = _discoveredDevices.value
                    if (currentList.none { it.ipAddress == resolvedDevice.ipAddress }) {
                        _discoveredDevices.value = currentList + resolvedDevice
                    }
                }
            } catch (e: Exception) {
                Log.e("WledDiscovery", "Exception resolving service: ${serviceInfo.serviceName}", e)
            }
        }
    }
}
