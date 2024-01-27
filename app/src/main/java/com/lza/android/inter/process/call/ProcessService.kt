package com.lza.android.inter.process.call

import com.lza.android.inter.process.library.interfaces.IPCNoProguard

/**
 * @author liuzhongao
 * @since 2024/1/14 23:41
 */
interface ProcessService : IPCNoProguard {

    val processName: String?

    fun getProcessInfo(): String?

    fun testFunction(path: String, parameters: Int): String

    suspend fun suspendTestFunction(path: String, parameters: Int): String

    suspend fun suspendPostDataToRemote(arrayParameter: Array<String>): Array<Int>

    suspend fun String.calculateStringCount(): Int
}