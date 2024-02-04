package com.lza.android.inter.process.library.bridge.parameter

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import java.io.Serializable

/**
 * basic container object for inter process call.
 *
 * @author liuzhongao
 * @since 2024/1/8 23:20
 */
internal class BridgeParameter : Parcelable, Serializable {

    internal var next: BridgeParameter? = null

    internal var map: MutableMap<String, Any?> = HashMap()
        private set

    private constructor() {
        this.map = HashMap()
    }

    private constructor(parcel: Parcel) : this() {
        this.readFromParcel(parcel)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeMap(this.map)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun readFromParcel(parcel: Parcel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.map = parcel.readHashMap(BridgeParameter::class.java.classLoader, String::class.java, Any::class.java) ?: HashMap()
        } else {
            this.map = parcel.readHashMap(BridgeParameter::class.java.classLoader) as? MutableMap<String, Any?> ?: HashMap()
        }
    }

    fun recycle() {
        this.map.clear()
        recycle(this)
    }

    companion object {
        private var bridgeParameterHead: BridgeParameter? = null

        @JvmStatic
        fun obtain(): BridgeParameter {
            synchronized(this) {
                val head = this.bridgeParameterHead
                if (head != null) {
                    this.bridgeParameterHead = head.next
                    head.next = null
                    return head
                }
            }
            return BridgeParameter()
        }

        @JvmStatic
        fun recycle(bridgeParameter: BridgeParameter) {
            synchronized(this) {
                bridgeParameter.next = this.bridgeParameterHead
                this.bridgeParameterHead = bridgeParameter
            }
        }

        private const val serialVersionUID: Long = -6434404036696064057L

        @JvmField
        val CREATOR = object : Parcelable.Creator<BridgeParameter> {
            override fun createFromParcel(parcel: Parcel): BridgeParameter {
                return BridgeParameter(parcel)
            }

            override fun newArray(size: Int): Array<BridgeParameter?> {
                return arrayOfNulls(size)
            }
        }
    }
}