package com.lza.android.inter.process.library.kotlin

/**
 * @author liuzhongao
 * @since 2024/2/2 11:58
 */
class UnHandledRuntimeException : RuntimeException {

    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
    constructor(
        message: String?,
        cause: Throwable?,
        enableSuppression: Boolean,
        writableStackTrace: Boolean
    ) : super(message, cause, enableSuppression, writableStackTrace)

    companion object {
        private const val serialVersionUID: Long = -3352692322677880373L
    }
}