package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.Request


/**
 * 用作处理各种远端发来的请求，具体有哪些请求请参考[Request]接口的实现
 *
 * @author liuzhongao
 * @since 2024/1/16 23:53
 */
interface BridgeInterceptor<T : Request> {

    fun shouldHandle(request: Request): Boolean

    fun handle(request: T): Any?
}