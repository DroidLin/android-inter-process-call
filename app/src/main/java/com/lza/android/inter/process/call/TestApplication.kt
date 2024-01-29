package com.lza.android.inter.process.call

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.lza.android.inter.process.library.ProcessCenter
import com.lza.android.inter.process.library.bridge.parameter.ProcessCallInitConfig
import com.lza.android.inter.process.library.bridge.parameter.ProcessRequestBundle
import com.lza.android.inter.process.library.component.broadcastConnectionIntent
import com.lza.android.inter.process.library.interfaces.ProcessConnectionAdapter
import com.lza.android.inter.process.library.interfaces.ProcessIdentifier
import java.lang.ref.WeakReference

/**
 * @author liuzhongao
 * @since 2024/1/14 23:21
 */
class TestApplication : Application() {

    private val currentProcessName by lazy { this.getCurrentProcessName() }

    private val connectionAdapter = object : ProcessConnectionAdapter {
        override fun onAttachToRemote(context: Context, bundle: ProcessRequestBundle) {
            val intent = broadcastConnectionIntent(context = context, requestBundle = bundle)
            context.sendBroadcast(intent)
        }
    }

    private val processIdentifier = object : ProcessIdentifier {
        override val keyForCurrentProcess: String
            get() {
                val currentProcessName = this@TestApplication.currentProcessName
                return when {
                    currentProcessName.contains("library") -> ProcessConst.KEY_LIBRARY_PROCESS
                    else -> ProcessConst.KEY_MAIN_PROCESS
                }
            }
    }

    private val processCallInitConfig: ProcessCallInitConfig
        get() = ProcessCallInitConfig.Builder()
            .setContext(context = this)
            .setProcessIdentifier(identifier = this.processIdentifier)
            .setConnectionAdapter(connectionAdapter = this.connectionAdapter)
            .build()

    val isMainProcess: Boolean get() = !this.currentProcessName.contains(":")

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        applicationReference = WeakReference(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = this.getCurrentProcessName()
        when {
            !processName.contains(":") -> this.initMainProcess()
            processName.contains("library") -> this.initLibraryProcess()
        }
    }

    private fun initMainProcess() {
        ProcessCenter.init(initConfig = this.processCallInitConfig)
        ProcessCenter.putService(ProcessService::class.java, MainProcessServiceImpl)
    }

    private fun initLibraryProcess() {
        ProcessCenter.init(initConfig = this.processCallInitConfig)
        ProcessCenter.putService(ProcessService::class.java, LibraryProcessServiceImpl)
    }

    companion object {
        @JvmStatic
        private var applicationReference: WeakReference<TestApplication>? = null

        @JvmStatic
        val application: TestApplication get() = requireNotNull(applicationReference?.get())
    }
}

fun Context.getCurrentProcessName(): String {
    val myPid = android.os.Process.myPid()
    val activityManager: ActivityManager =
        getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
    return activityManager.runningAppProcesses?.find { it.pid == myPid }?.processName ?: ""
}