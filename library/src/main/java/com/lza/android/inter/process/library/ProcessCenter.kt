package com.lza.android.inter.process.library

import com.lza.android.inter.process.library.bridge.ProcessInvocationHandle
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author liuzhongao
 * @since 2024/1/9 23:11
 */
object ProcessCenter : IPCenter {

    /**
     * make sure there is only one instance for remote call binder instance.
     */
    private val proxyInterfaceCache: MutableMap<Class<*>, WeakReference<Any>> = ConcurrentHashMap()

    override fun init(initConfig: ProcessCallInitConfig) {
        ProcessConnectionCenter.processCallInitConfig = initConfig
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
    ): T {
        val proxyInterface = this.proxyInterfaceCache[clazz]?.get()
        if (proxyInterface != null) {
            return proxyInterface as T
        }
        if (this.proxyInterfaceCache[clazz]?.get() == null) {
            synchronized(this.proxyInterfaceCache) {
                if (this.proxyInterfaceCache[clazz]?.get() == null) {
                    val processCallInitConfig = requireNotNull(ProcessConnectionCenter.processCallInitConfig) { "call init before getService!!" }
                    val currentProcessKey = processCallInitConfig.identifier.keyForCurrentProcess
                    val context = processCallInitConfig.context
                    val proxyInstance = Proxy.newProxyInstance(
                        clazz.classLoader,
                        arrayOf(clazz),
                        ProcessInvocationHandle(
                            proxyInterfaceClass = clazz,
                            currentProcessKey = currentProcessKey,
                            destinationProcessKey = destProcessKey,
                            interfaceDefaultImpl = defaultImpl,
                            context = context,
                            coroutineContext = coroutineContext
                        )
                    ) as T
                    this.proxyInterfaceCache[clazz] = WeakReference(proxyInstance)
                }
            }
        }
        return requireNotNull(this.proxyInterfaceCache[clazz]?.get() as? T)
    }
}