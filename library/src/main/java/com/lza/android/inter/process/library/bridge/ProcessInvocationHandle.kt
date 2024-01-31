package com.lza.android.inter.process.library.bridge

import android.content.Context
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.bridge.parameter.InvocationResponse
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.isSuspendFunction
import com.lza.android.inter.process.library.match
import com.lza.android.inter.process.library.unSupportedReturnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.jvm.isAccessible

/**
 * @author liuzhongao
 * @since 2024/1/10 23:00
 */
class ProcessInvocationHandle(
    private val proxyInterfaceClass: Class<*>,
    private val currentProcessKey: String,
    private val destinationProcessKey: String,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val interfaceDefaultImpl: Any? = null,
    private val contextGetter: () -> Context? = { null },
) : InvocationHandler, IPCNoProguard {

    private val availableCoroutineContext: CoroutineContext
        get() = if (this.coroutineContext == EmptyCoroutineContext) {
            Dispatchers.Default
        } else this.coroutineContext

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        return if (method.isSuspendFunction) {
            this.invokeKotlinSuspendFunction(this.proxyInterfaceClass, method, (args ?: emptyArray()))
        } else this.invokeNonSuspendKotlinFunction(this.proxyInterfaceClass, method, (args ?: emptyArray()))
    }

    /**
     * 针对被代理类的普通kotlin方法的兼容
     */
    private fun invokeNonSuspendKotlinFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>
    ): Any? {
        val tryConnectResult = runBlocking(this.availableCoroutineContext) {
            this@ProcessInvocationHandle.ensureBinderConnectionEstablished()
        }
        if (!tryConnectResult) {
            return this.onNonSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
        }

        val basicInterface = ProcessConnectionCenter[this@ProcessInvocationHandle.destinationProcessKey]
        if (basicInterface == null || !basicInterface.isStillAlive) return this.onNonSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
        val response = basicInterface.invokeRemoteProcessMethod(declaringJvmClass, method, args) as? InvocationResponse
        if (response?.throwable != null) throw response.throwable
        return if (response?.responseObject != null) {
            response.responseObject
        } else this.onNonSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
    }

    /**
     * 当远程连接失败或者rpcInterface不存在时触发的兜底逻辑
     *
     * 仅用于非挂起函数同步返回结果用！！！
     */
    private fun onNonSuspendFunctionFailureOrReturnNull(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>,
    ): Any? {
        if (method.returnType in unSupportedReturnType) {
            return null
        }
        val kClass = declaringJvmClass.kotlin
        // remains potential performance problems.
        val kCallable = kClass.members.find { it.match(method = method) }
            ?: throw IllegalArgumentException("can not find kotlin function represent target method: $method")
        if (kCallable.returnType.isMarkedNullable) {
            return null
        }
        if (this@ProcessInvocationHandle.interfaceDefaultImpl != null) {
            // no need try/catch, export exceptions to outside caller.
            return method.invoke(this@ProcessInvocationHandle.interfaceDefaultImpl, args)
        }
        throw IllegalArgumentException(
            "function return type requires non-null type, " +
                    "but returns null type after IPC call and the fallback operation!! " +
                    "please check."
        )
    }

    /**
     * 针对被代理类的kotlin协程挂起方法的兼容
     *
     * 当前类中的实现为阻塞式挂起，此种类型的挂起会阻塞 调用端的1个线程 + 被调用端的binder线程 + 1个协程挂起线程 = 最少2个线程
     */
    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>
    ): Any? {
        val continuation = args.filterIsInstance<Continuation<*>>().firstOrNull() as? Continuation<Any?>
            ?: throw IllegalArgumentException("no Continuation parameter find in argument!!")
        val parameterWithoutContinuation = args.filter { it !is Continuation<*> }.toTypedArray()
        val continuationProxy = ProcessBasicInterface.OneShotContinuation(continuation)
        return this::suspendInvokeKotlinFunction
            .apply { isAccessible = true }
            .call(declaringJvmClass, method, parameterWithoutContinuation, continuationProxy)
    }

    private suspend fun suspendInvokeKotlinFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>
    ): Any? {
        val tryConnectResult = this.ensureBinderConnectionEstablished()
        if (!tryConnectResult) {
            return this.onSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
        }

        val basicInterface = ProcessConnectionCenter[this.destinationProcessKey]
        if (basicInterface == null || !basicInterface.isStillAlive) {
            return this.onSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
        }

        return basicInterface.invokeSuspendRemoteProcessMethod(
            declaringClass = declaringJvmClass,
            method = method,
            args = args
        ) ?: this.onSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
    }

    /**
     * 当远程连接失败或者rpcInterface不存在时触发的兜底逻辑
     *
     * 仅用于协程挂起函数异步返回结果用！！！
     */
    private suspend fun onSuspendFunctionFailureOrReturnNull(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>
    ): Any? {
        if (method.returnType in unSupportedReturnType) {
            return null
        }
        val kClass = declaringJvmClass.kotlin
        // remains potential performance problems.
        val kCallable = kClass.members.find { it.match(method = method) }
            ?: throw IllegalArgumentException("can not find kotlin function represent target method: $method")
        if (kCallable.returnType.isMarkedNullable) {
            return null
        }
        if (this.interfaceDefaultImpl != null) {
            return suspendCoroutineUninterceptedOrReturn { continuation ->
                method.invoke(this.interfaceDefaultImpl, *args, continuation)
            }
        }
        throw IllegalArgumentException(
            "function return type requires non-null, " +
                    "but returns null after IPC call and the fallback operation!! " +
                    "please check."
        )
    }

    private suspend fun ensureBinderConnectionEstablished(): Boolean {
        return if (!ProcessConnectionCenter.isRemoteConnected(destKey = this.destinationProcessKey)) {
            val context = requireNotNull(this.contextGetter()) { "please call ProcessCenter#init, before launch process invocation." }
            ProcessConnectionCenter.tryConnectToRemote(
                context = context,
                selfKey = this@ProcessInvocationHandle.currentProcessKey,
                destKey = this@ProcessInvocationHandle.destinationProcessKey,
            )
        } else true
    }

    companion object {
        private const val TAG = "ProcessInvocationHandle"
    }
}