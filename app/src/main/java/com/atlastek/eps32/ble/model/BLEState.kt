/**
 * Created by onur on 1.11.2022
 */

package com.atlastek.eps32.ble.model

import com.juul.kable.State

public enum class BLEState {
    Connecting, Connected, Disconnecting, Disconnected;

    val text: String
    get() {
        return when (this) {
            Connected -> "Connected"
            Connecting -> "Connecting"
            Disconnecting -> "Disconnecting"
            Disconnected -> "Disconnected"
        }
    }
}


fun State.mapToBLE(): BLEState {
    return when(this) {
        is State.Connected -> BLEState.Connected
        is State.Connecting -> BLEState.Connecting
        is State.Disconnected -> BLEState.Disconnected
        is State.Disconnecting -> BLEState.Disconnecting
    }
}