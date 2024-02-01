package com.lza.android.inter.process.library.component

import android.app.Service
import android.content.Intent
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.bridge.parameter.ConnectionContext

/**
 * @author liuzhongao
 * @since 2024/1/21 13:45
 */
abstract class AbstractProcessService : Service() {

    /**
     * key for current process where this component exist.
     */
    protected abstract val currentProcessKey: String

    abstract fun onReceiveBinder(connectionContext: ConnectionContext)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestBundle = intent?.requestBundle
        if (requestBundle != null && this.currentProcessKey == requestBundle.connectionContext.destKey) {
            ProcessConnectionCenter.binderEstablished(processKey = requestBundle.connectionContext.selfKey, basicInterface = requestBundle.basicInterface)
            ProcessConnectionCenter.responseHandShakeToRemote(processKey = requestBundle.connectionContext.destKey, remoteBasicInterface = requestBundle.basicInterface)
            this.onReceiveBinder(connectionContext = requestBundle.connectionContext)
        }
        return super.onStartCommand(intent, flags, startId)
    }

}