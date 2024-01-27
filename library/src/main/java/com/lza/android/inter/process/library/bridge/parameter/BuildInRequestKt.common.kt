package com.lza.android.inter.process.library.bridge.parameter

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.lza.android.inter.process.library.ProcessCallFunction
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.interfaces.RemoteProcessSuspendCallback
import com.lza.android.inter.process.library.interfaces.bridgeInterface
import com.lza.android.library.interfaces.binder
import com.lza.android.library.interfaces.rpcInterface

/**
 * @author liuzhongao
 * @since 2024/1/12 00:23
 */

internal class InvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val isSuspendFunction: Boolean = false,
    val isKotlinFunction: Boolean = false,
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = parcel.readString() ?: "",
        interfaceMethodName = parcel.readString() ?: "",
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    InvocationRequest::class.java.classLoader,
                    String::class.java
                )
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    InvocationRequest::class.java.classLoader
                )
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    InvocationRequest::class.java.classLoader,
                    Any::class.java
                )
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    InvocationRequest::class.java.classLoader
                )
            }
        },
        isSuspendFunction = parcel.readInt() == 1,
        isKotlinFunction = parcel.readInt() == 1
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeInt(if (this.isSuspendFunction) 1 else 0)
        parcel.writeInt(if (this.isKotlinFunction) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -5664921035843080976L

        @JvmField
        val CREATOR = object : Parcelable.Creator<InvocationRequest> {
            override fun createFromParcel(parcel: Parcel): InvocationRequest {
                return InvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<InvocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}

internal data class HandShakeRequest(
    val processKey: String,
    val basicInterface: ProcessBasicInterface
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        processKey = parcel.readString() ?: "",
        basicInterface = ProcessBasicInterface.asInterface(
            bridgeInterface = ProcessCallFunction.Stub.asInterface(
                parcel.readStrongBinder()
            ).rpcInterface
        )
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.processKey)
        parcel.writeStrongBinder(this.basicInterface.bridgeInterface.binder)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {

        private const val serialVersionUID: Long = 1857002366830839274L

        @JvmField
        val CREATOR = object : Parcelable.Creator<HandShakeRequest> {
            override fun createFromParcel(parcel: Parcel): HandShakeRequest {
                return HandShakeRequest(parcel)
            }

            override fun newArray(size: Int): Array<HandShakeRequest?> {
                return arrayOfNulls(size)
            }
        }
    }

}

internal data class CallbackRequest(
    val data: Any?,
    val throwable: Throwable?
) : Request, Parcelable {
    constructor(parcel: Parcel) : this(
        data = parcel.readValue(CallbackRequest::class.java.classLoader),
        throwable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            parcel.readSerializable(CallbackRequest::class.java.classLoader, Throwable::class.java)
        } else {
            parcel.readSerializable() as? Throwable
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(this.data)
        parcel.writeSerializable(this.throwable)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = 7641505167833759715L

        @JvmField
        val CREATOR = object : Parcelable.Creator<CallbackRequest> {
            override fun createFromParcel(parcel: Parcel): CallbackRequest {
                return CallbackRequest(parcel)
            }

            override fun newArray(size: Int): Array<CallbackRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}

internal class SuspendInvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val isKotlinFunction: Boolean = false,
    val remoteProcessSuspendCallback: RemoteProcessSuspendCallback
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = parcel.readString() ?: "",
        interfaceMethodName = parcel.readString() ?: "",
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    SuspendInvocationRequest::class.java.classLoader,
                    String::class.java
                )
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    SuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    SuspendInvocationRequest::class.java.classLoader,
                    Any::class.java
                )
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    SuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        isKotlinFunction = parcel.readInt() == 1,
        remoteProcessSuspendCallback = RemoteProcessSuspendCallback.asInterface(
            remoteProcessCallInterface = ProcessCallFunction.Stub.asInterface(
                parcel.readStrongBinder()
            ).rpcInterface
        )
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeInt(if (this.isKotlinFunction) 1 else 0)
        parcel.writeStrongBinder(this.remoteProcessSuspendCallback.remoteProcessCallInterface.binder)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -5664921035843080976L

        @JvmField
        val CREATOR = object : Parcelable.Creator<SuspendInvocationRequest> {
            override fun createFromParcel(parcel: Parcel): SuspendInvocationRequest {
                return SuspendInvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<SuspendInvocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}