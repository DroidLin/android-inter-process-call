package com.lza.android.inter.process.library.bridge

import android.content.Context
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.interfaces.ExceptionHandler
import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.isSuspendFunction
import com.lza.android.inter.process.library.kotlin.OneShotContinuation
import com.lza.android.inter.process.library.kotlin.UnHandledRuntimeException
import com.lza.android.inter.process.library.match
import com.lza.android.inter.process.library.unSupportedReturnType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

/**
 * @author liuzhongao
 * @since 2024/1/10 23:00
 */
class ProcessInvocationHandle(
    private val context: Context,
    private val proxyInterfaceClass: Class<*>,
    private val currentProcessKey: String,
    private val destinationProcessKey: String,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val interfaceDefaultImpl: Any? = null,
    private val exceptionHandler: ExceptionHandler? = null
) : InvocationHandler, IPCNoProguard {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (!this.isExceptionHandled(throwable = throwable)) {
            throw UnHandledRuntimeException(throwable)
        }
    }

    private val availableCoroutineContext: CoroutineContext by lazy {
        if (this.coroutineContext == EmptyCoroutineContext) {
            (Dispatchers.Default + SupervisorJob() + this.coroutineExceptionHandler)
        } else (this.coroutineContext + this.coroutineExceptionHandler)
    }

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        return if (method.isSuspendFunction) {
            this.invokeKotlinSuspendFunction(this.proxyInterfaceClass, method, (args ?: emptyArray()))
        } else this.invokeNonSuspendKotlinFunction(this.proxyInterfaceClass, method, (args ?: emptyArray()))
    }

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
        if (basicInterface == null || !basicInterface.isStillAlive) {
            return this.onNonSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
        }
        return runWithExceptionHandle {
            basicInterface.invokeRemoteProcessMethod(
                declaringClass = declaringJvmClass,
                methodName = method.name,
                argTypes = method.parameterTypes ?: emptyArray(),
                args = args
            )
        } ?: this.onNonSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
    }

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
        if (kCallable.returnType.javaType in unSupportedReturnType || kCallable.returnType.isMarkedNullable) {
            return null
        }
        if (this@ProcessInvocationHandle.interfaceDefaultImpl != null) {
            // no need try/catch, export exceptions to outside caller.
            val fallbackData = runWithExceptionHandle {
                method.invoke(this@ProcessInvocationHandle.interfaceDefaultImpl, *args)
            }
            if (fallbackData != null) {
                return fallbackData
            }
        }
        throw IllegalArgumentException(
            "function return type requires non-null type, " +
                    "but returns null type after IPC call and the fallback operation!! " +
                    "please check."
        )
    }

    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        args: Array<Any?>
    ): Any? {
        val continuation = args.filterIsInstance<Continuation<*>>().firstOrNull() as? Continuation<Any?>
            ?: throw IllegalArgumentException("no Continuation parameter find in argument!!")
        val parameterTypeWithoutContinuation = method.parameterTypes.filter { it != Continuation::class.java }.toTypedArray()
        val parameterWithoutContinuation = args.filter { it !is Continuation<*> }.toTypedArray()
        val continuationProxy = OneShotContinuation(continuation)
        return this::suspendInvokeKotlinFunction
            .apply { isAccessible = true }
            .call(declaringJvmClass, method, parameterTypeWithoutContinuation, parameterWithoutContinuation, continuationProxy)
    }

    private suspend fun suspendInvokeKotlinFunction(
        declaringJvmClass: Class<*>,
        method: Method,
        argTypes: Array<Class<*>>,
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

        return runWithExceptionHandle {
            basicInterface.invokeSuspendRemoteProcessMethod(
                declaringClass = declaringJvmClass,
                methodName = method.name,
                argTypes = argTypes,
                args = args
            )
        } ?: this.onSuspendFunctionFailureOrReturnNull(declaringJvmClass, method, args)
    }

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
        if (kCallable.returnType.javaType in unSupportedReturnType || kCallable.returnType.isMarkedNullable) {
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
        return ProcessConnectionCenter.tryConnectToRemote(
            context = this.context,
            selfKey = this.currentProcessKey,
            destKey = this.destinationProcessKey,
        )
    }

    private inline fun <T : Any> runWithExceptionHandle(block: () -> T?): T? {
        val result = runCatching(block)
        val throwable = result.exceptionOrNull()
        if (result.isFailure && throwable != null) {
            if (this.isExceptionHandled(throwable)) {
                return null
            }
            throw UnHandledRuntimeException(throwable)
        }
        val data = result.getOrNull()
        if (data != null) {
            return data
        }
        return null
    }

    private fun isExceptionHandled(throwable: Throwable?): Boolean {
        throwable ?: return true
        return this.exceptionHandler?.handleException(throwable = throwable) ?: false
    }

    companion object {
        private const val TAG = "ProcessInvocationHandle"
    }
}