package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.DirectInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.Request

/**
 * @author liuzhongao
 * @since 2024/2/6 14:28
 */
typealias DirectRemoteCallback = (String, String, List<Any?>) -> Any?

internal fun directRemoteProcessCallBridgeInterceptor(block: DirectRemoteCallback): BridgeInterceptor<Request> {
    return DirectRemoteProcessCallBridgeInterceptor(block) as BridgeInterceptor<Request>
}

internal class DirectRemoteProcessCallBridgeInterceptor(
    private val block: DirectRemoteCallback
) : BridgeInterceptor<DirectInvocationRequest> {
    override fun shouldHandle(request: Request): Boolean = request is DirectInvocationRequest

    override fun handle(request: DirectInvocationRequest): Any? {
        return this.block.invoke(request.interfaceClassName, request.interfaceMethodName, request.interfaceParameters)
    }
}