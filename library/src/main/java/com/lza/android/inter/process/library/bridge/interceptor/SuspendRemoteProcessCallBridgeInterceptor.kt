package com.lza.android.inter.process.library.bridge.interceptor

import com.lza.android.inter.process.library.bridge.parameter.Request
import com.lza.android.inter.process.library.bridge.parameter.SuspendInvocationRequest
import com.lza.android.inter.process.library.interfaces.RemoteProcessSuspendCallback
import com.lza.android.inter.process.library.stringTypeConvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * 非阻塞式kotlin挂起函数远端被调用端实现
 *
 * @author liuzhongao
 * @since 2024/1/17 10:31
 */
internal fun suspendRemoteProcessCallInterceptor(block: suspend (Class<*>, Method, Array<Any?>) -> Any?): BridgeInterceptor<Request> {
    return SuspendRemoteProcessCallBridgeInterceptor(block = block) as BridgeInterceptor<Request>
}

internal class SuspendRemoteProcessCallBridgeInterceptor(
    private val block: suspend (Class<*>, method: Method, args: Array<Any?>) -> Any?
) : BridgeInterceptor<SuspendInvocationRequest> {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun shouldHandle(request: Request): Boolean = request is SuspendInvocationRequest

    override fun handle(request: SuspendInvocationRequest): Any? {
        val declaringJvmClass = Class.forName(request.interfaceClassName) as Class<Any>
        val parameterClassTypes = request.interfaceParameterTypes.stringTypeConvert
        return this.invokeKotlinSuspendFunction(
            declaringJvmClass = declaringJvmClass,
            methodName = request.interfaceMethodName,
            parameterTypes = parameterClassTypes,
            parameterValues = request.interfaceParameters,
            suspendCallback = request.remoteProcessSuspendCallback
        )
    }

    private fun invokeKotlinSuspendFunction(
        declaringJvmClass: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        parameterValues: List<Any?>,
        suspendCallback: RemoteProcessSuspendCallback
    ): Any? {
        val continuation = object : Continuation<Any?> {
            override val context: CoroutineContext get() = this@SuspendRemoteProcessCallBridgeInterceptor.coroutineScope.coroutineContext
            override fun resumeWith(result: Result<Any?>) = suspendCallback.callbackSuspend(data = result.getOrNull(), throwable = result.exceptionOrNull())
        }
        val functionInvocation = this.block.javaClass.getDeclaredMethod("invoke", Class::class.java, Method::class.java, Array::class.java, Continuation::class.java)
        // suspend functions need Continuation instance to be the last parameter in parameter array.
        val parameterTypesWithContinuation = parameterTypes + Continuation::class.java
        val method = declaringJvmClass.getDeclaredMethod(methodName, *(parameterTypesWithContinuation.toTypedArray()))
        return functionInvocation.invoke(this.block, declaringJvmClass, method, parameterValues.toTypedArray(), continuation)
    }
}