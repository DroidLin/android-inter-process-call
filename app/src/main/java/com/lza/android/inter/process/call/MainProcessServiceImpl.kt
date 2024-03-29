package com.lza.android.inter.process.call

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author liuzhongao
 * @since 2024/1/14 23:42
 */
object MainProcessServiceImpl : ProcessService {

    override val processName: String get() = "MainProcess"

    override fun getProcessInfo(): String? {
        return TestApplication.application.getCurrentProcessName()
    }

    override fun testFunction(path: String, parameters: Int): String {
        return "${TestApplication.application.getCurrentProcessName()}/${path}/$parameters"
    }

    override suspend fun suspendTestFunction(path: String, parameters: Int): String {
        return "${TestApplication.application.getCurrentProcessName()}/${path}/$parameters/${path.calculateStringCount()}"
    }

    override suspend fun suspendPostDataToRemote(arrayParameter: Array<String>): Array<Int> {
        return emptyArray()
    }

    override suspend fun String.calculateStringCount(): Int {
        return withContext(Dispatchers.IO) {
            throw NullPointerException("hhh")
        }
    }

    override fun testThrowable() {
    }
}