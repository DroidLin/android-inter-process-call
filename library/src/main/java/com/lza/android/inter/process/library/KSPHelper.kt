package com.lza.android.inter.process.library

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

/**
 * @author liuzhongao
 * @since 2024/2/5 22:29
 */
fun <T : Any> invokeDirectProperty(
    coroutineContext: CoroutineContext,
    androidContext: Context,
    currentProcessKey: String,
    destinationProcessKey: String,
    declaringClassName: String,
    propertyName: String
): T? {
    val isConnected = runBlocking(coroutineContext) {
        ProcessConnectionCenter.tryConnectToRemote(
            context = androidContext,
            selfKey = currentProcessKey,
            destKey = destinationProcessKey,
        )
    }
    if (!isConnected) {
        return null
    }
    val basicInterface = ProcessConnectionCenter[destinationProcessKey]
    if (basicInterface == null || !basicInterface.isStillAlive) {
        return null
    }
    return basicInterface.invokeDirectRemoteFunction(
        declaringClassName = declaringClassName,
        functionName = propertyName,
        argTypes = emptyList(),
        args = emptyList()
    ) as? T
}

fun <T : Any> invokeDirectKotlinFunction(
    coroutineContext: CoroutineContext,
    androidContext: Context,
    currentProcessKey: String,
    destinationProcessKey: String,
    declaringClassName: String,
    functionName: String,
    functionParameterTypes: List<Class<*>>,
    functionParameters: List<Any?>
): T? {
    val isConnected = runBlocking(coroutineContext) {
        ProcessConnectionCenter.tryConnectToRemote(
            context = androidContext,
            selfKey = currentProcessKey,
            destKey = destinationProcessKey,
        )
    }
    if (!isConnected) {
        return null
    }
    val basicInterface = ProcessConnectionCenter[destinationProcessKey]
    if (basicInterface == null || !basicInterface.isStillAlive) {
        return null
    }
    return basicInterface.invokeDirectRemoteFunction(
        declaringClassName = declaringClassName,
        functionName = functionName,
        argTypes = functionParameterTypes,
        args = functionParameters
    ) as? T
}

suspend fun <T : Any> invokeDirectSuspendKotlinFunction(
    androidContext: Context,
    currentProcessKey: String,
    destinationProcessKey: String,
    declaringClassName: String,
    functionName: String,
    functionParameterTypes: List<Class<*>>,
    functionParameters: List<Any?>
): T? {
    val isConnected = ProcessConnectionCenter.tryConnectToRemote(
        context = androidContext,
        selfKey = currentProcessKey,
        destKey = destinationProcessKey,
    )
    if (!isConnected) {
        return null
    }
    val basicInterface = ProcessConnectionCenter[destinationProcessKey]
    if (basicInterface == null || !basicInterface.isStillAlive) {
        return null
    }
    return basicInterface.invokeDirectRemoteSuspendFunction(
        declaringClassName = declaringClassName,
        functionName = functionName,
        argTypes = functionParameterTypes,
        args = functionParameters
    ) as? T
}