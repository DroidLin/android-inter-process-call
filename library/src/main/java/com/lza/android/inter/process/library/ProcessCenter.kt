package com.lza.android.inter.process.library

import android.content.Context
import com.lza.android.inter.process.library.bridge.ProcessInvocationHandle
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.interfaces.ExceptionHandler
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author liuzhongao
 * @since 2024/1/9 23:11
 */
object ProcessCenter : IPCenter {

    private val invocationHandleCache = ConcurrentHashMap<Class<*>, ProcessInvocationHandle>()
    /**
     * make sure there is only one instance for remote call binder instance.
     */
    private val proxyInterfaceCache: MutableMap<Class<*>, WeakReference<Any>> = ConcurrentHashMap()

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
    ): T = this.getService(destProcessKey, clazz, defaultImpl, coroutineContext, ExceptionHandler { false })

    override fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T?,
        coroutineContext: CoroutineContext,
        exceptionHandler: ExceptionHandler?
    ): T {
        val proxyInterface = this.proxyInterfaceCache[clazz]?.get()
        if (proxyInterface != null) {
            return proxyInterface as T
        }

        val processCallInitConfig = requireNotNull(ProcessConnectionCenter.processCallInitConfig) { "call init before getService!!" }
        val currentProcessKey = processCallInitConfig.identifier.keyForCurrentProcess
        val context = processCallInitConfig.context
        val remoteProcessInvocationHandle = this.getOrGenerateInvocationHandle(
            context = context,
            clazz = clazz,
            currentProcessKey = currentProcessKey,
            destinationProcessKey = destProcessKey,
            coroutineContext = coroutineContext,
            defaultImpl = defaultImpl,
        )
        if (exceptionHandler != null) {
            remoteProcessInvocationHandle.putExceptionHandler(exceptionHandler)
        }
        return this.generateDynamicProxyInstance(clazz, remoteProcessInvocationHandle)
    }

    private fun <T : IPCNoProguard> getOrGenerateInvocationHandle(
        context: Context,
        clazz: Class<T>,
        currentProcessKey: String,
        destinationProcessKey: String,
        coroutineContext: CoroutineContext,
        defaultImpl: T? = null
    ): ProcessInvocationHandle {
        if (this.invocationHandleCache[clazz] == null) {
            synchronized(this.invocationHandleCache) {
                if (this.invocationHandleCache[clazz] == null) {
                    val remoteProcessInvocationHandle = ProcessInvocationHandle(
                        proxyInterfaceClass = clazz,
                        currentProcessKey = currentProcessKey,
                        destinationProcessKey = destinationProcessKey,
                        interfaceDefaultImpl = defaultImpl,
                        context = context,
                        coroutineContext = coroutineContext,
                    )
                    val exceptionHandler = this.rootExceptionHandler
                    if (exceptionHandler != null) {
                        remoteProcessInvocationHandle.putExceptionHandler(exceptionHandler)
                    }
                    this.invocationHandleCache[clazz] = remoteProcessInvocationHandle
                }
            }
        }
        return requireNotNull(this.invocationHandleCache[clazz])
    }

    private fun <T : IPCNoProguard> generateDynamicProxyInstance(
        clazz: Class<T>,
        invocationHandler: InvocationHandler
    ): T {
        return (Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz), invocationHandler) as T)
            .also { instance -> this.proxyInterfaceCache[clazz] = WeakReference(instance) }
    }
}