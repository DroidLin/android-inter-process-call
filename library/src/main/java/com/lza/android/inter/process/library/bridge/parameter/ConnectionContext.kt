package com.lza.android.inter.process.library.bridge.parameter

import java.io.Serializable

/**
 * @author liuzhongao
 * @since 2024/1/15 00:17
 */
data class ConnectionContext(
    /**
     * 当前发起连接的key, 用以标记由谁发起
     */
    val selfKey: String,
    /**
     * 当前需要连接的key, 用以标记和谁连接
     */
    val destKey: String
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = -2100067288947454187L
    }
}
