package com.lza.android.inter.process.library.interfaces

import com.lza.android.inter.process.library.ProcessCenter
import com.lza.android.inter.process.library.ProcessImplementationCenter
import com.lza.android.inter.process.library.bridge.interceptor.directRemoteProcessCallBridgeInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.directSuspendRemoteProcessCallBridgeInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.handShakeBridgeInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.reflectRemoteProcessCallInterceptor
import com.lza.android.inter.process.library.bridge.interceptor.reflectSuspendRemoteProcessCallInterceptor
import com.lza.android.inter.process.library.bridge.parameter.DirectInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.DirectSuspendInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.HandShakeRequest
import com.lza.android.inter.process.library.bridge.parameter.InternalInvocationFailureResponse
import com.lza.android.inter.process.library.bridge.parameter.InvocationResponse
import com.lza.android.inter.process.library.bridge.parameter.ReflectionInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.ReflectionSuspendInvocationRequest
import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.invokeSuspend
import com.lza.android.inter.process.library.kotlin.OneShotContinuation
import com.lza.android.inter.process.library.safeUnbox
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * @author liuzhongao
 * @since 2024/1/20 17:14
 */
internal val ProcessBasicInterface.bridgeInterface: RemoteProcessCallInterface
    get() = when(this) {
        is ProcessBasicInterface.Stub -> this.bridgeInterface
        is ProcessBasicInterface.Proxy -> this.remoteBridgeInterface
        else -> throw IllegalArgumentException("unknown basic interface type: ${this.javaClass.name}")
    }

/**
 * all inter process communication happens through this interface.
 *
 * [ProcessBasicInterface.invokeRemoteProcessMethod] and [ProcessBasicInterface.invokeSuspendRemoteProcessMethod] two
 * receive function call parameters, returns result from remote process implementation.
 *
 * these two remote call will throw remote exceptions while catch internal binder exceptions,
 * will provide internal/external exception handler in the next release.
 */
internal sealed interface ProcessBasicInterface {

    val isStillAlive: Boolean

    fun onReceiveBinder(processKey: String, basicInterface: ProcessBasicInterface) {}

    fun linkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {}

    fun unlinkToDeath(deathRecipient: RemoteProcessCallInterface.DeathRecipient) {}

    fun invokeDirectRemoteFunction(
        declaringClassName: String,
        functionName: String,
        args: List<Any?>
    ): Any? = null

    suspend fun invokeDirectRemoteSuspendFunction(
        declaringClassName: String,
        functionName: String,
        args: List<Any?>
    ): Any? = null

    fun invokeRemoteProcessMethod(
        declaringClass: Class<*>,
        methodName: String,
        argTypes: Array<Class<*>>,
        args: Array<Any?>,
    ): Any? = null

    suspend fun invokeSuspendRemoteProcessMethod(
        declaringClass: Class<*>,
        methodName: String,
        argTypes: Array<Class<*>>,
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
            this.bridgeInterface += reflectRemoteProcessCallInterceptor { clazz, methodName, argTypes, args ->
                this.invokeRemoteProcessMethod(declaringClass = clazz, methodName = methodName, argTypes = argTypes, args = args)
            }
            this.bridgeInterface += reflectSuspendRemoteProcessCallInterceptor { clazz, methodName, argTypes, args ->
                this.invokeSuspendRemoteProcessMethod(declaringClass = clazz, methodName = methodName, argTypes = argTypes, args = args)
            }
            this.bridgeInterface += directRemoteProcessCallBridgeInterceptor { className, functionName, args ->
                this.invokeDirectRemoteFunction(className, functionName, args)
            }
            this.bridgeInterface += directSuspendRemoteProcessCallBridgeInterceptor { className, functionName, args ->
                this.invokeDirectRemoteSuspendFunction(className, functionName, args)
            }
        }

        override fun invokeDirectRemoteFunction(
            declaringClassName: String,
            functionName: String,
            args: List<Any?>
        ): Any? {
            val directAccessImpl = ProcessCenter.getOrGeneratedStubFunction(className = declaringClassName)
            return directAccessImpl.invokeNonSuspendFunction(functionName, args).safeUnbox()
        }

        override suspend fun invokeDirectRemoteSuspendFunction(
            declaringClassName: String,
            functionName: String,
            args: List<Any?>
        ): Any? {
            val directAccessImpl = ProcessCenter.getOrGeneratedStubFunction(className = declaringClassName)
            return directAccessImpl.invokeSuspendFunction(functionName, args).safeUnbox()
        }

        override fun invokeRemoteProcessMethod(
            declaringClass: Class<*>,
            methodName: String,
            argTypes: Array<Class<*>>,
            args: Array<Any?>
        ): Any? = declaringClass.getDeclaredMethod(methodName, *argTypes)
            .invoke(ProcessImplementationCenter[declaringClass], *args).safeUnbox()

