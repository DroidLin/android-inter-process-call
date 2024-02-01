package com.lza.android.inter.process.library.component

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle

fun broadcastConnectionIntent(context: Context, requestBundle: ProcessRequestBundle): Intent {
    val intent = Intent()
    intent.action = requestBundle.connectionContext.destKey
    intent.requestBundle = requestBundle
    intent.`package` = context.packageName
    return intent
}

fun contentProviderRequestParam(requestBundle: ProcessRequestBundle): Bundle {
    val bundle = Bundle()
    bundle.requestBundle = requestBundle
    return bundle
}

fun serviceLaunchIntent(requestBundle: ProcessRequestBundle): Intent {
    val intent = Intent()
    intent.requestBundle = requestBundle
    return intent
}