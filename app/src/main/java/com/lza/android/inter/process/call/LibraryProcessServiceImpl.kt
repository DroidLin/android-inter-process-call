package com.lza.android.inter.process.call

/**
 * @author liuzhongao
 * @since 2024/1/14 23:42
 */
object LibraryProcessServiceImpl : ProcessService {

    override val processName: String get() = "LibraryProcess"

    override fun getProcessInfo(): String? {
        return TestApplication.application.getCurrentProcessName()
    }

    override fun testFunction(path: String, parameters: Int): String {
        return "${TestApplication.application.getCurrentProcessName()}/${path}/$parameters"
    }

    override suspend fun suspendTestFunction(path: String, parameters: Int): String {
        return "${TestApplication.application.getCurrentProcessName()}/${path}/$parameters"
    }

    override suspend fun suspendPostDataToRemote(arrayParameter: Array<String>): Array<Int> {
        return arrayParameter.map { it.toIntOrNull() ?: 0 }.toTypedArray()
    }

    override suspend fun String.calculateStringCount(): Int {
        return length
    }
}