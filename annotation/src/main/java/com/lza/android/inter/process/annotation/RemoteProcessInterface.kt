package com.lza.android.inter.process.annotation

import kotlin.reflect.KClass

/**
 * @author liuzhongao
 * @since 2024/2/4 16:24
 */
@Target(AnnotationTarget.CLASS)
annotation class RemoteProcessInterface(
    val interfaceClass: KClass<*>,
    val name: String = "Hello",
)