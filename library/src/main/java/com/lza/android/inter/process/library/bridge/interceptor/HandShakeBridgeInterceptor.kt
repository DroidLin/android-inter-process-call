package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.HandShakeRequest
import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface

/**
 * @author liuzhongao
 * @since 2024/1/16 23:56
 */
internal fun handShakeBridgeInterceptor(handle: (String, ProcessBasicInterface) -> Unit): BridgeInterceptor<Request> {
    return HandShakeBridgeInterceptor(handle = handle) as BridgeInterceptor<Request>
}

/**
 * 一个简化版tcp握手协议，被连接端发起，连接端接受
 *
 * 目的是用于建立双向通道使用，调用方发起连接请求后，被调用方需要将自身binder句柄返回给调用方，
 * 以建立双向连接通道
 */
private class HandShakeBridgeInterceptor(
    private val handle: ((String, ProcessBasicInterface) -> Unit)? = null
) : BridgeInterceptor<HandShakeRequest> {

    override fun shouldHandle(request: Request): Boolean = request is HandShakeRequest

    override fun handle(request: HandShakeRequest): Any? {
        handle?.invoke(request.processKey, request.basicInterface)
        return null
    }
}