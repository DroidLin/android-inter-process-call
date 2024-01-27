package com.lza.android.inter.process.call

import com.lza.android.inter.process.library.component.AbstractProcessReceiver

/**
 * @author liuzhongao
 * @since 2024/1/17 11:54
 */
class MainProcessBroadcastReceiver : AbstractProcessReceiver() {

    override val broadcastRequireAction: String = ProcessConst.KEY_MAIN_PROCESS
}