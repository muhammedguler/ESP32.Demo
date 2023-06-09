/**
 * Created by onur on 1.11.2022
 */

package com.atlastek.eps32.ble.model


import kotlinx.coroutines.flow.Flow


data class BLEPayload(
    val rssi: Flow<Int>,
    val state: Flow<BLEState>,
    val data: Flow<ByteArray>,
){
    override fun toString(): String {
        return "BLEPayload(data='${data}', state=$state, rssi=$rssi)"
    }
}