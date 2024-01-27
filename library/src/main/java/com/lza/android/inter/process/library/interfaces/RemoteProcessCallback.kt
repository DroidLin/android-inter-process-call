package com.lza.android.inter.process.library.interfaces

import com.lza.android.inter.process.library.bridge.interceptor.callbackBridgeInterceptor
import com.lza.android.inter.process.library.bridge.parameter.CallbackRequest

/**
 * @author liuzhongao
 * @since 2024/1/21 13:01
 */
internal sealed interface RemoteProcessSuspendCallback {

    val remoteProcessCallInterface: RemoteProcessCallInterface

    fun callbackSuspend(data: Any?, throwable: Throwable?)

    companion object {
        @JvmStatic
        fun asInterface(remoteProcessCallInterface: RemoteProcessCallInterface): RemoteProcessSuspendCallback {
            return Proxy(remoteProcessCallInterface = remoteProcessCallInterface)
        }
    }

    abstract class Stub : RemoteProcessSuspendCallback {

        private val rpcInterface = RemoteProcessCallInterface.Stub()

        final override val remoteProcessCallInterface: RemoteProcessCallInterface
            get() = this.rpcInterface

        init {
            this.rpcInterface += callbackBridgeInterceptor { any, throwable ->
                this.callbackSuspend(data = any, throwable = throwable)
            }
        }
    }

    class Proxy(override val remoteProcessCallInterface: RemoteProcessCallInterface) :
        RemoteProcessSuspendCallback {
        override fun callbackSuspend(data: Any?, throwable: Throwable?) {
            val request = CallbackRequest(data = data, throwable = throwable)
            this.remoteProcessCallInterface.invoke(request = request)
        }
    }
}