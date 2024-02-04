package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.symbol.KSName

/**
 * @author liuzhongao
 * @since 2024/2/4 20:34
 */
data class RemoteProcessInterfaceModel(
    val interfaceClassName: KSName,
    val selfImplementationClassName: KSName,
)
