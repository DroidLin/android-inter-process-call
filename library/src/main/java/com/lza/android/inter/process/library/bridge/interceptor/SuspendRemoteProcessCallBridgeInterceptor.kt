package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.bridge.parameter.SuspendInvocationRequest
import com.lza.android.inter.process.library.stringTypeConvert
import com.lza.android.inter.process.library.interfaces.RemoteProcessSuspendCallback
import com.lza.android.inter.process.library.invokeSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

/**
 * 非阻塞式kotlin挂起函数远端被调用端实现
 *
 * @author liuzhongao
 * @since 2024/1/17 10:31
 */
internal fun suspendRemoteProcessCallInterceptor(block: suspend (Class<*>, method: Method, args: Array<Any?>) -> Any?): BridgeInterceptor<Request> {
    return SuspendRemoteProcessCallBridgeInterceptor(block = block) as BridgeInterceptor<Request>
}

internal class SuspendRemoteProcessCallBridgeInterceptor(
    private val block: suspend (Class<*>, method: Method, args: Array<Any?>) -> Any?
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
            // suspend functions need Continuation instance to be the last parameter in parameter array.
            val parameterTypesWithContinuation = parameterTypes + Continuation::class.java
            val method = declaringJvmClass.getDeclaredMethod(
                methodName,
                *(parameterTypesWithContinuation.toTypedArray())
            )
            kotlin.runCatching {
                this@SuspendRemoteProcessCallBridgeInterceptor.block.invoke(declaringJvmClass, method, parameterValues.toTypedArray())
            }
                .onSuccess { data -> remoteProcessSuspendCallback.callbackSuspend(data, null) }
                .onFailure { throwable -> remoteProcessSuspendCallback.callbackSuspend(null, throwable) }
                .onFailure { it.printStackTrace() }
        }
        return null
    }
}