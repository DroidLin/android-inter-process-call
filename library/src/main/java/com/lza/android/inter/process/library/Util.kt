package com.lza.android.inter.process.library

import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod


/**
 * @author liuzhongao
 * @since 2024/1/14 21:47
 */

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
    get() {
        val kClass = this.declaringClass.kotlin
        val kotlinFunction = kClass.memberFunctions.find { it.javaMethod == this }
            ?: kClass.memberExtensionFunctions.find { it.javaMethod == this }
        if (kotlinFunction != null) {
            return kotlinFunction.isSuspend
        }
        return parameterTypes.find { it.javaClass == Continuation::class.java } != null
    }