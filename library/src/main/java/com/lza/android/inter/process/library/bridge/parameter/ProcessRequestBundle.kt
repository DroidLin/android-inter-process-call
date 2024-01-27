package com.lza.android.inter.process.library.bridge.parameter

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.lza.android.inter.process.library.ProcessCallFunction
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.interfaces.binder
import com.lza.android.inter.process.library.interfaces.bridgeInterface
import com.lza.android.inter.process.library.interfaces.rpcInterface

/**
 * @author liuzhongao
 * @since 2024/1/20 01:14
 */
data class ProcessRequestBundle internal constructor(
    internal val basicInterface: ProcessBasicInterface,
    val connectionContext: ConnectionContext,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        basicInterface = ProcessBasicInterface.asInterface(ProcessCallFunction.Stub.asInterface(requireNotNull(parcel.readStrongBinder())).rpcInterface),
        connectionContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            parcel.readSerializable(ProcessRequestBundle::class.java.classLoader, ConnectionContext::class.java) as ConnectionContext
        } else parcel.readSerializable() as ConnectionContext
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStrongBinder(this.basicInterface.bridgeInterface.binder)
        parcel.writeSerializable(this.connectionContext)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProcessRequestBundle> {
        override fun createFromParcel(parcel: Parcel): ProcessRequestBundle {
            return ProcessRequestBundle(parcel)
        }

        override fun newArray(size: Int): Array<ProcessRequestBundle?> {
            return arrayOfNulls(size)
        }
    }
}
