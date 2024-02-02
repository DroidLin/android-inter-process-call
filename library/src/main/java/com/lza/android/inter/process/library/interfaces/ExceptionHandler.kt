package com.lza.android.inter.process.library.interfaces

/**
 * @author liuzhongao
 * @since 2024/2/2 10:16
 */
operator fun ExceptionHandler.plus(exceptionHandler: ExceptionHandler): ExceptionHandler =
    CombinedExceptionHandler(this, exceptionHandler)

fun interface ExceptionHandler {

    fun handleException(throwable: Throwable?): Boolean
}

private class CombinedExceptionHandler(
    private val one: ExceptionHandler,
    private val another: ExceptionHandler
) : ExceptionHandler {
    override fun handleException(throwable: Throwable?): Boolean {
        return one.handleException(throwable) || another.handleException(throwable)
    }
}