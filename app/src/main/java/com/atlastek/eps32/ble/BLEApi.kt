/**
 * Created by onur on 31.10.2022
 */

package com.atlastek.eps32.ble

import com.atlastek.eps32.ble.model.BLEPayload
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow

interface BLEApi {
    suspend fun scanAndConnect(): Peripheral?
    suspend fun readCharacteristic(peripheral: Peripheral): Characteristic?
    suspend fun sendCharacteristic(peripheral: Peripheral): Characteristic?
}