        override suspend fun invokeSuspendRemoteProcessMethod(
            declaringClass: Class<*>,
            methodName: String,
            argTypes: Array<Class<*>>,
            args: Array<Any?>
        ): Any? = declaringClass.getDeclaredMethod(methodName, *argTypes)
            .invokeSuspend(ProcessImplementationCenter[declaringClass], *args).safeUnbox()
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

        override fun invokeDirectRemoteFunction(
            declaringClassName: String,
            functionName: String,
            args: List<Any?>
        ): Any? {
            return this.invokeRemoteNonSuspendFunction {
                DirectInvocationRequest(
                    interfaceClassName = declaringClassName,
                    interfaceMethodName = functionName,
                    interfaceParameters = args
                )
            }
        }

        override suspend fun invokeDirectRemoteSuspendFunction(
            declaringClassName: String,
            functionName: String,
            args: List<Any?>
        ): Any? {
            return suspendCoroutineUninterceptedOrReturn { continuation ->
                this.invokeRemoteSuspendFunction(continuation) { suspendCallback ->
                    DirectSuspendInvocationRequest(
                        interfaceClassName = declaringClassName,
                        interfaceMethodName = functionName,
                        interfaceParameters = args,
                        remoteProcessSuspendCallback = suspendCallback
                    )
                }
            }
        }

        override fun invokeRemoteProcessMethod(
            declaringClass: Class<*>,
            methodName: String,
            argTypes: Array<Class<*>>,
            args: Array<Any?>
        ): Any? {
            return this.invokeRemoteNonSuspendFunction {
                ReflectionInvocationRequest(
                    interfaceClassName = declaringClass.name,
                    interfaceMethodName = methodName,
                    interfaceParameterTypes = argTypes.map { it.name },
                    interfaceParameters = args.toList(),
                    isKotlinFunction = true
                )
            }
        }

        override suspend fun invokeSuspendRemoteProcessMethod(
            declaringClass: Class<*>,
            methodName: String,
            argTypes: Array<Class<*>>,
            args: Array<Any?>
        ): Any? {
            return suspendCoroutineUninterceptedOrReturn { continuation ->
                this.invokeRemoteSuspendFunction(continuation) { suspendCallback ->
                    ReflectionSuspendInvocationRequest(
                        interfaceClassName = declaringClass.name,
                        interfaceMethodName = methodName,
                        interfaceParameterTypes = argTypes.map { it.name },
                        interfaceParameters = args.toList(),
                        remoteProcessSuspendCallback = suspendCallback
                    )
                }
            }
        }

        private inline fun invokeRemoteNonSuspendFunction(binderRequestBuilder: () -> Request): Any? {
            val request = binderRequestBuilder()
            return when (val response = this.remoteBridgeInterface.invoke(request = request)) {
                // for synchronized invocation, throw the remote exception in current
                // call stack is the best choice.
                is InvocationResponse -> {
                    if (response.throwable != null) {
                        throw Throwable(response.throwable)
                    }
                    response.responseObject
                }
                // you may ask why don`t we handle InternalInvocationFailureResponse like
                // what it is done in suspend remote invocation?
                // we don`t care about internal failures in synchronized function call.
                // use default backup logic instead.
                else -> null
            }
        }

        private inline fun invokeRemoteSuspendFunction(
            continuation: Continuation<Any?>,
            binderRequestBuilder: (RemoteProcessSuspendCallback) -> Request
        ): Any? {
            val continuationProxy = OneShotContinuation(continuation)
            val deathRecipient = object : RemoteProcessCallInterface.DeathRecipient {
                override fun binderDead() {
                    this@Proxy.unlinkToDeath(deathRecipient = this)
                    // returns null to handle backup logic.
                    continuationProxy.resume(null)
                }
            }
            val suspendCallback = object : RemoteProcessSuspendCallback.Stub() {
                override fun callbackSuspend(data: Any?, throwable: Throwable?) {
                    this@Proxy.unlinkToDeath(deathRecipient = deathRecipient)
                    if (throwable != null) {
                        continuationProxy.resumeWithException(Throwable(throwable))
                    } else continuationProxy.resume(data)
                }
            }
            val request = binderRequestBuilder(suspendCallback)
            this.linkToDeath(deathRecipient = deathRecipient)
            return when (val response = this.remoteBridgeInterface.invoke(request = request)) {
                // while binder disconnected, try to suspend current function
                // to wait for [RemoteProcessCallInterface.DeathRecipient] invocation
                is InternalInvocationFailureResponse -> COROUTINE_SUSPENDED
                // most common response instance from remote.
                is InvocationResponse -> {
                    // should clear reference of deathRecipient to prevent memory leak.
                    this.unlinkToDeath(deathRecipient = deathRecipient)
                    if (response.throwable != null) {
                        throw Throwable(response.throwable)
                    }
                    response.responseObject
                }
                else -> throw UnsupportedOperationException("unSupported ResponseType: ${response?.javaClass}")
            }
        }
    }
}