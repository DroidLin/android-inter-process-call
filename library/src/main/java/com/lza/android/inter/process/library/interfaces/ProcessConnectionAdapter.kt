package com.lza.android.inter.process.library.interfaces

import android.content.Context
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle

/**
 * 用作跨进程连接发起的适配层，取决于上层业务实现的区别，
 * 可以通过广播、Service、ContentProvider等基础组件传递binder对象到远端实现跨进程连接
 *
 * @author liuzhongao
 * @since 2024/1/20 01:12
 */
interface ProcessConnectionAdapter {

    fun onAttachToRemote(context: Context, bundle: ProcessRequestBundle)
}