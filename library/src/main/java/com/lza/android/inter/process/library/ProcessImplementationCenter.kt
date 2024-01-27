package com.lza.android.inter.process.library

import java.util.concurrent.ConcurrentHashMap

/**
 * @author liuzhongao
 * @since 2024/1/14 23:00
 */
internal object ProcessImplementationCenter {

    private val implMap: MutableMap<Class<*>, Any> = ConcurrentHashMap()

    operator fun <T : Any> set(clazz: Class<T>, impl: T) {
        implMap[clazz] = impl
    }

    operator fun <T : Any> get(clazz: Class<T>): T {
        return requireNotNull(implMap[clazz]) as T
    }
}