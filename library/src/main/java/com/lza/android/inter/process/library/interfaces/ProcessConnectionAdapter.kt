package com.lza.android.inter.process.library.interfaces

import android.content.Context
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle

/**
 * used as adapter for connect to remote.
 *
 * implementation can decide which way to connect to remote via static Broadcast,
 * Service Bind or the ContentProvider
 *
 * @author liuzhongao
 * @since 2024/1/20 01:12
 */
interface ProcessConnectionAdapter {

    fun onAttachToRemote(context: Context, bundle: ProcessRequestBundle)
}