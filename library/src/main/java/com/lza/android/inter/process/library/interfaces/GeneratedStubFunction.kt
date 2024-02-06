package com.lza.android.inter.process.library.interfaces

/**
 * @author liuzhongao
 * @since 2024/2/6 11:26
 */
interface GeneratedStubFunction : IPCNoProguard {

    fun invokeNonSuspendFunction(functionName: String, functionParameter: List<Any?>): Any?

    suspend fun invokeSuspendFunction(functionName: String, functionParameter: List<Any?>): Any?
}