package com.lza.android.inter.process.library.component

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle

/**
 * @author liuzhongao
 * @since 2024/1/10 21:52
 */
internal const val KEY_PROCESS_REQUEST_BUNDLE = "key_process_request_bundle"

internal var Intent.requestBundle: ProcessRequestBundle?
    get() = this.extras?.requestBundle
    set(value) {
        val extras = this.extras ?: Bundle()
        extras.requestBundle = value
        this.putExtras(extras)
    }

internal var Bundle.requestBundle: ProcessRequestBundle?
    set(value) {
        this.putParcelable(KEY_PROCESS_REQUEST_BUNDLE, value)
    }
    get() = kotlin.runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.getParcelable(KEY_PROCESS_REQUEST_BUNDLE, ProcessRequestBundle::class.java)
        } else this.getParcelable(KEY_PROCESS_REQUEST_BUNDLE)
    }.onFailure { it.printStackTrace() }.getOrNull()