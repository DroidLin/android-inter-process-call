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

internal operator fun RemoteProcessCallInterface.plusAssign(bridgeInterceptor: BridgeInterceptor<out Request>) {
    when (this) {
        is RemoteProcessCallInterface.Stub -> this.add(bridgeInterceptor)
        else -> {}
    }
}

internal operator fun RemoteProcessCallInterface.plus(bridgeInterceptor: BridgeInterceptor<out Request>) = apply {
    when (this) {
        is RemoteProcessCallInterface.Stub -> this.add(bridgeInterceptor)
        else -> {}
    }
}

/**
 * basic remote call interface, don`t modify this interface properties or functions!!.
 */
sealed interface RemoteProcessCallInterface {

    val isStillAlive: Boolean

    operator fun invoke(request: Request): Response?

    fun linkToDeath(deathRecipient: DeathRecipient)

    fun unlinkToDeath(deathRecipient: DeathRecipient)

    fun interface DeathRecipient {

        fun binderDead()
    }

    companion object {
        @JvmStatic
        fun asInterface(bridgeInterface: ProcessCallFunction): RemoteProcessCallInterface {
            return Proxy(binderInterface = bridgeInterface)
        }
    }

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

    open class Stub : RemoteProcessCallInterface {

        private val bridgeInterceptorChain: MutableList<BridgeInterceptor<Request>> = LinkedList()

        internal val bridgeInterface = object : ProcessCallBridgeInterface() {
            override fun invoke(bridgeParameter: BridgeParameter?) {
                val request = bridgeParameter?.request ?: return
                bridgeParameter.response = this@Stub.invoke(request = request)
            }
        }

        /**
         * local interface implementation is always alive.
         */
        override val isStillAlive: Boolean get() = true

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
