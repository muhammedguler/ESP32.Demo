/**
 * Created by onur on 9.06.2023
 */

package com.atlastek.eps32

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlastek.eps32.ble.BLEApi
import com.atlastek.eps32.ble.BLEApiImpl
import com.atlastek.eps32.ble.calcDistbyRSSI
import com.atlastek.eps32.ble.model.BLEState
import com.atlastek.eps32.ble.model.mapToBLE
import com.atlastek.eps32.ble.remoteRssi
import com.juul.kable.Peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber

class MainViewModel : ViewModel() {
    val bleApi: BLEApi

    private var bleJob: Job? = null
    var state = MutableLiveData<BLEState>().apply { value = BLEState.Disconnected }
    var rssi = MutableLiveData<Int>().apply { value = 0 }
    var value = MutableLiveData<String>()


    var peripheral: Peripheral? = null

    init {
        bleApi = BLEApiImpl(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    fun connect() {
        if (state.value == BLEState.Disconnected) {
            state.postValue(BLEState.Connecting)
            bleJob?.cancel()
            bleJob = viewModelScope.launch {
                val peripheral = bleApi.scanAndConnect()
                this@MainViewModel.peripheral = peripheral
                if (peripheral != null) {
                    viewModelScope.launch {
                        peripheral.state.collect {
                            state.postValue(it.mapToBLE())
                            if (it.mapToBLE() == BLEState.Disconnected) {
                                rssi.postValue(0)
                            }
                        }
                    }

                    val characteristic = bleApi.characteristic(peripheral)
                    if (characteristic != null) {
                        viewModelScope.launch {
                            peripheral.observe(characteristic).collect {
                                value.postValue(it.toString(Charsets.UTF_8))
                            }
                        }
                    } else {
                        peripheral.disconnect()
                        state.postValue(BLEState.Disconnected)
                        rssi.postValue(0)
                    }
                    viewModelScope.launch {
                        peripheral.remoteRssi().collect {
                            rssi.postValue(it.calcDistbyRSSI())
                        }
                    }
                } else {
                    state.postValue(BLEState.Disconnected)
                }

            }
        } else {
            state.postValue(BLEState.Disconnected)
            rssi.postValue(0)
            viewModelScope.launch {
                peripheral?.disconnect()
            }
        }
    }
}