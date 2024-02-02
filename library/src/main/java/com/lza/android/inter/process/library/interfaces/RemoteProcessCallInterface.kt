package com.lza.android.inter.process.library.interfaces

import android.os.IBinder
import com.lza.android.inter.process.library.ProcessCallFunction
import com.lza.android.inter.process.library.bridge.ProcessCallBridgeInterface
import com.lza.android.inter.process.library.bridge.interceptor.BridgeInterceptor
import com.lza.android.inter.process.library.bridge.parameter.BridgeParameter
import com.lza.android.inter.process.library.bridge.parameter.InternalInvocationFailureResponse
import com.lza.android.inter.process.library.bridge.parameter.InvocationResponse
import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.bridge.parameter.Response
import com.lza.android.inter.process.library.bridge.parameter.request
import com.lza.android.inter.process.library.bridge.parameter.response
import java.util.LinkedList

/**
 * @author liuzhongao
 * @since 2024/1/18 19:25
 */
val ProcessCallFunction.rpcInterface: RemoteProcessCallInterface
    get() = RemoteProcessCallInterface.asInterface(bridgeInterface = this)

internal val RemoteProcessCallInterface.binder: IBinder
    get() {
        return when (this) {
            is RemoteProcessCallInterface.Proxy -> this.binderInterface.asBinder()
            is RemoteProcessCallInterface.Stub -> this.bridgeInterface.asBinder()
            else -> throw IllegalArgumentException("")
        }
    }

/**
 * 最基础的远端调用接口，非必要不扩展该接口和aidl的接口内容，可能会破坏跨进程调用的设计思想
 *
 * 后续扩展时，不建议直接构造该函数的对象，可参考[ProcessBasicInterface]的实现.
 */
sealed interface RemoteProcessCallInterface {

    val isStillAlive: Boolean

    operator fun invoke(request: Request): Response?

    /**
     * 注册远端断连回调, 如果当前已经断开连接会立刻回调
     */
    fun linkToDeath(deathRecipient: DeathRecipient)

    fun unlinkToDeath(deathRecipient: DeathRecipient)

    fun interface DeathRecipient {

        /**
         * 远端断开连接时回调
         *
         * 通常发生在远端进程异常，导致进程被杀
         */
        fun binderDead()
    }

    companion object {
        @JvmStatic
        fun asInterface(bridgeInterface: ProcessCallFunction): RemoteProcessCallInterface {
            return Proxy(binderInterface = bridgeInterface)
        }
    }

    /**
     * 调用端，持有远端的binder句柄，调用其中的方法会指向远端进程
     */
    class Proxy(
        val binderInterface: ProcessCallFunction
    ) : RemoteProcessCallInterface {

        override val isStillAlive: Boolean get() = this.binderInterface.asBinder().isBinderAlive

        private val deathRecipientList = LinkedList<DeathRecipient>()

        init {
            val binderDeathRecipient = object : IBinder.DeathRecipient {
                override fun binderDied() {
                    this@Proxy.binderInterface.asBinder().unlinkToDeath(this, 0)
                    val deathRecipientCopy = synchronized(this@Proxy.deathRecipientList) {
                        this@Proxy.deathRecipientList.toList()
                    }
                    deathRecipientCopy.forEach { it.binderDead() }
                }
            }
            this.binderInterface.asBinder().linkToDeath(binderDeathRecipient, 0)
        }

        override fun invoke(request: Request): Response? {
            val bridgeParameter = BridgeParameter.obtain()
            bridgeParameter.request = request
            // Todo: 日志收集
            kotlin.runCatching { this.binderInterface.invoke(bridgeParameter) }
                .onFailure { it.printStackTrace() }
                .onFailure { bridgeParameter.response = InternalInvocationFailureResponse(null, it) }
            return try {
                bridgeParameter.response
            } finally {
                bridgeParameter.recycle()
            }
        }

        override fun linkToDeath(deathRecipient: DeathRecipient) {
            if (!this.isStillAlive) {
                deathRecipient.binderDead()
                return
            }
            synchronized(this.deathRecipientList) {
                if (!this.deathRecipientList.contains(deathRecipient)) {
                    this.deathRecipientList += deathRecipient
                }
            }
        }

        override fun unlinkToDeath(deathRecipient: DeathRecipient) {
            synchronized(this.deathRecipientList) {
                if (this.deathRecipientList.contains(deathRecipient)) {
                    this.deathRecipientList -= deathRecipient
                }
            }
        }
    }

    /**
     * 远端调用的实现类，被调用端
     */
    open class Stub : RemoteProcessCallInterface {

        private val bridgeInterceptorChain: MutableList<BridgeInterceptor<Request>> = LinkedList()

        internal val bridgeInterface = object : ProcessCallBridgeInterface() {
            override fun invoke(bridgeParameter: BridgeParameter?) {
                val request = bridgeParameter?.request ?: return
                bridgeParameter.response = this@Stub.invoke(request = request)
            }
        }

        override val isStillAlive: Boolean get() = true

        operator fun plus(bridgeInterceptor: BridgeInterceptor<out Request>) = apply {
            this.add(bridgeInterceptor)
        }

        operator fun plusAssign(bridgeInterceptor: BridgeInterceptor<out Request>) {
            this.add(bridgeInterceptor)
        }

        fun add(bridgeInterceptor: BridgeInterceptor<out Request>) {
            synchronized(this.bridgeInterceptorChain) {
                this.bridgeInterceptorChain.add(bridgeInterceptor as BridgeInterceptor<Request>)
            }
        }

        final override fun invoke(request: Request): Response? {
            val bridgeInterceptor = synchronized(this.bridgeInterceptorChain) {
                this.bridgeInterceptorChain.find { it.shouldHandle(request = request) }
            }
            val handleResult = kotlin.runCatching { bridgeInterceptor?.handle(request = request) }
            return if (handleResult.isSuccess) {
                InvocationResponse(handleResult.getOrNull(), null)
            } else InvocationResponse(null, handleResult.exceptionOrNull())
        }

        /**
         * local instance is never dead
         */
        final override fun linkToDeath(deathRecipient: DeathRecipient) {}

        /**
         * local instance is never dead
         */
        final override fun unlinkToDeath(deathRecipient: DeathRecipient) {}
    }
}
