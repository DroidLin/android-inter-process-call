package com.lza.android.inter.process.library

import com.lza.android.inter.process.library.bridge.ProcessInvocationHandle
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.interfaces.ExceptionHandler
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.reflect.Proxy
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author liuzhongao
 * @since 2024/1/9 23:11
 */
object ProcessCenter : IPCenter {

    private var rootExceptionHandler: ExceptionHandler? = null

    override fun init(initConfig: ProcessCallInitConfig) {
        ProcessConnectionCenter.processCallInitConfig = initConfig
    }

    override fun setRootExceptionHandler(exceptionHandler: ExceptionHandler) {
        this.rootExceptionHandler = exceptionHandler
    }

    override fun <T : IPCNoProguard> putService(clazz: Class<T>, impl: T?) {
        ProcessImplementationCenter[clazz] = impl
    }

    override fun <T : IPCNoProguard> getService(destProcessKey: String, clazz: Class<T>): T {
        return this.getService(destProcessKey, clazz, null)
    }

    override fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T?
    ): T = this.getService(destProcessKey, clazz, defaultImpl, EmptyCoroutineContext)

    override fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T?,
        coroutineContext: CoroutineContext
    ): T = this.getService(destProcessKey, clazz, defaultImpl, coroutineContext, null)

    override fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T?,
        coroutineContext: CoroutineContext,
        exceptionHandler: ExceptionHandler?
    ): T {
        val processCallInitConfig = requireNotNull(ProcessConnectionCenter.processCallInitConfig) { "call init before getService!!" }
        val currentProcessKey = processCallInitConfig.identifier.keyForCurrentProcess
        val context = processCallInitConfig.context
        val remoteProcessInvocationHandle = ProcessInvocationHandle(
            proxyInterfaceClass = clazz,
            currentProcessKey = currentProcessKey,
            destinationProcessKey = destProcessKey,
            interfaceDefaultImpl = defaultImpl,
            context = context,
            coroutineContext = coroutineContext,
            exceptionHandler = ExceptionHandler { throwable ->
                exceptionHandler?.handleException(throwable)
                    ?: this.rootExceptionHandler?.handleException(throwable)
                    ?: false
            }
        )
        return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz), remoteProcessInvocationHandle) as T
    }
}