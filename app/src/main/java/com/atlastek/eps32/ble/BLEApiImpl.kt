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
import java.util.UUID
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
            filters = listOf(Filter.NamePrefix( "Hamle"))
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
   /* val serviceUUID =
        UUID.nameUUIDFromBytes("6E400001-B5A3-F393-E0A9-E50E24DCCA9E".toByteArray())
    val characteristicUuidTX =
        UUID.nameUUIDFromBytes("6E400003-B5A3-F393-E0A9-E50E24DCCA9E".toByteArray())
    val characteristicUuidRX =
        UUID.nameUUIDFromBytes("6E400002-B5A3-F393-E0A9-E50E24DCCA9E".toByteArray())
*/



    override suspend fun readCharacteristic(peripheral: Peripheral): Characteristic? {
        val characteristic = peripheral.services?.firstOrNull {
            it.serviceUuid.toString().startsWith("6e400001")
        }?.characteristics?.firstOrNull {
            it.characteristicUuid.toString().startsWith("6e400003")
        }
        return characteristic
    }
    override suspend fun sendCharacteristic(peripheral: Peripheral): Characteristic? {
        val characteristic = peripheral.services?.firstOrNull {
            it.serviceUuid.toString().startsWith("6e400001")
        }?.characteristics?.firstOrNull {
            it.characteristicUuid.toString().startsWith("6e400002")
        }
        return characteristic
    }
    private var mtuChangeAttempt: Int = 0
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

