package com.lza.android.inter.process.library.bridge.parameter

import android.content.Context
import com.lza.android.inter.process.library.interfaces.ProcessConnectionAdapter
import com.lza.android.inter.process.library.interfaces.ProcessIdentifier

/**
 * @author liuzhongao
 * @since 2024/1/20 01:12
 */
class ProcessCallInitConfig private constructor(
    val context: Context,
    val connectionAdapter: ProcessConnectionAdapter,
    val identifier: ProcessIdentifier,
    val connectionTimeoutMills: Long = 10_000L,
) {

    class Builder {
        private var context: Context? = null
        private var connectionAdapter: ProcessConnectionAdapter? = null
        private var identifier: ProcessIdentifier? = null
        private var connectionTimeoutMills: Long = 10_000L

        fun setContext(context: Context) = apply { this.context = context }

        fun setConnectionAdapter(connectionAdapter: ProcessConnectionAdapter) =
            apply { this.connectionAdapter = connectionAdapter }

        fun setProcessIdentifier(identifier: ProcessIdentifier) =
            apply { this.identifier = identifier }

        fun connectionTimeoutMills(connectionTimeoutMills: Long) = apply {
            this.connectionTimeoutMills = connectionTimeoutMills
        }

        fun build(): ProcessCallInitConfig {
            val context: Context = requireNotNull(this.context)
            val connectionAdapter: ProcessConnectionAdapter = requireNotNull(this.connectionAdapter)
            val identifier: ProcessIdentifier = requireNotNull(this.identifier)
            return ProcessCallInitConfig(
                context = context,
                connectionAdapter = connectionAdapter,
                identifier = identifier,
                connectionTimeoutMills = this.connectionTimeoutMills
            )
        }
    }
}
