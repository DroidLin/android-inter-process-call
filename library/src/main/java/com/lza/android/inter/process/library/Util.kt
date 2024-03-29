package com.lza.android.inter.process.library

import com.lza.android.inter.process.library.interfaces.IPCNoProguard
import com.lza.android.inter.process.library.interfaces.IPCenter
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaMethod


/**
 * @author liuzhongao
 * @since 2024/1/14 21:47
 */
internal val unSupportedReturnType: List<Class<*>> = listOfNotNull(
    Void::class.java,
    Void::class.javaPrimitiveType,
    Unit::class.java,
    Unit::class.javaPrimitiveType
)

internal val Array<String>.stringTypeConvert: Array<Class<*>>
    get() = this.map { className -> className.stringTypeConvert }.toTypedArray()

internal val List<String>.stringTypeConvert: List<Class<*>>
    get() = this.map { className -> className.stringTypeConvert }

internal val String.stringTypeConvert: Class<*>
    get() = when (this) {
        Byte::class.java.name -> Byte::class.java
        Int::class.java.name -> Int::class.java
        Short::class.java.name -> Short::class.java
        Long::class.java.name -> Long::class.java
        Float::class.java.name -> Float::class.java
        Double::class.java.name -> Double::class.java
        Boolean::class.java.name -> Boolean::class.java
        Char::class.java.name -> Char::class.java
        else -> Class.forName(this)
    }

internal val Class<*>.isKotlinClass: Boolean
    get() = this.getDeclaredAnnotation(Metadata::class.java) != null

internal val Method.isSuspendFunction: Boolean
    get() = parameterTypes.lastOrNull() == Continuation::class.java

internal fun KCallable<*>.match(method: Method): Boolean {
    return when (this) {
        is KProperty<*> -> this.matchProperty(method = method)
        is KFunction<*> -> this.matchFunction(method = method)
        else -> false
    }
}

internal fun KProperty<*>.matchProperty(method: Method): Boolean {
    return this.getter.javaMethod == method
}

internal fun KFunction<*>.matchFunction(method: Method): Boolean {
    return this.javaMethod == method
}

internal suspend fun Method.invokeSuspend(instance: Any, vararg args: Any?): Any? {
    return kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation ->
        this.invoke(instance, *args, continuation)
    }
}

internal fun Any?.safeUnbox(): Any? {
    this ?: return null
    if (this.javaClass in unSupportedReturnType) {
        return null
    }
    return this
}

inline fun <reified T : IPCNoProguard> IPCenter.create(destinationProcessKey: String): T {
    return this.getService(destinationProcessKey, T::class.java)
}