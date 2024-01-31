package com.lza.android.inter.process.library.interfaces

import android.os.IBinder
import com.lza.android.inter.process.library.ProcessCallFunction
import com.lza.android.inter.process.library.bridge.interceptor.callbackBridgeInterceptor
import com.lza.android.inter.process.library.bridge.parameter.CallbackRequest

/**
 * @author liuzhongao
 * @since 2024/1/21 13:01
 */
internal val RemoteProcessSuspendCallback.binder: IBinder
    get() = when (this) {
        is RemoteProcessSuspendCallback.Stub -> rpcInterface.binder
        is RemoteProcessSuspendCallback.Proxy -> remoteProcessCallInterface.binder
        else -> throw UnsupportedOperationException("Unknown suspend callback type: ${this.javaClass.name}.")
    }

internal sealed interface RemoteProcessSuspendCallback {

    val remoteProcessCallInterface: RemoteProcessCallInterface

    fun callbackSuspend(data: Any?, throwable: Throwable?)

    companion object {
        @JvmStatic
        fun asInterface(remoteProcessCallInterface: RemoteProcessCallInterface): RemoteProcessSuspendCallback {
            return Proxy(remoteProcessCallInterface = remoteProcessCallInterface)
        }
        @JvmStatic
        fun asInterface(binder: IBinder): RemoteProcessSuspendCallback {
            return Proxy(remoteProcessCallInterface = ProcessCallFunction.Stub.asInterface(binder).rpcInterface)
        }
    }

    abstract class Stub : RemoteProcessSuspendCallback {

        val rpcInterface = RemoteProcessCallInterface.Stub()

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