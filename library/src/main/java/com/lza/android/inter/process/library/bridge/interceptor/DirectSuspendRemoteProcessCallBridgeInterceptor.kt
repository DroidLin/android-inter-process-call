package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.DirectSuspendInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.kotlin.OneShotContinuation
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * @author liuzhongao
 * @since 2024/2/6 14:32
 */
typealias DirectSuspendRemoteCallback = suspend (String, String, List<Any?>) -> Any?

internal fun directSuspendRemoteProcessCallBridgeInterceptor(block: DirectSuspendRemoteCallback): BridgeInterceptor<Request> {
    return DirectSuspendRemoteProcessCallBridgeInterceptor(block) as BridgeInterceptor<Request>
}

internal class DirectSuspendRemoteProcessCallBridgeInterceptor(
    private val block: DirectSuspendRemoteCallback,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) : BridgeInterceptor<DirectSuspendInvocationRequest> {

    override fun shouldHandle(request: Request): Boolean = request is DirectSuspendInvocationRequest

    override fun handle(request: DirectSuspendInvocationRequest): Any? {
        val suspendCallback = request.remoteProcessSuspendCallback
        val continuation = object : Continuation<Any?> {
            override val context: CoroutineContext get() = this@DirectSuspendRemoteProcessCallBridgeInterceptor.coroutineContext
            override fun resumeWith(result: Result<Any?>) { suspendCallback.callbackSuspend(data = result.getOrNull(), throwable = result.exceptionOrNull()) }
        }
        val oneShotContinuation = OneShotContinuation(continuation)
        return (this.block::invoke as Function4<String, String, List<Any?>, Continuation<Any?>, Any?>)
            .invoke(request.interfaceClassName, request.interfaceMethodName, request.interfaceParameters, oneShotContinuation)
    }
}