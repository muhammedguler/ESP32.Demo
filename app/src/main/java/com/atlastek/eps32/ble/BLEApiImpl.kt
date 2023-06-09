/**
 * Created by onur on 31.10.2022
 */

package com.atlastek.eps32.ble


import com.atlastek.eps32.ble.model.BLEPayload
import com.atlastek.eps32.ble.model.BLEState
import com.atlastek.eps32.ble.model.mapToBLE
import com.juul.kable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

private val SCAN_DURATION_MILLIS = TimeUnit.SECONDS.toMillis(10)

class BLEApiImpl(private val scope: CoroutineScope) : BLEApi {

    init {
        Timber.tag("BLE Api")
    }


    override suspend fun scanAndConnect(): Peripheral? {
        val advertisement = Scanner {
            filters = listOf(Filter.Name("DNS Keeper"))
        }.advertisements
            .catch { cause -> Timber.e(cause) }
            .onCompletion { cause -> Timber.i("onCompletion: ", cause) }
            .firstOrNull()

        if (advertisement != null) {
            return connect(advertisement)
        }

        return null

    }


    private suspend fun connect(advertisement: Advertisement): Peripheral {
        val peripheral = scope.peripheral(advertisement) as AndroidPeripheral
        peripheral.connect()
        tryToChangeMtu(peripheral)
        return peripheral
    }

    override suspend fun characteristic(peripheral: Peripheral): Characteristic? {
        val characteristic = peripheral.services?.firstOrNull {
            it.serviceUuid.toString().startsWith("4fafc201")
        }?.characteristics?.firstOrNull {
            it.characteristicUuid.toString().startsWith("beb5483e")
        }
        return characteristic
    }

    var mtuChangeAttempt = 0
    suspend fun tryToChangeMtu(peripheral: AndroidPeripheral) {
        try {
            peripheral.requestMtu(512)
            mtuChangeAttempt = 0
        } catch (e: GattStatusException) {
            if (mtuChangeAttempt < 5) {
                mtuChangeAttempt++
                delay(1_000)
                tryToChangeMtu(peripheral)
            } else {
                throw e
            }
        }
    }
}

fun Peripheral.remoteRssi() = flow {
    while (true) {
        val rssi = rssi()
        emit(rssi)
        delay(1_000L)
    }
}.catch { cause ->
    // todo: Investigate better way of handling this failure case.
    // When disconnecting, we may attempt to read `rssi` causing a `NotReadyException` but the hope is that `remoteRssi`
    // Flow would already be cancelled by the time the `Peripheral` is "not ready" (doesn't seem to be the case).
    if (cause !is NotReadyException && cause !is GattRequestRejectedException) throw cause
}


fun Int.calcDistbyRSSI(measurePower: Int = -67): Int {
    val iRssi = abs(this)
    val iMeasurePower = abs(measurePower)
    val power: Double = (iRssi - iMeasurePower) / (10 * 2.0)

    return if (10.0.pow(power) * 3.2808 < 1.0) {
        1
    } else if (10.0.pow(power) * 3.2808 > 1.0 && 10.0.pow(power) * 3.2808 < 10.0) {
        2
    } else {
        3
    }
}

