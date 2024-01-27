package com.lza.android.inter.process.library

import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.bridge.ProcessInvocationHandle
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * @author liuzhongao
 * @since 2024/1/9 23:11
 */
object ProcessCenter : IPCenter {

    /**
     * 同进程下相同interface对应的动态代理实现类对象尽量保持同一个
     */
    private val proxyInterfaceCache: MutableMap<Class<*>, WeakReference<Any>> = ConcurrentHashMap()

    override fun init(initConfig: ProcessCallInitConfig) {
        ProcessConnectionCenter.processCallInitConfig = initConfig
    }

    override fun <T : IPCNoProguard> putService(clazz: Class<T>, impl: T?) {
        ProcessImplementationCenter[clazz] = impl
    }

    override fun <T : IPCNoProguard> getService(
        destProcessKey: String,
        clazz: Class<T>,
        defaultImpl: T?
    ): T {
        val proxyInterface = proxyInterfaceCache[clazz]?.get()
        if (proxyInterface != null) {
            return proxyInterface as T
        }
        val processCallInitConfig = requireNotNull(ProcessConnectionCenter.processCallInitConfig) { "call init before getService!!" }
        val identifier = processCallInitConfig.identifier
        val proxyInstance = Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz),
            ProcessInvocationHandle(
                proxyInterfaceClass = clazz,
                currentProcessKey = identifier.keyForCurrentProcess,
                destinationProcessKey = destProcessKey,
                interfaceDefaultImpl = defaultImpl,
                contextGetter = { processCallInitConfig.context }
            )
        ) as T
        proxyInterfaceCache[clazz] = WeakReference(proxyInstance)
        return proxyInstance
    }
}