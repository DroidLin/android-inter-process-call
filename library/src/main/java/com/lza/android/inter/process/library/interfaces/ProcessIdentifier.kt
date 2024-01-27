package com.lza.android.inter.process.library.interfaces

/**
 * 用作判断当前进程的key
 *
 * @author liuzhongao
 * @since 2024/1/17 19:55
 */
interface ProcessIdentifier {
    val keyForCurrentProcess: String
}