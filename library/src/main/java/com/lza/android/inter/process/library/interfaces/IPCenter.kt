package com.lza.android.inter.process.library.interfaces

import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author liuzhongao
 * @since 2024/1/8 23:18
 */
interface IPCenter {

    fun init(initConfig: ProcessCallInitConfig)

    fun setRootExceptionHandler(exceptionHandler: ExceptionHandler)

    fun <T : IPCNoProguard> putService(clazz: Class<T>, impl: T?)

    fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
    ): T

    fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T? = null,
    ): T

    fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T? = null,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): T

    /**
     * @param defaultImpl call on remote process call failure
     */
    fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T? = null,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        exceptionHandler: ExceptionHandler? = null
    ): T
}