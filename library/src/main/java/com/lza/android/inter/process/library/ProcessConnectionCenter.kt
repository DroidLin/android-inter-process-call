package com.lza.android.inter.process.library

import android.annotation.SuppressLint
import android.content.Context
import com.lza.android.inter.process.library.bridge.parameter.ConnectionContext
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.interfaces.RemoteProcessCallInterface
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resumeWithException

/**
 * @author liuzhongao
 * @since 2024/1/9 23:42
 */
@SuppressLint("StaticFieldLeak")
internal object ProcessConnectionCenter {

    private val iBinderMap = ConcurrentHashMap<String, ProcessBasicInterface>()
    private val pendingConnectRequestMap = ConcurrentHashMap<String, Deferred<Boolean>>()

    internal lateinit var processCallInitConfig: ProcessCallInitConfig

    fun binderEstablished(processKey: String, basicInterface: ProcessBasicInterface) {
        val existBridgeInterface = iBinderMap[processKey]
        if (existBridgeInterface == null || !existBridgeInterface.isStillAlive) {
            basicInterface.linkToDeath(
                deathRecipient = object : RemoteProcessCallInterface.DeathRecipient {
                    override fun binderDead() {
                        basicInterface.unlinkToDeath(deathRecipient = this)
                        this@ProcessConnectionCenter.iBinderMap.remove(processKey)
                    }
                }
            )
            iBinderMap[processKey] = basicInterface
        }
    }

    fun responseHandShakeToRemote(processKey: String, remoteBasicInterface: ProcessBasicInterface) {
        kotlin.runCatching {
            remoteBasicInterface.onReceiveBinder(
                processKey = processKey,
                basicInterface = ProcessBasicInterface.Stub()
            )
        }.onFailure { it.printStackTrace() }
    }

    fun isRemoteConnected(destKey: String): Boolean = iBinderMap[destKey]?.isStillAlive ?: false

    suspend fun tryConnectToRemote(context: Context, selfKey: String, destKey: String): Boolean {
        val bridgeInterface = iBinderMap[destKey]
        if (bridgeInterface != null && bridgeInterface.isStillAlive) {
            return true
        }

        val previousAsyncConnectionTask = pendingConnectRequestMap[destKey]
        if (previousAsyncConnectionTask != null) {
            return previousAsyncConnectionTask.await()
        }

        return coroutineScope {
            val asyncConnectionTask: Deferred<Boolean> = async {
                suspendCancellableCoroutine { cancellableContinuation ->
                    if (!this@ProcessConnectionCenter::processCallInitConfig.isInitialized) {
                        cancellableContinuation.resumeWithException(NullPointerException("call ProcessCenter#init before trying to connect to remote."))
                        return@suspendCancellableCoroutine
                    }
                    fun <T> CancellableContinuation<T>.resumeOrElse(
                        result: Result<T>,
                        block: () -> Unit = {}
                    ) {
                        if (!this.isCancelled && !this.isCompleted) {
                            resumeWith(result = result)
                        } else block()
                    }

                    val timeoutJob = launch {
                        delay(10_000L)
                        this@ProcessConnectionCenter.pendingConnectRequestMap.remove(destKey)
                        cancellableContinuation.resumeOrElse(result = Result.success(value = false)) {
                            // Todo: 补充连接超时返回监控日志
                        }
                    }
                    val basicInterface = object : ProcessBasicInterface.Stub() {

                        override fun onReceiveBinder(
                            processKey: String,
                            basicInterface: ProcessBasicInterface
                        ) {
                            timeoutJob.cancel()
                            binderEstablished(
                                processKey = processKey,
                                basicInterface = basicInterface
                            )
                            pendingConnectRequestMap.remove(processKey)
                            cancellableContinuation.resumeOrElse(result = Result.success(value = true)) {
                                // Todo: 补充连接超时返回监控日志
                            }
                        }
                    }
                    val connectionAdapter = processCallInitConfig.connectionAdapter
                    val requestBundle = ProcessRequestBundle(
                        basicInterface = basicInterface,
                        connectionContext = ConnectionContext(selfKey = selfKey, destKey = destKey)
                    )
                    kotlin.runCatching {
                        connectionAdapter.onAttachToRemote(
                            context = context,
                            bundle = requestBundle
                        )
                    }.onFailure { cancellableContinuation.resumeOrElse(Result.failure(exception = it)) { /*Todo: 补充连接超时返回监控日志*/ } }
                }
            }
            this@ProcessConnectionCenter.pendingConnectRequestMap[destKey] = asyncConnectionTask
            asyncConnectionTask.await()
        }
    }

    /**
     * 如果binder连接失败了，那么不会返回，不然会出现较多DeadObjectException
     */
    operator fun get(processKey: String): ProcessBasicInterface? {
        val existsBridgeInterface = iBinderMap[processKey]
        if (existsBridgeInterface == null || !existsBridgeInterface.isStillAlive) {
            return null
        }
        return existsBridgeInterface
    }
}