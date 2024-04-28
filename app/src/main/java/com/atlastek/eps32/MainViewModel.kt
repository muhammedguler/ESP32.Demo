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
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainViewModel : ViewModel() {
    val bleApi: BLEApi

    private var bleJob: Job? = null
    var state = MutableLiveData<BLEState>().apply { value = BLEState.Disconnected }
    var rssi = MutableLiveData<Int>().apply { value = 0 }
    var value = MutableLiveData<String>()
    lateinit var ekgCh1Series: LineGraphSeries<DataPoint>
    lateinit var ekgCh2Series: LineGraphSeries<DataPoint>
    lateinit var ekgCh3Series: LineGraphSeries<DataPoint>
    private var graphLastXValue = 0.0
    var dataCount = 0
    var peripheral: Peripheral? = null
    var nowSecond = System.currentTimeMillis() / 1000.0
    var oldSecond = System.currentTimeMillis() / 1000.0

    init {
        bleApi = BLEApiImpl(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
    @OptIn(ExperimentalUnsignedTypes::class)
    fun messageParsing(scope: CoroutineScope, array: UByteArray) {
        val id =
            array[0].toString(16).padStart(2, '0') +
                    array[1].toString(16).padStart(2, '0') +
                    array[2].toString(16).padStart(2, '0') +
                    array[3].toString(16).padStart(2, '0')
        val batVoltage = (array[4].toInt() * 256 + array[5].toInt()) / 1000.0
        val batPercentage = array[6].toInt() * 0.4
        val temperature = array[7]
        val markArrhythmia = array[8]
        val chgStatus = array[9]
        val sampleRate = (array[10] * 256u + array[11]).toInt().toDouble()
        val time = (2000u + array[12]).toString() +
                "-" + array[13].toString().padStart(2, '0') +
                "-" + array[14].toString().padStart(2, '0') +
                " " + array[15].toString().padStart(2, '0') +
                ":" + array[16].toString().padStart(2, '0') +
                ":" + array[17].toString().padStart(2, '0')

        val lblString: String = "ID : " + id + "\t\t pil gerilimi : " +
                String.format("%.3f", batVoltage) + System.lineSeparator() +
                "\t\t pil yüzde : " + String.format("%.1f", batPercentage) +
                "\t\t Sıcaklık :" + temperature.toString() + "\t\t ChgStat : " +
                chgStatus.toString() + System.lineSeparator() + "markArrhythmia : " +
                markArrhythmia.toString() + "\t\t time :" + time

        value.postValue(lblString)

        val len = array.size

        for (i in 0..((len / 9) - 3)) {
            val ekgCh1 =
                ((array[18 + i * 9] * 65536u + array[19 + i * 9] * 256u + array[20 + i * 9]).toInt()
                    .toDouble() / 10485.76) - 400.0
            val ekgCh2 =
                ((array[21 + i * 9] * 65536u + array[22 + i * 9] * 256u + array[23 + i * 9]).toInt()
                    .toDouble() / 10485.76) - 400.0
            val ekgCh3 =
                ((array[24 + i * 9] * 65536u + array[25 + i * 9] * 256u + array[26 + i * 9]).toInt()
                    .toDouble() / 10485.76) - 400.0

            dataCount += 1
            println("ch1: $ekgCh1 ch2: $ekgCh2 ch3:$ekgCh3")
            if (dataCount % 10 == 0) {
                scope.async {
                    try {

                        ekgCh1Series.appendData(
                            DataPoint(graphLastXValue, ekgCh1),
                            true,
                            1000
                        )
                        ekgCh2Series.appendData(
                            DataPoint(graphLastXValue, ekgCh2),
                            true,
                            1000
                        )
                        ekgCh3Series.appendData(
                            DataPoint(graphLastXValue, ekgCh3),
                            true,
                            1000
                        )
                        graphLastXValue += 10.0 / sampleRate
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private var dateSendRequired: Boolean = false

    @OptIn(ExperimentalUnsignedTypes::class)
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
                    val readCharacteristic = bleApi.readCharacteristic(peripheral)
                    val sendCharacteristic = bleApi.sendCharacteristic(peripheral)
                    if ((readCharacteristic != null) && (sendCharacteristic != null)) {
                        if (dateSendRequired) {
                            dateSendRequired = false
                            viewModelScope.launch {
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                val current = LocalDateTime.now().format(formatter)
                                val charset = Charsets.UTF_8
                                val sendData = ("SETDATE $current").toByteArray(charset)
                                peripheral.write(
                                    sendCharacteristic,
                                    sendData,
                                    WriteType.WithoutResponse
                                )
                            }
                        } else {
                            viewModelScope.launch {
                                peripheral.observe(readCharacteristic).collect {
                                    val array = it.toUByteArray()
                                    messageParsing(this, array)
                                }
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