package com.lza.android.inter.process.library.kotlin

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class OneShotContinuation<T>(
    private val continuation: Continuation<T>,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext
) : Continuation<T> {

    @Volatile
    private var continuationResumed: Boolean = false

    override val context: CoroutineContext get() = this.continuation.context + this.coroutineContext

    override fun resumeWith(result: Result<T>) = resumeOnFailure(result = result)

    @JvmOverloads
    fun resumeOnFailure(result: Result<T>, onFailure: (Result<T>) -> Unit = {}) {
        if (!this.continuationResumed) {
            this.continuation.resumeWith(result)
            this.continuationResumed = true
        } else onFailure(result)
    }
}