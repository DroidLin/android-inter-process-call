package com.lza.android.inter.process.library.interfaces

import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig

/**
 * @author liuzhongao
 * @since 2024/1/8 23:18
 */
interface IPCenter {

    fun init(initConfig: ProcessCallInitConfig)

    fun <T : IPCNoProguard> putService(clazz: Class<T>, impl: T?)

    /**
     * @param defaultImpl 用于跨进程调用失败后当前进程的降级操作
     */
    fun <T : IPCNoProguard> getService(destProcessKey: String, clazz: Class<T>, defaultImpl: T? = null): T
}