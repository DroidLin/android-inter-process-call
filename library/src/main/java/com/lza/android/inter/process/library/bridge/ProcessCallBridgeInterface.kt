package com.lza.android.inter.process.library.bridge

import com.lza.android.inter.process.library.ProcessCallFunction


/**
 * 处理各种跨进程请求请求
 *
 * 请求需要满足实现[Request]接口,
 * 同步调用的返回值需要满足实现[java.io.Serializable]或[android.os.Parcelable]接口
 *
 * @author liuzhongao
 * @since 2024/1/14 15:27
 */
internal abstract class ProcessCallBridgeInterface : ProcessCallFunction.Stub()