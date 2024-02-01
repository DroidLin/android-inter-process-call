package com.lza.android.inter.process.library.component

import android.content.ContentProvider
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import com.lza.android.inter.process.library.ProcessConnectionCenter
import com.lza.android.inter.process.library.bridge.parameter.ConnectionContext

/**
 * @author liuzhongao
 * @since 2024/1/21 12:12
 */
abstract class AbstractProcessContentProvider : ContentProvider() {

    /**
     * key for current process where this component exist.
     */
    protected abstract val currentProcessKey: String

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? {
        val requestBundle = queryArgs?.requestBundle
        if (requestBundle != null && this.currentProcessKey == requestBundle.connectionContext.destKey) {
            ProcessConnectionCenter.binderEstablished(processKey = requestBundle.connectionContext.selfKey, basicInterface = requestBundle.basicInterface)
            ProcessConnectionCenter.responseHandShakeToRemote(processKey = requestBundle.connectionContext.destKey, remoteBasicInterface = requestBundle.basicInterface)
            this.onReceiveBinder(connectionContext = requestBundle.connectionContext)
        }
        return super.query(uri, projection, queryArgs, cancellationSignal)
    }

    abstract fun onReceiveBinder(connectionContext: ConnectionContext)
}