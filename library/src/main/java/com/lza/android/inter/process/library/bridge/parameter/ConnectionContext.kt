package com.lza.android.inter.process.library.bridge.parameter

import java.io.Serializable

/**
 * @author liuzhongao
 * @since 2024/1/15 00:17
 */
data class ConnectionContext(
    /**
     * process key for send connection process.
     */
    val selfKey: String,
    /**
     * key for target process.
     */
    val destKey: String
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = -2100067288947454187L
    }
}
