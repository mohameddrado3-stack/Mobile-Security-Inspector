package com.msi.inspector.domain.model

/**
 * Stable SQLite representation for tri-state VPN observations.
 * -1 means Android did not provide enough information to determine the state.
 */
object VpnObservationStorage {
    const val UNKNOWN = -1

    fun encode(value: Boolean?): Int = when (value) {
        true -> 1
        false -> 0
        null -> UNKNOWN
    }

    fun decode(value: Int): Boolean? = when (value) {
        1 -> true
        0 -> false
        else -> null
    }
}
