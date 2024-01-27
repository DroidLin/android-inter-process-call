package com.lza.android.inter.process.library.bridge.parameter

import android.os.Build
import android.os.Parcel
import android.os.Parcelable

/**
 * @author liuzhongao
 * @since 2024/1/14 15:32
 */

internal class InvocationResponse(
    val responseObject: Any?,
    val throwable: Throwable?,
) : Response, Parcelable {

    constructor(parcel: Parcel) : this(
        responseObject = parcel.readValue(Any::class.java.classLoader),
        throwable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            parcel.readSerializable(InvocationRequest::class.java.classLoader, Throwable::class.java)
        } else {
            parcel.readSerializable() as? Throwable
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(this.responseObject)
        parcel.writeSerializable(this.throwable)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val serialVersionUID: Long = 4029474819295458252L

        @JvmField
        val CREATOR = object : Parcelable.Creator<InvocationResponse> {
            override fun createFromParcel(parcel: Parcel): InvocationResponse {
                return InvocationResponse(parcel)
            }

            override fun newArray(size: Int): Array<InvocationResponse?> {
                return arrayOfNulls(size)
            }
        }
    }

}
