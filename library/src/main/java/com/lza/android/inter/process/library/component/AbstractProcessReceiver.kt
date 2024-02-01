package com.lza.android.inter.process.library.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.bridge.parameter.ConnectionContext

/**
 * @author liuzhongao
 * @since 2024/1/9 00:04
 */
abstract class AbstractProcessReceiver : BroadcastReceiver() {

    /**
     * key for current process where this component exist.
     */
    protected abstract val broadcastRequireAction: String

    final override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action.isNullOrEmpty() || action != this.broadcastRequireAction) {
            return
        }
        val requestBundle = intent.requestBundle
        if (requestBundle == null) {
            Log.d(TAG, "receive action: ${this.broadcastRequireAction}, but null with key: KEY_BINDER")
            return
        }
        val basicInterface = requestBundle.basicInterface
        val connectionContext = requestBundle.connectionContext
        if (this.broadcastRequireAction == connectionContext.destKey) {
            ProcessConnectionCenter.binderEstablished(
                processKey = connectionContext.selfKey,
                basicInterface = basicInterface
            )
            ProcessConnectionCenter.responseHandShakeToRemote(
                processKey = connectionContext.destKey,
                remoteBasicInterface = basicInterface
            )
            this.onReceiveBinder(connectionContext = connectionContext)
        } else Log.d(TAG, "not correct broadcast action key.")
    }

    open fun onReceiveBinder(connectionContext: ConnectionContext) {}

    companion object {
        private const val TAG = "ProcessConnection"
    }
}