package com.lza.android.inter.process.call

import com.lza.android.inter.process.library.component.AbstractProcessReceiver

/**
 * @author liuzhongao
 * @since 2024/1/9 22:28
 */
class LibraryProcessBroadcastReceiver : AbstractProcessReceiver() {

    override val broadcastRequireAction: String get() = ProcessConst.KEY_LIBRARY_PROCESS
}