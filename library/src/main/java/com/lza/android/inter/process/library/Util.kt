package com.lza.android.inter.process.library

import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction


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
    return requireNotNull(this.kotlinFunction).callSuspend(instance, *args)
}

internal fun Any?.safeUnbox(): Any? {
    this ?: return null
    if (this.javaClass in unSupportedReturnType) {
        return null
    }
    return this
}