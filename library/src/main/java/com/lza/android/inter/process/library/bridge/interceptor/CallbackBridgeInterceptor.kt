package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.CallbackRequest
import com.lza.android.inter.process.library.bridge.parameter.Request

/**
 * @author liuzhongao
 * @since 2024/1/17 10:14
 */
fun callbackBridgeInterceptor(block: (Any?, Throwable?) -> Unit): BridgeInterceptor<Request> {
    return CallbackBridgeInterceptor(handle = block) as BridgeInterceptor<Request>
}

/**
 * 用作回调式远端调用，目前用于 suspend 函数的非阻塞式调用，
 * 主要目的是不长时间阻塞进程内binder通信的线程，来提高app运行性能
 */
private class CallbackBridgeInterceptor(
    private val handle: (Any?, Throwable?) -> Unit,
) : BridgeInterceptor<CallbackRequest> {
    override fun shouldHandle(request: Request): Boolean = request is CallbackRequest

    override fun handle(request: CallbackRequest): Any? {
        this.handle(request.data, request.throwable)
        return null
    }
}