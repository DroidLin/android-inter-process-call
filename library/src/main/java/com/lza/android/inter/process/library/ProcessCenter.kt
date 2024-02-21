package com.lza.android.inter.process.library

import android.content.Context
import com.lza.android.inter.process.library.bridge.ProcessInvocationHandle
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.interfaces.ExceptionHandler
import com.lza.android.inter.process.library.interfaces.GeneratedStubFunction
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author liuzhongao
 * @since 2024/1/9 23:11
 */
object ProcessCenter : IPCenter {

    private var rootExceptionHandler: ExceptionHandler? = null

    private val interfaceStubGeneratedFunctionMap = ConcurrentHashMap<String, GeneratedStubFunction>()

    override fun init(initConfig: ProcessCallInitConfig) {
        ProcessConnectionCenter.init(initConfig)
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
        val processCallInitConfig = ProcessConnectionCenter.initConfig
        val currentProcessKey = processCallInitConfig.identifier.keyForCurrentProcess
        val context = processCallInitConfig.context

        val defaultExceptionHandler = ExceptionHandler { throwable ->
            exceptionHandler?.handleException(throwable)
                ?: this.rootExceptionHandler?.handleException(throwable)
                ?: false
        }

        val generatedClass = kotlin.runCatching { Class.forName("${clazz.name}_Generated_Proxy") }.getOrNull()
        if (generatedClass != null) {
            val instance = kotlin.runCatching {
                val constructor = generatedClass.getDeclaredConstructor(Context::class.java, String::class.java, String::class.java, CoroutineContext::class.java, clazz, ExceptionHandler::class.java)
                constructor?.newInstance(context, currentProcessKey, destProcessKey, coroutineContext, defaultImpl, defaultExceptionHandler)
            }.getOrNull() as? T
            if (instance != null) {
                return instance
            }
        }

        val remoteProcessInvocationHandle = ProcessInvocationHandle(
            proxyInterfaceClass = clazz,
            currentProcessKey = currentProcessKey,
            destinationProcessKey = destProcessKey,
            interfaceDefaultImpl = defaultImpl,
            context = context,
            coroutineContext = coroutineContext,
            exceptionHandler = defaultExceptionHandler
        )
        return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz), remoteProcessInvocationHandle) as T
    }

    internal fun getOrGeneratedStubFunction(className: String): GeneratedStubFunction {
        if (this.interfaceStubGeneratedFunctionMap[className] == null) {
            synchronized(this) {
                if (this.interfaceStubGeneratedFunctionMap[className] == null) {
                    val interfaceClass = kotlin.runCatching { Class.forName(className) }.getOrNull()
                        ?: throw ClassNotFoundException("cannot find basic class for interface: ${className}.")
                    val generatedClass = kotlin.runCatching { Class.forName("${className}_Generated_Stub") }.getOrNull()
                        ?: throw ClassNotFoundException("cannot find generated class referenced to ${className}.")
                    val developerImplementationInstance = ProcessImplementationCenter[interfaceClass]
                        ?: throw NullPointerException("no impl set in service, have you called IPCenter#putService before?")
                    if (!GeneratedStubFunction::class.java.isAssignableFrom(generatedClass)) {
                        throw VerifyError("unrecognized implementation class ${generatedClass.name}")
                    }
                    val constructor = generatedClass.getDeclaredConstructor(interfaceClass)
                    this.interfaceStubGeneratedFunctionMap[className] = constructor.newInstance(developerImplementationInstance) as GeneratedStubFunction
                }
            }
        }
        return requireNotNull(this.interfaceStubGeneratedFunctionMap[className])
    }
}