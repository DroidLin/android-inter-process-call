package com.lza.android.inter.process.library.bridge.parameter

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.lza.android.inter.process.library.ProcessCallFunction
import com.lza.android.inter.process.library.interfaces.ProcessBasicInterface
import com.lza.android.inter.process.library.interfaces.RemoteProcessSuspendCallback
import com.lza.android.inter.process.library.interfaces.binder
import com.lza.android.inter.process.library.interfaces.bridgeInterface
import com.lza.android.inter.process.library.interfaces.rpcInterface
import java.io.Serial
import java.io.Serializable

/**
 * @author liuzhongao
 * @since 2024/1/12 00:23
 */

internal class ReflectionInvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val isKotlinFunction: Boolean = false,
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = parcel.readString() ?: "",
        interfaceMethodName = parcel.readString() ?: "",
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(list, ReflectionInvocationRequest::class.java.classLoader, String::class.java)
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(list, ReflectionInvocationRequest::class.java.classLoader)
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(list, ReflectionInvocationRequest::class.java.classLoader, Any::class.java)
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(list, ReflectionInvocationRequest::class.java.classLoader)
            }
        },
        isKotlinFunction = parcel.readInt() == 1
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeInt(if (this.isKotlinFunction) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -5664921035843080976L

        @JvmField
        val CREATOR = object : Parcelable.Creator<ReflectionInvocationRequest> {
            override fun createFromParcel(parcel: Parcel): ReflectionInvocationRequest {
                return ReflectionInvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<ReflectionInvocationRequest?> {
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

internal data class ReflectionSuspendInvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val remoteProcessSuspendCallback: RemoteProcessSuspendCallback
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = parcel.readString() ?: "",
        interfaceMethodName = parcel.readString() ?: "",
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    ReflectionSuspendInvocationRequest::class.java.classLoader,
                    String::class.java
                )
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    ReflectionSuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    ReflectionSuspendInvocationRequest::class.java.classLoader,
                    Any::class.java
                )
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    ReflectionSuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        remoteProcessSuspendCallback = RemoteProcessSuspendCallback.asInterface(
            parcel.readStrongBinder()
        )
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeStrongBinder(this.remoteProcessSuspendCallback.remoteProcessCallInterface.binder)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -5664921035843080976L

        @JvmField
        val CREATOR = object : Parcelable.Creator<ReflectionSuspendInvocationRequest> {
            override fun createFromParcel(parcel: Parcel): ReflectionSuspendInvocationRequest {
                return ReflectionSuspendInvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<ReflectionSuspendInvocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}

internal data class DirectInvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val isKotlinFunction: Boolean = false,
) : Request, Parcelable, Serializable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = requireNotNull(parcel.readString()),
        interfaceMethodName = requireNotNull(parcel.readString()),
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(list, DirectInvocationRequest::class.java.classLoader, String::class.java)
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(list, DirectInvocationRequest::class.java.classLoader)
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(list, DirectInvocationRequest::class.java.classLoader, Any::class.java)
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(list, DirectInvocationRequest::class.java.classLoader)
            }
        },
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeByte(if (this.isKotlinFunction) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -4844349218410633780L

        @JvmField
        val CREATOR = object : Parcelable.Creator<DirectInvocationRequest> {
            override fun createFromParcel(parcel: Parcel): DirectInvocationRequest {
                return DirectInvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<DirectInvocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}

internal data class DirectSuspendInvocationRequest(
    val interfaceClassName: String,
    val interfaceMethodName: String,
    val interfaceParameterTypes: List<String>,
    val interfaceParameters: List<Any?>,
    val remoteProcessSuspendCallback: RemoteProcessSuspendCallback
) : Request, Parcelable {

    constructor(parcel: Parcel) : this(
        interfaceClassName = parcel.readString() ?: "",
        interfaceMethodName = parcel.readString() ?: "",
        interfaceParameterTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    DirectSuspendInvocationRequest::class.java.classLoader,
                    String::class.java
                )
            }
        } else {
            ArrayList<String>().also { list ->
                parcel.readList(
                    list,
                    DirectSuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        interfaceParameters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    DirectSuspendInvocationRequest::class.java.classLoader,
                    Any::class.java
                )
            }
        } else {
            ArrayList<Any>().also { list ->
                parcel.readList(
                    list,
                    DirectSuspendInvocationRequest::class.java.classLoader
                )
            }
        },
        remoteProcessSuspendCallback = RemoteProcessSuspendCallback.asInterface(
            parcel.readStrongBinder()
        )
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.interfaceClassName)
        parcel.writeString(this.interfaceMethodName)
        parcel.writeList(this.interfaceParameterTypes)
        parcel.writeList(this.interfaceParameters)
        parcel.writeStrongBinder(this.remoteProcessSuspendCallback.remoteProcessCallInterface.binder)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = -5664921035843080976L

        @JvmField
        val CREATOR = object : Parcelable.Creator<DirectSuspendInvocationRequest> {
            override fun createFromParcel(parcel: Parcel): DirectSuspendInvocationRequest {
                return DirectSuspendInvocationRequest(parcel)
            }

            override fun newArray(size: Int): Array<DirectSuspendInvocationRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}
