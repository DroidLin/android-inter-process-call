package com.lza.android.inter.process.library.component

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle
import com.lza.android.inter.process.library.component.requestBundle

/**
 * 发起广播时，需要保证对应进程一定能收到广播，否则可选择使用静态广播
 */
fun broadcastConnectionIntent(context: Context, requestBundle: ProcessRequestBundle): Intent {
    val intent = Intent()
    intent.action = requestBundle.connectionContext.destKey
    intent.requestBundle = requestBundle
    intent.`package` = context.packageName
    return intent
}

/**
 * 调用端补充与[android.content.ContentProvider]对应的[android.net.Uri]
 */
fun contentProviderRequestParam(requestBundle: ProcessRequestBundle): Bundle {
    val bundle = Bundle()
    bundle.requestBundle = requestBundle
    return bundle
}

/**
 * 调用端需补充[android.content.ComponentName]
 */
fun serviceLaunchIntent(requestBundle: ProcessRequestBundle): Intent {
    val intent = Intent()
    intent.requestBundle = requestBundle
    return intent
}