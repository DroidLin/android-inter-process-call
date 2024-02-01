package com.lza.android.inter.process.library

import android.annotation.SuppressLint
import android.content.Context
import com.lza.android.inter.process.library.bridge.parameter.ConnectionContext
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.interfaces.RemoteProcessCallInterface
import com.lza.android.inter.process.library.kotlin.OneShotContinuation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * @author liuzhongao
 * @since 2024/1/9 23:42
 */
@SuppressLint("StaticFieldLeak")
internal object ProcessConnectionCenter {

    private val iBinderMap = ConcurrentHashMap<String, ProcessBasicInterface>()
    private val pendingConnectRequestMap = ConcurrentHashMap<String, Deferred<Boolean>>()

    private val coroutineMutex = Mutex()

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

    fun isRemoteConnected(destKey: String): Boolean = this.iBinderMap[destKey]?.isStillAlive ?: false

    suspend fun tryConnectToRemote(context: Context, selfKey: String, destKey: String): Boolean {
        val bridgeInterface = this.iBinderMap[destKey]
        if (bridgeInterface != null && bridgeInterface.isStillAlive) {
            return true
        }

        if (this.pendingConnectRequestMap[destKey] == null) {
            this.coroutineMutex.withLock {
                if (this.pendingConnectRequestMap[destKey] == null) {
                    this.pendingConnectRequestMap[destKey] = coroutineScope {
                        async {
                            suspendCoroutine { continuation ->
                                val oneShotContinuation = OneShotContinuation(continuation = continuation)
                                if (!this@ProcessConnectionCenter::processCallInitConfig.isInitialized) {
                                    oneShotContinuation.resumeWithException(NullPointerException("call ProcessCenter#init before trying to connect to remote."))
                                    return@suspendCoroutine
                                }

                                val timeoutJob = launch {
                                    delay(10_000L)
                                    this@ProcessConnectionCenter.pendingConnectRequestMap.remove(destKey)
                                    oneShotContinuation.resume(false)
                                }
                                val basicInterface = object : ProcessBasicInterface.Stub() {

                                    override fun onReceiveBinder(
                                        processKey: String,
                                        basicInterface: ProcessBasicInterface
                                    ) {
                                        timeoutJob.cancel()
                                        this@ProcessConnectionCenter.binderEstablished(
                                            processKey = processKey,
                                            basicInterface = basicInterface
                                        )
                                        this@ProcessConnectionCenter.pendingConnectRequestMap.remove(processKey)
                                        oneShotContinuation.resume(true)
                                    }
                                }
                                val connectionAdapter = this@ProcessConnectionCenter.processCallInitConfig.connectionAdapter
                                val requestBundle = ProcessRequestBundle(
                                    basicInterface = basicInterface,
                                    connectionContext = ConnectionContext(selfKey = selfKey, destKey = destKey)
                                )
                                kotlin.runCatching {
                                    connectionAdapter.onAttachToRemote(
                                        context = context,
                                        bundle = requestBundle
                                    )
                                }.onFailure { oneShotContinuation.resumeWithException(it) }
                            }
                        }
                    }
                }
            }
        }
        return this.pendingConnectRequestMap[destKey]?.await() ?: false
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