package com.lza.android.library.bridge

import android.content.Context
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.bridge.parameter.InvocationResponse
import com.lza.android.library.interfaces.RemoteProcessCallInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberExtensionProperties
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

/**
 * @author liuzhongao
 * @since 2024/1/10 23:00
 */
class ProcessInvocationHandle(
    private val proxyInterfaceClass: Class<*>,
    private val currentProcessKey: String,
    private val destinationProcessKey: String,
    private val interfaceDefaultImpl: Any? = null,
    private val contextGetter: () -> Context? = { null },
) : InvocationHandler {

    private val coroutineScope = CoroutineScope(context = Dispatchers.Default + SupervisorJob())

    override fun invoke(proxy: Any?, method: Method?, args: Array<Any?>?): Any? {
        requireNotNull(method) { "require method not null." }

        val declaringClass = method.declaringClass
        val kClass = declaringClass.kotlin
        val kCallable = kClass.memberProperties.find { it.javaGetter == method }
            ?: kClass.memberExtensionProperties.find { it.javaGetter == method }
            ?: kClass.memberFunctions.find { it.javaMethod == method }
            ?: kClass.memberExtensionFunctions.find { it.javaMethod == method }
        return if (kCallable is KFunction<*> && kCallable.isSuspend) {
            this.invokeKotlinSuspendFunction(declaringClass, method, kCallable, (args ?: emptyArray()))
        } else if (kCallable is KFunction<*>) {
            this.invokeKotlinSimpleFunction(declaringClass, method, kCallable, (args ?: emptyArray()))
        } else if (kClass is KProperty<*>) {
            this.invokeKotlinProperty(declaringClass, method, kClass, (args ?: emptyArray()))
        } else throw IllegalArgumentException("can not find kotlin function represent target method: $method")
    }

    /**
     * 针对被代理类的kotlin field写法的兼容，包括内部的扩展属性和普通属性
     */
    private fun invokeKotlinProperty(
        declaringJvmClass: Class<*>,
        method: Method,
        kotlinProperty: KProperty<Any?>,
        args: Array<Any?>
    ): Any? {
        val connectResult = this.ensureBinderConnectionEstablished { requester ->
            runBlocking(this.coroutineScope.coroutineContext) { requester() }
        }
        // Todo: 日志收集
        if (!connectResult) return this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinProperty, args)

        val basicInterface = ProcessConnectionCenter[this@ProcessInvocationHandle.destinationProcessKey]
        if (basicInterface == null || !basicInterface.isStillAlive) return this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinProperty, args)
        val response = basicInterface.invokeRemoteProcessMethod(declaringJvmClass, method, args) as? InvocationResponse
        if (response?.throwable != null) throw response.throwable

        return if (response?.responseObject != null) {
            response.responseObject
        } else this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinProperty, args)
    }

    /**
     * 当远程连接失败或者rpcInterface不存在时触发的兜底逻辑
     *
     * 仅用于非挂起函数同步返回结果用！！！
     */
    private fun dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(
        method: Method,
        kotlinCallable: KCallable<Any?>,
        args: Array<Any?>,
    ): Any? {
        if (kotlinCallable.returnType.isMarkedNullable) {
            return null
        }
        if (this@ProcessInvocationHandle.interfaceDefaultImpl != null) {
            // 不需要try-catch，若是该函数内部出现错误，那么直接对外抛出
            return method.invoke(this@ProcessInvocationHandle.interfaceDefaultImpl, args)
        }
        throw IllegalArgumentException(
            "function return type requires non-null type, " +
                    "but returns null type after IPC call and the fallback operation!! " +
                    "please check."
        )
    }

    /**
     * 针对被代理类的普通kotlin方法的兼容
     */
    private fun invokeKotlinSimpleFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        kotlinFunction: KFunction<Any?>,
        args: Array<Any?>
    ): Any? {
        val connectResult = this.ensureBinderConnectionEstablished { requester ->
            runBlocking(this.coroutineScope.coroutineContext) { requester() }
        }
        // Todo: 日志收集
        if (!connectResult) return this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args)

        val basicInterface = ProcessConnectionCenter[this@ProcessInvocationHandle.destinationProcessKey]
        if (basicInterface == null || !basicInterface.isStillAlive) return this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args)
        val response = basicInterface.invokeRemoteProcessMethod(declaringJvmClass, method, args) as? InvocationResponse
        if (response?.throwable != null) throw response.throwable
        return if (response?.responseObject != null) {
            response.responseObject
        } else this.dealSimpleFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args)
    }

    /**
     * 针对被代理类的kotlin协程挂起方法的兼容
     *
     * 当前类中的实现为阻塞式挂起，此种类型的挂起会阻塞 调用端的1个线程 + 被调用端的binder线程 + 1个协程挂起线程 = 最少2个线程
     */
    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        kotlinFunction: KFunction<Any?>,
        args: Array<Any?>
    ): Any {
        this.coroutineScope.launch {
            val continuation = args.filterIsInstance<Continuation<*>>().firstOrNull() as? Continuation<Any?>
                ?: throw IllegalArgumentException("no Continuation parameter find in argument!!")
            val connectResult = this@ProcessInvocationHandle.ensureBinderConnectionEstablished { requester -> requester() }
            // Todo: 日志收集
            if (!connectResult) { // connect timeout.
                this@ProcessInvocationHandle.dealSuspendFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args, continuation)
                return@launch
            }

            val basicInterface = ProcessConnectionCenter[this@ProcessInvocationHandle.destinationProcessKey]
            if (basicInterface == null || !basicInterface.isStillAlive) {
                this@ProcessInvocationHandle.dealSuspendFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args, continuation)
                return@launch
            }

            val deathRecipient = object : RemoteProcessCallInterface.DeathRecipient {
                override fun binderDead() {
                    basicInterface.unlinkToDeath(deathRecipient = this)
                    // Todo: 日志收集
                    this@ProcessInvocationHandle.dealSuspendFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args, continuation)
                    this@launch.cancel() // 清理当前还处于suspend状态的协程处理器，避免内存泄漏
                }
            }

            basicInterface.linkToDeath(deathRecipient = deathRecipient)
            val (data, throwable) = basicInterface.invokeSuspendRemoteProcessMethod(declaringJvmClass, method, args) as Pair<Any?, Throwable?>
            basicInterface.unlinkToDeath(deathRecipient = deathRecipient)
            if (throwable != null) {
                continuation.resumeWithException(exception = throwable)
                return@launch
            }
            if (data != null) {
                continuation.resume(data)
            } else this@ProcessInvocationHandle.dealSuspendFunctionOnConnectionFailureOrEmptyRpcInterface(method, kotlinFunction, args, continuation)
        }
        return kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
    }

    /**
     * 当远程连接失败或者rpcInterface不存在时触发的兜底逻辑
     *
     * 仅用于协程挂起函数异步返回结果用！！！
     */
    private fun dealSuspendFunctionOnConnectionFailureOrEmptyRpcInterface(
        method: Method,
        kotlinCallable: KCallable<Any?>,
        args: Array<Any?>,
        continuation: Continuation<Any?>
    ) {
        if (kotlinCallable.returnType.isMarkedNullable) {
            continuation.resume(null)
            return
        }
        if (this@ProcessInvocationHandle.interfaceDefaultImpl != null) {
            // 不需要try-catch，若是该函数内部出现错误，那么直接对外抛出
            kotlin.runCatching { method.invoke(this@ProcessInvocationHandle.interfaceDefaultImpl, args) }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
            return
        }
        val exception = IllegalArgumentException(
            "function return type requires non-null, " +
                    "but returns null after IPC call and the fallback operation!! " +
                    "please check."
        )
        continuation.resumeWithException(exception)
        return
    }

    private inline fun ensureBinderConnectionEstablished(wrapper: (suspend () -> Boolean) -> Boolean): Boolean {
        return if (!ProcessConnectionCenter.isRemoteConnected(destKey = this.destinationProcessKey)) {
            val context = requireNotNull(this.contextGetter()) { "please call ProcessCenter#init, before launch process invocation." }
            wrapper {
                ProcessConnectionCenter.tryConnectToRemote(
                    context = context,
                    selfKey = this@ProcessInvocationHandle.currentProcessKey,
                    destKey = this@ProcessInvocationHandle.destinationProcessKey
                )
            }
        } else true
    }

    companion object {
        private const val TAG = "ProcessInvocationHandle"
    }
}