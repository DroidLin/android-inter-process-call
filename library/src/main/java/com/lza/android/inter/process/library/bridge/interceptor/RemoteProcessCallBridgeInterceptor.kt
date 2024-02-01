package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.InvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.stringTypeConvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用于接收正常非suspend函数调用，作为兜底，也提供了kotlin挂起函数的实现（阻塞式）
 *
 * @author liuzhongao
 * @since 2024/1/17 00:13
 */
internal fun remoteProcessCallInterceptor(block: (Class<*>, method: Method, args: Array<Any?>) -> Any?): BridgeInterceptor<Request> {
    return RemoteProcessCallBridgeInterceptor(block = block) as BridgeInterceptor<Request>
}

internal class RemoteProcessCallBridgeInterceptor(
    private val block: (Class<*>, method: Method, args: Array<Any?>) -> Any?
) : BridgeInterceptor<InvocationRequest> {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun shouldHandle(request: Request): Boolean = request is InvocationRequest

    override fun handle(request: InvocationRequest): Any? {
        val declaringJvmClass = Class.forName(request.interfaceClassName) as Class<Any>
        val parameterClassTypes = request.interfaceParameterTypes.stringTypeConvert
        return if (request.isKotlinFunction) {
            this.invokeKotlinFunction(
                declaringJvmClass = declaringJvmClass,
                methodName = request.interfaceMethodName,
                parameterTypes = parameterClassTypes,
                parameterValues = request.interfaceParameters
            )
        } else this.invokeJavaMethod(
            declaringJvmClass = declaringJvmClass,
            methodName = request.interfaceMethodName,
            parameterTypes = parameterClassTypes,
            parameterValues = request.interfaceParameters
        )
    }

    private fun invokeKotlinFunction(
        declaringJvmClass: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>,
        isSuspendFunction: Boolean = false
    ): Any? {
        return if (isSuspendFunction) {
            this.invokeKotlinSuspendFunction(
                declaringJvmClass = declaringJvmClass,
                methodName = methodName,
                parameterTypes = parameterTypes,
                parameterValues = parameterValues,
            )
        } else this.invokeKotlinSimpleFunction(
            declaringJvmClass = declaringJvmClass,
            methodName = methodName,
            parameterTypes = parameterTypes,
            parameterValues = parameterValues,
        )
    }

    @Deprecated("not in use any more.", replaceWith = ReplaceWith("suspendRemoteProcessCallInterceptor"))
    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>
    ): Any? {
        val deferredTask = this.coroutineScope.async<Any?> {
            suspendCancellableCoroutine { continuation ->
                // suspend functions need Continuation instance to be the last parameter in parameter array.
                val parameterTypesWithContinuation = parameterTypes + Continuation::class.java
                val parameterValuesWithContinuation = parameterValues + continuation
                val method = declaringJvmClass.getDeclaredMethod(
                    methodName,
                    *(parameterTypesWithContinuation.toTypedArray())
                )
                kotlin.runCatching {
                    this@RemoteProcessCallBridgeInterceptor.block.invoke(
                        declaringJvmClass,
                        method,
                        parameterValuesWithContinuation.toTypedArray()
                    )
                }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }
        }
        return runBlocking { deferredTask.await() }
    }

    private fun invokeKotlinSimpleFunction(
        declaringJvmClass: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>
    ): Any? {
        val method =
            declaringJvmClass.getDeclaredMethod(methodName, *(parameterTypes.toTypedArray()))
        return this.block.invoke(declaringJvmClass, method, parameterValues.toTypedArray())
    }

    private fun invokeJavaMethod(
        declaringJvmClass: Class<Any>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>
    ): Any? {
        val method =
            declaringJvmClass.getDeclaredMethod(methodName, *(parameterTypes.toTypedArray()))
        return this.block.invoke(declaringJvmClass, method, parameterValues.toTypedArray())
    }
}