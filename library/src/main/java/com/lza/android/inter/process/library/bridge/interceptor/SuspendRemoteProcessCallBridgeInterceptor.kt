package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.bridge.parameter.SuspendInvocationRequest
import com.lza.android.inter.process.library.stringTypeConvert
import com.lza.android.inter.process.library.interfaces.RemoteProcessSuspendCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * 非阻塞式kotlin挂起函数远端被调用端实现
 *
 * @author liuzhongao
 * @since 2024/1/17 10:31
 */
internal fun suspendRemoteProcessCallInterceptor(block: (Class<*>, method: Method, args: Array<Any?>) -> Any?): BridgeInterceptor<Request> {
    return SuspendRemoteProcessCallBridgeInterceptor(block = block) as BridgeInterceptor<Request>
}

internal class SuspendRemoteProcessCallBridgeInterceptor(
    private val block: (Class<*>, method: Method, args: Array<Any?>) -> Any?
) : BridgeInterceptor<SuspendInvocationRequest> {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun shouldHandle(request: Request): Boolean = request is SuspendInvocationRequest

    override fun handle(request: SuspendInvocationRequest): Any? {
        val declaringJvmClass = Class.forName(request.interfaceClassName) as Class<Any>
        val parameterClassTypes = request.interfaceParameterTypes.stringTypeConvert
        return this.invokeKotlinSuspendFunction(
            declaringJvmClass = declaringJvmClass,
            methodName = request.interfaceMethodName,
            parameterTypes = parameterClassTypes,
            parameterValues = request.interfaceParameters,
            remoteProcessSuspendCallback = request.remoteProcessSuspendCallback
        )
    }

    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>,
        remoteProcessSuspendCallback: RemoteProcessSuspendCallback
    ): Any? {
        this.coroutineScope.launch {
            val (data, throwable) = suspendCancellableCoroutine<Pair<Any?, Throwable?>> { continuation ->
                // suspend functions need Continuation instance to be the last parameter in parameter array.
                val parameterTypesWithContinuation = parameterTypes + Continuation::class.java
                val parameterValuesWithContinuation = parameterValues + continuation
                val method = declaringJvmClass.getDeclaredMethod(
                    methodName,
                    *(parameterTypesWithContinuation.toTypedArray())
                )
                kotlin.runCatching {
                    this@SuspendRemoteProcessCallBridgeInterceptor.block.invoke(
                        declaringJvmClass,
                        method,
                        parameterValuesWithContinuation.toTypedArray()
                    )
                }
                    .onSuccess { continuation.resume(it to null) }
                    .onFailure { continuation.resume(null to it) }
            }
            remoteProcessSuspendCallback.callbackSuspend(data, throwable)
        }
        return null
    }
}