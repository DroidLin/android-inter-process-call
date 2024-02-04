package com.lza.android.inter.process.annotation

import kotlin.reflect.KClass

/**
 * @author liuzhongao
 * @since 2024/2/4 16:24
 */
annotation class RemoteProcessInterface(
    val clazz: KClass<*>,
)