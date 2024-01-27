package com.lza.android.inter.process.library.bridge.parameter

/**
 * @author liuzhongao
 * @since 2024/1/11 00:17
 */

private const val KEY_PARAMETER_REQUEST = "key_parameter_request"
private const val KEY_PARAMETER_RESPONSE = "key_parameter_response"

internal var BridgeParameter.request: Request?
    set(value) {
        this.map[KEY_PARAMETER_REQUEST] = value
    }
    get() = this.map[KEY_PARAMETER_REQUEST] as? Request

internal var BridgeParameter.response: Response?
    set(value) {
        this.map[KEY_PARAMETER_RESPONSE] = value
    }
    get() = this.map[KEY_PARAMETER_RESPONSE] as? Response