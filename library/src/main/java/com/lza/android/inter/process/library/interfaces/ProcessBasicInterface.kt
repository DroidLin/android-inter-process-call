package com.lza.android.inter.process.library.interfaces

import com.lza.android.inter.process.library.ProcessImplementationCenter
import com.lza.android.inter.process.library.bridge.interceptor.handShakeBridgeInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.remoteProcessCallInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.suspendRemoteProcessCallInterceptor
import com.lza.android.inter.process.library.bridge.parameter.HandShakeRequest
import com.lza.android.inter.process.library.bridge.parameter.InternalInvocationFailureResponse
import com.lza.android.inter.process.library.bridge.parameter.InvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.InvocationResponse
import com.lza.android.inter.process.library.bridge.parameter.SuspendInvocationRequest
import com.lza.android.inter.process.library.invokeSuspend
import com.lza.android.inter.process.library.safeUnbox
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException

/**
 * 一些和跨进程连接相关的一些调用会放置在此，
 * 连接时的双向通道打通等支持后续可扩展
 *
 * 这里进行封装的目的很简单，只是为了将[com.lza.android.library.bridge.parameter.Request]一层抹平,
 * 让后续的开发者不需要在关注这部分内容，直接面向已有的、新增的接口开发，减少出bug的次数
 *
 * @author liuzhongao
 * @since 2024/1/20 17:14
 */
internal val ProcessBasicInterface.bridgeInterface: RemoteProcessCallInterface
    get() = when(this) {
        is ProcessBasicInterface.Stub -> this.bridgeInterface
        is ProcessBasicInterface.Proxy -> this.remoteBridgeInterface
        else -> throw IllegalArgumentException("unknown basic interface type: ${this.javaClass.name}")
    }

internal sealed interface ProcessBasicInterface {

    val isStillAlive: Boolean

    fun onReceiveBinder(processKey: String, basicInterface: ProcessBasicInterface) {}

    /**
     * 注册远端断连回调, 如果当前已经断开连接会立刻回调
     */
    fun linkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {}

    fun unlinkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {}

    fun invokeRemoteProcessMethod(
        declaringClass: Class<*>,
        method: Method,
        args: Array<Any?>,
    ): Any? = null

    suspend fun invokeSuspendRemoteProcessMethod(
        declaringClass: Class<*>,
        method: Method,
        args: Array<Any?>,
    ): Any? = null

    companion object {
        @JvmStatic
        fun asInterface(bridgeInterface: RemoteProcessCallInterface): ProcessBasicInterface {
            return Proxy(remoteBridgeInterface = bridgeInterface)
        }
    }

    open class Stub : ProcessBasicInterface {

        val bridgeInterface = RemoteProcessCallInterface.Stub()

        final override val isStillAlive: Boolean get() = true

        init {
            this.bridgeInterface += handShakeBridgeInterceptor { processKey, basicInterface ->
                this.onReceiveBinder(processKey, basicInterface)
            }
            this.bridgeInterface += remoteProcessCallInterceptor { clazz, method, args ->
                this.invokeRemoteProcessMethod(declaringClass = clazz, method = method, args = args)
            }
            this.bridgeInterface += suspendRemoteProcessCallInterceptor { clazz, method, args ->
                this.invokeSuspendRemoteProcessMethod(declaringClass = clazz, method = method, args = args)
            }
        }

        override fun invokeRemoteProcessMethod(
            declaringClass: Class<*>,
            method: Method,
            args: Array<Any?>
        ): Any? = method.invoke(ProcessImplementationCenter[declaringClass], *args).safeUnbox()

        override suspend fun invokeSuspendRemoteProcessMethod(
            declaringClass: Class<*>,
            method: Method,
            args: Array<Any?>
        ): Any? = method.invokeSuspend(ProcessImplementationCenter[declaringClass], *args).safeUnbox()
    }

    class Proxy(val remoteBridgeInterface: RemoteProcessCallInterface) : ProcessBasicInterface {

        override val isStillAlive: Boolean get() = this.remoteBridgeInterface.isStillAlive

        override fun linkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {
            this.remoteBridgeInterface.linkToDeath(deathRecipient)
        }

        override fun unlinkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {
            this.remoteBridgeInterface.unlinkToDeath(deathRecipient)
        }

        override fun onReceiveBinder(processKey: String, basicInterface: ProcessBasicInterface) {
            this.remoteBridgeInterface.invoke(request = HandShakeRequest(processKey, basicInterface))
        }

        override fun invokeRemoteProcessMethod(
            declaringClass: Class<*>,
            method: Method,
            args: Array<Any?>
        ): Any? {
            val request = InvocationRequest(
                interfaceClassName = declaringClass.name,
                interfaceMethodName = method.name,
                interfaceParameterTypes = method.parameterTypes.map { it.name },
                interfaceParameters = args.toList(),
                isSuspendFunction = false,
                isKotlinFunction = true
            )
            return this.remoteBridgeInterface.invoke(request = request)
        }

        override suspend fun invokeSuspendRemoteProcessMethod(
            declaringClass: Class<*>,
            method: Method,
            args: Array<Any?>
        ): Any? {
            return suspendCoroutineUninterceptedOrReturn { continuation ->
                val continuationProxy = OneShotContinuation(continuation)
                val deathRecipient = object : RemoteProcessCallInterface.DeathRecipient {
                    override fun binderDead() {
                        this@Proxy.unlinkToDeath(deathRecipient = this)
                        continuationProxy.resume(null)
                    }
                }
                val suspendCallback = object : RemoteProcessSuspendCallback.Stub() {
                    override fun callbackSuspend(data: Any?, throwable: Throwable?) {
                        this@Proxy.unlinkToDeath(deathRecipient = deathRecipient)
                        if (throwable != null) {
                            continuationProxy.resumeWithException(throwable)
                        } else continuationProxy.resume(data)
                    }
                }
                val parameterTypeExcludeContinuation = method.parameterTypes.filter { it != Continuation::class.java }.map { it.name }
                val parameterValueExcludeContinuation = args.filter { it !is Continuation<*> }
                val request = SuspendInvocationRequest(
                    interfaceClassName = declaringClass.name,
                    interfaceMethodName = method.name,
                    interfaceParameterTypes = parameterTypeExcludeContinuation,
                    interfaceParameters = parameterValueExcludeContinuation,
                    isKotlinFunction = true,
                    remoteProcessSuspendCallback = suspendCallback
                )
                this.linkToDeath(deathRecipient = deathRecipient)
                when (val response = this.remoteBridgeInterface.invoke(request = request)) {
                    is InternalInvocationFailureResponse -> COROUTINE_SUSPENDED
                    is InvocationResponse -> {
                        if (response.throwable != null) {
                            throw response.throwable
                        }
                        response.responseObject
                    }
                    else -> throw UnsupportedOperationException("unSupported ResponseType: ${response?.javaClass}")
                }
            }
        }
    }

    class OneShotContinuation<T>(
        private val continuation: Continuation<T>,
        private val coroutineContext: CoroutineContext = EmptyCoroutineContext
    ) : Continuation<T> {

        @Volatile
        private var continuationResumed: Boolean = false

        override val context: CoroutineContext get() = this.continuation.context + coroutineContext

        override fun resumeWith(result: Result<T>) {
            if (!this.continuationResumed) {
                this.continuation.resumeWith(result)
                this.continuationResumed = true
            }
        }
    }
}